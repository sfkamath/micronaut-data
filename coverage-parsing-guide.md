# Parsing JaCoCo coverage & jvm-hotpath reports

Reusable recipes for `data-nitrite`. Run from the repo root. Both reports are
produced by the gradle tasks already wired into `data-nitrite/build.gradle`.

## 1. Generate the reports

```sh
# JaCoCo: merged HTML + XML across test, rocksDbPresentTest, spatialPresentTest
./gradlew :micronaut-data-nitrite:coverageReport
#   -> data-nitrite/build/reports/jacoco/jacoco.xml   (parse this — stable, no cd needed)
#   -> data-nitrite/build/reports/jacoco/index.html

# jvm-hotpath: per-line execution counts
./gradlew :micronaut-data-nitrite:test
#   -> data-nitrite/build/jvm-hotpath/execution-report.json
```

Parse the **XML**, not the HTML. The HTML is split into one file per package
directory (the source of the old `no such file or directory` error); the XML is
a single file with the same data and stable element names.

## 2. JaCoCo — per-class instruction & branch coverage

```sh
python3 - <<'EOF'
import re
doc = open('data-nitrite/build/reports/jacoco/jacoco.xml').read()
# Edit this list to the classes you care about (simple names).
targets = ['NitritePredicateVisitor', 'CollectionProjectionMapper', 'SpatialFilterFactory']
for m in re.finditer(r'<class name="([^"]+)"[^>]*>(.*?)</class>', doc, re.S):
    simple = m.group(1).split('/')[-1]
    if simple not in targets:
        continue
    # class-level totals are the LAST counters in the block (after all methods)
    ic = re.findall(r'<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"', m.group(2))
    mi, ci = (int(ic[-1][0]), int(ic[-1][1])) if ic else (0, 0)
    tot = mi + ci
    pct = round(100 * ci / tot) if tot else 0
    print(f"{simple:42} instr {pct:3}% (missed {mi}/{tot})")
EOF
```

JaCoCo XML counter `type`s: `INSTRUCTION`, `BRANCH`, `LINE`, `METHOD`, `CLASS`.
Drop the `targets` filter to dump the whole module.

## 3. JaCoCo — per-method gaps within one class

Find the least-covered methods to target tests precisely:

```sh
python3 - <<'EOF'
import re
doc = open('data-nitrite/build/reports/jacoco/jacoco.xml').read()
cls = 'NitritePredicateVisitor'
block = re.search(r'<class name="[^"]*'+cls+r'"[^>]*>(.*?)</class>', doc, re.S).group(1)
rows = []
for m in re.finditer(r'<method name="([^"]+)"[^>]*>(.*?)</method>', block, re.S):
    ic = re.search(r'<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"', m.group(2))
    if not ic:
        continue
    mi, ci = int(ic.group(1)), int(ic.group(2)); tot = mi + ci
    rows.append((round(100*ci/tot) if tot else 0, mi, m.group(1)))
for pct, missed, name in sorted(rows):
    print(f"{pct:3}%  missed {missed:3}  {name}")
EOF
```

## 4. jvm-hotpath — hottest lines

The JSON is `{files: [{path, counts: {"<line>": <hits>, ...}}]}`.

```sh
R=data-nitrite/build/jvm-hotpath/execution-report.json

# Top 20 hottest lines across all instrumented files
jq -r '[.files[] | select((.counts|length)>0)
        | {p:.path, c:(.counts|to_entries)}
        | .c[] as $c | {file:.p, line:($c.key|tonumber), hits:$c.value}]
       | sort_by(.hits) | reverse | .[0:20][]
       | "\(.hits)\t\(.file):\(.line)"' "$R"

# Hot lines for one file only
jq -r --arg f PredicateVisitor '.files[] | select(.path|test($f))
       | .path as $p | (.counts|to_entries[]) | "\(.value)\t\($p):\(.key)"' "$R" \
  | sort -rn | head
```

`select((.counts|length)>0)` skips files that were loaded but never executed.
`io.micronaut.data.nitrite` is our code; `org.dizitart.no2` frames are the
embedded Nitrite engine (instrumented via the source JARs configured in
`jvmHotpath { sourcepath }`).

## 5. Pitfalls — read this before trusting any coverage number

These are the mistakes that have actually burned us. Each one wasted a round
trip. Validate against the XML yourself; do not trust a tool's self-reported
percentage.

### 5.1 Never trust a stat you did not parse from the XML

Tool summaries have reported numbers that the XML flatly contradicts —
`NitriteFilterAST` "8% branch" when the class has **zero** `BRANCH` counters,
`NitriteQueryBuilderHelper` "93%" when the XML rounds to 94%. Re-run the
script in §2/§3 and read the raw `missed`/`covered` before reporting a delta.
"Coverage hasn't moved" from a tool means nothing until the XML confirms it.

### 5.2 A class with no `BRANCH` counter has no branch coverage — it is n/a, not 0%

If `<class>` emits no `<counter type="BRANCH">`, the class has no branches.
`NitriteFilterAST` is a 5-instruction shell — instruction 100%, branch **n/a**.
Do not compute or report a branch percentage for it. The §2 script already
guards this (`if bc else`); keep that guard.

### 5.3 "Missed" on a runtime class ≠ "this code is compile-only / dead"

The biggest false assumption. A `visit*` method on `NitritePredicateVisitor`,
or a builder method, showing 0% does **not** mean it is annotation-processor
(AP) code that belongs in the `compile` package.

- The AP runs at **build time**, outside the JaCoCo agent. AP-only code is
  therefore invisible to coverage — it reports as missed *regardless* of how
  well exercised it is. So "missed" can never prove "AP-only."
- Conversely, the genuinely AP-only logic (literal inlining, `Pattern.quote`,
  `convertLikeToRegex`) already lives behind `CompileExpressionHandler` in the
  `...builder.compile` package. The visitor body is handler-agnostic and runs
  identically at runtime via `JpaSpecificationExecutor` / in-process criteria.
- Verification gate (see `NITRITE-REFACTOR-ANALYSIS.md`): comment the
  `exclude '.../compile/**'` in `coverageReport` back in and confirm
  `compile.**` shows **0** runtime coverage. Non-zero there = misclassified
  runtime code that must move out of `compile`. Coverage *on* the visitor is
  expected and correct — it confirms the class is runtime code.

Decision rule: to lift a runtime class's coverage, **write a runtime criteria
test** (§5.4). Do not move the code to `compile` to make the number go away.

### 5.4 Instrument the visitor/builder at runtime with the in-process build string

Derived / `@Query` repository queries are compiled to JSON by the AP at build
time — uninstrumented. Only criteria-builder queries run the visitor under the
JaCoCo agent. To exercise a `visit*` path inside the test JVM (so it counts),
build the query string in-process and assert the JSON — no DB round trip:

```groovy
PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
def root = query.from(SomeEntity)
query.where(criteriaBuilder.equal(root.get("name"), criteriaBuilder.literal("x")))
// EMPTY_METADATA -> selects RuntimeExpressionHandler, runs the visitor here:
String json = query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
expect:
    json.contains('"$eq"')
```

`AnnotationMetadata.EMPTY_METADATA` is what flips `NitriteQueryBuilder` to the
runtime handler. A round-trip `repository.find(...)` is the wrong tool here —
it can return `Optional.empty()` for shapes the doc store does not persist as
queried (e.g. composite `@JoinColumn` joins) and proves nothing about the
builder path.

### 5.5 When coverage will not move, check these before assuming dead code

Real root causes that each looked like "the test doesn't work":

- **Join not referenced in the predicate.** `addLookups` is driven by
  `query.getJoinPaths()`; a join only registers if a property of the joined
  entity appears in `where`. Use `cb.equal(join.get("x"), …)`, not
  `root.get("y")`.
- **Wrong annotation type read.** The helper reads
  `io.micronaut.data.annotation.sql.JoinColumn`, not the `jakarta` one — the AP
  remaps jakarta → sql per value. Fixtures must use the type the code reads.
- **Repeatable container has no mapper.** The jakarta `@JoinColumns` container
  is not remapped; use Micronaut-native `@JoinColumns`/`@JoinColumn` in
  fixtures so the values reach `getAnnotationValuesByType`.

### 5.6 Class-level counter is the LAST counter in the `<class>`, not the first

JaCoCo emits per-`<method>` counters first, then the class summary counters at
the end of the `<class>` element. A naive `re.search(... INSTRUCTION ...)` over
the class block returns the **first method's** counter, not the class total —
e.g. `NitritePredicateVisitor` reads as "100% (0/16)" instead of the real
"86% (151/1104)". Always take the **last** match:

```python
ic = re.findall(r'<counter type="INSTRUCTION" missed="(\d+)" covered="(\d+)"', block)[-1]
```

The §2 and §3 scripts above are correct (§3 scopes to one `<method>` so its
counter is unambiguous); only ad-hoc one-off parses fall into this trap.

### 5.7 `@EmbeddedId` is NOT composite identity

`hasCompositeIdentity()` is true only for **multiple `@Id` properties**. An
`@EmbeddedId` (e.g. `Settlement`/`SettlementPk`) is a *single* identity whose
value is an embedded object — `hasIdentity()` true, `hasCompositeIdentity()`
false. To exercise the `visitIdEquals` composite-ID throw, use a fixture with
two bare `@Id` fields (`CompositeIdEntity`), not an `@EmbeddedId` entity.

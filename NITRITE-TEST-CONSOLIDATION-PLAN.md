# Nitrite Test Consolidation — Plan & Handoff

**Branch:** `benchmark-5.0.x-new`. **Executor:** Sonnet, phased. **Reviewer/verifier:** human + this plan.
Authoritative working doc — pick up here after compaction or in a new session.

## Goal

Fold redundant tests in the `data-nitrite` module without losing behavioral coverage.
~582 module tests; testwise reports **498 own zero exclusively-owned lines**. That is a
*candidate* list, NOT a delete list (see the line≠behavior rule below).

## HARD DIRECTIVES (user — non-negotiable)

1. **TCK tests: KEEP. Never fold, move, or delete.** (`micronaut-data-tck`, `micronaut-data-document-tck`.)
2. **mongoport tests** (`io.micronaut.data.nitrite.mongoport.*`) were ported from
   micronaut-data-mongodb as a trustworthy reference baseline ("immune to wrong tests").
   The **pristine port lives on branch `mongo-tests-ported`**. On the working branch the port
   commit (`e3fc49e67`) may already mix port + extensions, so **diff against `mongo-tests-ported`,
   not against that commit**, to isolate true extensions.
3. mongoport ports belong **rolled into mainline module tests, NOT kept as a separate TCK-like suite.**
4. **Move-and-keep ALL distinct assertions.** A test that is line-redundant but asserts a
   behavior mainline lacks is MOVED to mainline, never dropped — even when it owns zero unique lines.
5. **Keep packages aligned with `main`.** When relocating tests, mirror the package structure used on
   the main branch / upstream mongo test layout — do not invent new package names or flatten
   arbitrarily. Keeps diffs against `mongo-tests-ported` and upstream clean.

## THE CORE RULE: line-redundant ≠ behavior-redundant

testwise `unique_lines=0` means every line a test touches is also touched by another test.
It does NOT mean the test is behaviorally redundant. Worked example (proven):

`mongoport/NitriteCriteriaSpec` rejects `prod/sum/diff/lower/upper/length` operator-expressions —
all 6 throw through ONE shared line in `NitriteQueryBuilder`, so 5 of them are zero-unique by
testwise. But each asserts a *distinct operator + message*. Mainline only covers `prod` and
`length` (in `NitriteQueryBuilderRegressionSpec`). So:
- `prod`, `length` → mainline already asserts → **DROP** from mongoport.
- `sum`, `diff`, `lower`, `upper` → no mainline equivalent → **MOVE** to mainline (keep assertion).

**A pure delta loop would over-delete `sum/diff/lower/upper` (zero delta on drop). Do not trust
the delta loop alone — gate every drop with "does a mainline test assert this same behavior?"**

## NAMING & PACKAGE CONVENTION (user — applies to all migrated/touched specs)

**Harness split, encoded in the name (binary, no third bucket):**
- **`*Spec`** = `@MicronautTest` integration (real beans + Nitrite DB). Any spec that needs a
  Micronaut context uses `@MicronautTest` — manual-context wiring (programmatic ApplicationContext/
  bean lookup without the annotation) is **migrated to `@MicronautTest`**, NOT given a separate suffix.
- **`*UnitSpec`** = zero-context, pure-construction (`new RuntimeCriteriaBuilder()`, `new` prod class,
  static utils) + `createRoot`/`getQuery`-style asserts. Never touches a context.

**Package = production area under test (production-mirror):**
- query/criteria building & encoding → `io.micronaut.data.nitrite.model.query.builder`
- runtime persistence/ops/cascade → `io.micronaut.data.nitrite.runtime`
- mapping → `io.micronaut.data.nitrite.runtime.mapping`; query parse/binder → `...runtime.query`

Applied so far: 10 zero-context specs renamed to `*UnitSpec` (NitriteCriteriaUnitSpec,
CompileExpressionHandlerUnitSpec, ValueConverterUnitSpec, NameUtilsUnitSpec,
NitriteTypeRegistryUnitSpec, CollectionAggregatorUnitSpec, NitriteQueryParserUnitSpec,
SpatialFilterAbsentUnitSpec, ValueResolverUnitSpec, PatternConverterUnitSpec). Phase 3 association
specs land in `runtime.*` as `*Spec` (integration). Outstanding: 13 manual-context specs still need
conversion to `@MicronautTest` (separate behavior-preserving task; names already correct as `*Spec`).

## THE ENVELOPE METHOD (preferred — measured 2026-06-14, supersedes blind per-test classify)

Instead of classifying 37+ tests one-by-one, measure what ALL of mongoport uniquely covers in
ONE bulk-disable, then migrate only that. Mechanism committed as a property-gated filter on
`coverageTest` (build.gradle): `-PexcludeMongoport` excludes `io.micronaut.data.nitrite.mongoport.*`.

Loop:
1. Full baseline: `./gradlew :micronaut-data-nitrite:coverageReport --rerun-tasks` → `refresh_coverage`.
2. Excluded: `./gradlew :micronaut-data-nitrite:coverageReport --rerun-tasks -PexcludeMongoport` → `refresh_coverage`.
3. `diff_snapshots baseId=<excluded> targetId=<full>` → "improved" methods = the mongoport envelope.
   (diff is large; slice the saved tool-result file with python, filter base_pct==0 vs >0.)

**Pre-check (done): zero real code deps.** No non-mongoport test imports a mongoport entity/repo
(only a comment in `NitriteQueryBuilderSpec:341`). Bulk-disable is compile-safe; step "migrate
shared entities first" is a no-op for safety, BUT when a mongoport spec is finally DELETED its
entities/repositories in `mongoport/entities` + `mongoport/repositories` become orphans — relocate
or delete them with the spec (keep packages aligned with main).

### MEASURED ENVELOPE (snapshots: 83=full, 84=excluded; HEAD b1ffe334e5; 931 tests → 766 excl)

Only **31 production methods** ride on ALL of mongoport (0 regressions). The "498 zero-unique-line
tests" candidate list is mostly noise — real exclusive behavior is small + concrete.

**13 methods drop to ZERO when mongoport excluded (covered NOWHERE else — hard must-migrate):**
- `NitritePredicateVisitor` ×8: `visit`, `visitIdEquals`, `visitIsEmpty`(+λ), `visitIsNotEmpty`(+λ),
  `visitLessThanOrEquals`, `getRequiredProperty` — all **NitriteCriteriaSpec** (criteria visitor).
- `NitriteQueryBuilderHelper.lookup` + `lambda$addLookups$0` — NitriteCriteriaSpec.
- `DefaultNitriteRepositoryOperations.persistManyAssociationBatch` — ManyToMany/OneToMany specs.
- `NitriteEntityMapper.lambda$idEqualsFilter$0` — Composite/EmbeddedId specs.
- `NitriteQueryBuilder$1.getUpdate` (0 lines, anon-class method).

**18 methods PARTIAL (mainline covers; mongoport adds branches = line≠behavior residue, assertion-gate):**
`appendOperatorExpression` 0.43→1.0 (sum/diff/lower/upper operators), `visitEquals`/`visitNotEquals`
0.75→1.0, `getFieldNameForNullCheck` 0.13→1.0, `NitriteFieldNameResolver.asPath` 0.25→1.0 /`resolve`
0.44→0.86, `convertAssociation`, `buildProjection`, `visitSinglePredicate`, `fromDocumentInternal`,
`EntityOperations.execute`, `isAssociationStoredEmbedded`, `persistNewCascadeChildren`,
`buildEntityMeta`, `extractPropertyFromSingleArg`, `buildNestedFilter`, `JoinFetcher.fetchSingleLevel`,
`addLookups`.

**Consequence for execution:** migration target = keep these 31 methods ≥ current pct with mongoport
gone. Any mongoport test NOT touching one of the 31 = safe wholesale DELETE (no migration needed).
NitriteCriteriaSpec owns the bulk (8 of 13 zero + most partials) → Phase 1, highest value, NOT a dup.
Association specs (ManyToMany/OneToMany/Composite/EmbeddedId) own real exclusive methods → Phase 3
must MIGRATE, not delete.

### RECOVERY-DRIVEN MIGRATION (replaces the per-test delta loop for the bulk)
After migrating a cluster into mainline: re-run the excluded build + diff. A migrated method should
NO LONGER appear in the envelope (mainline now covers it). When the envelope is empty, every
remaining mongoport test is provably redundant → delete the lot. Shared-line residue (the 18
partials, esp. operator branches) still needs the assertion-gate below — bulk drop can't see them.

## FOLD DECISION (per redundant mongoport test)

```
is the asserted behavior already covered by a MAINLINE test?
  ├─ yes → DROP the mongoport test
  └─ no  → MOVE it to the mainline spec (adapt harness if needed), keep the assertion
```
"Covered by mainline" = a non-mongoport, non-TCK test asserts the same behavior/branch. Confirm with
`get_tests_covering_method` (which tests cover the production method/branch) + read the assertion.

## FOLD DESTINATIONS

- Criteria / query-builder extensions → **`NitriteQueryBuilderSpec`** — it already has the
  in-process criteria harness (`createRoot` / `getQuery` / `PersistentEntityCriteriaBuilder`) the
  mongoport criteria extensions depend on.
- Operator-rejection / regression-style → **`NitriteQueryBuilderRegressionSpec`**
  (renamed this session from `QueryBuilder2BugSpec` — long-lived regression suite; `prod`/`length`
  rejection already live there, add the missing `sum`/`diff`/`lower`/`upper`).
- CRUD / repository behavior → the matching `*RepositorySpec` mainline (e.g. `PersonRepositorySpec`,
  `DocumentRepositorySpec`).

**HARNESS MISMATCH (critical).** The mongoport criteria spec and the mainline specs use DIFFERENT
in-process harnesses — they are NOT copy-paste compatible:
- `mongoport/NitriteCriteriaSpec`: `createRoot(criteriaQuery)` / `getQuery(criteriaQuery)` /
  `criteriaBuilder` + `criteriaQuery` fields.
- `NitriteQueryBuilderSpec`: `runtimeEntityRegistry` + inline `(PersistentEntityCriteriaBuilder) cb` casts.
- `NitriteQueryBuilderRegressionSpec`: repository + `PredicateSpecification` (repo-style, e.g.
  `eventRepository.findAll({ root, cb -> cb.prod(...) })`).
Moving a test = REWRITE to the destination's idiom, not copy. For operator-rejection the repo-style
in the regression spec is the lowest-friction target (`prod`/`length` already there as the template).
Verify the rewrite hits the SAME production throw (same exception type + message) via the delta loop.

## TESTWISE OVER MCP — exact loop

Per-test coverage is wired via the committed `jvmCoverageMCPTest` Gradle task
(commit `0628066c24`; JaCoCo pinned 0.8.14 — a version skew silently writes zero exec files).

1. Make ONE change (drop a cluster, or move a cluster to mainline).
2. Run BOTH:
   - `./gradlew :micronaut-data-nitrite:jvmCoverageMCPTest` (per-test exec → `build/jvm-coverage/testwise/`)
   - `./gradlew :micronaut-data-nitrite:coverageReport --rerun-tasks` (full line/branch report)
   Both must stay green.
3. `refresh_coverage` (jvm-coverage-mcp) — ingests both the merged report and testwise data.
4. `diff_snapshots baseId=<prev> targetId=<new>` filtered to the affected production class.
   **Required outcome for a DROP: zero line AND zero branch delta.** Non-zero → the cluster owned
   coverage → it was NOT a pure dup → revert the drop and MOVE instead.
   For a MOVE: net delta across the whole module must be zero (coverage relocated, not lost).
5. MCP tools available: `get_redundant_tests`, `get_test_uniqueness` (redundantOnly=true),
   `get_tests_covering_method`, `diff_snapshots`, `refresh_coverage`, `get_method_coverage`.
   Never read jacoco XML directly.

**NOTE — testwise is a separate task BY DESIGN.** `coverageReport` does NOT regenerate per-test exec
(only `jvmCoverageMCPTest` does) so normal/coverage builds are not slowed by sequential testwise
collection. Consequence: after a fold, `coverageReport`+`refresh_coverage` gives current LINE/BRANCH
(the correctness gate) but STALE testwise/method-grain data. Re-run `jvmCoverageMCPTest` only when you
need fresh redundancy/method-grain data for the NEXT classification — not for verifying the current fold.

**Baseline snapshot at handoff: 81** (testwise, 582 tests). Re-baseline to your newest snapshot
between kept changes (diff against the immediately-prior snapshot, never a stale one).
**Stale-base trap:** if a baseline snapshot was captured on a different commit, its diff reads
everything 0→1 (line_offset mismatch) — useless. Verify via the TARGET absolute (post-fold method at
100% branch + suite green), not the broken delta.

## WORKLIST

Extended mongoport specs (diff vs `mongo-tests-ported` to isolate extensions), most-extended first:

| spec | post-port commits | redundant | phase |
|---|---|---|---|
| `mongoport/NitriteCriteriaSpec` | 11 | 34 | **1 (reference, in progress)** |
| `mongoport/NitriteDocumentRepositorySpec` | 3 | 36 | 2 |
| `mongoport/NitriteSortSpec` | 2 | — | 3 |
| `mongoport/NitriteOneToOneSpec` | 2 | — | 3 |
| `mongoport/NitriteOneToManySpec` | 2 | — | 3 |
| `mongoport/NitriteMultiOneToManySpec` | 2 | — | 3 |
| `mongoport/NitriteManyToManySpec` | 2 | — | 3 |
| `mongoport/NitriteEmbeddedIdSpec` | 2 | — | 3 |

Pure ports (1 commit, untouched since port) — Phase 4, lower risk, mostly DROP-if-mainline-covers:
`NitriteUpsertSpec, NitriteRuntimeSpec, NitriteProjectionSpec, NitriteManyToOneSpec,
NitriteJsonViewSpec, NitriteIdsSpec, NitriteEmbeddedSpec, NitriteDocumentTransactionSpec,
NitriteCustomStorageSpec, NitriteCompositeSpec, NitriteMultipleDataSourceSpec`.

NON-mongoport redundancy (separate effort, after mongoport): `PersonRepositorySpec` (80 redundant —
likely TCK-style distinct finders, treat as MOVE/keep, high caution), `NitriteQueryParserSpec` (27).

## PHASES & GATES

- **Phase 1 — `NitriteCriteriaSpec` (reference fold).** Diff vs `mongo-tests-ported`. Classify all
  37 extensions drop/move. Execute operator cluster first (proven). Verify via the testwise loop.
  Commit. This is the template every later phase copies.
- **Phase 2 — `NitriteDocumentRepositorySpec`.**
- **Phase 3 — the six ×2 extended specs.**
- **Phase 4 — pure ports.**
- Each phase: one spec, one commit, full testwise loop green, zero net coverage delta, distinct
  assertions preserved. No commit without explicit human approval (standing project rule).

## CONSTRAINTS (standing)

No Mockito / no Spock `Mock`/`Spy`/`Stub`. Real beans + real instances. No private reflection.
Surgical edits. Commit only with explicit permission. See memory [[nitrite-test-consolidation]],
[[nitrite-coverage-state]].

## PHASE 1 RESULT (NitriteCriteriaSpec) — DONE via RELOCATION

Clean-harness-split (user directive): @MicronautTest stays one spec, pure-unit (`RuntimeCriteriaBuilder`,
no context) is another. NitriteCriteriaSpec was ALREADY entirely unit-harness, so the "fold into
NitriteQueryBuilderSpec" idea was wrong — correct move was **relocate it wholesale to mainline**.

Executed:
1. Migrated orphan entity set `NitriteTestEntity`→`NitriteOtherEntity`→`NitriteSimpleEntity`
   (closed graph) from `mongoport/entities` → `io.micronaut.data.nitrite.model` (java). Explicit
   `@MappedEntity` names ⇒ collection/query strings unchanged. Fixed imports in 3 mongoport repos +
   the criteria spec.
2. `git mv` `NitriteQueryBuilderSpec` + `NitriteQueryBuilderRegressionSpec` (flat) →
   `io.micronaut.data.nitrite.model.query.builder` (production-mirror, joins CompileExpressionHandlerSpec).
3. `git mv` `NitriteCriteriaSpec` `mongoport` → `model.query.builder` (de-mongoported; now the
   dedicated **unit** criteria spec). NitriteQueryBuilderSpec stays the @MicronautTest integration spec.

Measured (full baseline 83 vs excluded 85): **envelope 31 → 11.** Relocating NitriteCriteriaSpec
recovered **20 methods** (all 8 NitritePredicateVisitor exclusives + lookup/addLookups + every criteria
partial). Zero coverage lost. Excluded build green (881 tests).

Remaining **11 envelope methods are association/persistence/mapping** owned by other mongoport specs
(ManyToMany/OneToMany/Composite/EmbeddedId/Embedded) → Phase 3. The 2 still-zero:
`persistManyAssociationBatch`, `idEqualsFilter$0`.

Dedupe note: NitriteCriteriaSpec proved ADDITIVE (20 exclusive methods) → minimal overlap with
NitriteQueryBuilderSpec; within-mainline dedupe deferred, needs fresh jvmCoverageMCPTest, do NOT
delete blind (line≠behavior). Orphan mongoport repos (NitriteTest/Other/SimpleRepository) now point
at moved entities; delete with mongoport at the end if unused.

UNCOMMITTED at this point: all the above moves + `-PexcludeMongoport` build.gradle flag. Snapshots:
83 full baseline, 85 excluded post-relocation.

## PHASE 3a RESULT (ManyToMany + OneToMany) — DONE

Moved (all self-contained, no external users): specs → `runtime` (`*Spec`, @MicronautTest);
entities `NitriteMtmCourse`/`NitriteMtmStudent`/`NitriteOtoChild`/`NitriteOtoParent` → `model`;
repos `NitriteMtmStudentRepository`/`NitriteOtoParentRepository` → `repository`.

Gotcha: both specs `implements NitriteTestPropertyProvider` — a shared mongoport TRAIT (in-memory
Nitrite config, 17 users: 15 still-mongoport + these 2). To avoid double-editing the 15 specs that
will themselves migrate later, the trait STAYS in mongoport for now and the 2 migrated specs import it
qualified. **NitriteTestPropertyProvider migrates LAST**, when mongoport is nearly empty.

Measured (full 88 vs excluded 87): **envelope 11 → 9.** Recovered `persistManyAssociationBatch`
(was zero) + `fetchSingleLevel`. Full build green: 931 tests. New full baseline = snapshot 88.

Remaining 9: 1 zero = `idEqualsFilter$0` (Composite/EmbeddedId → next). Rest are association/mapping
partials owned by OneToOne/MultiOneToMany/ManyToOne/Composite/EmbeddedId/Embedded/Runtime/DocumentRepository.

## PHASE 3b RESULT (Composite + EmbeddedId) — DONE

Same pattern as 3a (self-contained). Specs → `runtime`; entities `NitriteItemGroup`/`NitriteProject`/
`NitriteProjectId`/`NitriteShipment`/`NitriteShipmentId` → `model`; repos (ItemGroup/Project/Shipment)
→ `repository`; trait imported qualified.

Measured (full 90 vs excluded 89): **envelope 9 → 7.** Recovered `idEqualsFilter$0` (last zero-method)
+ `extractPropertyFromSingleArg`. Full build green: 931. New baseline = snapshot 90.

**No zero-methods remain.** The 7 remaining are all PARTIALS (mainline already covers; mongoport adds
branches = line≠behavior residue): `convertAssociation`, `fromDocumentInternal`, `EntityOperations.execute`,
`isAssociationStoredEmbedded`, `persistNewCascadeChildren`, `buildEntityMeta`, `buildNestedFilter`.
Owned by remaining mongoport: OneToOne, MultiOneToMany, ManyToOne, Embedded, Runtime, DocumentRepository.
From here the migrate-and-recover loop continues; once these specs are relocated, the residual partials
either recover or prove to be assertion-gated line≠behavior cases (move the distinct assertion, never
delete blind).

## PHASE 2 RESULT (NitriteDocumentRepositorySpec) — DONE via RELOCATION (Gemini deletion reverted)

Gemini had DELETED this spec (committed entity/repo removal `0312c01a4f` + staged spec deletion),
claiming redundant. Its verification was INVALID: the spec is a mongoport spec, so the
`-PexcludeMongoport` build excludes it regardless of deletion — "897 excluded green" is identical with
or without it and cannot detect loss. Numbers also stale ("31 methods"; it's 7).

Reviewed properly: full-with-spec (96) vs full-without (99) = ZERO coverage delta — so the 12 tests own
zero unique LINES. BUT several assert distinct query/mapping BEHAVIORS (not-like-parameterized,
array-contains, map-of-objects, paginated find, criteria-IN, multi-in-params) — line-redundant ≠
behavior-redundant. Per the move-and-keep directive: RELOCATE, don't delete.

Hard-reset to 3e (`7de4f30bec`), then relocated like every other phase: spec → `runtime`; entities
`NitriteDocument`/`NitriteDocumentOwner` → `model`; repo `NitriteDocumentEntityRepository` →
`repository` (incl. its static `Specifications.tagsArrayContains` import); trait qualified. Full build
green: 931. Coverage identical to baseline 96 (zero delta, snapshot 100). All 12 tests preserved.

## DONE THIS SESSION

- testwise wiring committed (`0628066c24`).
- `QueryBuilder2BugSpec` → `NitriteQueryBuilderRegressionSpec` (rename, uncommitted until phase 1).
- Method + policy proven on the `NitriteCriteriaSpec` operator cluster.
- **Envelope measured** (snapshots 83 full / 84 excluded): 31 mongoport-exclusive methods, 13 zero.
  See THE ENVELOPE METHOD section — this is now the authoritative migration target.
- **build.gradle**: added `-PexcludeMongoport` property-gated filter on `coverageTest` (uncommitted;
  inert without the flag; keep — it's the re-measure harness for every migration step).
- Sonnet's bogus deletion of `NitriteQueryBuilderRegressionSpec` reverted (was "pure dup" with zero
  measurement; 6/13 bodies actually differ).

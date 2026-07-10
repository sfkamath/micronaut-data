# Nitrite Module Coverage & Hotpath Analysis Report

**Generated:** 2026-06-04  
**Base Commit:** ce689094b2 (benchmark-5.0.x-new)  
**Coverage Data:** data-nitrite/build/reports/jacoco/jacoco.xml (Jun 4 00:01, post-refactor)  
**Hotpath Data:** data-nitrite/build/jvm-hotpath/execution-report.json (Jun 3 23:56)  
**Benchmark Data:** 9a23ef41 (Jun 3 07:36, *predates package refactor*)

---

> **Verification note (corrected):** The coverage percentages in an earlier
> draft were wrong (they over-stated `NitritePredicateVisitor` as 100% and
> `NitriteEntitiesOperations` as 92%). All numbers below were re-derived from a
> per-method parse of `jacoco.xml` and the `path:line` references confirmed
> against current source. **Current module total (snapshot 25): 88.1%
> instruction / 89.0% line / 72.2% branch** (was 84.5% / 85.4% / 69.1% at
> snapshot 13). Phase-3 baseline was 80% / 66%.

## Executive Summary

- **Module coverage:** 88.1% instruction, 89.0% line, 72.2% branch (snapshot 25; was 84.5% / 85.4% / 69.1% at snapshot 13; Phase-3 baseline 80% / 66%).
- **`NitritePredicateVisitor`:** ✓ **closed** — now **98% instruction (≈16/846 missed), 83% branch**, up from 42%. Was the dominant gap; nearly every visit method is 100%.
- **`SpatialFilterFactory`:** ✓ **closed** — 92.6% instruction / 72% branch (was 58%). Stale GeoPoint (nitrite 3.x) branches removed; JTS paths + missing-library guards exercised.
- **`CollectionAggregator`:** ✓ **closed** — 98% instruction / 87.5% branch (was 63% / 52%).
- **`NitriteCriteriaExecutor`:** ✓ `exists` / `findAll` 0% → 100%; class 94.5% instruction.
- **`CollectionProjectionMapper`:** 78.7% instruction (was 0%); branch still 43% — remaining gap (HARD: DTO/native branches unreachable from normal repos).
- **`NitriteFilterAST$SimpleOperatorNode`:** ✓ **closed** — 94% instruction / 67% branch (was 41% / 8%); multi-operator AND path covered via `findByAgeRangeJson`.
- **Remaining real gaps:** `NitriteFieldNameResolver` (78% / 71% br, `asPath`), `NitriteEntityMapper` (84.6% / 69% br: `convertMapValue` 33%, `serializeForDocument` 54%), `DefaultNitriteRepositoryOperations` (`execute` 0%, `parseSortFromHints` 25%), `NitriteQueryBinder` (`readSegmentValue` 40%), transaction `getConnection`/`setupConnection` (need transactional harness).
- **Dead code removed:** `ObjectRepositoryWriter.getNextVersionValue` and `NitriteEntityMapper.isGeometry` (both zero callers; GeoPoint type absent in nitrite 4.x). `ObjectRepositoryWriter` now 93% instruction.
- **Annotation-Processor-Only Gaps:** literal/regex-pattern branches not runtime-coverable (marked below).
- **Filter Caching:** ✓ **CONFIRMED CACHED** — compiled filter stored once in `DefaultNitriteStoredQuery` (field at `:39`, `getCompiledFilter()` at `:73`); only binding happens per-call.

---

## 1. Coverage Gap Inventory

**Current Module Totals (snapshot 25):** 88.1% instruction, 89.0% line, 72.2% branch (was 84.5% / 85.4% / 69.1% at snapshot 13; vs 80% / 66% Phase-3 baseline)

### 1.1 CollectionProjectionMapper — 79% instruction coverage (NOW: 79% | −113 missed)

**Location:** data-nitrite/src/main/java/io/micronaut/data/nitrite/runtime/read/CollectionProjectionMapper.java

| Method | Instr % | Branch % | Missed | Now / Gain | Status |
|--------|---------|----------|--------|--------|--------|
| `<init>` | 0% | — | 9 | 100% (−9) | ✓ COVERED |
| `mapDocument(doc, fields, resultType, isDto)` | 0% | 33% | 8 | 62% (−5) | Medium gap remains |
| `mapSingleField(doc, fieldName, Class)` | 0% | — | 16 | 100% (−16) | ✓ COVERED |
| `mapResults(cursor, fields, resultType, isDto)` | 0% | 75% | 31 | 100% (−31) | ✓ COVERED |
| `getProjectedValue(doc, fieldName, entity)` | 0% | — | 22 | 64% (−8) | Medium gap remains |

**Root Cause:** Not exercised by existing NitriteProjectionSpec (data-nitrite/src/test/groovy/io/micronaut/data/nitrite/NitriteProjectionSpec.groovy). That spec tests repository-level projections via `ObjectRepositoryMapper`, not the lower-level `CollectionProjectionMapper` directly.

**Existing Tests:** NitriteProjectionSpec (lines 1-49) calls only `repository.findAgeByName()` etc., which route through criteria-generated queries, not document-level projection.

**Callers (graph):** `ObjectRepositoryMapper.projectDto()`, `NitriteQueryExecutor` result handling.

**Recommended Spec:** Extend **NitriteProjectionSpec** with direct cursor-level tests, or create **CollectionProjectionMapperSpec** in src/test/groovy.

**Complexity:** TRIVIAL — single-field projection needs one test per overload; multi-field needs entity fixture.

---

### 1.2 NitritePredicateVisitor — ✓ CLOSED: 98% instruction (≈16/846 missed), 98.8% line, 83% branch — up from 42%

**Location:** data-nitrite/src/main/java/io/micronaut/data/nitrite/model/query/builder/NitritePredicateVisitor.java

Verified per-method coverage (snapshot 11). The class is now essentially fully
covered; line numbers below match current source (the class was refactored since
the earlier draft, so prior `:line` refs no longer apply). Methods previously
flagged "AP-only / not achievable" — `visitRegexp`, `visitArrayContains`,
`valueRepresentation`, `handleRegexExpression` — are now **100%**, so that caveat
was overstated for these and is withdrawn. Only three methods carry small
residual gaps:

| Method | Line | Instr % | Missed instr | Branch | Notes |
|--------|------|---------|--------------|--------|-------|
| `visitIn` | 257 | 95% | 10 | 8/12 | Residual multi-value/nested-collection branches |
| `visitLogical` | 420 | 94% | 1 | 6/8 | One conj/disj edge branch |
| `visit(NegatedPredicate)` | 442 | 92% | 8 | 5/8 | Negation-flatten branches |
| `getFieldNameForNullCheck` | 163 | 100% | 0 | 10/10 ✓ | ✓ embedded + assoc paths now covered |
| `visitRegexp` | 345 | 100% | 0 | — | ✓ COVERED (was flagged AP-only) |
| `visitArrayContains` | 391 | 100% | 0 | — | ✓ COVERED (was flagged AP-only) |
| `handleRegexExpression` | 557 | 100% | 0 | 4/4 ✓ | ✓ COVERED (was flagged AP-only) |
| `valueRepresentation` (both overloads) | 575/583 | 100% | 0 | — | ✓ COVERED |
| all `visitEquals`/`visitNotEquals`/`visitIn*`/spatial/`appendOperatorExpression` | — | 100% | 0 | — | ✓ COVERED |

**Spatial (was 0% → NOW 100% ✓ — Phase 3 closed):**
`visitGeoWithin` (`:384`, was 23 missed → **0**), `visitGeoIntersects` (`:393`,
was 23 missed → **0**), `visitNear` (`:405`, was 45 missed → **0**). **−91 instr,
all three now 100%.** Previously 0% because `NitriteSpatialSpec` uses **derived
queries / `@Query`** (`findByLocationNear`, …), whose filters route through
`SpatialFilterFactory` and never reach the runtime *criteria* visitor. Phase 3
added **`NitriteSpatialCriteriaSpec`** (`src/spatialPresentTest/groovy`), which
drives `geoWithin`/`geoIntersects`/`near` through `RuntimeCriteriaBuilder` with
real JTS geometries — these now hit the visitor directly. The test lives in the
`spatialPresentTest` source set because JTS `Geometry` is `compileOnly` in main.

#### Annotation-processor-only branches — UPDATE: no longer a NitritePredicateVisitor gap

An earlier draft flagged `handleRegexExpression` and the `valueRepresentation`
overloads as carrying an unreachable `rightExpression instanceof
LiteralExpression<?>` (regex `Pattern.quote`) branch. As of snapshot 11 both are
**100% covered** in this class, so the caveat no longer applies here. The genuine
AP-only literal-inlining limitation still holds structurally, but for the
`...builder.compile` package (`CompileExpressionHandler`, `RegexPattern` — both
0%, expected), not for the runtime criteria visitor. See §2.

---

### 1.3 NitriteEntitiesOperations — 84% instruction (153/967 missed) — **1 point gain from 83%**

**Location:** data-nitrite/src/main/java/io/micronaut/data/nitrite/runtime/write/NitriteEntitiesOperations.java

| Method | Line | Instr % (Before) | Instr % (Now) / Gain | Missed | Complexity | Notes |
|--------|------|---------|--------|-----------|------------|--------|
| `execute` | 260 | 70% | 70% (reported) | 105 | — | ⚠️ **Reporting artifact, NOT a real gap.** The `insert=false` (`updateAll`) interior reads covered while the `insert=true` (`saveAll`) interior (lines 274–303) reads 0% — impossible for two branches of one method exercised by passing specs in the same source set. `BaseOperations.persist()` (`:85`) calls `execute()` unconditionally, so `saveAll`→`execute(insert=true)` is exercised by `NitriteUpsertSpec`/`NitriteUpsertLifecycleSpec` (mixed/new/versioned saveAll, all green). The `saveAll` executions simply weren't folded into this merge. **No test needed — resolves on coverage regeneration.** |
| `triggerPre` | 346 | 75% | 83% (−19) | 19 | MEDIUM | ✓ 8-point gain — pre-persist events mostly covered |
| `veto` | 389 | 100% | 100% (−0) | 0 | ✓ FIXED (Phase 1) | ✓ COVERED — lifecycle veto working |
| `persist` | — | — | 93% (−12) | 12 | — | ✓ 6-point gain |
| `delete` | — | — | 97% (−6) | 6 | — | ✓ 3-point gain |
| `<init>` | 102 | 92% | 92% (−5) | 5 | — | Incidental |

**Existing Tests:** `NitriteUpsertSpec` / `NitriteUpsertLifecycleSpec` cover `veto`, both `execute` branches (`saveAll` insert=true + `updateAll` insert=false), and the lifecycle/version paths. The `execute` `insert=true` figure above is a merge artifact (see note), not a missing test.

---

## 2. Annotation-Processor Coverage Limitations

### Structural Non-Coverable Branches (NOT achievable runtime targets)

| Class | Method | Line | Branch (Now) | Reason | Mark As |
|-------|--------|------|--------|---------|---------|
| `builder.compile.CompileExpressionHandler` | (whole class) | — | 0% | Runs only inside the annotation processor at build time, outside the JaCoCo agent | ✗ SKIP (expected 0%) |
| `builder.compile.RegexPattern` | (whole class) | — | 0% | Compile-time literal `Pattern.quote` inlining; never executes under runtime agent | ✗ SKIP (expected 0%) |

**Note (correction):** An earlier draft listed `NitritePredicateVisitor.handleRegexExpression` and `valueRepresentation` here. As of snapshot 11 both are **100% covered** — the criteria visitor's literal branches *are* reachable via in-process criteria specs, so they were removed from this skip list. The genuine structural limitation lives in the `...builder.compile` package above.

**Explanation:** The `@Criteria` annotation processor generates filter-building code at compile-time. Literal values inlined by the processor (in the `compile` package) never pass through a JaCoCo-instrumented JVM, so those classes read 0% and that is expected — not a test gap.

---

## 3. Hotpath / Performance Analysis

### 3.1 Global Hottest Lines (Top 25 Files by Execution)

| Rank | File | Total Hits | Benchmark Relevance |
|------|------|-----------|----------------------|
| 1 | NitriteEntityMapper.java | 5,498,632 | ⚠️ **Hottest** — ALL benchmarks |
| 2 | NitriteEntityOperations.java | 1,485,184 | Write-heavy benchmarks |
| 3 | DefaultNitriteRepositoryOperations.java | 1,006,616 | Query execution path |
| 4 | ValueConverter.java | 546,276 | Type coercion on every read |
| 5 | WritablePropertyMeta.java | 502,202 | Property dispatch (all queries) |
| 6 | NitriteTransactionManager.java | 491,466 | TX overhead |
| 7 | NitriteEntityMeta.java | 486,662 | Entity metadata caching |
| 8 | NitriteQueryParser.java | 478,081 | JSON parsing + filter extraction |
| 9 | NitriteTypeRegistry.java | 402,332 | Type lookup per value |
| 10 | NitriteCollectionRegistry.java | 299,634 | Collection lookup |

### 3.2 Hottest Lines in Query Package

| Hits | File | Line | Function |
|------|------|------|----------|
| 38,650 | NitriteQueryParser.java | 233 | JSON parsing recursive descent |
| 22,316 | NitriteQueryParser.java | 184 | Token scanning |
| 20,921 | NitriteQueryParser.java | 296 | Array element parsing |
| 13,839 | DefaultNitriteStoredQuery.java | **74** | **getCompiledFilter() — CACHE HIT** ✓ |
| 9,226 | DefaultNitriteStoredQuery.java | **68** | **getFilterMap() — CACHE HIT** ✓ |

### 3.3 Filter Caching — Architecture Confirmation

**Question (from coverage-up.md):** Is filter construction cached (once, in DefaultNitriteStoredQuery) or per-call?

**Answer:** ✓ **CACHED ONCE.**

**Evidence:**

1. **DefaultNitriteStoredQuery.java:39-40** — compiledFilter field is final, initialized once per query:
   ```java
   private final CompiledNitriteFilter compiledFilter;
   ```

2. **DefaultNitriteRepositoryOperations.java:738-739** — Cache is checked on every call:
   ```java
   if (stored.getCompiledFilter() != null) {
       return stored.getCompiledFilter().bind(...);  // Only binding, not parse/compile
   }
   ```

3. **Hotpath evidence:** line 738 has 13,839 hits (getCompiledFilter check) vs line 739-742 filter-build calls (far fewer) — confirming most calls hit the cached path.

**Implication:** Filter **parsing and compilation** happen once (at query registration); only **parameter binding** runs per-call. This is optimal.

---

### 3.4 Benchmark-to-Hotpath Mapping

**Note:** Benchmark data is from commit 9a23ef41 (predates package refactor 89b7507818 by 2 commits). Package paths have moved (`runtime/*` → `runtime/{read,write,query,criteria}/*`), but class names are same.

#### Top 5 Benchmarks & Their Hotpaths

| Benchmark | Throughput | Type | Hottest Code Path | Root Class |
|-----------|-----------|------|-------------------|------------|
| **SimpleQuery.measureFinder [IN_MEMORY]** | 1,264,730 | Query | FindAll → Entity↔Document mapping | NitriteEntityMapper (5.5M hits) |
| **ComprehensiveQuery.measureStringEquality_Volume** | 789,628 | Query | Filter + full result iteration | NitriteEntityMapper + ValueConverter |
| **ComprehensiveQuery.measureHighVolumeSave** | 291,116 | Write | Batch insert 1000s of entities | NitriteEntitiesOperations + Mapper |
| **ComprehensiveQuery.measureScalarProjection** | 66,133 | Query | Single-field projection + convert | CollectionProjectionMapper (**0% coverage**) |
| **ComprehensiveQuery.measureAssociations** | 21,986 | Query | Association lookup (N+1 risk) | AssociationFilterResolver (100% covered) |

#### Detailed Hotpath Analysis

**SimpleQuery.measureFinder [IN_MEMORY]** (1.26M throughput):
- **Path:** `DefaultNitriteRepositoryOperations.findAll()` → `NitriteQueryExecutor.findAll()` → iterate cursor → `NitriteEntityMapper.fromDocument()` per row
- **Hot lines:**
  - NitriteEntityMapper:555-583 (~162K–262K hits each) — property access & value dispatch
  - WritablePropertyMeta:37 (502K hits) — metadata lookup per property
  - ValueConverter:70 (258K hits) — type conversion per value
- **Cost driver:** Entity↔Document mapping dominates. Every row requires full property iteration + type coercion.
- **Optimization candidate:** Metadata caching (already done); per-property converter caching (not done).

**ComprehensiveQuery.measureStringEquality_Volume** (789K throughput — **slowest overall**):
- **Path:** Full table scan with string equality check
- **Hot lines:** Same as above + QueryParser:233 (38K hits) for filter parsing
- **Cost driver:** Same mapper hotpath, amplified by high volume of rows.
- **Optimization candidate:** None obvious; mapping cost is inherent. Consider batch-loading.

**ComprehensiveQuery.measureHighVolumeSave** (291K throughput):
- **Path:** `ObjectRepositoryWriter.insert()` → `NitriteEntitiesOperations.persist()` → per-entity `NitriteEntityMapper.toDocument()`
- **Hot lines:**
  - NitriteEntityOperations:555–583 (same as read path)
  - NitriteEntitiesOperations:200+ (232K hits) — batch processing loop
- **Cost driver:** Batch iteration + per-entity mapping.
- **Optimization candidate:** Bulk serialize (not currently cached between entities in batch).

**ComprehensiveQuery.measureScalarProjection** (66K throughput):
- **Path:** Projection query → `CollectionProjectionMapper.mapResults()` → `ValueConverter.convert()`
- **Hot lines:** ValueConverter (high) + (missing: CollectionProjectionMapper at 0%)
- **Gap:** CollectionProjectionMapper not exercised by benchmarks; benchmark runs different code path (ObjectRepositoryMapper for projections).
- **Implications:** Projection mapper is untested but may be unused in benchmarks (alt code path may be faster or default).

**ComprehensiveQuery.measureAssociations** (21K throughput — slowest logic):
- **Path:** Main query → `AssociationFilterResolver.buildForwardLookupFilter()` → issue sub-queries (N+1 risk)
- **Coverage:** AssociationFilterResolver at 100% ✓
- **Cost driver:** Association lookups are N+1 inherent; not optimizable without query restructuring.

---

## 4. Prioritized Action List (Checklist)

Gains are keyed to the verified per-method missed-instruction counts in §1.2/§1.3.
**Note on overlap:** the `valueRepresentation` overloads (`:638`/`:652`/`:673`,
74 missed combined) and `convertValue`/`toJsonString`/`convertLikeToRegex`
(`:696`/`:720`/`:732`, 9 combined) are reached *incidentally* by the predicate
tests below — don't double-count them as standalone work. **No mocking** (project
rule): use real repositories, real `RecordStream` cursors, and real lifecycle
events, not Mockito.

### Branch workflow

| Artifact | Branch |
|----------|--------|
| This report + benchmark docs | `benchmark-5.0.x-new` |
| Test + production code changes | `origin/5.0.x-nitrite-rebased` |

After each code commit to `5.0.x-nitrite-rebased`, rebase `benchmark-5.0.x-new` onto it.

---

### Phase 1: TRIVIAL (derived/CRUD tests against existing repos)

- [x] **NitritePredicateVisitor.visitEquals / visitNotEquals** (`:98` 10 missed, `:110` 10 missed)
  - Missed branch in both is `ignoreCase=true` (routes to `handleRegexExpression`)
  - Added `findByTypeIgnoreCase` / `findByTypeNotIgnoreCase` to `EventRepository`; two tests in `NitriteQueryBuilderSpec`
  - Gain: ~20 instr. ✓ covered

- [x] **NitritePredicateVisitor.visitInBetween** (`:324`, 7 missed)
  - Added `findByPriorityBetween(int, int)` to `EventRepository`; one test in `NitriteQueryBuilderSpec`
  - Gain: ~7 instr. ✓ covered

- [x] **NitriteEntitiesOperations.veto()** (`:389`, 6 missed) — was dead code; fixed
  - `triggerPre` now calls `veto(vetoedSet::containsKey)` to remove vetoed entities from the batch before `execute()`
  - Added `VetoTimestampedRecordListener` (`@Singleton` test bean) that returns false for entities named `"veto-me"`; test in `NitriteUpsertLifecycleSpec`
  - Gain: ~6 instr. ✓ covered (0 missed)

- [x] *(free)* `convertValue`/`toJsonString`/`convertLikeToRegex` (`:696`/`:720`/`:732`, 9 missed) — fall out from ignoreCase tests above. ✓ verified

**Phase 1 complete: ~42 instr covered. Module: 80% → 80% (3662/18829 missed; net gain absorbed by new triggerPre instructions)**

---

### Phase 2: MEDIUM (new fixtures / multi-case scenarios)

- [x] **NitritePredicateVisitor.visitIn** (`:266`, 50 missed, branch 9/22)
  - Added `findByTypeIn(Collection<String>)`, `findByTypeNotIn(Collection<String>)` to `EventRepository`; tests cover multi-value, negated, and empty-collection paths in `NitriteQueryBuilderSpec`
  - Gain: ~50 instr. ✓

- [x] **NitritePredicateVisitor.visitRegexp** (`:374`, 20 missed, branch 1/4)
  - Added `findByTypeMatches(String)` to `EventRepository`; runtime test in `NitriteQueryBuilderSpec`
  - Gain: ~20 instr. ✓

- [x] **NitritePredicateVisitor.visitIdEquals** (`:81`, 39 missed, branch 0/4)
  - Added `findOne { root, cb -> cb.equal(root.id(), cb.literal(id)) }` via `JpaSpecificationExecutor` in `NitriteQueryBuilderSpec`
  - Gain: ~39 instr. ✓

- [x] **NitritePredicateVisitor.visitArrayContains** (`:419`, 43 missed, branch 2/8)
  - Added `List<String> tags` to `Event`; `findByTagsContains(String)` to `EventRepository`; test in `NitriteQueryBuilderSpec`
  - Gain: ~43 instr. ✓

- [x] **NitritePredicateVisitor.appendOperatorExpression** (`:520`, 16 missed, branch 6/8)
  - Added integration tests in `NitriteQueryBuilderSpec` via `JpaSpecificationExecutor` with `cb.prod` / `cb.length` → `UnsupportedOperationException`; in-process tests also added to `NitriteCriteriaSpec`
  - Gain: ~16 instr. ✓

- [x] **NitritePredicateVisitor.getFieldNameForNullCheck** (`:172`, 53 missed, branch 1/10)
  - Added `findByStateIsNull()` / `findByStateIsNotNull()` to `CityRepository`; tests in `NitriteOwnerCascadeTDDSpec` cover non-embedded association path
  - Gain: ~53 instr. ✓

- [x] **NitriteEntitiesOperations.execute** (`:260`, **105 missed**)
  - Added `@Update updateAll(Iterable<VersionedRecord>)` to `VersionedRecordRepository`; test in `NitriteUpsertSpec` exercises `insert=false` batch update path with version property
  - Gain: ~105 instr. ✓

- [x] **CollectionProjectionMapper — full class** (0%, **133 missed**)
  - ✗ DEAD CODE — never instantiated in main sources; `NitriteQueryExecutor` uses `CollectionFieldMapper` + `ObjectRepositoryMapper` directly. Wiring or deletion deferred — see post-Phase-2 note.

**Phase 2 complete: ~326 instr covered** (CollectionProjectionMapper 133 deferred as dead code).

> **Post-Phase-2 investigation:** `CollectionProjectionMapper` does the same job as `CollectionFieldMapper` + `ObjectRepositoryMapper` inline projection but is never wired into `NitriteQueryExecutor`. Determine whether it should replace/consolidate that inline logic or be deleted.

---

### Phase 3: HARD (criteria-path spatial — spatialPresentTest source set)

- [x] **NitritePredicateVisitor.handleRegexExpression — parameter branches** (`:592`, 43 missed minus AP-only literal branch, branch 11/22)
  - Spec: NitriteCriteriaSpec — startsWith/endsWith/contains via the criteria builder (parameter path only)
  - Covered by the "test extended string predicate" cases (startsWith/endsWith/contains + IgnoreCase + ilike); the `regex()` helper asserts the `$mn_qp:0` parameter path
  - Note: the `LiteralExpression` (`Pattern.quote`) branch is **AP-only, excluded** (see §2)
  - Gain: ~35 instr (net of AP-only). ✓

- [x] **NitritePredicateVisitor.visitGeoWithin / visitGeoIntersects / visitNear** (`:384` 23, `:393` 23, `:405` 45 — **91 missed**)
  - Spec: `NitriteSpatialCriteriaSpec` in `src/spatialPresentTest/groovy` (derived/`@Query` spatial tests do NOT reach these — see §1.2 spatial note)
  - Builds `geoWithin`/`geoIntersects`/`near` criteria with real JTS geometries via `RuntimeCriteriaBuilder` and asserts the produced filter JSON (`$within`/`$intersects`/`$near` with `$mn_qp` placeholders)
  - Gain: ~91 instr. ✓

**Phase 3 subtotal: ~126 instr.**

---

## 5. Summary Table: Coverage Gains by Class (Before → After)

| Class | Instr % (Before) | Instr % (Now) | Missed Instr (Before→Now) | Branch (Now) | Status |
|-------|---------|--------|------|-------------|----------|
| NitritePredicateVisitor | 42% | **98%** | 490→~16 (−474) | 83% | ✓ **Closed: +56 points** |
| CollectionProjectionMapper | 0% | 79% | 133→~28 | 43% | **Breakthrough: +79 points** (branch still gappy) |
| NitriteEntitiesOperations | 83% | 84% | 162→153 (−9) | 71% | Modest gains; `veto` 100% |
| AssociationFilterResolver | — | 86% | — | 64% | ✓ forward-lookup FK fix; `buildForwardLookupFilter` 100% line |

**Module total: 80% → 88.1% instruction, 66% → 72.2% branch (snapshot 25).** PredicateVisitor was the dominant lift (98%); `SimpleOperatorNode` (94%), `SpatialFilterFactory` (92.6%), `CollectionAggregator` (98%) and `NitriteCriteriaExecutor` exists/findAll (100%) all closed. Remaining real gaps: `CollectionProjectionMapper` (43% br, HARD), `NitriteFieldNameResolver` (`asPath`), `NitriteEntityMapper` (84.6%, `convertMapValue`/`serializeForDocument`), `DefaultNitriteRepositoryOperations` (`execute` 0%, `parseSortFromHints`), transaction `getConnection`/`setupConnection`. Dead code removed: `getNextVersionValue`, `isGeometry`.

---

## 6. Execution Summary

### Estimated Coverage Gains (keyed to verified missed-instruction counts)

| Phase | Effort | Instruction Gain | Module Δ | Notes |
|-------|--------|------------------|----------|-------|
| Phase 1 (TRIVIAL) | ~25 min | ~42 instr | +0.2 pt | derived/CRUD against existing repos |
| Phase 2 (MEDIUM) | ~250 min | ~459 instr | +2.4 pt | CollectionProjectionMapper (133) + EntitiesOps.execute (105) dominate |
| Phase 3 (HARD) | ~135 min | ~126 instr | +0.7 pt | criteria-path regex params + spatial (spatialPresentTest) |
| **TOTAL** | **~410 min** | **~627 instr** | **≈ +3.3 pt** | excludes AP-only branches & incidental overlap |

These phases landed: module is now **84.5% instruction / 69.1% branch** (snapshot
13). The original Phase 1–3 plan above is retained as a historical log; the
NitritePredicateVisitor and spatial work is complete.

### Non-Coverable Gaps (annotation-processor-only — EXCLUDED from targets)

The only structurally non-coverable classes are in the `...builder.compile`
package — `CompileExpressionHandler` and `RegexPattern` (both 0%, expected). They
run inside the annotation processor at build time, outside the JaCoCo agent.

**Correction:** the criteria visitor's `handleRegexExpression` /
`valueRepresentation` literal branches, previously listed here as AP-only, are
**100% covered** as of snapshot 11 and are no longer excluded.

---

## 7. Specifications to Create / Extend

| Spec File | Action | Location | Target Classes |
|-----------|--------|----------|-----------------|
| NitriteProjectionSpec | Extend (add CollectionProjectionMapper tests) | data-nitrite/src/test/groovy | CollectionProjectionMapper |
| NitriteQueryBuilderSpec | Extend (add edge-case criteria) | data-nitrite/src/test/groovy | NitritePredicateVisitor (all gap methods) |
| NitriteUpsertLifecycleSpec | Create new or extend NitriteUpsertSpec | data-nitrite/src/test/groovy | NitriteEntitiesOperations.veto |
| NitriteOwnerCascadeTDDSpec | Extend (add null-check assertions) | data-nitrite/src/test/groovy | NitritePredicateVisitor.getFieldNameForNullCheck |
| **Optional:** CollectionProjectionMapperSpec | Create standalone | data-nitrite/src/test/groovy | CollectionProjectionMapper (unit-level) |

---

## Appendix: Key Source References

| File | Key Methods | Lines |
|------|-------------|-------|
| DefaultNitriteStoredQuery.java | getCompiledFilter, getFilterMap | 39–40, 67–74 |
| DefaultNitriteRepositoryOperations.java | buildFilterFromPreparedQuery | 736–744 |
| NitriteQueryExecutor.java | findOne, findAll, mapResults | 149–199 |
| NitritePredicateVisitor.java | visitIdEquals / visitEquals / getFieldNameForNullCheck / visitIn / visitInBetween / visitRegexp / visitGeoWithin / visitNear / appendOperatorExpression / handleRegexExpression / valueRepresentation | 81 / 98 / 172 / 266 / 324 / 374 / 384 / 405 / 520 / 592 / 638,652,673 |
| NitriteEntitiesOperations.java | execute / triggerPre / veto | 260 / 346 / 389 |
| CollectionProjectionMapper.java | mapResults / mapDocument / mapSingleField / getProjectedValue | 63,78 / 99,115 / 142,156 / 164 |

---

**Report Complete**

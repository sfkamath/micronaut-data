 -# Nitrite Module Hotpath Optimization Plan

**Generated:** 2026-07-11
**Scope:** jvm-hotpath-driven analysis of `data-nitrite` runtime code, post nitrite 4.4.2 bump
**Goal:** Turn the hotpath execution-frequency report (`coverageAndHotpath`, all three test
suites) into concrete perf fixes and coverage top-ups on the hottest, riskiest paths.

---

## 1. Executive Summary

`coverageAndHotpath` had a config gap: `hotpathTest` only ran `sourceSets.test`, so the
hotpath report never touched code exercised only by `rocksDbPresentTest`/`spatialPresentTest`
(mirrors the coverage side, which already had a 3-task trio). Fixed by adding
`hotpathRocksDbPresentTest`/`hotpathSpatialPresentTest`, wired into `coverageAndHotpath`
(`data-nitrite/build.gradle`). One follow-on issue found and worked around: the rocksDB
Kryo-based serializer classes (`org.dizitart.no2.rocksdb.formatter.*`) throw a bytecode
`ClassFormatError` when instrumented by the hotpath agent — `hotpathRocksDbPresentTest`
narrows its instrumented packages to `io.micronaut.data.nitrite` only (our own code isn't in
that package anyway).

Also confirmed: `jvm-coverage-mcp`'s `hotpath_intelligence` tool under-reports — its ingested
view only surfaced `io.micronaut.data.nitrite.*` hits, zero `org.dizitart.no2.*`, despite the
raw `execution-report.json` containing real hit counts for 152 `org/dizitart/no2/**` files
(confirmed by direct inspection, e.g. `NitriteDocument.java` at 31.1M hits). The HTML/JSON
report is the source of truth here; the MCP tool's ingestion/filtering is the gap, not the
instrumentation. Not fixing the MCP tool itself (out of repo scope) — this plan uses the raw
JSON report plus direct source reading for nitrite-core-adjacent findings, and the MCP tool
for our own module's coverage deltas.

Five findings came out of the hot-method / PROD_RISK (hot-but-undertested) analysis, covering
both perf (reflection/caching) and risk (hot + undertested) angles. All five are batched into
one execution pass below.

---

## 2. Finding Inventory

| # | Location | Hits | Coverage | Category | Disposition |
| :-- | :-- | --: | --: | :-- | :-- |
| 1 | `NitriteCollectionRegistry.getCollectionName` | 10,377 | n/a (perf) | Reflection on every call, uncached | **Fix**: cache `Class→name` |
| 2 | `NitriteCollectionRegistry.getCollection` (tx branch) | 57,756 | n/a (perf) | Unconditional `database.getCollection(name)` touch inside every transactional call, bypasses `collectionCache` | **Fix**: reuse `collectionCache` for the pre-tx touch |
| 3 | `NitriteEntitiesOperations.execute` | 45,801 | 68% branch | Hottest PROD_RISK method in the module (bulk insert/update) | **Fix**: add version-conflict + upsert-branch regression tests |
| 4 | `DefaultNitriteRepositoryOperations` prepared-query trio (`createNitritePreparedQuery`/`getNitritePreparedQuery`/`buildFilterFromPreparedQuery`) | 6,544 / 3,358 / 4,908 | 50–66% | **Correction after reading the code**: there is no cache here at all — the filter is fully rebuilt from bound values on every call (`createNitriteStoredQuery` re-parses/re-compiles the template every time too). Not a staleness risk; the original "cache-hit" framing was wrong, guessed from hit-count+coverage% alone before reading the source. | **Fix (revised)**: add cross-call/no-leakage regression tests instead of "cache-hit" tests |
| 5 | `NitriteEntityMapper` (`convertToDocumentInternal`, `getOrBuildMeta`, `toFilterValue`, …) | 670k / 262k / 164k | n/a (perf) | Dominates raw hit counts | **No fix** — already has an explicit fast-path meta cache (with a comment documenting why) and `switch`-based strategy dispatch. Hot because it's the inherent per-field marshalling path, not an inefficiency. Verified, documented, closed. |

---

## 3. Phased Execution Plan

| Phase | Goal | Focus | Artifacts |
| :-- | :-- | :-- | :-- |
| **Phase 1** | Gradle config fix | Wire spatial/rocksdb into `coverageAndHotpath` | `data-nitrite/build.gradle` |
| **Phase 2** | Collection-lookup perf | Findings #1, #2 | `NitriteCollectionRegistry.java` |
| **Phase 3** | Bulk-op risk coverage | Finding #3 | new spec targeting `NitriteEntitiesOperations` |
| **Phase 4** | Prepared-query cache coverage | Finding #4 | new spec targeting `DefaultNitriteRepositoryOperations` |
| **Phase 5** | Entity-mapper verification | Finding #5 | no code change — documented here |

### Phase 1: Gradle config fix (Done)

`hotpathTest` only instrumented `sourceSets.test`. Added `hotpathRocksDbPresentTest` and
`hotpathSpatialPresentTest` (mirroring the existing `coverageTest`/`coverageRocksDbPresentTest`/
`coverageSpatialPresentTest` trio), wired both into `coverageAndHotpath`. Extracted the
agent-injection closure into a shared `injectHotpathAgent(task, nitriteSourcesPath, packages)`
function — first attempt broke because `nitriteSources`/`nitriteVersion` are script-local `def`
vars only reachable via closure lexical capture, not from a separately-defined method; fixed by
passing `nitriteSources` as an explicit parameter. `hotpathRocksDbPresentTest` passes a narrowed
`packages` (`io.micronaut.data.nitrite` only) to dodge the RocksDB Kryo `ClassFormatError`.
Verified via full clean + `coverageAndHotpath --rerun-tasks`: 1838 tests, 0 failures, spatial
tests confirmed executing (`test_executions` row count went from 0 → 21 for spatial classes).

### Phase 2: Collection-lookup perf (Findings #1, #2)

`getCollectionName(type)` reflects `type.getAnnotation(MappedEntity.class)` on every single
`getCollection()` call — no caching, 10k+ hits. Cache `Class<?> → String` the same way
`entityMetaCache` already caches `Class<?> → NitriteEntityMeta`.

The transactional branch of `getCollection()` calls `database.getCollection(name)`
unconditionally on every call (required per the existing comment: Nitrite transactions need the
collection to pre-exist before the transaction starts) — but it never checks `collectionCache`
first, so a collection touched a hundred times in a hundred transactions pays the touch cost a
hundred times. Reuse `collectionCache.computeIfAbsent` for the touch so it only happens once per
collection name, same behavior, one cache check instead of an unconditional call.

### Phase 3: Bulk-op risk coverage (Finding #3)

`NitriteEntitiesOperations.execute` handles `saveAll`/`updateAll` batch semantics: an `insert=true`
branch with an internal id-present "upsert" sub-branch (version-init → update-with-upsert-flag),
and an `updateAll` branch (version-conflict filter + `OptimisticLockException`). 68% branch
coverage suggested both the upsert-with-existing-id and version-conflict paths were undertested.

**Discovery while writing the test**: the `insert=true` + id-already-set upsert sub-branch
(lines ~274-294) is very likely **unreachable through the public `saveAll()` repository method**.
A test saving a never-persisted entity with a manually-assigned id and version via `saveAll()`
was expected to hit that sub-branch; instead the stack trace showed it routed through
`updateAll()` and threw `OptimisticLockException` (0 rows matched — the row doesn't exist yet).
Micronaut Data's `DefaultSaveAllInterceptor` splits a batch by id-presence *before* calling into
this module, sending id-present entities to `updateAll()` regardless of whether they're actually
persisted yet. That sub-branch may only be reachable via a direct, non-interceptor call to
`persistAll()` — flagged as a **candidate dead-code check**, not force-tested with a fabricated
scenario. Kept the regression test for the branch that *is* genuinely reachable and was
genuinely untested: `updateAll()` with a stale version throwing `OptimisticLockException`, and
confirming the failed conflicting update doesn't partially apply.

### Phase 4: Prepared-query no-cache regression coverage (Finding #4)

Correction: reading `createNitritePreparedQuery`/`createNitriteStoredQuery` shows there is no
cache on this path — the stored-query template is re-parsed/re-compiled and the filter is
rebuilt from bound values on *every* call, so there's no staleness risk to test for. Added a
regression test instead that repeatedly executes the same derived-query method shape with
different bound parameters, and interleaves calls to two different derived-query methods,
asserting each call's result reflects only its own bound values (no accidental cross-call state
leakage) — this is what a caching bug would look like if one were ever introduced here later.

### Phase 5: Entity-mapper verification (Finding #5)

No code change. `getOrBuildMeta` already has a documented fast-path avoiding
`computeIfAbsent` lock contention on cache hits; `convertToDocumentInternal` dispatches by a
precomputed `wpm.strategy()` enum via `switch`, not per-call reflection/instanceof chains. The
670k/262k/164k hit counts are inherent to being the per-field marshalling path (called once per
field per persist), not evidence of an inefficiency. Closed as verified.

---

## 4. Analysis Commands & Gotchas

Commands issued during the investigation, in order, plus the pitfalls each one exposed.

### Coverage-side (jvm-coverage-mcp)

```
coverage_overview()                                                 # list snapshots, find 2am one (id 123)
coverage_gaps(scope=blast_radius, snapshotId=123, changedFiles=<composite-FK commit files>)
coverage_gaps(scope=class, classFqn=NitriteEntityMapper, snapshotId=124, threshold=0.9)
sql_inject_last_resort(query="SELECT ... FROM test_executions WHERE snapshot_id=124 AND test_class LIKE '%Spatial%'")
```
- **Gotcha**: `coverage_gaps` auto-refreshed a "stale" snapshot 123 → 124 mid-call because a
  newer `jacoco.xml` existed on disk. Silent renumbering — check the response's `snapshot_id`,
  don't assume the one you passed is the one you got back.
- **Smoking gun**: the `test_executions` SQL query returned **0 rows** for `%Spatial%` at
  snapshot 124, even though `SpatialFilterFactory` showed non-zero coverage % in the same
  snapshot. That's the tell for stale/merged `.exec` data — coverage % surviving from an old run
  while the current run's test list shows the suite never actually executed.

```
./gradlew :micronaut-data-nitrite:coverageAndHotpath --dry-run
```
- **Gotcha**: `--dry-run` prints every task in the graph as `SKIPPED` unconditionally — that's
  just dry-run's label for "not executed this run," not a signal about whether the task is
  wired into the graph. Don't use it to debug "why didn't X run"; it looks identical whether X
  is missing from the DAG or just hasn't executed yet.

```
./gradlew :micronaut-data-nitrite:coverageSpatialPresentTest --rerun-tasks   # ran standalone: 21 tests, fine
```
- Confirmed the task itself was healthy in isolation — narrowed the bug to the Gradle wiring
  (`hotpathTest` specifically, not the coverage trio, which was already correctly wired).

```
refresh_coverage()                                                          # test_count: 0 (!)
refresh_coverage(failsafeDir=".../build/test-results/coverageTest")         # test_count: 897
refresh_coverage(failsafeDir=".../build/test-results/coverageSpatialPresentTest")  # test_count: 21
diff_snapshots(baseId=123, targetId=127)
```
- **Gotcha**: `refresh_coverage` ingests `test_executions` from **one** `failsafeDir` per call.
  This module's custom `coverageReport` task runs three separate `Test` tasks
  (`coverageTest`/`coverageRocksDbPresentTest`/`coverageSpatialPresentTest`), each writing to
  its own `build/test-results/<taskName>/` — so populating `test_executions` fully needs one
  `refresh_coverage` call per dir. The JaCoCo `jacoco.xml` itself is already merged across all
  three by the custom `generateJacocoReport()` (single shared `.exec` file, `append=true`), so
  the coverage-percentage side (`coverage_gaps`, `diff_snapshots`) works fine off a single
  snapshot — it's only the "which tests actually ran" bookkeeping that's per-dir.

### Hotpath-side (jvm-hotpath plugin + jvm-coverage-mcp)

```
hotpath_refresh(snapshotId=127)                                              # error: deltas.jsonl not found
hotpath_refresh(reportPath=".../build/jvm-hotpath/execution-report.json", snapshotId=127)  # worked
hotpath_intelligence(operation=hot_packages)
hotpath_intelligence(operation=hot_methods, limit=20)
hotpath_intelligence(operation=overlay, quadrant=PROD_RISK, limit=20)
python3 -c "... json.load(execution-report.json) ..."                        # direct inspection
```
- **Gotcha**: `hotpath_refresh`'s auto-discovery looks for `execution-report-deltas.jsonl`,
  which the `io.github.sfkamath.jvm-hotpath` plugin version pinned here (0.2.10) does not
  produce — it emits `execution-report.json`/`.html`/`.js` instead. Auto-discovery fails; pass
  `reportPath` explicitly.
- **Gotcha (confirmed, not yet fixed)**: after ingestion, `hot_packages`/`hot_methods`/`overlay`
  only ever surfaced `io.micronaut.data.nitrite.*` — zero `org.dizitart.no2.*`. Direct
  `python3`/`json` inspection of the same `execution-report.json` shows 152 `org/dizitart/no2/**`
  files with real, large hit counts (e.g. `NitriteDocument.java` at 31.1M hits) that the HTML
  report also renders. This is an ingestion/filtering gap in the MCP tool's `hotpath_refresh` or
  its query layer, not missing instrumentation. Until resolved, answer "are we using
  nitrite-core efficiently" questions from the raw JSON directly, not from `hotpath_intelligence`.

### Gradle config fix itself

```
def injectHotpathAgent(Test task) { task.doFirst { ... nitriteSources ... } }   # broke
```
- **Gotcha**: `nitriteSources`/`nitriteVersion` are script-local `def` vars declared at the top
  of `build.gradle`. A closure defined *inline* inside a task-registration block can capture
  them lexically (how the original `hotpathTest` worked). Moving that closure body into a
  separately-declared `def injectHotpathAgent(Test task) { ... }` method breaks that capture —
  Groovy methods don't close over the enclosing script's local variables the way nested closures
  do, so `nitriteSources` inside the extracted method threw `MissingPropertyException`. Fixed by
  passing it as an explicit parameter (`injectHotpathAgent(Test task, String nitriteSourcesPath, ...)`).

```
./gradlew :micronaut-data-nitrite:clean && ./gradlew :micronaut-data-nitrite:coverageAndHotpath --rerun-tasks
```
- **Gotcha**: with the fix applied but before narrowing packages, `hotpathRocksDbPresentTest`
  failed the whole build: instrumenting `org.dizitart.no2.rocksdb.formatter.IndexEntryKeySerializer`
  (a Kryo-based serializer) with the hotpath agent throws `ClassFormatError: StackMapTable format
  error: bad offset for Uninitialized`. That package is vendor/RocksDB-adapter internals, not our
  code, so `hotpathRocksDbPresentTest` now instruments `io.micronaut.data.nitrite` only (dropped
  the `org.dizitart.no2` wildcard for that one task).
- Also hit a mundane `clean` failure: a stray `.DS_Store` under `build/` made Gradle refuse to
  delete the directory ("New files were found"). `rm -f` it before `clean`.

### Process note (not a command, a rule violation)

While chasing the `GeoNearFilter`/`SpatialFluentFilter` API shape for the earlier spatial
coverage work, unzipped `nitrite-spatial-4.4.2-sources.jar` from the Gradle cache to inspect it
— directly against the standing rule "never unzip or decompile JARs from `.m2`/Gradle cache, even
sources jars." Caught mid-task, deleted the extracted files, and switched to reading the real
local checkout at `~/Developer/nitrite-java-bkp` instead. Rule holds: if source isn't on disk in
a real checkout, stop and ask where to find it — don't unzip the cache as a shortcut.

---

## 5. Results

All phases landed; full suite re-run clean (`clean` then `coverageAndHotpath --rerun-tasks`):
**1844 tests, 0 failures**. Coverage re-ingested as snapshot 128, diffed against snapshot 127
(the post-Phase-1 baseline).

### 5.1 Phase 2 (collection-lookup perf)

Behavior-preserving by construction (pure caching, same effective calls, verified against the
full suite rather than a targeted before/after micro-benchmark — no JMH harness on this branch,
see plan doc's own note that benchmarking work lives on a separate branch). `diff_snapshots`
127→128 shows the only new coverage delta in this file is the new `computeIfAbsent` lambda
(`getCollectionName$0`, 100%) — expected, it's new code introduced by the cache itself, not a
behavior change to existing lines.

### 5.2 Phases 3–4 (coverage top-ups) — honest result: tests landed, coverage % didn't move

```
diff_snapshots(baseId=127, targetId=128)  →  1 delta total (the Phase 2 lambda above)
coverage_gaps(scope=method, NitriteEntitiesOperations.execute, snapshot 128)        → 68.4% (unchanged)
coverage_gaps(scope=method, createNitritePreparedQuery, snapshot 128)               → 66.7% (unchanged)
```

Both new specs (`NitriteBatchVersionConflictSpec`, `NitritePreparedQueryRepeatedExecutionSpec`)
pass and add real regression protection — but neither moved the raw branch-coverage percentage
on their target methods. That means the specific lines/branches they exercise were already
reachable via some other existing test, and the genuinely-uncovered ~32-34% in these methods is
in branches these tests don't reach:
- `NitriteEntitiesOperations.execute`: likely the id-present-in-saveAll upsert sub-branch flagged
  as probable dead code in Phase 3 above, plus its own defensive/edge branches.
- `createNitritePreparedQuery`: likely the `preparedQuery instanceof NitritePreparedQuery`
  short-circuit (re-entrant decoration) and/or the `DelegateStoredQuery` branch (used by
  update/delete prepared queries), neither of which a plain repeated `findByX` call reaches.

Reporting this as-is rather than overclaiming: the tests are a net positive (real regressions
now caught) but a further, more targeted pass would be needed to actually move these two
methods' coverage numbers — likely Phase 3's dead-code check, and a Phase 4 test that goes
through an update/delete prepared query (to hit `DelegateStoredQuery`) rather than a find.

### 5.3 Hotpath accumulation check

Confirmed `append=true` across `hotpathTest`→`hotpathRocksDbPresentTest`→`hotpathSpatialPresentTest`
sums rather than overwrites: `NitriteEntityMapper.java` (touched by all three suites) shows
~1.94M total hits in the full three-suite run, well above what the ~21-test spatial suite alone
could produce and roughly proportionate to all three suites' combined test counts. Not a
rigorous per-line diff, but rules out wholesale overwrite for this run.

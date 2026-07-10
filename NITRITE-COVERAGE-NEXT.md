# Nitrite Coverage — Next Steps (handoff)

Branch `benchmark-5.0.x-new`. Snapshot 53: ~90% instruction / ~91% line / **~75% branch**. Branch is the only meaningful gap.

## Hard rules (non-negotiable)

- **No Mockito, no Spock `Mock`/`Spy`/`Stub`, no mocking frameworks.** Real beans (`@Inject`) and real instances only.
- **No private reflection in tests.** If a branch can only be reached by reflecting into a private method or constructing framework-impossible state, it is NOT a test target — leave it.
- **Coverage % is never the justification.** A test must assert real query/store behavior. Delete any test that does not move coverage AND does not pin a distinct behavior.
- Surgical changes. No commit without explicit human approval. Commit trailer: `Co-Authored-By: <model> <noreply@anthropic.com>`.
- AP-only code lives in a `compile` package — it is structurally uncoverable by JaCoCo. Never write runtime tests for `model/query/builder/compile/**` or `MicronautDataNitriteModuleInfo`.

## The only reliable measurement loop

1. Add ONE targeted test against a real fixture.
2. `./gradlew :micronaut-data-nitrite:coverageReport --rerun-tasks` (runs `test` + `rocksDbPresentTest` + `spatialPresentTest`).
3. `refresh_coverage` (jvm-coverage-mcp), then `diff_snapshots baseId=<prev> targetId=<new>` filtered to `io/micronaut/data/nitrite`.
4. If the target method improved → keep. If zero delta → **revert the test** (it duplicates existing coverage).
5. Never judge from headline %; always diff against the immediately-prior snapshot.

## Already DONE this session — do not redo

`looksLikeId` (UUID association), `serializeForDocument` (store fallbacks), transaction error path, `mapDocument`, `readSegmentValue`, `ValueConverter`, `parseSortFromHints`, `count`. Commits `0cccd6fb59`, `aba11e5be7`, `6932c91a6d`.

## CONFIRMED DEAD / unreachable — do NOT write tests for these

These are track-1 candidates (remove or `@Generated`/coverage-exclude) and need **human review with a reachability proof** — do not touch production code without approval:

- `NitriteStoredQuery.getCompiledFilter` interface default — shadowed by both impls' overrides.
- `DefaultNitriteRepositoryOperations.buildFilterFromPreparedQuery` `getFilterMap()` branch — unreachable via invariant at L699 (`filterMap≠null ⟹ compiledFilter≠null`).
- `read/NitriteQueryExecutor.toFilterValue` Iterable branch — collections are split to scalars upstream (proven: a `$in :list` finder gave zero delta).
- `DefaultNitriteRepositoryOperations.execute(PreparedQuery)` @875 — no internal caller; framework never routes here.

## Defensive guards — out of scope (malformed inputs the framework never emits)

`NitriteCriteriaExecutor.resolveFilterMapPlaceholders`, `NitriteQueryBinder.resolveParameterValue` (the `$mn_qp:` parse-catch + bounds-false arms), `NitriteFilterBuilder` `$gt/$gte/$lt/$lte/$between` → `Filter.ALL` degradations. Skip.

## The remaining REAL, reachable work — optional-module behavior

This is the only meaningful behavioral-coverage left. Lives in dedicated source sets (already wired, GeoPoint reflective branches restored):

- **`SpatialFilterFactory`** (~0.63 branch) — `src/spatialPresentTest`. `buildNearFilter` center-type dispatch (GeoPoint vs coordinate array vs Point), `createSpatialFilter` within/intersects, null / non-map / unsupported-type guards. Existing specs: `spatialPresentTest/.../runtime/query/SpatialFilterFactorySpec.groovy`, `NitriteSpatialSpec`, `NitriteSpatialCriteriaSpec`. Extend with real `@Query` spatial finders + geometry inputs.
- **`NitriteOperationsFactory.loadSpatialModule` / `loadRocksDbModule`** — module-load branches, exercised only in the present-test source sets. Lower value (infra, not query/store).

Spatial is real query behavior (geometry → filter), reachable, and safe (additive tests, no production edits). Start there.

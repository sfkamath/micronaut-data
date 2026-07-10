# Gemini tasks — close remaining Nitrite coverage gaps

Baseline: snapshot 25 — module **88.1% instr / 89.0% line / 72.2% br**.
Gaps below are the real, coverable residuals (see `coverage-up.md` for full context).

## Ground rules (read first)

- **Branch workflow:** write test/production changes on `5.0.x-nitrite-rebased`.
  Then rebase `benchmark-5.0.x-new` onto it
  (`git rebase 5.0.x-nitrite-rebased benchmark-5.0.x-new`). Docs-only commits go on
  `benchmark`. **Back up both branches before any rebase.**
- **Run tests:** Gradle path is `:micronaut-data-nitrite` (NOT `:data-nitrite`). Run the
  **full** `:micronaut-data-nitrite:coverageReport` then refresh coverage before judging
  any method — a single-spec run overwrites `jacoco.xml` and makes covered methods read 0%.
- **Spatial/transaction tests** live in dedicated source sets: `src/spatialPresentTest`
  (nitrite-spatial + JTS on runtime classpath) and the transactional specs. JTS/spatial are
  `compileOnly` in the main test set — absent at normal test runtime.
- **DO NOT delete production code to raise coverage.** Before calling anything "dead code,"
  verify against the actual dependency version on disk (sources are in the Gradle cache;
  `data-nitrite/build.gradle` resolves `org.dizitart:*:sources`). Example: the GeoPoint
  branch *looked* live but referenced `org.dizitart.no2.spatial.GeoPoint`, removed in
  nitrite 4.x — confirm that kind of claim, don't assume either way.
- Coverage queries: use `jvm-coverage-mcp` (`get_method_coverage`, `get_coverage_gaps`).
  Never read `jacoco.xml` directly.

## Gaps, ranked — each is a TDD task (write failing test first, then make it pass)

### 1. `DefaultNitriteRepositoryOperations.execute(PreparedQuery)` — 0% (`:875`)
Raw `@Query` execute path (update/delete-style `@Query` returning a result).
**Highest value, currently untouched.**
- Add a repository method annotated with an `@Query` that performs a raw operation, call it,
  assert the result.
- A commented-out `execute(PreparedQuery)` block exists in `CriteriaPersonRepositorySpec.groovy`
  — start there; it was stubbed but disabled. Build a real `PreparedQuery` path via a
  repository `@Query` method rather than mocking.

### 2. `NitriteEntityMapper` — 84.6% instr / 69% br
- `convertMapValue` (33%, `:751`) — exercise a `Map<String, T>`-typed entity property with
  non-trivial value types. Add an entity with `Map<String, SomeType>` and round-trip it.
- `serializeForDocument` (54%, `:843`) — custom serialization path; hit via a property whose
  type needs document serialization (nested object / embedded).
- `toNitriteFilterValue` (65%, `:165`) — filter-value coercion edges (enum, date, numeric).

### 3. `NitriteFieldNameResolver.asPath(Collection, PersistentProperty)` — 25% (`:45`)
Collection/association path resolution. Drive it through a criteria query that resolves a
nested association path (e.g. `root.get("assoc").get("field")`).

### 4. `NitriteQueryBinder.readSegmentValue(Object, String)` — 40% (`:330`)
Parameter-segment reading for `@Query` placeholders that traverse nested properties
(`:param.field`). Add a `@Query` method binding a nested property segment.

### 5. Transactional connection paths — both 0%
- `NitriteTransactionManager.getConnection()` (`:83`) and
  `NitriteConnectionOperations.setupConnection(ConnectionStatus)` (`:64`).
- Default test config is `transactional = false`. Add a spec with
  `@MicronautTest(transactional = true)` or an explicit `@Transactional` repository operation
  so the connection lifecycle runs. `NitriteTransactionSpec.groovy` already exists — extend it.

### 6. `DefaultNitriteRepositoryOperations` smaller edges
- `parseSortFromHints(Map)` 25% (`:618`) — pass `Sort` via `@QueryHint` / `Pageable.from(sort)`
  and assert ordering.
- `deleteAll(DeleteBatchOperation)` 64% (`:512`), `count(PagedQuery)` 66% — batch-delete and
  counted-paged-query branches.

### 7. `CollectionProjectionMapper.mapDocument` — 50% (`:87`), class branch 43% — **HARD, do last**
DTO projection path + native multi-field `return doc` branch are not reachable from a normal
`ObjectRepository`. Needs a `NitriteCollection`-level projection or a DTO `@Query` projection
harness. If after a genuine attempt a branch is structurally unreachable from supported
repository usage, **document it as such in `coverage-up.md`** — do not delete the code.

## Exclusions (do not chase — expected 0%, structurally uncoverable)
`io.micronaut.data.nitrite.model.query.builder.compile.*` (`CompileExpressionHandler`,
`RegexPattern`), `MicronautDataNitriteModuleInfo`, `NitriteStoredQuery` — these run inside the
annotation processor at build time, outside the JaCoCo agent.

## Definition of done
Each task: failing test → passing test → full `coverageReport` → refresh → confirm the target
method moved off its old %. Commit per-gap on `5.0.x-nitrite-rebased`, rebase benchmark, update
`coverage-up.md` + `nitrite-coverage-hotpath-report.md` to the new snapshot on benchmark.

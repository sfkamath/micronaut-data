# Coverage gap plan: data-nitrite → 90%+ instruction

Parsing recipes (how to measure coverage and hotpaths) live in
[coverage-parsing-guide.md](coverage-parsing-guide.md).

**Status: landed (snapshot 25 — module 88.1% instr / 89.0% line / 72.2% branch,
up from 80% / 66% baseline; was 84.5% / 69.1% at snapshot 13).** Status column
below reconciled to measured per-class coverage. ✅ = closed/high, 🟡 = partial
(real gap remains), ⬜ = untouched. Est.-gain column was always a guesstimate —
kept for context only.

## Where to add tests

| Status | Existing spec to extend | Classes covered | Measured now |
| ------ | ----------------------- | ---------------- | ------------ |
| ✅ done | NitriteQueryBuilderSpec | NitriteQueryBuilder, NitritePredicateVisitor, NitriteFilterBuilder | PredicateVisitor **98%** (was 42%); QueryBuilder 83%, FilterBuilder 89% |
| 🟡 partial | NitriteProjectionSpec | CollectionProjectionMapper, ObjectRepositoryMapper | ProjectionMapper 79% instr but **branch 43%** remains; ObjRepoMapper 88% |
| ✅ done | NitriteForwardAssociationLookupSpec | AssociationFilterResolver | 86% instr; `buildForwardLookupFilter` 100% line (FK-column bug fixed) |
| 🟡 partial | PersonRepositorySpec / DocumentRepositorySpec | DefaultNitriteRepositoryOperations, NitriteQueryExecutor | DefaultOps 86%, QueryExecutor 87% — incidental, no targeted gaps closed |
| ✅ done | NitriteUpsertSpec / NitriteUpsertLifecycleSpec / NitriteVersionSpec | ObjectRepositoryWriter, NitriteEntitiesOperations, NitriteEntityOperations | EntitiesOps 84% (`veto` 100%); ObjectRepositoryWriter **93%** (dead `getNextVersionValue` removed) |
| ✅ done | SpatialFilterFactorySpec (spatialPresentTest) | SpatialFilterFactory, NitritePredicateVisitor spatial methods | Visitor spatial **100%**; SpatialFilterFactory **92.6% instr / 72% br** (JTS spatial branches covered; GeoPoint branch retained — optional `nitrite-spatial` type, uncovered until 4.3.3 ships) |
| ✅ done | CollectionAggregatorSpec | CollectionAggregator | **98% instr / 87.5% branch** — executeAggregate + group-by/avg/sum landed |

**Top real gaps still open** (snapshot 25, lowest branch coverage first):
- `CollectionProjectionMapper` 78.7% instr / **42.9% branch** — `mapDocument` 50%; DTO
  path + native multi-field branches not reachable from normal ObjectRepository repos. HARD.
- `NitriteFieldNameResolver` 77.9% instr / 71% br — `asPath` 25% (collection-path edges).
- `NitriteEntityMapper` 84.6% instr / 69% br — residual: `convertMapValue` 33%,
  `serializeForDocument` 54%, `toNitriteFilterValue` 65%.
- `DefaultNitriteRepositoryOperations` 85.7% instr / 63% br — `execute(PreparedQuery)` 0%
  (raw `@Query`), `parseSortFromHints` 25%, `deleteAll`/`count` edges.
- `NitriteQueryBinder` 81% instr / 63% br — `readSegmentValue` 40%.
- `NitriteTransactionManager` `getConnection` 0% / `NitriteConnectionOperations`
  `setupConnection` 0% — non-transactional default test config; need transactional harness.

**Closed since snapshot 13:** `SpatialFilterFactory` 58%→92.6% (real JTS spatial tests
added), `CollectionAggregator` 63%→98%, `NitriteCriteriaExecutor`
`exists`/`findAll` 0%→100%, `NitriteFilterAST$SimpleOperatorNode` (multi-operator AND).
**Dead code removed:** `ObjectRepositoryWriter.getNextVersionValue` and
`NitriteEntityMapper.isGeometry` (both zero callers). NOTE: `GeoPoint`
(`org.dizitart.no2.spatial.GeoPoint`) is a real optional type in `nitrite-spatial`
(requires unreleased 4.3.3+); its reflective branches are forward-compat, NOT dead —
retained, with a coverage gap expected while the dep is absent from the test classpath.

`...builder.compile` (`CompileExpressionHandler`, `RegexPattern`) 0% is expected
(AP-only) — not a target.

## Spec-to-gap detail

Per-method targets. Verify each against a freshly generated `jacoco.xml` (see the
parsing guide) before checking it off — don't trust the estimates.

### NitriteQueryBuilderSpec
> ✅ Mostly landed — NitritePredicateVisitor is now 98% (visitIdEquals, visitRegexp,
> visitIn, getFieldNameForNullCheck, handleRegexExpression, valueRepresentation,
> and spatial visit methods all covered). Residual: `visitIn`/`visitLogical`/
> `visit(NegatedPredicate)` small branch gaps only.
- lookup — join queries with `@Join` / `findBy` traversing associations
- buildLimitAndOffset — paginated queries (`Pageable`)
- buildInsert — insert-style criteria path
- visitIdEquals — `findById` via criteria
- visitRegexp — `findByNameMatches`
- visitStartsWith / visitEndsWith / visitContains / visitInBetween — derived query methods
- visitNear / visitGeoWithin / visitGeoIntersects — move to NitriteSpatialSpec
- visitIn / visitNotEquals — `findByNameIn(...)`, `findByAgeNotEqual`
- getFieldNameForNullCheck — `findByNameIsNull` / `findByNameIsNotNull`
- handleRegexExpression / valueRepresentation — regex and value-coercion edge cases

### NitriteProjectionSpec
- CollectionProjectionMapper — projections on `NitriteCollection` (not `ObjectRepository`)
- ObjectRepositoryMapper.projectDto — DTO projection from `ObjectRepository`

### NitriteForwardAssociationLookupSpec
> ✅ Landed — `buildForwardLookupFilter` 100% line (FK-column bug fixed: was
> filtering on raw assoc name `widget` instead of `widget_id`). `looksLikeId` and
> `buildNestedFilter` also exercised. Class 86% instr / 64% branch.
- AssociationFilterResolver.buildForwardLookupFilter — forward join (filter child by parent ID)
- AssociationFilterResolver.looksLikeId — filtering an association by an ID-like value
- buildNestedFilter — deeper association chains

### CollectionAggregatorSpec
> ✅ Landed — 98% instr / 87.5% br.
- CollectionAggregator.executeAggregate — `count()` / `sum()` / `avg()`, empty collection, multi-field group by ✅

### NitriteEntityMetaDispatchSpec / NitriteEntityMapperSpec
- serializeForDocument — custom serialization path
- getEntityIdAsDocument — composite/embedded ID as document
- getMapValueByName / convertMapValue — `Map<String, T>` typed properties
- eqWithNumericCoercion — numeric type-mismatch equality (int vs long vs double)
- ~~isGeometry~~ — REMOVED (dead private method, zero callers)

### DefaultNitriteRepositoryOperations
- parseSortFromHints — `Sort` hints via `@QueryHint` (25% — still open)
- execute — raw execute path (`@Query` updates) (**0% — still open**)
- ~~findStream / findAll~~ — ✅ covered
- ~~exists~~ — ✅ covered (`existsById`)
- deleteAll — `deleteAll(Iterable<ID>)` overload (64% — partial)

### NitriteUpsertSpec / NitriteUpsertLifecycleSpec
- ~~ObjectRepositoryWriter.getNextVersionValue~~ — REMOVED (dead code, no callers)
- NitriteEntitiesOperations.veto — batch operation veto path ✅

### NitriteTransactionSpec
- NitriteCriteriaExecutor.findAll / exists — ✅ covered (100%) via CriteriaPersonRepositorySpec

### SpatialFilterFactory (SpatialFilterFactorySpec, spatialPresentTest)
> ✅ Landed — 92.6% instr / 72% br. JTS Point/Coordinate/Geometry paths and
> missing-library guards are now exercised. GeoPoint branch retained (optional
> `nitrite-spatial` type, needs unreleased 4.3.3+) — gap expected, not dead code.
- buildNearFilter — Near filter distance/unit/null-geometry branches ✅
- createSpatialFilter — unsupported spatial filter fallback path ✅

---

# Hotpath / performance analysis

Separate from coverage. Parsing recipes in
[coverage-parsing-guide.md](coverage-parsing-guide.md).

## From-memory hypotheses (suspects, not findings)

1. **Per-row entity↔Document mapping** — `NitriteEntityMapper` →
   `WritablePropertyMeta` dispatch → `ValueConverter` → `NitriteTypeRegistry`
   lookup, executed per property per row. On any benchmark returning large
   result sets this dominates. `NitriteEntityMeta` is cached per type, so the
   suspect is the per-value converter/registry lookup, not metadata resolution.
2. **Association lookups = extra DB round-trips** — `buildReverseLookupFilter` /
   `buildForwardLookupFilter` / `JoinFetcher` issue sub-queries per association.
   Classic N+1. Hot on any `findByAssoc*` or join-fetch benchmark.
3. **Filter binding per execution** — compiled filter is cached at
   `DefaultNitriteStoredQuery`, so parse+compile should be once; `bind()` runs
   every call. If the benchmark shows query-by-id/simple-filter as surprisingly
   costly, binding allocation is the suspect.
4. **PathResolver.resolve** — triple linear scan over
   `getPersistentProperties()` with 3 fallbacks, per field. Only hot if it runs
   per-execution; if it's behind the compiled-filter cache it's a one-time cost
   and irrelevant. The single biggest "depends" — needs confirming where it sits
   relative to the cache.

**Gating question for all four:** is filter construction cached or per-call?
That one fact reorders the whole list.

## Deep-dive jobs (mechanical extraction + one join)

### A. Rank the hotpath globally, then restrict to our code

```sh
R=data-nitrite/build/jvm-hotpath/execution-report.json
# per-file total hits, main sources only
jq '[.files[] | select(.path|test("nitrite")) | {file:.path, total:([.counts[]]|add // 0)}] | sort_by(.total) | reverse | .[0:25]' $R
# hottest individual lines in mapping + query packages
jq '[.files[] | select(.path|test("runtime/(mapping|query)")) | .path as $p | (.counts|to_entries[]) | {file:$p, line:(.key|tonumber), hits:.value}] | sort_by(.hits) | reverse | .[0:30]' $R
```

### B. Extract benchmark facts
From `/Users/sfk/Desktop/benchmark-results-analysis.md` (outside the repo): per-scenario name, throughput/latency, and
(if present) alloc rate or GC. Flag the slowest 3–5 scenarios and whether each is
read / write / query-by-association.

### C. Map code paths to scenarios
For each slow scenario, list the main-source classes on its execution path
(e.g. read → `NitriteEntityMapper`/`ValueConverter`; assoc-query →
`AssociationFilterResolver`/`JoinFetcher`).

### D. Join (the actual deliverable)
A table — scenario → its hottest lines (from A) → cost (from B) → candidate
optimization. Anything both high-hits *and* on a slow scenario's path is a real
target; high-hits on a fast scenario is noise.

Also answer the gating question explicitly: does filter construction happen once
(cached in `DefaultNitriteStoredQuery`) or per `findAll`? — by checking call
sites of `buildFilterFromJson` / `PathResolver.resolve` against where the cached
compiled filter is used.

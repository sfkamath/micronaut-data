 -# Nitrite Module Coverage & Hotpath Analysis Report: Refactor Conclusion

**Generated:** 2026-06-04
**Scope:** Post-Refactor (Compile/Runtime Split)
**Goal:** Document the resolution of uninstrumentable AP-only coverage gaps and define the path forward for remaining runtime coverage gaps.

---

## 1. Executive Summary: Post-Refactor State
The refactor successfully decoupled Annotation Processor (AP)-only literal handling from runtime query infrastructure.

- **"High Signal" Coverage achieved:** Uninstrumentable `LiteralExpression` branches have been moved to the `compile` package and excluded from JaCoCo.
- **Accountability Restored:** Structural runtime features (Joins, Projections, AST binding) remain tracked, revealing legitimate gaps that require additional testing.
- **Coverage Accuracy:** JaCoCo reports now reflect achievable runtime code paths only.

---

## 2. Coverage Gap Inventory (Post-Refactor)

These are the remaining gaps in the "Accountable" runtime code, which now represent valid testable features.

### 2.1 NitriteQueryBuilderHelper (Structural Runtime Features)
| Method | Gap | Status |
| :--- | :--- | :--- |
| `addLookups` | Composite Key Joins | Untested |
| `buildProjection` | `COUNT`/`COUNT_DISTINCT` | Untested |
| `buildProjection` | Compound Selections | Untested |

### 2.2 NitriteFilterAST (Binding Logic)
| Method | Gap | Status |
| :--- | :--- | :--- |
| `toFilter` | Complex Nested AND/OR | Untested |

### 2.3 NitritePredicateVisitor (Unsupported Runtime Operations)
| Method | Gap | Status |
| :--- | :--- | :--- |
| `appendOperatorExpression` | Unsupported JPA Ops (`length`, `trim`) | Requires error-path testing |
| `getFieldNameForNullCheck` | Embedded Association paths | Requires fixture testing |

---

## 3. Phased Execution Plan (Remaining Coverage)

Following the structure of the previous report, this plan focuses on exercising the identified runtime gaps.

| Phase | Goal | Focus | Artifacts |
| :--- | :--- | :--- | :--- |
| **Phase 1** | **Structural Logic** | Composite Keys, Aggregations | `NitriteQueryBuilderHelper` |
| **Phase 2** | **Complex Filtering** | Nested AND/OR, Unsupported Ops | `NitriteFilterAST`, `NitritePredicateVisitor` |
| **Phase 3** | **Projection Cleanup** | Resolve `CollectionProjectionMapper` (Dead Code) | `CollectionProjectionMapper` |

### Detailed Execution Plan

#### Phase 1: Structural Runtime Coverage (Medium Effort)
*   **Composite Key Joins**: Create a new integration spec exercising `Join` on multiple fields.
*   **Projections**: Extend `NitriteProjectionSpec` to include tests for `COUNT`, `COUNT_DISTINCT`, and compound tuple projections.

#### Phase 2: Complex Filtering & Error Paths (Hard Effort)
*   **Error-path tests**: 8 tests written in `NitriteCriteriaSpec.groovy` covering `BinaryExpression` (SUM/DIFF/PROD), `UnaryExpression` (LOWER/UPPER/LENGTH), composite identity `visitIdEquals`, and single-predicate conjunction path.
*   **Gaps (deferred)**: `NitriteFilterAST` (empty children, multi-operator) requires `NitriteFilterBuilder` construction with real entity mapper — risk of NPE without execution. `ExistsSubqueryPredicate` needs `PersistentEntitySubquery` instance. Several branches unreachable through public Criteria API (`$in→$nin` edge, `visitIn` Collection branch, `handleRegexExpression` Map branch).

#### Phase 3: Cleanup (Low Effort)
*   **Dead Code Resolution**: Finalize the investigation into `CollectionProjectionMapper`. If determined to be duplicate/unused (likely, given current `NitriteQueryExecutor` usage), delete it to simplify the codebase.


---
## 5. Coverage Baseline & Phase 1 Results

### Pre-Phase Implementation (Baseline)

| Class | Instruction % | Branch % | Missed Instr |
| :--- | :--- | :--- | :--- |
| `NitriteQueryBuilderHelper` | 70% | 58% | 148 |
| `NitriteFilterAST` | 100% | 100% | 0 |
| `NitritePredicateVisitor` | 84% | 58% | 176 |
| `CollectionProjectionMapper`| 79% | 43% | 20 |

### Post-Phase 1 (After Compound Selection + Join Tests)

| Class | Instruction % | Branch % | Missed Instr | Δ Instr | Δ Branch |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `NitriteQueryBuilderHelper` | **74%** | **63%** | **128** | +4pp | +5pp |
| `NitriteFilterAST` | 100% | 100% | 0 | — | — |
| `NitritePredicateVisitor` | 84% | 58% | 176 | — | — |
| `CollectionProjectionMapper`| 79% | 43% | 20 | — | — |

**Phase 1 impact:** `NitriteQueryBuilderHelper` instruction coverage improved from 70%→74%, branch coverage from 58%→63%. The remaining 128 missed instructions are in the list-based `lookup` overload (composite-key join helper, 106 instr — at this stage believed unreachable; later disproved and covered to 86%, see below) and a few edge-case branches in `addLookups` and `buildProjection` lambdas. The uncovered `visitIdEquals` (39 instr) and `visitIn` lambda (7 instr) remain in `NitritePredicateVisitor`.

### Post-Composite FK + Final Phase 1

| Class | Instruction % | Branch % | Missed Instr | Δ Instr | Δ Branch |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `NitriteQueryBuilderHelper` | **94%** | **74%** | **33** | +24pp | +16pp |
| `NitriteFilterAST` | 100% | n/a | 0 | — | — |
| `NitritePredicateVisitor` | 84% | 58% | 176 | — | — |
| `CollectionProjectionMapper`| 79% | 43% | 20 | — | — |

*\*`NitriteFilterAST` has no branches (5 instructions, 1 line) — it is a thin shell; the AST logic lives in separate classes counted elsewhere.*

**Composite FK resolution:** The last remaining coverage gap in `NitriteQueryBuilderHelper` — the list-based `lookup` overload — is now exercised via the composite FK join test (`CompositeFkChild` with two `@JoinColumn` annotations). The method went from 0% (106/106 missed) to 86% (15/106), then to **89% instruction / 75% branch** (11/96, 1/4) after removing a strictly-unreachable `matches.size() == 1` ternary (the single-field case already returns via the `localFields.size() == 1` guard). The one remaining uncovered branch is that guard's single-field path, kept as a cheap defensive check. The 3 remaining uncovered instructions in `addLookups` and 16 in `buildProjection` are edge-case branches in projection handling.

---

### Post-Phase 2 (After NitriteCriteriaSpec error-path + edge-case tests)

| Class | Instruction % | Branch % | Missed Instr | Δ Instr | Δ Branch |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `NitriteQueryBuilderHelper` | 94% | 74% | 33 | — | — |
| `NitriteFilterAST` | 100% | n/a | 0 | — | — |
| `NitritePredicateVisitor` | **98%** | **83%** | **19** | +14pp | +25pp |
| `CollectionProjectionMapper`| 78% | 42% | 20 | — | — |

**Phase 2 impact:** `NitritePredicateVisitor` instruction coverage improved from 84%→98%, branch coverage from 58%→83%. Tests added to `NitriteCriteriaSpec` covered: `getFieldNameForNullCheck` embedded path (`Settlement` `@EmbeddedId`), `visitIn` BindingParameter + empty-collection branches, `visitIdEquals` composite-ID throw + single-ID success path, `handleRegexExpression` non-path early return, `ExistsSubqueryPredicate` throw, negated `between`, single-predicate conjunction, and `BinaryExpression`/`UnaryExpression` unsupported-op throws.

**Remaining 19 missed instructions in `NitritePredicateVisitor`:**
| Method | Line | Instr% | Miss | Note |
|--------|-----:|-------:|-----:|------|
| `visitIn` | 257 | 87% | 10 | nested-collection empty branch + `resolvedValues.isEmpty()` defensive path |
| `visitLogical` | 420 | 98% | 1 | single missed branch |
| `visit` | 442 | 89% | 8 | partial — likely a specific predicate overload not yet exercised |

`CollectionProjectionMapper` (78%, 20 missed) unchanged — Phase 3 dead-code investigation pending.

---

### Post-Phase 4 (Type-Diverse Mapping round-trip via Event extension)

| Class | Instruction % | Branch % | Missed Instr | Δ Instr | Δ Branch |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `NitriteEntityMapper` | **77%** | **63%** | **573** | +0pp | +20pp |
| `NitriteQueryBuilderHelper` | 94% | 74% | 33 | — | — |
| `NitriteFilterAST` | 100% | n/a | 0 | — | — |
| `NitritePredicateVisitor` | 98% | 83% | 19 | — | — |
| `CollectionProjectionMapper`| 78% | 42% | 20 | — | — |

**Phase 4 impact:** `NitriteEntityMapper` branch coverage +20pp to 63% via type-diverse round-trip test (`TypeDiverseEventSpec`). Extended existing `Event` entity with diverse field types: enum Status, temporal types (LocalDate, LocalDateTime, Instant), BigDecimal amount, byte[] data, List<String> tags, Map<String, String> metadata, Optional<String> optionalDescription, nested @Embeddable EventLocation. Instruction coverage remains ~77% because core gaps (`serializeForDocument` 0%, `getEntityIdAsDocument` 0%, `isGeometry` 0%) require edge cases (Map<K,ComplexV>, circular refs, geometry types).

---

### Post-Phase 5 (Aggregation execution tests)

| Class | Instruction % | Branch % | Missed Instr | Δ Instr | Δ Branch |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `CollectionAggregator` | **68%** | **60%** | **58** | +18.4pp | +25pp |
| `NitriteEntityMapper` | 77% | 63% | 573 | — | — |
| `NitriteQueryBuilderHelper` | 96% | 74% | 33 | — | — |
| `NitritePredicateVisitor` | 98% | 83% | 19 | — | — |
| `CollectionProjectionMapper`| 78% | 42% | 20 | — | — |

**Phase 5 impact:** `CollectionAggregator` branch coverage +25pp to 60.4%, line coverage +18.4pp to 68.4%. Tests cover:
- Numeric aggregation: max, min, sum, avg on Integer/Double via Document mocks
- Temporal aggregation: max, min on LocalDate/LocalDateTime
- Helper methods: extractAggFunc(), extractFieldName(), isAggregationMethod()
- Edge cases: empty docs, null docs, snake_case field name conversion
- Remaining gaps (~44 missed): String date parsing fallback, Comparable fallback (generic max/min), sum/avg on temporal types (unsupported by design)

Extended EventRepository with aggregation method declarations for future integration testing (not yet wired by AP).

---

## 6. Module-Wide Gap Inventory (Full Report, all 3 suites)

Source: full `coverageReport` (test + rocksDbPresentTest + spatialPresentTest), 2026-06-04 21:34.
Module instruction coverage **83%** (15763/18929). Phases 1–2 above closed the
builder/visitor seam (`NitritePredicateVisitor` now 98%, `NitriteQueryBuilderHelper`
94%). Remaining gaps are **whole runtime execution flows** (map → operate → resolve →
aggregate), not builder string paths — they are reached by **repository integration
tests**, not in-process `getQuery()` assertions.

### 6.1 Uncoverable / excluded (do not chase)

| Class | Missed | Why uncoverable |
| :--- | ---: | :--- |
| `compile/CompileExpressionHandler` | 189 | AP-only — runs at build time, outside JaCoCo agent (guide §5.3). 0% is correct. |
| `compile/RegexPattern` | 6 | Same `compile/**` package; AP-only. |
| `MicronautDataNitriteModuleInfo` | 15 | Generated module descriptor. |

**195 instr structurally invisible.** Excluding `compile/**` + module-info, the real
runtime denominator is ~18725 → module ≈ **84%**. These do not move with any test.

### 6.2 Real runtime targets (ranked by missed instruction)

| Class | Missed | Instr% | Hot method(s) | Phase |
| :--- | ---: | ---: | :--- | :--- |
| `NitriteEntityMapper` | 589 | 77% | `fromDocumentInternal` 78%, `toDocumentValue` 40%, `serializeForDocument` **0%**, `getEntityIdAsDocument` **0%**, `eqWithNumericCoercion` 81% | **4** |
| `DefaultNitriteRepositoryOperations` | 248 | 84% | broad CRUD/batch/tx | **6** |
| `AssociationFilterResolver` (+`$FieldFilterProvider` 128) | 333 | 73% | `buildForwardLookupFilter` **0%**, `looksLikeId` **0%**, reverse 74% | **7** |
| `NitriteFilterBuilder$SubQueryExecutor` | 179 | 59% | `buildNearFilter` 60%, `createSpatialFilter` 53% | **8** |
| `NitriteQueryBinder` | 174 | 81% | binder paths | **6** |
| `CollectionAggregator` | 165 | 49% | `executeAggregate` **30%** (137 in one method) | **5** |
| `NitriteQueryExecutor` / `NitriteEntitiesOperations` / `NitriteEntityOperations` | 157 / 153 / 107 | 84/84/88% | execution + entity ops breadth | **6** |
| `NitriteCollectionRegistry` | 76 | 77% | collection lifecycle | **6** |
| `NitriteFilterAST$SimpleOperatorNode` | 63 | 41% | operator-node eval | **7** |
| `NitriteFieldNameResolver` / `ObjectRepositoryWriter` | 58 / 39 | 78/57% | name resolution, writer | **6** |

---

## 7. Phased Execution Plan — Module Expansion (Phases 4–8)

| Phase | Goal | Target | Effort | Delegatable |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 4** | Type-diverse entity round-trip | `NitriteEntityMapper` | **Low** | Haiku |
| **Phase 5** | Aggregation execution | `CollectionAggregator` | **Medium** | Sonnet/Haiku |
| **Phase 6** | Repository operations breadth | `DefaultNitriteRepositoryOperations`, executors, binder, registry | **Medium** | Sonnet |
| **Phase 7** | Association / join resolution | `AssociationFilterResolver`, `SimpleOperatorNode` | **Hard** | self / Sonnet w/ guardrails |
| **Phase 8** | Geo / spatial subqueries | `SubQueryExecutor` (spatialPresentTest) | **Hard** | self / Sonnet |

### Phase 4: Type-Diverse Mapping (Low Effort) — biggest easy win

`serializeForDocument` (50, **0%**) and `getEntityIdAsDocument` (38, **0%**) are never
entered because no test persists the value shapes that trigger them. Pure save→find
round-trip, no query logic.

*   **One type-rich fixture entity** + repo: fields covering enum, `LocalDate`/`Instant`,
    `BigDecimal`, `byte[]`, nested `@Embeddable`, `List<String>`/`Map`, nullable wrapper,
    UUID id, generated-vs-assigned id. Save, find, assert equality.
*   Hits `toDocumentValue` (74, 40%), `fromDocumentInternal` (112, 78%),
    `serializeForDocument` (50, 0%), `getEntityIdAsDocument` (38, 0%),
    `convertToDocumentInternal` (40, 81%) in one suite. **~250 missed reachable.**
*   Mechanical, no join/Optional.empty traps (guide §5.5 N/A) → **Haiku-safe** with an
    explicit field list.

### Phase 5: Aggregation Execution (Medium Effort) — densest single method

`CollectionAggregator.executeAggregate` = **137 missed in ONE method at 30%.** Highest
single-method leverage in the module.

*   Aggregation queries via repo: `count`, `countDistinct`, `sum`, `avg`, `min`, `max`,
    `group by` + `having`, multi-field grouping.
*   Mind the round-trip trap (guide §5.4/§5.5): assert returned aggregate values, not
    builder JSON. Pattern-repetitive once first test lands → **Sonnet/Haiku** with the
    operation list.

### Phase 6: Repository Operations Breadth (Medium Effort)

Broad incidental coverage; partly lifted free by Phases 4/5/7. Targeted top-ups:

*   `DefaultNitriteRepositoryOperations` (248): batch insert/update/delete, `updateAll`,
    `deleteAll`, `findAll(Pageable)`, transactional paths.
*   `NitriteQueryBinder` (174), `NitriteQueryExecutor` (157), `NitriteEntitiesOperations`
    (153), `NitriteEntityOperations` (107), `NitriteCollectionRegistry` (76),
    `NitriteFieldNameResolver` (58), `ObjectRepositoryWriter` (39).
*   Mostly CRUD/pageable/sort ITs → **Sonnet**.

### Phase 7: Association / Join Resolution (Hard Effort)

`buildForwardLookupFilter` (69, **0%**) and `looksLikeId` (46, **0%**) never run — no test
queries *across* a `@Relation`. High trap risk (guide §5.5: join must be referenced in the
predicate; correct annotation type; `Optional.empty()` for unpersisted shapes).

*   Forward lookup: `cb.equal(join.get("x"), …)` across `@Relation(ONE_TO_MANY/MANY_TO_ONE)`.
*   Reverse lookup (32, 74%), nested (29, 90%), `SimpleOperatorNode` eval (63, 41%).
*   **Self or Sonnet with explicit guardrails** — same class of bug that stalled earlier
    join work. Not Haiku.

### Phase 8: Geo / Spatial Subqueries (Hard Effort)

`SubQueryExecutor.buildNearFilter` (118, 60%) + `createSpatialFilter` (61, 53%) — 179 in
`spatialPresentTest` source set. Needs JTS geometry fixtures + `nitrite-spatial`.

*   `near`/`within`/`intersects` queries over `Point`/`Polygon` fields.
*   Specialized (geometry construction, spatial index) → **self or Sonnet**, not Haiku.

### Projected impact

Phases 4–5 alone ≈ **300+ reachable missed** (type mapping + aggregation), mechanical,
delegatable — module 84% → ~86%. Adding 7–8 (association + geo, ~330) → ~88%. Phase 6 is
the long tail toward 90%+.

### Delegation summary

| Difficulty | Phases | Who | Why |
| :--- | :--- | :--- | :--- |
| Low | 4 | **Haiku** | pure round-trip, explicit field list, no traps |
| Medium | 5, 6 | **Sonnet / Haiku** | pattern-repetitive once seeded |
| Hard | 7, 8 | **self / Sonnet+guardrails** | join semantics + geometry; high `Optional.empty` trap risk |

The hard reasoning (dead-vs-defensive classification, AP-vs-runtime seam) is **already
done** in Phases 1–2. Remaining work is test authoring against known targets → safe to
delegate by difficulty tier above.


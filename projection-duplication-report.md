# Projection Duplication Analysis: CollectionProjectionMapper Wiring

## Executive Summary
**Duplication introduced: YES**. The wiring of `CollectionProjectionMapper` has introduced:
1. **Dead code**: Three convenience overload pairs in `CollectionProjectionMapper` are never called.
2. **Abandoned legacy methods**: `ObjectRepositoryMapper.projectDto()` and all three `CollectionFieldMapper.project()` overloads are now dead code (no remaining callers after the rewire).
3. **Logic duplication**: `CollectionProjectionMapper.mapDocument()` duplicates the DTO/field extraction logic that previously lived in `ObjectRepositoryMapper.projectDto()` and `CollectionFieldMapper.project()`.

---

## Caller Analysis

### CollectionProjectionMapper Methods

| Method | Called From | Live Calls |
|--------|-------------|-----------|
| `mapResults(cursor, fields, resultType, isDto)` (line 62, convenience) | None | **DEAD** |
| `mapResults(cursor, fields, entity, resultType, isDto)` (line 77, full) | `NitriteQueryExecutor:289`, `NitriteQueryExecutor:309` | ✓ LIVE (2 callers) |
| `mapDocument(doc, fields, resultType, isDto)` (line 98, convenience) | None | **DEAD** |
| `mapDocument(doc, fields, entity, resultType, isDto)` (line 114, full) | `NitriteQueryExecutor:192`, `NitriteQueryExecutor:200` | ✓ LIVE (2 callers) |
| `mapSingleField(doc, fieldName, resultType)` (line 141, convenience) | None | **DEAD** |
| `mapSingleField(doc, fieldName, entity, resultType)` (line 155, full) | None | **DEAD** (never used) |
| `getProjectedValue(doc, fieldName, entity)` (line 163, private) | Called internally by `mapDocument` and `mapSingleField` | ✓ LIVE |

### ObjectRepositoryMapper Methods

| Method | Called From | Live Calls |
|--------|-------------|-----------|
| `loadEntity(doc, entityType)` (line 52) | `NitriteQueryExecutor:208`, `NitriteQueryExecutor:317` | ✓ LIVE (2 callers) |
| `projectDto(doc, dtoType)` (line 68) | None | **DEAD** (replaced by `projectionMapper.mapDocument()` at `NitriteQueryExecutor:192`) |

### CollectionFieldMapper Methods

| Method | Called From | Live Calls |
|--------|-------------|-----------|
| `project(doc, fieldName, resultType)` (line 67, convenience) | None | **DEAD** |
| `project(doc, fieldName, entity, resultType)` (line 81, full) | None | **DEAD** (replaced by `projectionMapper.mapDocument()` at `NitriteQueryExecutor:200`) |
| `project(doc, query, methodName, entity, resultType)` (line 104, 3-param query variant) | None | **DEAD** |
| `extractFieldName(query, methodName)` (line 124) | `NitriteQueryExecutor:197`, `NitriteQueryExecutor:300` | ✓ LIVE (field name extraction only) |

---

## Duplication Details

### 1. Convenience Overloads Are Never Called
**Files**: `/Users/sfk/Developer/micronaut-data/data-nitrite/src/main/java/io/micronaut/data/nitrite/runtime/read/CollectionProjectionMapper.java`

- Lines 62-64: `mapResults(cursor, fields, resultType, isDto)` delegates to line 77 variant
- Lines 98-100: `mapDocument(doc, fields, resultType, isDto)` delegates to line 114 variant  
- Lines 141-143: `mapSingleField(doc, fieldName, resultType)` delegates to line 155 variant

**Verdict**: These are **convenience overloads** but `NitriteQueryExecutor` always passes the `entity` parameter (see lines 192, 200, 289, 309 of `NitriteQueryExecutor.java`), so the convenience variants are never invoked. They add surface area without benefit.

### 2. Logic Duplication in mapDocument vs projectDto
**Files**:
- `CollectionProjectionMapper:114-130` (mapDocument full body)
- `ObjectRepositoryMapper:68-73` (projectDto method)

Both methods call `entityMapper.fromDocument(doc, resultType)` to map a document to a DTO. However:
- `ObjectRepositoryMapper.projectDto()` is now **unreachable** after the rewire.
- The logic is duplicated in `CollectionProjectionMapper.mapDocument()` lines 125.

### 3. Logic Duplication in mapDocument vs project (single-field overload)
**Files**:
- `CollectionProjectionMapper:114-130` (mapDocument, handles single-field case at lines 119-122)
- `CollectionFieldMapper:81-91` (project with entity, single-field extraction)

Both perform the same operation for single-field projections:
```java
// CollectionProjectionMapper:119-122
String normalized = entityMapper.normalizeFieldName(fieldName, entity);
Object value = doc.get(normalized);
if (value == null && !normalized.equals(fieldName)) {
    value = doc.get(fieldName);
}
return valueConverter.convert(value, resultType);

// CollectionFieldMapper:85-90 (identical logic)
String normalized = entityMapper.normalizeFieldName(fieldName, entity);
Object value = doc.get(normalized);
if (value == null && !normalized.equals(fieldName)) {
    value = doc.get(fieldName);
}
return valueConverter.convert(value, resultType);
```

Yet `CollectionFieldMapper.project()` is now **unreachable**.

---

## Impact Timeline: Before vs After Rewire

### Before (HEAD~3):
```
NitriteQueryExecutor
├─ DTO projection → entityMapperHandler.projectDto()
├─ Single-field projection → nativeProjectionHandler.project(doc, fieldName, entity, resultType)
└─ Full entity → entityMapperHandler.loadEntity()
```

### After (HEAD):
```
NitriteQueryExecutor
├─ DTO projection → projectionMapper.mapDocument(doc, fields=[], entity, resultType, isDto=true)
├─ Single-field projection → projectionMapper.mapDocument(doc, fields=[fieldName], entity, resultType, isDto=false)
└─ Full entity → entityMapperHandler.loadEntity()
```

**Result**: `ObjectRepositoryMapper.projectDto()` and all `CollectionFieldMapper.project()` overloads became orphaned.

---

## Recommendations

### 1. **Remove Convenience Overloads (Safe)**
**Action**: Delete the following from `CollectionProjectionMapper.java`:
- Lines 62-64: `mapResults(cursor, fields, resultType, isDto)` convenience overload
- Lines 98-100: `mapDocument(doc, fields, resultType, isDto)` convenience overload
- Lines 141-143: `mapSingleField(doc, fieldName, resultType)` convenience overload

**Rationale**: These are never called; they only add unnecessary API surface and create false flexibility. `NitriteQueryExecutor` always has the entity metadata available and uses the full overloads.

**Lines affected**: 62-64, 98-100, 141-143 (total ~10 lines)

---

### 2. **Delete mapSingleField (Full Overload) - Unused**
**Action**: Delete the following from `CollectionProjectionMapper.java`:
- Lines 141-161: Both the convenience overload and the full overload for `mapSingleField()`

**Rationale**: 
- Neither overload is called anywhere in the codebase (not by `NitriteQueryExecutor` or any tests).
- The functionality is subsumed by `mapDocument()` when `fields.size() == 1`.
- This method was likely added as future-proofing but is not used.

**Lines affected**: 141-161 (total ~21 lines)

---

### 3. **Delete Dead Methods in ObjectRepositoryMapper (Safe)**
**Action**: Delete from `ObjectRepositoryMapper.java`:
- Lines 60-73: `projectDto()` method (entire Javadoc + body)

**Rationale**: 
- No remaining callers after rewire (was called at `NitriteQueryExecutor:189` in HEAD~3, now routed through `projectionMapper.mapDocument()` at line 192).
- Functionality is now centralized in `CollectionProjectionMapper.mapDocument()`.
- Keeping it creates false alternative paths and violates the "single responsibility" principle of the rewire.

**Lines affected**: 59-73 (total ~15 lines)

---

### 4. **Delete Dead Methods in CollectionFieldMapper (Safe)**
**Action**: Delete from `CollectionFieldMapper.java`:
- Lines 58-91: All `project()` overloads (both convenience and full body)
  - Convenience: lines 67-69
  - Full with entity: lines 81-91
- Lines 93-115: The 3-parameter query-based `project()` overload

**Rationale**:
- These were called at:
  - `NitriteQueryExecutor:195` (old code, now `projectionMapper.mapDocument()`)  
  - Nested `project()` call within the 3-param overload (internal, no external callers)
- Functionality is now unified in `CollectionProjectionMapper.mapDocument()` and `mapSingleField()`.
- `extractFieldName()` (line 124) must be kept; it is still called by `NitriteQueryExecutor:197` and `:300`.

**Lines affected**: 58-115 (total ~57 lines, but keep lines 117-137 for `extractFieldName()`)

**Note**: Retain `CollectionFieldMapper` as a class for `extractFieldName()` or move it to a utility if desired.

---

## Summary Table: Cleanup Scope

| Artifact | Current Status | Safe to Delete | Impact |
|----------|----------------|---|---|
| `CollectionProjectionMapper.mapResults(4-param)` | Dead code | ✓ YES | Remove convenience overload |
| `CollectionProjectionMapper.mapDocument(4-param)` | Dead code | ✓ YES | Remove convenience overload |
| `CollectionProjectionMapper.mapSingleField(both)` | Never used | ✓ YES | Remove 21 lines (unused method) |
| `ObjectRepositoryMapper.projectDto()` | Dead code | ✓ YES | Remove 15 lines (orphaned after rewire) |
| `CollectionFieldMapper.project(all 3 overloads)` | Dead code | ✓ YES | Remove ~55 lines (functionality moved to `projectionMapper`) |
| `CollectionFieldMapper.extractFieldName()` | Live (2 callers) | ✗ NO | Keep for field name extraction |

**Total lines safe to delete**: ~105 lines of dead/duplicate code.

---

## Verification
To confirm the safety of these deletions, run:
```bash
cd data-nitrite
mvn clean verify -Pquality
```

All tests should pass, confirming that no code path depends on the deleted methods.

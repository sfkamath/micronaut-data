# Plan: Nitrite Compile-Time vs. Runtime Package Split

## 1. Objective
Resolve "Coverage Drag" in the `micronaut-data-nitrite` module by structuring the
packages so that **compile-time (Annotation Processor) code is unambiguously
separated from runtime code**.

The aim is *not* to inflate coverage numbers. It is to remove ambiguity: once
compile-only code lives in its own package, every remaining gap in the runtime
coverage report is a *genuine, achievable* runtime branch that simply lacks a
test — never an "ignore it, that's AP code" artifact. This lets us raise runtime
coverage honestly, without false presumptions about what is and isn't reachable.

## 2. Research & Identification
XML-based JaCoCo analysis showed ~25% of the "missing" coverage in
`NitritePredicateVisitor` and `NitriteQueryBuilder` came from branches handling
`LiteralExpression`.

### Key Findings
- **Annotation Processor (AP) path**: uses `LiteralExpression` to inline values
  (e.g. `{"age": 18}`) and generates static regexes via `Pattern.quote`.
- **Runtime path**: uses `BindingParameter` (e.g. `{"age": "$mn_qp:0"}`) for safe,
  dynamic query execution.
- **Instrumentability**: JaCoCo cannot instrument the AP during compilation, so
  literal-handling branches are physically impossible to cover under standard
  runtime tests. They drag the runtime numbers down with noise.

## 3. The Split

### A. Strategic Decoupling
Introduce `NitriteExpressionHandler` to abstract the value-resolution seam, with
two implementations:
- **`CompileExpressionHandler`** — all `LiteralExpression`, `Pattern.quote`, and
  literal SQL→regex logic. Lives in
  `io.micronaut.data.nitrite.model.query.builder.compile`. **AP-only.**
- **`RuntimeExpressionHandler`** — `BindingParameter` resolution and standard
  runtime value conversion.

`NitriteQueryBuilder` is environment-aware: it selects `CompileExpressionHandler`
when constructed with `AnnotationMetadata` (AP context) and defaults to
`RuntimeExpressionHandler` for standard runtime/criteria execution.

### B. What stays in the runtime accountability zone
- **`NitriteQueryBuilderHelper`** and the `ast` package stay **included** in
  coverage. They are used by the AP but are shared **infrastructure** that also
  runs at runtime (Joins, Projections, filter binding). Observed runtime
  coverage: `NitriteQueryBuilderHelper` ~69%, AST ~90%. Keeping them visible
  keeps real structural gaps accountable.
- Only code that evaluates source-code literals and bakes them into the query is
  "genuinely" compile-time. That, and only that, belongs in `compile`.

## 4. Verification Gate — BEFORE enabling the exclude
The JaCoCo exclude for `...builder.compile.**` is **intentionally left commented
out** in `build.gradle`:

```groovy
//  exclude 'io/micronaut/data/nitrite/model/query/builder/compile/**'
```

This is a deliberate verification step, not an oversight. With the exclude OFF we
run the coverage report and inspect the `compile` package:

- **Expected: 0 runtime coverage** across the entire `compile` package. If a class
  in `compile` truly is AP-only, no runtime test can touch it, so it must report
  0 covered instructions.
- **If any class in `compile` reports > 0 runtime coverage**, it was
  **misclassified** — it is reachable at runtime and does not belong in `compile`.
  Move it back into a runtime-visible package and re-verify.

Only once the entire `compile` package is confirmed at 0 runtime coverage do we
uncomment the exclude. At that point excluding it removes pure noise without
hiding any reachable branch.

## 5. Definition of Done
1. All literal/`Pattern.quote`/AP-only logic isolated in `...builder.compile`.
2. Coverage report (exclude OFF) shows `compile.**` at **0** runtime coverage —
   confirming nothing reachable was swept in.
3. Exclude uncommented; report regenerated.
4. Resulting runtime coverage for `NitritePredicateVisitor` /
   `NitriteQueryBuilder` reflects only achievable branches, so remaining gaps are
   actionable test targets.

### Notes
- Package layout (`model.query.builder`) stays aligned with Micronaut Data's
  document-store conventions (Mongo / Azure Cosmos).
- Build sanity so far: `./gradlew :micronaut-data-nitrite:classes` compiles.

# Targeted Benchmark Workflow

`run-benchmarks.sh` can benchmark selected commits instead of walking every
commit in the default range. This is useful for focused before/after checks,
such as the Nitrite 4.4.2 version bump.

## Target Commit Examples

Pass one or more target commits explicitly when you only need a focused
comparison. For example, the Nitrite 4.4.2 version-change check used:

```text
7312169386  before Nitrite 4.4.2
d3dd60e1c8  bump Nitrite to 4.4.2
```

Run only a selected pair:

```bash
benchmarks/benchmark-micronaut-data-nitrite/run-benchmarks.sh \
    --commit 7312169386 \
    --commit d3dd60e1c8 \
    --results-file benchmarks/benchmark-micronaut-data-nitrite/build/jmh/nitrite-version-bump-results.csv
```

## Keeping the Harness Diff Clean (preferred over rerere)

The script attaches the benchmark harness to older commits with:

```bash
git rebase --onto "$target_commit" "$harness_parent" "$harness_commit"
```

The harness's only footprint in `data-nitrite/build.gradle` is now two small
insertions, both anchored to the `plugins {` block's invariant opening line
(`id "io.micronaut.build.internal.data-module"` has been the first entry in
that block since the module's inception):

```gradle
plugins {
    id "io.github.sfkamath.llm-build-compactor" version "0.4.2"
    id "io.github.sfkamath.jvm-hotpath" version "0.2.12"
    id "jacoco"
    id "io.micronaut.build.internal.data-module"
    ...
}
apply from: "$rootDir/data-nitrite/data-nitrite-harness.gradle"
```

Everything else the harness needs (jacoco tasks, hotpath config, coverage
tasks) lives in the sidecar `data-nitrite/data-nitrite-harness.gradle`, a
brand-new file the rebase just adds — new files never conflict. Because both
hunks in `build.gradle` are pinned to content that hasn't moved across the
module's history, the harness commit now rebases onto old commits (verified
back to `7312169386`, previously the documented conflict case below) with
**no conflict at all** — no `rerere` needed.

If you extend the harness and the diff footprint grows again, prefer
re-anchoring the insertion to invariant lines (as above) over reaching for
`rerere`. `rerere` is a valid fallback, but it's last resort: it papers over
an unstable diff shape instead of fixing it, and a recorded resolution only
covers the *exact* conflict shape it was recorded against — a shape that
silently drifts as the harness file changes.

## Fallback: Pre-Recording a Rebase Conflict Resolution with rerere

If a future harness change reintroduces a conflict that can't be avoided by
anchoring to stable content (e.g. the insertion genuinely depends on
surrounding context that varies across history), fall back to recording the
resolution once with Git `rerere` before running the targeted script. This
lets Git reuse the same resolution during the automated benchmark run.

1. Make sure `rerere` is enabled:

   ```bash
   git config rerere.enabled true
   git config rerere.autoupdate true
   ```

2. From the benchmark harness branch, capture the current harness commits:

   ```bash
   HARNESS_COMMIT=$(git rev-parse HEAD)
   HARNESS_PARENT=$(git rev-parse HEAD^)
   ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)
   ```

3. Manually attach the harness to the conflicting target commit:

   ```bash
   git rebase --onto "$target_commit" "$HARNESS_PARENT" "$HARNESS_COMMIT"
   ```

4. Resolve `data-nitrite/build.gradle` as appropriate for the harness commit
   and target historical commit.

5. Stage the resolved file and continue the rebase once so `rerere` records
   the reusable postimage:

   ```bash
   git add data-nitrite/build.gradle
   GIT_EDITOR=true git rebase --continue
   ```

   Git should print:

   ```text
   Recorded resolution for 'data-nitrite/build.gradle'.
   ```

6. Return to the benchmark branch:

   ```bash
   git checkout "$ORIGINAL_BRANCH"
   ```

7. Optional: repeat the manual transplant to verify `rerere` is ready.

   Git will still report that the cherry-pick/rebase stopped, but with
   `rerere.autoupdate=true` it should also report:

   ```text
   Staged 'data-nitrite/build.gradle' using previous resolution.
   ```

   Abort that verification rebase and return to the benchmark branch:

   ```bash
   git rebase --abort
   git checkout "$ORIGINAL_BRANCH"
   ```

8. Run the targeted benchmark script.

   On the same conflict shape, `rerere` should apply the recorded resolution
   automatically. With `rerere.autoupdate=true`, Git should also stage the
   resolved file during the script's rebase step. The script then runs
   `git rebase --continue` and benchmarks the resolved checkout.

## Commit File Option

For repeatable target sets, put commits or ranges in a file:

```text
# Example focused comparison
7312169386
d3dd60e1c8
```

Then run:

```bash
benchmarks/benchmark-micronaut-data-nitrite/run-benchmarks.sh \
    --commit-file benchmarks/benchmark-micronaut-data-nitrite/nitrite-version-bump.commits \
    --results-file benchmarks/benchmark-micronaut-data-nitrite/build/jmh/nitrite-version-bump-results.csv
```

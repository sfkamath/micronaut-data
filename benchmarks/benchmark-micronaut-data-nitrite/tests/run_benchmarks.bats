#!/usr/bin/env bats

setup() {
    export TEST_ROOT="${BATS_TMPDIR}/run-benchmarks-${BATS_TEST_NUMBER}"
    export MOCK_DIR="$TEST_ROOT/mocks"
    export FAKE_PROJECT_ROOT="$TEST_ROOT/project"
    export FAKE_SCRIPT_DIR="$TEST_ROOT/benchmark-module"
    export BENCHMARK_RESULTS_FILE="$TEST_ROOT/results/benchmark-results.csv"
    export BENCHMARK_SCRIPT_DIR="$FAKE_SCRIPT_DIR"
    export BENCHMARK_PROJECT_ROOT="$FAKE_PROJECT_ROOT"
    export BENCHMARK_JMH_RESULTS="$FAKE_SCRIPT_DIR/build/results/jmh/results.csv"

    mkdir -p "$MOCK_DIR" "$FAKE_PROJECT_ROOT" "$FAKE_SCRIPT_DIR"
    export PATH="$MOCK_DIR:$PATH"

    cat > "$MOCK_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "$*" in
    "rev-parse --abbrev-ref HEAD")
        echo "test-branch"
        ;;
    "rev-parse HEAD")
        echo "harness-commit"
        ;;
    "rev-parse HEAD^")
        echo "harness-parent"
        ;;
    rev-parse\ --verify\ --quiet\ *^{commit})
        value="$4"
        echo "${value%%^*}"
        ;;
    "rev-list --reverse 9cc22d4e2e^..harness-parent")
        echo "commit1"
        echo "commit2"
        ;;
    "rev-list --reverse abc..def")
        echo "range1"
        echo "range2"
        ;;
    log\ -1\ --format=%s\ *)
        echo "message for ${*:4}"
        ;;
    rebase\ --onto\ *)
        echo "rebase $*" >> "${TEST_ROOT}/git-calls.log"
        if [[ "$*" == *"rerere-ready"* ]]; then
            exit 1
        fi
        ;;
    "diff --name-only --diff-filter=U")
        ;;
    "rebase --continue")
        echo "rebase --continue" >> "${TEST_ROOT}/git-calls.log"
        ;;
    "checkout -q test-branch")
        echo "checkout test-branch" >> "${TEST_ROOT}/git-calls.log"
        ;;
    *)
        echo "unexpected git invocation: $*" >&2
        exit 99
        ;;
esac
EOF
    chmod +x "$MOCK_DIR/git"

    cat > "$FAKE_PROJECT_ROOT/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

echo "Mock gradle run"
mkdir -p "$(dirname "$BENCHMARK_JMH_RESULTS")"
cat > "$BENCHMARK_JMH_RESULTS" <<'CSV'
benchmark,mode,threads,samples,score,error,units
"mock.Benchmark","thrpt",1,1,100,,ops/s
CSV
EOF
    chmod +x "$FAKE_PROJECT_ROOT/gradlew"

    export SCRIPT_TO_TEST="${BATS_TEST_DIRNAME}/../run-benchmarks.sh"
    export QUICK_SCRIPT="${BATS_TEST_DIRNAME}/../run-quick.sh"
    export ANALYZE_SCRIPT="${BATS_TEST_DIRNAME}/../analyze-results.sh"
}

teardown() {
    rm -rf "$TEST_ROOT"
}

@test "quick mode benchmarks current HEAD without rebasing" {
    run bash "$SCRIPT_TO_TEST" --quick

    [ "$status" -eq 0 ]
    [[ "$output" == *"Running quick benchmark on current HEAD: harness-commit"* ]]
    [[ "$output" == *"Mock gradle run"* ]]
    [ -f "$BENCHMARK_RESULTS_FILE" ]
    grep -q '^harness-commit,message for harness-commit,mock.Benchmark,thrpt,1,1,100,,ops/s$' "$BENCHMARK_RESULTS_FILE"
    [ ! -f "$TEST_ROOT/git-calls.log" ]
}

@test "default cross-commit mode benchmarks configured history" {
    run bash "$SCRIPT_TO_TEST"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Found 2 commits to benchmark"* ]]
    [[ "$output" == *"Rebasing harness onto: commit1"* ]]
    [[ "$output" == *"Rebasing harness onto: commit2"* ]]
    grep -q '^commit1,message for commit1,mock.Benchmark,thrpt,1,1,100,,ops/s$' "$BENCHMARK_RESULTS_FILE"
    grep -q '^commit2,message for commit2,mock.Benchmark,thrpt,1,1,100,,ops/s$' "$BENCHMARK_RESULTS_FILE"
    grep -q 'checkout test-branch' "$TEST_ROOT/git-calls.log"
}

@test "explicit commit list selects only requested commits" {
    run bash "$SCRIPT_TO_TEST" --commits "abc123,def456"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Found 2 commits to benchmark"* ]]
    [[ "$output" == *"Rebasing harness onto: abc123"* ]]
    [[ "$output" == *"Rebasing harness onto: def456"* ]]
    ! grep -q '^commit1,' "$BENCHMARK_RESULTS_FILE"
}

@test "commit range expands through git rev-list" {
    run bash "$SCRIPT_TO_TEST" --commits "abc..def"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Rebasing harness onto: range1"* ]]
    [[ "$output" == *"Rebasing harness onto: range2"* ]]
}

@test "rerere-resolved rebase continues and benchmarks commit" {
    run bash "$SCRIPT_TO_TEST" --commit rerere-ready

    [ "$status" -eq 0 ]
    [[ "$output" == *"Rebase continued after applying recorded conflict resolution"* ]]
    grep -q 'rebase --continue' "$TEST_ROOT/git-calls.log"
    grep -q '^rerere-ready,message for rerere-ready,mock.Benchmark,thrpt,1,1,100,,ops/s$' "$BENCHMARK_RESULTS_FILE"
}

@test "commit file ignores comments and blank lines" {
    commit_file="$TEST_ROOT/commits.txt"
    cat > "$commit_file" <<'EOF'
# before nitrite bump
7312169386

d3dd60e1c8 # nitrite bump
EOF

    run bash "$SCRIPT_TO_TEST" --commit-file "$commit_file"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Rebasing harness onto: 7312169386"* ]]
    [[ "$output" == *"Rebasing harness onto: d3dd60e1c8"* ]]
}

@test "quick mode rejects commit selection" {
    run bash "$SCRIPT_TO_TEST" --quick --commit abc123

    [ "$status" -eq 2 ]
    [[ "$output" == *"--quick cannot be combined with commit selection options"* ]]
}

@test "run-quick.sh uses shared benchmark helpers" {
    run bash "$QUICK_SCRIPT"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Running quick benchmark on current HEAD: harness-commit"* ]]
    [[ "$output" == *"Mock gradle run"* ]]
    grep -q '^harness-commit,message for harness-commit,mock.Benchmark,thrpt,1,1,100,,ops/s$' "$BENCHMARK_RESULTS_FILE"
}

@test "analyze-results.sh uses configured result paths" {
    mkdir -p "$(dirname "$BENCHMARK_RESULTS_FILE")"
    cat > "$BENCHMARK_RESULTS_FILE" <<'EOF'
commit,commit_message,benchmark,mode,threads,samples,score,error,units
commit1,first,mock.Benchmark,thrpt,1,1,100,,ops/s
commit2,second,mock.Benchmark,thrpt,1,1,125,,ops/s
EOF
    export BENCHMARK_ANALYSIS_FILE="$TEST_ROOT/results/analysis.md"

    run bash "$ANALYZE_SCRIPT"

    [ "$status" -eq 0 ]
    [[ "$output" == *"Benchmark Analysis"* ]]
    [ -f "$BENCHMARK_ANALYSIS_FILE" ]
    grep -q 'commit1' "$BENCHMARK_ANALYSIS_FILE"
    grep -q 'commit2' "$BENCHMARK_ANALYSIS_FILE"
}

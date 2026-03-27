#!/bin/bash
#
# Run JMH benchmarks across all commits and accumulate results.
#
# Setup (once before running):
#   git add benchmarks/ && git commit -m "chore(benchmarks): add JMH harness"
#
# For each target commit the script rebases the harness commit on top of it,
# runs benchmarks, then moves on — no checkout of uncommitted state.
#
# Outputs: build/jmh/benchmark-results.csv
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/build/jmh"
RESULTS_FILE="$RESULTS_DIR/benchmark-results.csv"
JMH_DEFAULT_RESULTS="$SCRIPT_DIR/build/results/jmh/results.csv"
GRADLE_TASK=":micronaut-benchmarks:micronaut-benchmark-micronaut-data-nitrite:jmh"
START_COMMIT="bec66351ac65c182e86eea732029ba5606eaf834"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Captured at startup so the trap and restore step can always get back
ORIGINAL_BRANCH=""
HARNESS_COMMIT=""
HARNESS_PARENT=""

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Perf commits to benchmark: START_COMMIT inclusive up to just before the harness
get_commits() {
    git log --oneline --reverse "${START_COMMIT}^..${HARNESS_PARENT}" | awk '{print $1}'
}

get_commit_message() {
    git log -1 --format="%s" "$1"
}

run_benchmark() {
    local commit=$1
    local commit_msg=$2

    log_info "Rebasing harness onto: $commit ($commit_msg)"

    # Place the harness commit on top of the target; leaves HEAD detached.
    if ! git rebase --onto "$commit" "$HARNESS_PARENT" "$HARNESS_COMMIT" -q 2>/dev/null; then
        log_warn "Rebase conflict on $commit, skipping"
        git rebase --abort 2>/dev/null || true
        return 0
    fi

    log_info "Running benchmarks..."
    rm -f "$JMH_DEFAULT_RESULTS"

    # Some early commits predate logback.xml; create it temporarily so Micronaut
    # doesn't abort with LoggingSystemException.
    local logback="$SCRIPT_DIR/src/main/resources/logback.xml"
    local logback_created=false
    if [ ! -f "$logback" ]; then
        mkdir -p "$(dirname "$logback")"
        printf '<configuration>\n    <root level="WARN"/>\n</configuration>\n' > "$logback"
        logback_created=true
    fi

    cd "$PROJECT_ROOT"
    ./gradlew $GRADLE_TASK --rerun-tasks -DllmCompactor.enabled=false -q 2>/dev/null || true

    $logback_created && rm -f "$logback"

    if [ -f "$JMH_DEFAULT_RESULTS" ]; then
        # JMH CSV quotes string fields — strip quotes, then prepend commit metadata.
        # Commas in the commit message are replaced with semicolons to avoid skewing fields.
        local safe_msg="${commit_msg//,/;}"
        tail -n +2 "$JMH_DEFAULT_RESULTS" | tr -d '"' | while IFS=, read -r benchmark mode threads samples score error units; do
            echo "$commit,$safe_msg,$benchmark,$mode,$threads,$samples,$score,$error,$units"
        done >> "$RESULTS_FILE"
        log_info "Results saved for $commit"
    else
        log_warn "No results file generated for $commit"
    fi
}

main() {
    # Validate: must be on a branch with the harness committed at HEAD
    ORIGINAL_BRANCH=$(git rev-parse --abbrev-ref HEAD)
    if [ "$ORIGINAL_BRANCH" = "HEAD" ]; then
        log_error "Detached HEAD detected. Commit the benchmark harness first, then run this script."
        exit 1
    fi

    HARNESS_COMMIT=$(git rev-parse HEAD)
    HARNESS_PARENT=$(git rev-parse HEAD^)

    log_info "Harness commit : $HARNESS_COMMIT"
    log_info "Start commit   : $START_COMMIT"
    log_info "Results file   : $RESULTS_FILE"

    mkdir -p "$RESULTS_DIR"
    echo "commit,commit_message,benchmark,mode,threads,samples,score,error,units" > "$RESULTS_FILE"

    local commits=($(get_commits))
    local total=${#commits[@]}
    local current=1

    log_info "Found $total commits to benchmark"

    for commit in "${commits[@]}"; do
        log_info "Processing commit $current/$total"
        local msg=$(get_commit_message "$commit")
        run_benchmark "$commit" "$msg"
        ((current++))
    done

    log_info "Restoring branch to $ORIGINAL_BRANCH"
    git checkout -q "$ORIGINAL_BRANCH"

    log_info "========================================="
    log_info "Benchmark accumulation complete!"
    log_info "Results saved to: $RESULTS_FILE"
    log_info "========================================="

    if [ -f "$RESULTS_FILE" ] && [ $(wc -l < "$RESULTS_FILE") -gt 1 ]; then
        log_info "Summary by commit:"
        tail -n +2 "$RESULTS_FILE" | cut -d',' -f1,2 | sort -u | while read line; do
            echo "  $line"
        done
    fi
}

# On interrupt: clean up any temp logback.xml, abort any in-progress rebase, restore branch
trap 'log_warn "Interrupted! Restoring..."; rm -f "$SCRIPT_DIR/src/main/resources/logback.xml"; git rebase --abort 2>/dev/null || true; [ -n "$ORIGINAL_BRANCH" ] && git checkout -q "$ORIGINAL_BRANCH"; exit 1' INT TERM

main "$@"

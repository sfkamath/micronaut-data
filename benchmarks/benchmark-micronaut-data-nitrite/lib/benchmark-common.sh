#!/bin/bash

if [ -z "${SCRIPT_DIR:-}" ]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi

BENCHMARK_MODULE_DIR="${BENCHMARK_SCRIPT_DIR:-$SCRIPT_DIR}"
PROJECT_ROOT="${BENCHMARK_PROJECT_ROOT:-$(cd "$BENCHMARK_MODULE_DIR/../.." && pwd)}"
if [ -n "${BENCHMARK_RESULTS_FILE:-}" ]; then
    RESULTS_FILE="$BENCHMARK_RESULTS_FILE"
    RESULTS_DIR="$(dirname "$RESULTS_FILE")"
else
    if [ -n "${RESULTS_FILE:-}" ]; then
        RESULTS_DIR="${BENCHMARK_RESULTS_DIR:-$(dirname "$RESULTS_FILE")}"
    else
        RESULTS_DIR="${BENCHMARK_RESULTS_DIR:-$BENCHMARK_MODULE_DIR/build/jmh}"
        RESULTS_FILE="$RESULTS_DIR/benchmark-results.csv"
    fi
fi

JMH_DEFAULT_RESULTS="${BENCHMARK_JMH_RESULTS:-$BENCHMARK_MODULE_DIR/build/results/jmh/results.csv}"
GRADLE_TASK="${BENCHMARK_GRADLE_TASK:-:micronaut-benchmarks:micronaut-benchmark-micronaut-data-nitrite:jmh}"
RESULTS_HEADER="commit,commit_message,benchmark,mode,threads,samples,score,error,units"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { printf '%b[INFO]%b %s\n' "$GREEN" "$NC" "$1"; }
log_warn() { printf '%b[WARN]%b %s\n' "$YELLOW" "$NC" "$1"; }
log_error() { printf '%b[ERROR]%b %s\n' "$RED" "$NC" "$1" >&2; }

require_value() {
    local option=$1
    local value=${2:-}

    if [ -z "$value" ]; then
        log_error "$option requires a value"
        return 1
    fi
}

get_commit_message() {
    git log -1 --format="%s" "$1"
}

sanitize_csv_field() {
    local value=$1
    value="${value//\"/}"
    value="${value//,/;}"
    value="${value//$'\r'/ }"
    value="${value//$'\n'/ }"
    printf '%s' "$value"
}

prepare_logback() {
    local logback="$BENCHMARK_MODULE_DIR/src/main/resources/logback.xml"

    if [ -f "$logback" ]; then
        printf 'false:%s' "$logback"
        return
    fi

    mkdir -p "$(dirname "$logback")"
    printf '<configuration>\n    <root level="WARN"/>\n</configuration>\n' >"$logback"
    printf 'true:%s' "$logback"
}

run_gradle_benchmark() {
    local logback_state
    local logback_created
    local logback_path

    rm -f "$JMH_DEFAULT_RESULTS"
    logback_state=$(prepare_logback)
    logback_created="${logback_state%%:*}"
    logback_path="${logback_state#*:}"

    (
        cd "$PROJECT_ROOT"
        ./gradlew "$GRADLE_TASK" --rerun-tasks -DllmCompactor.enabled=false -q
    ) || true

    if [ "$logback_created" = true ]; then
        rm -f "$logback_path"
    fi
}

write_results_header() {
    mkdir -p "$RESULTS_DIR"
    printf '%s\n' "$RESULTS_HEADER" >"$RESULTS_FILE"
}

append_jmh_results() {
    local commit=$1
    local commit_msg=$2
    local safe_msg

    if [ ! -f "$JMH_DEFAULT_RESULTS" ]; then
        log_warn "No results file generated for $commit"
        return 1
    fi

    safe_msg=$(sanitize_csv_field "$commit_msg")
    tail -n +2 "$JMH_DEFAULT_RESULTS" | tr -d '"' | while IFS=, read -r benchmark mode threads samples score error units; do
        [ -z "${benchmark:-}" ] && continue
        printf '%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
            "$commit" "$safe_msg" "$benchmark" "$mode" "$threads" "$samples" "$score" "$error" "$units"
    done >>"$RESULTS_FILE"
    return 0
}

benchmark_current_checkout() {
    local commit=$1
    local commit_msg=$2

    log_info "Running benchmarks..."
    run_gradle_benchmark

    if append_jmh_results "$commit" "$commit_msg"; then
        log_info "Results saved for $commit"
    fi
}

print_results_summary() {
    log_info "========================================="
    log_info "Benchmark accumulation complete!"
    log_info "Results saved to: $RESULTS_FILE"
    log_info "========================================="

    if [ -f "$RESULTS_FILE" ] && [ "$(wc -l <"$RESULTS_FILE")" -gt 1 ]; then
        log_info "Summary by commit:"
        tail -n +2 "$RESULTS_FILE" | cut -d',' -f1,2 | sort -u | while IFS= read -r line; do
            printf '  %s\n' "$line"
        done
    fi
}

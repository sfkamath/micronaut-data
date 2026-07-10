#!/bin/bash
#
# Compatibility entry point for a current-HEAD Nitrite benchmark run.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_FILE="${BENCHMARK_RESULTS_FILE:-${BENCHMARK_SCRIPT_DIR:-$SCRIPT_DIR}/build/jmh/results-$(date +%Y%m%d-%H%M%S).csv}"

# shellcheck source=benchmarks/benchmark-micronaut-data-nitrite/lib/benchmark-common.sh
source "$SCRIPT_DIR/lib/benchmark-common.sh"

commit=$(git rev-parse HEAD)
message=$(get_commit_message "$commit")

log_info "Running quick benchmark on current HEAD: $commit ($message)"
log_info "Results file: $RESULTS_FILE"

write_results_header
benchmark_current_checkout "$commit" "$message"
print_results_summary

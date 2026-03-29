#!/bin/bash
#
# Quick benchmark runner - runs at current HEAD and saves results.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/build/jmh"
RESULTS_FILE="$RESULTS_DIR/results-$(date +%Y%m%d-%H%M%S).csv"
JMH_DEFAULT_RESULTS="$SCRIPT_DIR/build/results/jmh/results.csv"
GRADLE_TASK=":micronaut-benchmarks:micronaut-benchmark-micronaut-data-nitrite:jmh"

mkdir -p "$RESULTS_DIR"
rm -f "$JMH_DEFAULT_RESULTS"

echo "Running benchmarks..."
echo "Results: $RESULTS_FILE"

cd "$PROJECT_ROOT"
./gradlew $GRADLE_TASK --rerun-tasks -DllmCompactor.enabled=false

if [ -f "$JMH_DEFAULT_RESULTS" ]; then
    cp "$JMH_DEFAULT_RESULTS" "$RESULTS_FILE"
    echo ""
    echo "Results saved to: $RESULTS_FILE"
    echo ""
    echo "Quick summary:"
    column -t -s',' "$RESULTS_FILE"
else
    echo "No results file generated."
    exit 1
fi

#!/bin/bash
#
# Run JMH benchmarks for the Nitrite module at the current checkout or across
# selected historical commits with this harness commit rebased on top.
#
# Examples:
#   ./run-benchmarks.sh
#   ./run-benchmarks.sh --quick
#   ./run-benchmarks.sh --commit 7312169386 --commit d3dd60e1c8
#   ./run-benchmarks.sh --commits "7312169386,d3dd60e1c8"
#   ./run-benchmarks.sh --commits "7312169386..d3dd60e1c8"
#   ./run-benchmarks.sh --commit-file commits.txt
#
# Outputs: build/jmh/benchmark-results.csv
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=benchmarks/benchmark-micronaut-data-nitrite/lib/benchmark-common.sh
source "$SCRIPT_DIR/lib/benchmark-common.sh"

START_COMMIT="${BENCHMARK_START_COMMIT:-9cc22d4e2e}"

ORIGINAL_BRANCH=""
HARNESS_COMMIT=""
HARNESS_PARENT=""
QUICK=false
SELECTED_SPECS=""
COMMIT_FILES=""

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Modes:
  --quick                 Benchmark the current checkout without rebasing.
  --commit <rev>          Benchmark one commit. Can be repeated.
  --commits <spec>        Benchmark comma/space-separated commits or rev ranges.
  --commit-file <path>    Read commits/ranges from a file. Blank lines and # comments are ignored.

Selection:
  --start-commit <rev>    Default history start when no explicit commits are selected.
  --results-file <path>   Write the accumulated CSV to this path.
  --help                  Show this help.

Default mode benchmarks every commit from START_COMMIT through the harness parent.
Range specs use git rev-list --reverse, for example 7312169386..d3dd60e1c8.
EOF
}

parse_args() {
    while [ "$#" -gt 0 ]; do
        case "$1" in
        --quick)
            QUICK=true
            ;;
        --commit)
            require_value "$1" "${2:-}" || {
                usage
                exit 2
            }
            SELECTED_SPECS="${SELECTED_SPECS}${2}"$'\n'
            shift
            ;;
        --commits)
            require_value "$1" "${2:-}" || {
                usage
                exit 2
            }
            SELECTED_SPECS="${SELECTED_SPECS}${2}"$'\n'
            shift
            ;;
        --commit-file)
            require_value "$1" "${2:-}" || {
                usage
                exit 2
            }
            COMMIT_FILES="${COMMIT_FILES}${2}"$'\n'
            shift
            ;;
        --start-commit)
            require_value "$1" "${2:-}" || {
                usage
                exit 2
            }
            START_COMMIT="$2"
            shift
            ;;
        --results-file)
            require_value "$1" "${2:-}" || {
                usage
                exit 2
            }
            RESULTS_FILE="$2"
            RESULTS_DIR="$(dirname "$RESULTS_FILE")"
            shift
            ;;
        --help | -h)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 2
            ;;
        esac
        shift
    done

    if [ "$QUICK" = true ] && { [ -n "$SELECTED_SPECS" ] || [ -n "$COMMIT_FILES" ]; }; then
        log_error "--quick cannot be combined with commit selection options"
        exit 2
    fi
}

append_commit_file_specs() {
    local file
    local line

    while IFS= read -r file || [ -n "$file" ]; do
        [ -z "$file" ] && continue
        if [ ! -f "$file" ]; then
            log_error "Commit file not found: $file"
            exit 2
        fi

        while IFS= read -r line || [ -n "$line" ]; do
            line="${line%%#*}"
            line="${line//$'\t'/ }"
            line="${line#"${line%%[![:space:]]*}"}"
            line="${line%"${line##*[![:space:]]}"}"
            [ -z "$line" ] && continue
            SELECTED_SPECS="${SELECTED_SPECS}${line}"$'\n'
        done <"$file"
    done <<<"$COMMIT_FILES"
}

expand_commit_spec() {
    local spec=$1
    local token

    spec="${spec//,/ }"
    for token in $spec; do
        if [[ $token == *".."* ]]; then
            git rev-list --reverse "$token"
        else
            git rev-parse --verify --quiet "${token}^{commit}"
        fi
    done
}

get_commits() {
    local spec

    append_commit_file_specs

    if [ -z "$SELECTED_SPECS" ]; then
        git rev-list --reverse "${START_COMMIT}^..${HARNESS_PARENT}"
        return
    fi

    while IFS= read -r spec || [ -n "$spec" ]; do
        [ -z "$spec" ] && continue
        expand_commit_spec "$spec"
    done <<<"$SELECTED_SPECS" | awk '!seen[$0]++'
}

continue_rebase_if_resolved() {
    local unmerged

    unmerged=$(git diff --name-only --diff-filter=U)
    if [ -n "$unmerged" ]; then
        return 1
    fi

    GIT_EDITOR=true git rebase --continue
}

run_rebased_benchmark() {
    local commit=$1
    local commit_msg=$2

    log_info "Rebasing harness onto: $commit ($commit_msg)"

    if ! git rebase --onto "$commit" "$HARNESS_PARENT" "$HARNESS_COMMIT" -q 2>/dev/null; then
        if continue_rebase_if_resolved; then
            log_info "Rebase continued after applying recorded conflict resolution"
        else
            log_warn "Rebase conflict on $commit, skipping"
            git rebase --abort 2>/dev/null || true
            return 0
        fi
    fi

    benchmark_current_checkout "$commit" "$commit_msg"
}

restore_branch() {
    if [ -n "$ORIGINAL_BRANCH" ]; then
        log_info "Restoring branch to $ORIGINAL_BRANCH"
        git checkout -q "$ORIGINAL_BRANCH"
    fi
}

run_quick_mode() {
    local commit
    local msg

    commit=$(git rev-parse HEAD)
    msg=$(get_commit_message "$commit")

    log_info "Running quick benchmark on current HEAD: $commit ($msg)"
    write_results_header
    benchmark_current_checkout "$commit" "$msg"
    print_results_summary
}

run_cross_commit_mode() {
    local commits_file
    local total
    local current=1
    local commit
    local msg

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

    write_results_header
    commits_file=$(mktemp "${TMPDIR:-/tmp}/run-benchmarks-commits.XXXXXX")
    get_commits >"$commits_file"
    total=$(wc -l <"$commits_file")
    total="${total//[[:space:]]/}"

    log_info "Found $total commits to benchmark"

    while IFS= read -r commit || [ -n "$commit" ]; do
        [ -z "$commit" ] && continue
        log_info "Processing commit $current/$total"
        msg=$(get_commit_message "$commit")
        run_rebased_benchmark "$commit" "$msg"
        ((current++))
    done <"$commits_file"
    rm -f "$commits_file"

    restore_branch
    print_results_summary
}

cleanup_on_interrupt() {
    log_warn "Interrupted! Restoring..."
    rm -f "$SCRIPT_DIR/src/main/resources/logback.xml"
    git rebase --abort 2>/dev/null || true
    restore_branch
    exit 1
}

main() {
    parse_args "$@"

    if [ "$QUICK" = true ]; then
        run_quick_mode
    else
        run_cross_commit_mode
    fi
}

trap cleanup_on_interrupt INT TERM

main "$@"

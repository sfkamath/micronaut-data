#!/bin/bash
#
# Analyze accumulated benchmark results.
# • Terminal  — benchmark x commit matrix with multi-shade RAG colour coding
# • Markdown  — build/jmh/benchmark-results-analysis.md  (arrows + plain numbers)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=benchmarks/benchmark-micronaut-data-nitrite/lib/benchmark-common.sh
source "$SCRIPT_DIR/lib/benchmark-common.sh"

MD_FILE="${BENCHMARK_ANALYSIS_FILE:-$RESULTS_DIR/benchmark-results-analysis.md}"

if [ ! -f "$RESULTS_FILE" ]; then
    log_error "Results file not found: $RESULTS_FILE"
    log_error "Run run-benchmarks.sh first."
    exit 1
fi

awk -F',' '
BEGIN {
    # ── ANSI colour palette ──────────────────────────────────────────────
    RESET = "\033[0m"
    G6    = "\033[7;32m"   # reverse green (bg) > +200 %
    G5    = "\033[1;4;32m" # bold + underline   +100 .. +200 %
    G4    = "\033[4;32m"   # underline green    +50 .. +100 %
    G3    = "\033[1;32m"   # bold green         +20 .. +50 %
    G2    = "\033[0;32m"   # green              +5 .. +20 %
    G1    = "\033[2;32m"   # dim green          +1 .. +5 %
    AM    = "\033[0;33m"   # amber              -1 .. +1 %
    R1    = "\033[2;31m"   # dim red            -1 .. -5 %
    R2    = "\033[0;31m"   # red                -5 .. -20 %
    R3    = "\033[1;31m"   # bold red           < -20 %

    nc = 0; nb = 0
    NUM_W = 10   # visible width of a score cell
    KEY_W = 44   # visible width of the benchmark label column
}

# Skip header line wherever it appears; commit hashes are hex so this is unambiguous
$1 == "commit" { next }

{
    commit = $1
    bench  = $3
    score  = $7 + 0
    p11 = $11; gsub(/[\r\n \t]/, "", p11)
    p10 = $10; gsub(/[\r\n \t]/, "", p10)
    param = (p11 != "") ? p11 : (p10 != "") ? p10 : ""

    # Strip carriage returns, newlines, and surrounding whitespace from param.
    # Without this, a trailing \r in param causes the key to contain \r,
    # which makes the terminal cursor jump back to column 0 when printing,
    # overwriting the row label with the closing bracket.
    gsub(/[\r\n \t]/, "", param)

    sub(/^benchmark\./, "", bench)
    key = (param != "") ? bench " [" param "]" : bench

    if (!(commit in seen_c)) {
        commits[++nc] = commit
        seen_c[commit] = nc
        cmsg[commit] = $2
    }
    if (!(key in seen_b)) {
        benchmarks[++nb] = key
        seen_b[key] = nb
    }

    scores[key, commit] = score
    have[key, commit]   = 1
}

# ── colour helpers ───────────────────────────────────────
function shade(pct,    c) {
    if      (pct > 200) c = G6
    else if (pct > 100) c = G5
    else if (pct >  50) c = G4
    else if (pct >  20) c = G3
    else if (pct >   5) c = G2
    else if (pct >   1) c = G1
    else if (pct >  -1) c = AM
    else if (pct >  -5) c = R1
    else if (pct > -20) c = R2
    else                c = R3
    return c
}

# markdown arrow indicator for a percentage change
function md_arrow(pct) {
    if      (pct > 200) return "↑↑↑↑↑↑"
    else if (pct > 100) return "↑↑↑↑↑"
    else if (pct >  50) return "↑↑↑↑"
    else if (pct >  20) return "↑↑↑"
    else if (pct >   5) return "↑↑"
    else if (pct >   1) return "↑"
    else if (pct >  -1) return "→"
    else if (pct >  -5) return "↓"
    else if (pct > -20) return "↓↓"
    else                return "↓↓↓"
}

function abs(x) { return (x >= 0) ? x : -x }

END {
    # ── build commit header arrays ───────────────────────
    # terminal header line
    t_hdr = sprintf("%-*s", KEY_W, "Benchmark")
    t_sep = ""
    for (k = 1; k <= KEY_W; k++) t_sep = t_sep "-"

    # markdown header + separator rows
    m_hdr = "| Benchmark"
    m_sep = "|---"

    for (i = 1; i <= nc; i++) {
        tag   = substr(commits[i], 1, 8)
        t_hdr = t_hdr " | " sprintf("%-*s", NUM_W, tag)
        t_sep = t_sep "-+-" sprintf("%-*s", NUM_W, "----------")
        m_hdr = m_hdr " | " tag
        m_sep = m_sep " |---:"
    }
    t_hdr = t_hdr " | Change"
    t_sep = t_sep "-+--------"
    m_hdr = m_hdr " | Change |"
    m_sep = m_sep " |---: |"

    # ── terminal header ──────────────────────────────────
    print "=============================================="
    print "Benchmark Analysis"
    print "=============================================="
    print ""
    print t_hdr
    print t_sep

    # ── markdown header ──────────────────────────────────
    print "# Benchmark Analysis" > MD_FILE
    print "" > MD_FILE
    print m_hdr > MD_FILE
    print m_sep > MD_FILE

    # ── data rows ────────────────────────────────────────
    for (b = 1; b <= nb; b++) {
        key   = benchmarks[b]
        label = key
        if (length(label) > KEY_W) label = substr(label, 1, KEY_W - 1) "~"

        t_row = sprintf("%-*s", KEY_W, label)
        m_row = "| " key

        first = ""; prev = ""

        for (i = 1; i <= nc; i++) {
            c = commits[i]

            if ((key SUBSEP c) in have) {
                s     = scores[key, c]
                t_cell = sprintf("%*d", NUM_W, s)
                m_cell = sprintf("%d", s)

                if (prev != "") {
                    pct    = (s - prev) / prev * 100
                    t_cell = shade(pct) t_cell RESET
                    m_cell = m_cell " " md_arrow(pct) sprintf("%.1f%%", pct)
                }

                if (first == "") first = s
                prev = s
            } else {
                t_cell = sprintf("%*s", NUM_W, "-")
                m_cell = "-"
            }

            t_row = t_row " | " t_cell
            m_row = m_row " | " m_cell
        }

        # overall first → last change
        if (first != "" && prev != "" && first != 0) {
            op  = (prev - first) / first * 100
            sgn = (op >= 0) ? "+" : ""
            t_row = t_row " | " shade(op) sprintf("%s%.1f%%", sgn, op) RESET
            m_row = m_row " | " md_arrow(op) sprintf(" %s%.1f%%", sgn, op)
        } else {
            t_row = t_row " |    N/A"
            m_row = m_row " | N/A"
        }

        print t_row
        print m_row " |" > MD_FILE
    }

    # ── commit legend ─────────────────────────────────────
    print ""
    print "Commits (oldest → newest):"
    print "" > MD_FILE
    print "## Commits (oldest → newest)" > MD_FILE
    print "" > MD_FILE

    for (i = 1; i <= nc; i++) {
        c   = commits[i]
        msg = cmsg[c]
        if (length(msg) > 64) msg = substr(msg, 1, 61) "..."
        short = substr(c, 1, 8)
        printf "  %s  %s\n",          short, msg
        printf "- `%s`  %s\n", short, msg > MD_FILE
    }

    # ── colour key (terminal only) ────────────────────────
    print ""
    printf "Colour key (vs previous commit):  "
    printf G6 ">+200%%" RESET "  "
    printf G5 "+100..200%%" RESET "  "
    printf G4 "+50..100%%" RESET "  "
    printf G3 "+20..50%%" RESET "  "
    printf G2 "+5..20%%" RESET "  "
    printf G1 "+1..5%%" RESET "  "
    printf AM "~neutral" RESET "  "
    printf R1 "-1..5%%" RESET "  "
    printf R2 "-5..20%%" RESET "  "
    printf R3 ">-20%%" RESET "\n"

    print "" > MD_FILE
    print "_Arrows: ↑/↓ small · ↑↑/↓↓ medium · ↑↑↑/↓↓↓ large change vs previous commit_" > MD_FILE

    print ""
    print "Full results : " RESULTS_FILE
    print "Markdown     : " MD_FILE
    print "=============================================="
}
' MD_FILE="$MD_FILE" RESULTS_FILE="$RESULTS_FILE" "$RESULTS_FILE"

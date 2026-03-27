#!/usr/bin/env python3
"""
Micronaut Data Nitrite Hotpath Analysis Script

Analyzes execution-report.json from jvmHotpathTest and outputs structured JSON.

Usage:
    ./gradlew :micronaut-data-nitrite:jvmHotpathTest --rerun-tasks
    python3 scripts/analyze-hotpath.py

Output: build/jvm-hotpath/hotpath-analysis.json
"""

import json
import re
from pathlib import Path
from dataclasses import dataclass, field, asdict
from typing import Optional


@dataclass
class LineExecution:
    line: int
    count: int


@dataclass
class Hotspot:
    rank: int
    file: str
    path: str
    method: str
    line_start: int
    line_end: int
    total_executions: int
    lines: list[LineExecution] = field(default_factory=list)
    source_snippet: str = ""
    recommendation: str = ""


def find_methods(content: str, counts: dict[str, int]) -> list[tuple[str, int, int, int, list[tuple[int, int]]]]:
    """
    Find methods in source code by parsing braces.
    Returns list of (method_name, start_line, end_line, total_executions, hot_lines)
    where hot_lines is list of (line, count) tuples sorted by count desc.
    """
    lines = content.split('\n')
    methods = []
    i = 0
    
    while i < len(lines):
        line = lines[i]
        line_num = i + 1
        
        # Detect method declarations
        if is_method_declaration(line):
            method_name = extract_method_name(line)
            if method_name:
                # Find method end by brace counting
                brace_count = 0
                started = False
                end_line = line_num
                
                for j in range(i, min(i + 500, len(lines))):
                    for ch in lines[j]:
                        if ch == '{':
                            brace_count += 1
                            started = True
                        elif ch == '}':
                            brace_count -= 1
                    
                    if started and brace_count == 0:
                        end_line = j + 1
                        break
                
                # Collect all hot lines in method range
                hot_lines = []
                for ln in range(line_num, end_line + 1):
                    count = counts.get(str(ln), 0)
                    if count > 0:
                        hot_lines.append((ln, count))
                
                # Sort by count descending
                hot_lines.sort(key=lambda x: -x[1])
                
                # Sum executions in method range
                total = sum(count for _, count in hot_lines)
                
                if total > 1000:  # Threshold for significance
                    methods.append((method_name, line_num, end_line, total, hot_lines))
        i += 1
    
    return methods


def is_method_declaration(line: str) -> bool:
    """Check if a line looks like a method declaration."""
    # Must have parentheses and opening brace (or be followed by them)
    if '(' not in line:
        return False
    
    # Exclude control structures
    control_keywords = ['if', 'for', 'while', 'switch', 'catch', 'synchronized', 'try', 'assert']
    match = re.search(r'\b(\w+)\s*\(', line)
    if match and match.group(1) in control_keywords:
        return False
    
    # Look for method indicators
    has_visibility = any(kw in line for kw in ['public ', 'private ', 'protected '])
    has_static = 'static ' in line
    has_final = 'final ' in line
    has_return_type = any(kw in line for kw in [
        'void ', 'int ', 'long ', 'boolean ', 'String ', 'Object ',
        'Class ', 'List ', 'Map ', 'Set ', 'Optional ', 'Document ',
        'Filter ', 'Nitrite', 'Runtime', 'Persistent', 'jakarta.',
        'io.micronaut', 'Collection ', 'Iterable ', 'Array', 'Record '
    ])
    
    return has_visibility or has_static or has_final or has_return_type


def extract_method_name(line: str) -> Optional[str]:
    """Extract method name from a declaration line."""
    # Match method name before (
    match = re.search(r'\b(\w+)\s*\([^)]*\)', line)
    if match:
        name = match.group(1)
        # Exclude keywords that might slip through
        if name not in ['if', 'for', 'while', 'switch', 'catch', 'synchronized', 
                        'try', 'assert', 'new', 'return', 'throw']:
            return name
    return None


def get_source_snippet(content: str, start_line: int, end_line: int, max_lines: int = 15) -> str:
    """Extract source code snippet for a method."""
    lines = content.split('\n')
    snippet_lines = lines[start_line - 1:min(start_line + max_lines - 1, end_line)]
    return '\n'.join(snippet_lines)


def generate_recommendation(method: str, source: str, total_execs: int, lines: list[LineExecution]) -> list[list[str]]:
    """Return empty recommendations array - to be filled manually after analysis."""
    return []


def analyze_hotpath(report_path: str, output_path: str) -> dict:
    """Main analysis function."""
    
    # Load report
    with open(report_path) as f:
        data = json.load(f)
    
    # Collect all methods from all files
    all_methods = []
    
    for file_entry in data['files']:
        if not file_entry['counts']:
            continue
        
        path = file_entry['path']
        filename = path.split('/')[-1]
        content = file_entry['content']
        counts = file_entry['counts']
        
        methods = find_methods(content, counts)
        
        for method_name, start, end, total, hot_lines in methods:
            # Get top N hottest lines
            top_lines = [LineExecution(line=ln, count=count) for ln, count in hot_lines[:15]]
            
            # Get source snippet
            snippet = get_source_snippet(content, start, end)
            
            all_methods.append({
                'file': filename,
                'path': path,
                'method': method_name,
                'line_start': start,
                'line_end': end,
                'total_executions': total,
                'lines': top_lines,
                'source_snippet': snippet,
                'full_content': content  # For recommendation generation
            })
    
    # Sort by total executions
    all_methods.sort(key=lambda x: -x['total_executions'])
    
    # Generate recommendations and build final output
    hotspots = []
    for rank, m in enumerate(all_methods[:200], 1):  # Top 200 hotspots
        lines_dict = [asdict(l) for l in m['lines']]
        recs = generate_recommendation(m['method'], m['source_snippet'], m['total_executions'], m['lines'])

        hotspot = {
            'rank': rank,
            'file': m['file'],
            'path': m['path'],
            'method': m['method'],
            'line_start': m['line_start'],
            'line_end': m['line_end'],
            'total_executions': m['total_executions'],
            'lines': lines_dict,
            'source_snippet': m['source_snippet'],
            'recommendations': recs
        }
        hotspots.append(hotspot)
    
    # Build output
    output = {
        'generatedAt': data['generatedAt'],
        'summary': {
            'totalFiles': len([f for f in data['files'] if f['counts']]),
            'totalExecutions': sum(sum(f['counts'].values()) for f in data['files']),
            'hotspotsAnalyzed': len(hotspots)
        },
        'hotspots': hotspots
    }
    
    # Write JSON output
    output_file = Path(output_path)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_file, 'w') as f:
        json.dump(output, f, indent=2)
    
    return output


def main():
    # Default paths
    base_dir = Path(__file__).parent.parent
    report_path = base_dir / 'build' / 'jvm-hotpath' / 'execution-report.json'
    output_path = base_dir / 'build' / 'jvm-hotpath' / 'hotpath-analysis.json'
    
    if not report_path.exists():
        import sys
        print(f"Error: Report not found at {report_path}", file=sys.stderr)
        print(f"Run: ./gradlew :micronaut-data-nitrite:jvmHotpathTest --rerun-tasks", file=sys.stderr)
        return 1
    
    analyze_hotpath(str(report_path), str(output_path))
    return 0


if __name__ == '__main__':
    exit(main())

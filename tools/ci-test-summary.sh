#!/usr/bin/env bash
# Prints, per module, how many tests a Gradle test task ran and how long they took, then the total.
# Usage: tools/ci-test-summary.sh <task name, e.g. integrationTest>   (run from the repository root)
set -euo pipefail
task="${1:?test task name}"
total=0
while IFS= read -r dir; do
  module="${dir%%/build/*}"
  compgen -G "$dir/*.xml" >/dev/null || continue
  count=0
  seconds=0
  for xml in "$dir"/*.xml; do
    [ -z "${VERBOSE:-}" ] || echo "  $(basename "$xml") $(grep -m1 -o ' tests="[0-9]*"' "$xml")"
    head="$(grep -m1 '<testsuite ' "$xml")"
    n="$(sed -E 's/.* tests="([0-9]+)".*/\1/' <<<"$head")"
    t="$(sed -E 's/.* time="([0-9.]+)".*/\1/' <<<"$head")"
    count=$((count + n))
    seconds="$(awk -v a="$seconds" -v b="$t" 'BEGIN { printf "%.0f", a + b }')"
  done
  printf '%-28s %5d tests %5ss\n' "$module" "$count" "$seconds"
  total=$((total + count))
done < <(find . -type d -path "*/build/test-results/$task" | sort)
echo "TOTAL $task tests: $total"

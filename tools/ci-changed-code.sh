#!/usr/bin/env bash
# Prints code=true when the head SHA, against its merge base with the base SHA, changes anything but Markdown outside docs/reference/, else code=false.
# Usage: tools/ci-changed-code.sh <base sha> <head sha>
set -euo pipefail
base="$1"
head="$2"
files="$(git diff --name-only "$base...$head")"
code=false
while IFS= read -r file; do
  [ -z "$file" ] && continue
  case "$file" in
    docs/reference/*) code=true ;;
    *.md) ;;
    *) code=true ;;
  esac
done <<< "$files"
echo "code=$code"

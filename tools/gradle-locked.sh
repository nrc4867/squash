#!/usr/bin/env bash
# Runs ./gradlew with the given arguments while holding the machine-wide Squash build lock, so builds from the
# main checkout and every worktree never overlap. Waits for the lock; a lock whose holder process is gone is taken over.
# Usage, from the main checkout or any worktree:  tools/gradle-locked.sh check
set -u
LOCK="${SQUASH_GRADLE_LOCK:-$HOME/.squash-gradle.lock}"
: "${JAVA_HOME:=$HOME/.jdks/corretto-25.0.1}"
export JAVA_HOME
announced=0
while ! mkdir "$LOCK" 2>/dev/null; do
  holder="$(cat "$LOCK/pid" 2>/dev/null || true)"
  if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
    rm -rf "$LOCK"
    continue
  fi
  if [ "$announced" = 0 ]; then
    echo "gradle-locked: waiting for $(cat "$LOCK/who" 2>/dev/null || echo another build)" >&2
    announced=1
  fi
  sleep 15
done
echo $$ > "$LOCK/pid"
echo "$(pwd) $*" > "$LOCK/who"
trap 'rm -rf "$LOCK"' EXIT INT TERM
./gradlew "$@"

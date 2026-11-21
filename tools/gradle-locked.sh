#!/usr/bin/env bash
# Runs ./gradlew with the given arguments while holding one of two machine-wide Squash build locks, taken in the
# order builds asked for them.
#   heavy: everything else (check, test tiers, look, drag): one at a time across the main checkout and every worktree.
#   light: the recipe engine's compile (:app:recipe, :app:recipeLauncher): one at a time, beside at most one heavy build,
#          so a character build never waits behind a full check. It still waits while a heavy build runs in its own checkout.
# A waiting build holds a ticket in the lock's queue and takes the lock only when its ticket is the oldest one whose
# process is alive; a lock or ticket whose process is gone is cleared.
# With SQUASH_GRADLE_UNLESS set, that command runs once the lock is held, and Gradle runs only when it fails: a compile another build
# finished while this one waited is not run again.
# Usage, from the main checkout or any worktree:  tools/gradle-locked.sh check
set -u
HEAVY="${SQUASH_GRADLE_LOCK:-$HOME/.squash-gradle.lock}"
LIGHT="$HEAVY-light"
POLL=5
: "${JAVA_HOME:=$HOME/.jdks/corretto-25.0.1}"
export JAVA_HOME

lane="heavy"
for arg in "$@"; do
  case "$arg" in
    :app:recipe|:app:recipeLauncher) lane="light" ;;
  esac
done
for arg in "$@"; do
  case "$arg" in
    -*|:app:recipe|:app:recipeLauncher) ;;
    *) lane="heavy" ;;
  esac
done
if [ "$lane" = light ]; then LOCK="$LIGHT"; else LOCK="$HEAVY"; fi
QUEUE="$LOCK.queue"
mkdir -p "$QUEUE"

alive() { [ -n "$1" ] && kill -0 "$1" 2>/dev/null; }

clear_dead_lock() {
  local holder
  holder="$(cat "$1/pid" 2>/dev/null || true)"
  if [ -n "$holder" ] && ! alive "$holder"; then rm -rf "$1"; fi
}

oldest_live_ticket() {
  local t pid
  for t in $(ls "$QUEUE" 2>/dev/null | sort); do
    pid="${t##*-}"
    if alive "$pid"; then echo "$t"; return; fi
    rm -f "$QUEUE/$t"
  done
}

heavy_here() {
  [ -d "$HEAVY" ] && [ "$(cut -d' ' -f1 "$HEAVY/who" 2>/dev/null)" = "$(pwd)" ]
}

ticket="$(date +%s%N)-$$"
: > "$QUEUE/$ticket"
cleanup() { rm -f "$QUEUE/$ticket"; [ "$(cat "$LOCK/pid" 2>/dev/null)" = "$$" ] && rm -rf "$LOCK"; }
trap cleanup EXIT
trap 'exit 130' INT TERM

announced=0
while :; do
  clear_dead_lock "$LOCK"
  clear_dead_lock "$HEAVY"
  if [ "$(oldest_live_ticket)" = "$ticket" ] && { [ "$lane" = heavy ] || ! heavy_here; } && mkdir "$LOCK" 2>/dev/null; then
    break
  fi
  if [ "$announced" = 0 ]; then
    echo "gradle-locked: waiting ($lane) for $(cat "$LOCK/who" 2>/dev/null || echo the builds queued before this one)" >&2
    announced=1
  fi
  sleep "$POLL"
done
echo $$ > "$LOCK/pid"
echo "$(pwd) $*" > "$LOCK/who"
rm -f "$QUEUE/$ticket"
if [ -n "${SQUASH_GRADLE_UNLESS:-}" ] && eval "$SQUASH_GRADLE_UNLESS"; then exit 0; fi
./gradlew "$@"

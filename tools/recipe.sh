#!/usr/bin/env bash
# Builds a character recipe and judges it, headless: on the recipe server when one runs on this machine with this checkout's engine,
# else in a JVM of its own. Gradle first compiles the engine, under its lock, only when a source changed, into a snapshot folder of its
# own that no later compile touches:
#   tools/recipe.sh <recipe.json> [key=value ...]   the character file, renders, measurements, and with reference= a compare
#   tools/recipe.sh watch <recipe.json> [key=value ...]   the same, then again each time the recipe file is saved, on the server or in
#     one warm JVM outside Gradle and its lock, until Ctrl-C; a save with the same text builds nothing
#   tools/recipe.sh probe <recipe.json> [question ...]   frame facts at rest, in body space (x forward, y up, z right), no renders:
#     part=<id>[,<id>] or part=all (the default)   extent, axis and v directions, joints and sockets with body points
#     uv=<part>:<u>,<v>[,<patch>]   the body point there        point=<x>,<y>,<z>   the part, u and v nearest that point
#     face=<head>:<x>,<y>   the u and v at that place on the head's face (x to its left, y up)
#   tools/recipe.sh export <character.json> [out=<recipe.json>]   a character file written as a recipe
#   tools/recipe.sh kit [check|json|probe|heights] <character.ts> [option ...]   a TypeScript character file anywhere on disk, checked,
#     run and written as <name>.recipe.json beside it (or in out=), then built with these options (kit/README.md)
#   tools/recipe.sh crop <sheet.png> [out=<folder>] [ground=#rrggbb]   a sheet of views on one background split by its gaps into
#     view-1.png, view-2.png ... (default folder <sheet>-views beside it)
#   tools/recipe.sh serve [port=<n>] [limit=<n>] [timeout=<minutes>]   the recipe server: one warm JVM and GPU device building for
#     every agent and checkout on 127.0.0.1 only (port 47830), <limit> builds at a time (2), each given up after <timeout> minutes (20);
#     when this checkout's engine is compiled again it finishes its builds and starts again on the new one; until Ctrl-C
# Options: out=<folder> (default build/recipes/<id>), render=false (character file only), measure=false,
# close=face,hand,foot (the default; also hands, feet, joins, orbit (the head round with and without its view keys), hair (the head framed alone from a hair outline's front, three-quarter, side and back), mouth (the first mouth framed close from the front, three-quarter and side, and with faces=shapes saying each shape), turn (the whole character from eight ways round in its own shading), strip (the front in the display pose, at rest and in a T-pose side by side), part ids such as close=foot-l,head, or none), around=false (no back and
# top views), only=<id>,... (draw just those parts; no close-ups, no measure unless measure=true), outfits=<id,...|all>,
# poses=<name,...|all>, set=part:name:value,... (set=head:mouth.shape:o shows a mouth shape as a preview), faces=shapes (the face close-up
# saying each of the nine mouth shapes, and a contact sheet of them), reference=<png> with eye=x,y,z target=x,y,z fov=<degrees> or fit=true (search
# the camera, print it, then score) [ground=#rrggbb] [scale=<body units a metre>], targets=<file>,<file> (proportion targets from
# `kit sheet measure`, checked beside the recipe's own in measure.txt).
# Paths may be relative to where it is run from and may hold spaces. Prints every file it writes. Exits 2 on a recipe error.
# SQUASH_RECIPE_PORT=<n> sends to a server on another port; SQUASH_RECIPE_SERVER=off always builds in a JVM of its own.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
here="$(pwd)"

# A path as the JVM reads it, from where this was run.
absolute() {
  local path="$1"
  case "$path" in
    /*|[A-Za-z]:*) ;;
    *) path="$here/$path" ;;
  esac
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$(realpath -m "$path")"; else realpath -m "$path"; fi
}

# A value with its percent signs and spaces encoded, so it survives the space-separated Gradle property.
encoded() {
  local value="${1//%/%25}"
  printf '%s' "${value// /%20}"
}

launcher="$root/app/build/recipe-launcher"
engines="$root/app/build/recipe-engine"
# True when no engine snapshot is pointed at or a source, build script or Stretch source the engine is compiled from is newer than
# the pointer.
launcher_stale() {
  [ -f "$launcher/engine.txt" ] || return 0
  [ -f "$engines/$(cat "$launcher/engine.txt")/args.txt" ] || return 0
  local sources=("$root")
  [ -d "$root/../stretch" ] && sources+=("$root/../stretch")
  find "${sources[@]}" \( \( -name build ! -path '*/src/*' \) -o -name .gradle -o -name .git -o -name .worktrees -o -name node_modules -o -name .kotlin       -o -name .idea -o -name kit -o -name docs -o -name examples -o -name recipes -o -name templates \) -prune     -o -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.java' -o -name '*.properties' -o -name '*.toml' -o -path '*/src/*/resources/*' \)     -newer "$launcher/engine.txt" -print -quit | grep -q .
}
# Compiles the engine and snapshots it when it is stale; a build that waited on the lock while another compiled it compiles nothing.
compile_engine() {
  cd "$root"
  if launcher_stale; then
    SQUASH_GRADLE_UNLESS="bash '$root/tools/recipe.sh' fresh" bash tools/gradle-locked.sh -q --console=plain :app:recipeLauncher
    touch "$launcher/engine.txt"
  fi
}

if [ $# -eq 0 ]; then
  sed -n '2,29p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 2
fi
if [ "$1" = fresh ]; then
  ! launcher_stale
  exit
fi
if [ "$1" = serve ]; then
  shift
  # The server exits 3 when the engine was compiled again under it, once its builds are done; it starts again on the new snapshot.
  while :; do
    compile_engine
    engine="$engines/$(cat "$launcher/engine.txt")"
    set +e
    "$(cat "$engine/java.txt")" "@$(absolute "$engine/args.txt")" serve=true "$@"
    code=$?
    set -e
    [ "$code" = 3 ] || exit "$code"
  done
fi
watch=0
if [ "$1" = watch ]; then
  watch=1
  shift
  [ $# -ge 1 ] || { echo "tools/recipe.sh watch needs a recipe" >&2; exit 2; }
fi
case "$1" in
  kit)
    shift
    bash_path="$BASH"
    if command -v cygpath >/dev/null 2>&1; then bash_path="$(cygpath -w "$BASH")"; fi
    SQUASH_BASH="$bash_path" exec node "$root/kit/run.ts" "$@"
    ;;
  export|probe|crop)
    [ $# -ge 2 ] || { echo "tools/recipe.sh $1 needs a file" >&2; exit 2; }
    args="$1=$(encoded "$(absolute "$2")")"
    shift 2
    ;;
  *)
    args="recipe=$(encoded "$(absolute "$1")")"
    shift
    ;;
esac
for option in "$@"; do
  case "$option" in
    out=*|reference=*) args="$args ${option%%=*}=$(encoded "$(absolute "${option#*=}")")" ;;
    targets=*)
      files=""
      IFS=',' read -ra listed <<< "${option#*=}"
      for file in "${listed[@]}"; do files="${files:+$files,}$(absolute "$file")"; done
      args="$args targets=$(encoded "$files")" ;;
    *) args="$args $(encoded "$option")" ;;
  esac
done
# Every build runs from the snapshot the pointer names when it starts, outside Gradle and its lock.
compile_engine
version="$(cat "$launcher/engine.txt")"
engine="$engines/$version"
# A server with this engine takes the build; the client exits 75 when there is none, or it runs another engine or is stopping.
if [ "${SQUASH_RECIPE_SERVER:-on}" != off ] && command -v node >/dev/null 2>&1; then
  set +e
  # shellcheck disable=SC2086
  node "$root/kit/cli/server-client.ts" --engine "$version" $([ "$watch" = 1 ] && echo --watch) -- \
    "root=$(encoded "$(absolute "$root")")" $args
  code=$?
  set -e
  [ "$code" = 75 ] || exit "$code"
fi
java="$(cat "$engine/java.txt")"
if [ "$watch" = 1 ]; then
  # shellcheck disable=SC2086
  exec "$java" "@$(absolute "$engine/args.txt")" $args watch=true
fi
status="$(mktemp)"
trap 'rm -f "$status"' EXIT
args="$args status=$(encoded "$(absolute "$status")")"
# shellcheck disable=SC2086
"$java" "@$(absolute "$engine/args.txt")" $args || true
code="$(cat "$status" 2>/dev/null || true)"
exit "${code:-1}"

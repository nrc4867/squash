#!/usr/bin/env bash
# Builds a character recipe and judges it, headless, in its own JVM; Gradle compiles the engine first, under its lock, only when
# a source changed:
#   tools/recipe.sh <recipe.json> [key=value ...]   the character file, renders, measurements, and with reference= a compare
#   tools/recipe.sh watch <recipe.json> [key=value ...]   the same, then again each time the recipe file is saved, in one warm JVM
#     outside Gradle and its lock, until Ctrl-C; a save with the same text builds nothing
#   tools/recipe.sh probe <recipe.json> [question ...]   frame facts at rest, in body space (x forward, y up, z right), no renders:
#     part=<id>[,<id>] or part=all (the default)   extent, axis and v directions, joints and sockets with body points
#     uv=<part>:<u>,<v>[,<patch>]   the body point there        point=<x>,<y>,<z>   the part, u and v nearest that point
#     face=<head>:<x>,<y>   the u and v at that place on the head's face (x to its left, y up)
#   tools/recipe.sh export <character.json> [out=<recipe.json>]   a character file written as a recipe
#   tools/recipe.sh kit [check|json|probe|heights] <character.ts> [option ...]   a TypeScript character file anywhere on disk, checked,
#     run and written as <name>.recipe.json beside it (or in out=), then built with these options (kit/README.md)
#   tools/recipe.sh crop <sheet.png> [out=<folder>] [ground=#rrggbb]   a sheet of views on one background split by its gaps into
#     view-1.png, view-2.png ... (default folder <sheet>-views beside it)
# Options: out=<folder> (default build/recipes/<id>), render=false (character file only), measure=false,
# close=face,hand,foot (the default; also hands, feet, joins, or part ids such as close=foot-l,head), around=false (no back and
# top views), only=<id>,... (draw just those parts; no close-ups, no measure unless measure=true), outfits=<id,...|all>,
# poses=<name,...|all>, set=part:name:value,..., reference=<png> with eye=x,y,z target=x,y,z fov=<degrees> or fit=true (search
# the camera, print it, then score) [ground=#rrggbb] [scale=<body units a metre>].
# Paths may be relative to where it is run from and may hold spaces. Prints every file it writes. Exits 2 on a recipe error.
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

if [ $# -eq 0 ]; then
  sed -n '2,21p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 2
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
    *) args="$args $(encoded "$option")" ;;
  esac
done
launcher="$root/app/build/recipe-launcher"
# True when the launcher is missing or older than any source, build script or Stretch source the engine is compiled from.
launcher_stale() {
  [ -f "$launcher/args.txt" ] || return 0
  local sources=("$root")
  [ -d "$root/../stretch" ] && sources+=("$root/../stretch")
  find "${sources[@]}" \( -name build -o -name .gradle -o -name .git -o -name .worktrees -o -name node_modules -o -name .kotlin       -o -name .idea -o -name kit -o -name docs -o -name examples -o -name recipes -o -name templates \) -prune     -o -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.java' -o -name '*.properties' -o -name '*.toml' -o -path '*/src/*/resources/*' \)     -newer "$launcher/args.txt" -print -quit | grep -q .
}
# The engine is compiled through Gradle, under its lock, only when a source changed since the last launcher; every build then
# runs in its own JVM straight from the compiled classes, outside Gradle and its lock.
cd "$root"
if launcher_stale; then
  bash tools/gradle-locked.sh -q --console=plain :app:recipeLauncher
  touch "$launcher/args.txt"
fi
java="$(cat "$launcher/java.txt")"
if [ "$watch" = 1 ]; then
  # shellcheck disable=SC2086
  exec "$java" "@$(absolute "$launcher/args.txt")" $args watch=true
fi
status="$(mktemp)"
trap 'rm -f "$status"' EXIT
args="$args status=$(encoded "$(absolute "$status")")"
# shellcheck disable=SC2086
"$java" "@$(absolute "$launcher/args.txt")" $args || true
code="$(cat "$status" 2>/dev/null || true)"
exit "${code:-1}"

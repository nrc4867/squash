#!/usr/bin/env bash
# Builds a character recipe and judges it, headless, under the machine-wide build lock:
#   tools/recipe.sh <recipe.json> [key=value ...]   the character file, renders, measurements, and with reference= a compare
#   tools/recipe.sh export <character.json> [out=<recipe.json>]   a character file written as a recipe
# Options: out=<folder> (default build/recipes/<id>), render=false (character file only), measure=false, close=face,hand,foot
# (also hands, feet, joins), outfits=<id,...|all>, poses=<name,...|all>, set=part:name:value,...,
# reference=<png> eye=x,y,z target=x,y,z fov=<degrees> [ground=#rrggbb] [scale=<body units a metre>].
# Paths may be relative to where it is run from. Prints every file it writes.
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

if [ $# -eq 0 ]; then
  sed -n '2,8p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 2
fi
if [ "$1" = "export" ]; then
  args="export=$(absolute "$2")"
  shift 2
else
  args="recipe=$(absolute "$1")"
  shift
fi
for option in "$@"; do
  case "$option" in
    out=*|reference=*) args="$args ${option%%=*}=$(absolute "${option#*=}")" ;;
    *) args="$args $option" ;;
  esac
done
cd "$root"
exec bash tools/gradle-locked.sh --max-workers=2 --priority=low -q --console=plain :app:recipe "-Precipe=$args"

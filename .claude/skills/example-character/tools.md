# Tools

Every command runs from any folder; paths may be relative and may hold spaces. `<squash>` is the Squash checkout (or a worktree of it).

## The kit file and its commands

`tools/recipe.sh kit [command] <file.ts> [options]`, commands and helpers in `kit/README.md`:

| command | use it to |
| --- | --- |
| `check` | typecheck only, a second or two: after every edit before a build |
| `json` | write `<name>.recipe.json` beside the file, no build |
| `build` (the default) | typecheck, write the JSON, build, render, measure |
| `probe <questions>` | frame facts of the built parts at rest (below) |
| `heights` | the file's exported `heights` (`heightMarks`) planned against built: where sinks and the feet moved the plan |
| `sheet <command> <image>` | read a reference sheet (`reading-a-reference.md`) |

A type error prints `cat.ts:12:5: error TS...` and exits 2. Character files use only syntax Node can strip: no `enum`, no `namespace`; import types with `type` in the braces (`import { recipe, type RecipePart } from "squash-kit"`). The default export is a recipe, or a function returning one.

A JSON recipe builds with `tools/recipe.sh <recipe.json>`; its schema is `schema/recipe.schema.json` (`"$schema": "../schema/recipe.schema.json"` for an editor). `recipes/fennec.recipe.json` (templated quadruped, fur, face marks, a mouth), `recipes/woman-dressed.recipe.json` (template-free biped, hand-made sockets and joints, outfits) and `recipes/painted-tee.recipe.json` (painted cloth areas, holes, prints) are the worked JSON examples; `kit/examples/mannequin.ts` the worked kit file.

## What a build writes

It runs in its own JVM, outside Gradle and the build lock (Gradle runs first, under its lock, only when the engine's sources changed since the last build), and prints every file it writes, under `build/recipes/<id>/` of the checkout unless `out=` says otherwise:

- `<id>.json`: the character file; `./gradlew :app:run -Popen=<file>` opens it in the app.
- `renders/<outfit>.png`: front, three-quarter and side in the display pose. `<outfit>-back-top.png`: from behind, and from above with the front up. `<outfit>-rest.png` when the display pose differs from rest. `<outfit>-pose-<name>.png` per named pose. `<outfit>-face.png`, and `<outfit>-face-<expression>.png` per expression. `<outfit>-foot.png` (the first foot on a leg), `hand.png` (the first hand or paw on an arm), `<outfit>-<part>.png` per part id in `close=`. A character with no outfits is `as-saved`.
- `measure.txt`: the numbers (`judging.md`), among them `part ids` (every part with its role), `proportions:` (the built head, hand, foot and eye sizes), `FLAG target` lines against the recipe's `targets` and a `targets=` file, and each garment's hem heights.

About 5 to 10 s a run once the engine is compiled; the first run after a source change also compiles it. A recipe error stops the run before anything renders and names the JSON path (`recipe error at $.parts[3].socket: torso has no socket "nek"; it offers neck, tail, front-leg, back-leg`), exit 2. Warnings (`template paw has no parameter "toe"; it has ...`) do not stop it: read them, an ignored warning is a feature that silently did nothing.

## Options

After the file, as `key=value`; the kit passes them on unchanged:

| option | does |
| --- | --- |
| `render=false` | the character file only, a few seconds |
| `measure=false` | skip the measurements |
| `close=face,hand,foot` | the close-ups (the default); also part ids (`close=ear-l,tail,head`), each framed close per outfit; `hands` and `feet` the bare hand or paw at rest, curled, open and spread from six sides; `joins` the neck, shoulder and hip; `none` no close-ups |
| `only=head,ear-l` | draw and frame just those parts in every view; no close-ups, no measure unless `measure=true` |
| `around=false` | leave out the back and top views |
| `outfits=a,b` or `all`, `poses=a,b` or `all` | which outfits (default the shown one) and named poses (default all) |
| `set=part:name:value,...` | try a parameter or a mouth control without editing (`set=head:mouth.open:1`); an unknown key warns with what the part has |
| `out=<folder>` | where the JSON and the build go |
| `reference=<png> fit=true` | search the camera that best matches the reference's silhouette, print it as `fit: eye=... target=... fov=...`, then compare through it (`judging.md`) |
| `reference=<png> eye=x,y,z target=x,y,z fov=30` | compare through that camera; `ground=#rrggbb` keys the background, `scale=` gives body units a metre |

`tools/recipe.sh watch <recipe.json> [options]` builds, then builds again on each save of the recipe in one warm process (about 5 s a rebuild); Ctrl-C stops it. With the kit, run `kit json` after each edit and leave `watch` on the JSON it writes.

`tools/recipe.sh export <character.json> [out=<recipe.json>]` writes any character file, an app-made one too, as a recipe, printing `not carried:` for what a recipe cannot hold.

`tools/recipe.sh crop <sheet.png> [out=<folder>]` splits a sheet on one flat background into `view-1.png`... by its gaps; `kit sheet views` does the same with labels, clutter and shadows handled, and is the one to use.

## Probe

```
tools/recipe.sh kit probe <file.ts> part=head uv=head:0.6,0.2 point=0.9,0.5,-0.1 face=head:0.1,0.1
```

About 10 s, no renders. It builds and prints, at rest, in body space (x forward, y up, z the character's right):

| question | prints |
| --- | --- |
| `part=<id>[,<id>]`, `part=all` (the default) | origin and box; which way u runs and Side's +y faces; which way v 0, 0.25, 0.5, 0.75 face; for a head the reach of its `face` places; each joint with hinge, limits and body point; each socket with u, v, body point and facing |
| `uv=<part>:<u>,<v>[,<patch>]` | the body point there |
| `point=<x>,<y>,<z>` | the part, patch, u and v nearest that point |
| `face=<head>:<x>,<y>` | the u and v at that face place |

Ask it before placing a socket, region, mark, eye or paint stroke by u and v, before posing a bone, and to find how long a part came out once seated (a brim, a lens). A question it cannot answer prints why; the rest are answered and it exits 2.

## The reference

`docs/reference/` is generated from the code, so it is current; `docs/reference/README.md` indexes it:

- `templates.md`: every template's newest version, rest, the length its outline x runs to, which way Side's and Top's y face, what each view sets, sockets, joints (hinge, limits, which way positive degrees move), areas, patches, landmarks and parameters with range and meaning.
- `plans.md`: each plan's root, sockets, slots and palette. `proportions.md`: each plan built undrawn, every part's box and height above the ground at `size` 1.
- `frames.md`: body space, the part frame by rest (Side's and Top's +y, where v 0 to 0.75 face), roll, and which way `degrees`, `lean`, `turn` and `twist` move a bone.
- `part-parameters.md`: every dotted parameter and the recipe field that writes it.
- `face-and-clothes.md`: mouth roles and controls, regions, eyes and every eye look field with its units per head, and how garments, areas, strokes, covers, redraws and swaps combine.

`docs/hld/parts.md` says what each template is built from and what its versions add; `docs/hld/surface.md` how the one surface, ink, eyes, ears and garments work; `docs/hld/look.md` shading, lines, marks, eyes and mouths.

## Plans

Pick the plan before the first part: `blank` for a template-free body (a tube or blob root; the usual choice for a biped or a creature drawn from a sheet), `biped`, `canine-quadruped`, `bird` or `fish` for a templated one. A templated plan's root and sockets come from `plans.md`; on `blank` every socket is hand-made.

## Examples and goldens

- The shipped examples (`examples/*.json`) are built from Kotlin fixtures with golden tests; leave them unless asked. `recipes/fennec.recipe.json` and `recipes/woman-dressed.recipe.json` build two of them, render for render.
- A new shipped example is `recipes/<name>.recipe.json`; the recipe module's tests build every recipe there. Write it with the kit (`kit json out=<repo>/recipes`) or by hand.

# Squash kit

Write a character as one TypeScript file, anywhere on disk, and build it with one command. The file imports typed helpers from `squash-kit`; the runner typechecks it, runs it, writes its recipe JSON beside it, and builds and renders that recipe with `tools/recipe.sh`. The character file needs no `package.json`, no install, no git and no worktree.

## Quick start

Write `C:\anywhere\cat.ts`:

```ts
import { oval, outline, palette, recipe } from "squash-kit";

const colors = palette({ fur: "#c08040", ink: [0.1, 0.08, 0.08] });
const body = oval({ center: [0.1, 0], rx: 0.1, ry: 0.08 });

export default recipe({
  id: "cat",
  name: "Cat",
  plan: "blank",
  palette: colors.slots,
  parts: [{ id: "body", template: "blob", color: colors.slot("fur"), outlines: { side: outline(body), top: outline(body) } }],
});
```

Run, from anywhere:

```
<squash>/tools/recipe.sh kit C:/anywhere/cat.ts
```

It prints `cat.recipe.json` (written beside `cat.ts`), then everything `tools/recipe.sh` prints: the character file, the renders and `measure.txt`. A type error stops it before anything is written, as `cat.ts:12:5: error TS2322: ...`, exit 2. The first run installs the kit's own dependencies inside `kit/` from its lock file; nothing is installed beside the character file.

`kit/examples/mannequin.ts` is a whole biped: a height plan, tube limbs with front and back profiles, a neck, an egg head, template hands and feet, and a tee.

## Commands

`tools/recipe.sh kit [command] <file.ts> [options]`, or `node <squash>/kit/run.ts [command] <file.ts> [options]`:

| command | does |
| --- | --- |
| `build` (the default) | typecheck, run, write `<name>.recipe.json`, build and render it |
| `check` | typecheck only |
| `json` | typecheck, run and write the JSON, no build |
| `probe` | write the JSON, then `tools/recipe.sh probe` with the questions after the file (`part=head`, `uv=head:0.5,0.2`, ...) |
| `heights` | write the JSON, probe every part, and print the file's exported `heights` planned against built |
| `sheet <command> <image>` | read a character sheet image, before any build: see [Reading a character sheet](#reading-a-character-sheet) |

Options after the file go to `tools/recipe.sh` unchanged: `render=false`, `outfits=all`, `poses=all`, `close=face,hand,foot,joins`, `set=part:name:value`, `reference=...`. `out=<folder>` puts both the recipe JSON and the build there.

The default export is a recipe, or a function returning one (it may be async). Character files use only syntax Node can strip (no `enum`, no `namespace`), and import types with `import type` or `type` in the braces; the typecheck says so when they don't.

## The recipe

| helper | one line |
| --- | --- |
| `recipe` | `export default recipe({ id: "cat", name: "Cat", plan: "blank", parts })` fills `format`. |
| types | every schema type by its schema name: `RecipePart`, `RecipeGarment`, `BonePose`, `RecipeRegion`, ... |

## Body plan

Heights above the ground in body units; spans turn them into a part's outline x and socket or joint u.

| helper | one line |
| --- | --- |
| `bodyPlan` | `const plan = bodyPlan({ ankle: 0.045, knee: 0.47, hip: 0.84, crotch: 0.78, shoulder: 1.39, torsoTop: 1.48 }, { hips: 0.16 })` |
| `plan.h`, `plan.w`, `plan.between` | `plan.h("knee")`, `plan.w("hips")`, `plan.between("knee", "hip")` |
| `plan.upright` | `const torso = plan.upright("crotch", "torsoTop"); torso.u(plan.h("shoulder"))` is the shoulder socket's u. |
| `plan.hanging` | `const leg = plan.hanging("hip", "ankle"); leg.x(plan.h("knee"))` is the knee's outline x. |
| `heightPlan` | heights alone: `heightPlan({ hip: 0.7, knee: 0.4 })`. |
| `uprightSpan`, `hangingSpan`, `lengthSpan` | a span from numbers: `lengthSpan(0.57).u(0.28)` is an elbow's u on an arm 0.57 long. |
| `span.length`, `.x`, `.u`, `.at` | length, outline x and u at a position, position at a u. |
| `heightMarks` | `export const heights = heightMarks(plan, { knee: { part: "leg-l", joint: "joint.1" } })` for the `heights` command. |
| `checkHeights`, `formatHeightChecks` | the same check from code, over a `ProbeReport`. |

A part's outline x runs from its socket end to its drawn length (`templates.md`'s "outline x runs 0 to" is a template's own length). The root sits at the origin and the ground is under the lowest point, so a plan's heights hold once the feet reach the ground: `heights` shows where sinks and the feet move things.

## Frames

| helper | one line |
| --- | --- |
| `restFrames` | `restFrames.down.side` is `"forward"`: where Side's and Top's +y and v 0, 0.25, 0.5, 0.75 face, per rest (`frames.md`). |
| `vToward` | `vToward("up", "left")` is 0.75; a pair's left side may say `"outer"` or `"inner"`. |
| `vBetween` | `vBetween("up", "forward", "left", 0.5)` is 0.875. |
| `opposite`, `pairDirection` | `opposite("forward")` is `"back"`; `pairDirection("outer")` is `"left"`. |
| `orient` | `{ ...orient([1, 0, -1], [0, 1, 0]) }` is the `rest`, `lean`, `outward` and `roll` pointing a part forward and out with its up up; it throws past what lean and outward reach (85 degrees off the nearest rest). |
| `around` | `around([1, 0, -1], [0, 1, 0], 5)` is five directions evenly round a forward-out facing, the first up: petals. |

## Shapes

Shapes are `{ points, corners, closed }`; `outline(shape)` makes a recipe outline.

| helper | one line |
| --- | --- |
| `tube` | `tube({ rest: "down", length: leg.length, at: stations.map(leg.x), profiles: { front, back, outer, inner } })` gives `{ side, top }`; Side takes the profiles across its y for that rest, Top the others, a lone Top side is mirrored; `closed: true` rounds both ends (a torso, a head); `tip: 0` ends flat. |
| `roundStations` | `const egg = roundStations(0.25, 9, 0.55); tube({ ..., closed: true, at: egg.at, profiles: { front: egg.radii(0.098), ... } })` |
| `outline` | `outline(oval({ rx: 0.1 }))` is `{ through, corners, closed }`. |
| `shape`, `mapShape` | `shape(points, [2])`; `mapShape(s, (p) => scale(p, 2))`. |
| `mirroredLoop` | `mirroredLoop([[0, 0], [0.1, 0.06], [0.2, 0]])` closes a symmetric loop from its +y half. |
| `twoSidedLoop`, `twoSidedOpen` | a loop or an open limb outline from separate +y and -y sides. |
| `oval` | `oval({ center: [0.1, 0], rx: 0.1, ry: 0.06, points: 8, turn: 15 })` |
| `arc` | `arc({ center: [0, 0], rx: 0.05, from: 200, to: 340 })` |
| `polygon`, `regularPolygon`, `rectangle` | `regularPolygon(5, 0.04)`; every place a corner. |
| `roundCorners` | `roundCorners(rectangle([0, 0], [0.1, 0.05]), 0.01)` |
| `teardrop` | `teardrop({ length: 0.06, width: 0.02 })`, round at x 0, pointed at the far end. |
| `leaf` | `leaf({ length: 0.3, width: 0.16, base: 0.17 })`, an open ear or fin Top with its tip a corner; as an `ear` (version 3) Top it sets the ear's width, and its `thickness`, `cup` and `rim` give the joined ear its back, hollow and rim. |
| `tuft` | `tuft({ spikes: 3, length: 0.08, width: 0.04 })`, an open outline of pointed tips. |
| `furLock` | `const lock = furLock({ length: 0.2, width: 0.03, curl: 0.05 }); outlines: { side: outline(lock.side), top: outline(lock.top) }` |
| geometry | `Point`, `translate`, `scale`, `rotate`, `flipX`, `flipY`, `lerp`, `steps`, `roundTo`, `roundPoints`. |

## Parts

| helper | one line |
| --- | --- |
| `tubePart` | `tubePart({ id: "legs", on: "torso", socket: "socket.3", ...pair("leg"), joints }, { rest: "down", length, profiles })`, a generic tube at `FINE` (96 rings, 32 segments). |
| `FINE`, `SMALL` | `generic` samplings for big and small generic parts. |
| `socket` | `socket(2, torso.u(plan.h("shoulder")), vToward("up", "left"), { mirrored: true })`; `{ sink: 0, outset: 0.016 }` stands a part just off a shirt of fit 0.012. |
| `digitPatch` | `socket(1, 0.7, 0.25, { patch: digitPatch(2) })` lies on a hand's third finger and rides it, a ring; `digitPatch("thumb")`, `digitPatch(0, "toes")`. Regions and marks take the same `patch`. |
| `ringSocket` | `ringSocket(4, torso.u(plan.h("waist")), 12, { sink: 0, outset: 0.02 })`, twelve sockets round a part: studs round a belt. |
| `joint` | `joint(1, leg.u(plan.h("knee")), { low: 0, high: 140 })` |
| `socketName`, `jointName` | `socketName(2)` is `"socket.2"`. |
| `pair` | `{ ...pair("arm") }` names the sides `arm-l` and `arm-r`. |
| `sides` | `sides("arm").right` is `"arm-r"`. |
| `digitCurl` | `digitCurl("hand-l", { count: 3, curl: [-6, -10, -6], thumb: [6, 4, 2] })` poses every joint of a hand's three numbered fingers and its thumb; `row: "toes"` for a foot or paw. |
| `bothSides` | `displayPose: bothSides("arm", { joint: "base", lean: -28 })` |

## Face

Face places are the head seen from straight ahead: x to the character's left, y up, in the head's drawn units from its socket. `tools/recipe.sh kit probe <file> part=head` prints a head's reach.

| helper | one line |
| --- | --- |
| `faceRegion` | `faceRegion("mask", "cream", oval({ center: [0, 0.12], rx: 0.08, ry: 0.06 }))` |
| `uvRegion` | `uvRegion("blaze", "cream", [[0.6, 0.9], [0.9, 0.95], [0.9, 0.05], [0.6, 0.1]], { patch: "body" })` |
| `faceMark`, `uvMark` | `faceMark("brow", arc({ center: [0.05, 0.2], rx: 0.03, from: 30, to: 150 }), { slot: "brow", mirrored: true })` |
| `smile` | `faceMark("mouth-line", smile({ center: [0, 0.05], width: 0.04, depth: 0.01 }))`; negative depth frowns. |
| `taper` | `width: taper(0.3)` thins a line's ends. |
| `faceEye` | `faceEye("eyes", { on: "head", face: [0.05, 0.14], look: { white: "eye-white" } })`, mirrored unless told. |

## Clothes

| helper | one line |
| --- | --- |
| `cover` | `cover("torso", { from: torso.u(0.9), to: 0.96 })`, hems snapped to the 64 by 32 chart's cell edges. |
| `band` | `band("torso", torso.u(plan.h("waist")), 0.04)`, at least two cells wide. |
| `snapU`, `snapV` | the nearest cell edge. |
| `garment` | `garment({ id: "tee", slot: "top", color: "shirt", fit: 0.012, covers })`; its name from its id. |
| `through`, `hide`, `redraw`, `swap` | `changes: { neck: through(), "foot-l": swap("blob", { outlines }) }` |
| `outfit` | `outfit("casual", { top: "tee" })` |
| `wardrobe` | `wardrobe([tee], [outfit("casual", { top: "tee" })])`, slots read from the garments, shown in the first outfit. |
| `slots`, `LAYER` | `slots("top", "feet")`; `layer: LAYER.over`. |
| `printOn`, `printAll` | `regions: [printOn("torso", uvRegion("badge", "red", leaf({ ... }), { ink: true }))]`, a region or mark printed on the cloth over a body part. |

## Props

| helper | one line |
| --- | --- |
| `prop` | `prop({ id: "heels", on: "feet", socket: 1, garment: "pumps", color: "patent", rest: "down", side, top })` |
| `roundProp` | `roundProp({ id: "button", on: "torso", socket: 5, garment: "coat", color: "button", radius: 0.012, depth: 0.008 })` |

A prop sits on a hand-made socket of the part it is on, usually `socket(n, u, v, { sink: 0 })`; on cloth add `outset` a little past the garment's `fit`. `prop` takes `lean`, `outward`, `roll` and `topMiddle` (a lens drawn curved in Top wraps the face).

## Palette

| helper | one line |
| --- | --- |
| `palette` | `const colors = palette({ skin: "#d8b48e", ink: [0.1, 0.08, 0.08] })`; `palette: colors.slots`, `color: colors.slot("skin")` fails on a slot not declared. `palette({ lens: "#203040" }, { opacity: { lens: 0.35 } })` makes a part coloured `lens` see-through. |
| `mix`, `shade` | `shade("#d8b48e", -0.2)` is darker, `mix(a, b, 0.5)` halfway. |
| `toRgb`, `toHex` | convert between `#rrggbb` and `[r, g, b]`. |

## Reading a character sheet

`tools/recipe.sh kit sheet <command> <image> [options]` reads a PNG or JPEG character sheet before any build: where the views are, how tall each part is in heads and body units, the colours, and radii ready for `tube`. Each command writes JSON and images to `out=<folder>` (default `<image>-sheet` beside the image) and prints every path it writes. Look at the images: they are drawn to be read by eye.

The background is keyed from the sheet's border: its dominant colour, a gentle gradient fitted to it, and a flood fill from the edges, so off-white, graded or lightly textured paper works. Views are the figures standing on it, found as connected blobs, with thin strokes (hatching, ground lines) cut so they do not join two figures. A small piece near a figure joins it (a detached tail, ear or floating flower); a cluster of small pieces (swatches, text, dots) and anything else small is `other`, with its box. A cast shadow under the feet is split off and reported, so each view stands on its own lowest point. Views are numbered in reading order, rows from the top, and each is measured in its own head units, so views at different scales on one sheet are fine.

Options every command takes:

| option | does |
| --- | --- |
| `out=<folder>` | where the files go |
| `view=2`, `view=<name>`, `view=front` | the view to read: its number, its name, or its guessed facing; the first view when left out |
| `names=front,threeq,back` | names for the views in reading order, used in file names and by `view=` |
| `box=x,y,w,h` | take this box of the sheet as the one view (for `zoom` and `trace`, the region instead) |
| `boxes=x,y,w,h;x,y,w,h` | take these boxes as the views, in this order |
| `ground=#rrggbb`, `groundTolerance=24` | the background colour, and how far (0 to 255 a channel) a pixel may stray from it |
| `holes=x,y;x,y` | pixels in background a figure closes off, such as the gap a curled tail makes against a leg |
| `height=1.6`, `heightTo=top` | the figure's height in body units, default 1, from the ground to its crown, so ears, buns and hair tufts above the skull do not stretch it; `heightTo=top` measures to its highest point instead |
| `landmarks=crotch:251,556;knee:240,660` | landmarks read off the image, in its pixels: they replace what is found |

| command | writes | example |
| --- | --- | --- |
| `views` | `views.png` (the whole sheet, each view boxed and numbered with its facing guess, shadows dashed, `other` in grey), `<view>.png` a view (with a margin, other figures painted out), `views.json` (each view's box, pixel height, shadow, and facing guess with its confidence; the `other` boxes) | `tools/recipe.sh kit sheet views C:/refs/fox.png names=front,threeq,back` |
| `measure` | `measure-<view>.json`, and a summary of every landmark | `tools/recipe.sh kit sheet measure C:/refs/fox.png view=front height=1.6` |
| `grid` | `grid-<view>.png`: the view with a line every half head from the crown down (labelled `1.5H` and its sheet row `Y413`) and a line a landmark (labelled with its height in body units and its row; green found, orange estimated, blue given), and `measure-<view>.json` | `tools/recipe.sh kit sheet grid C:/refs/fox.png view=side height=1.6` |
| `zoom` | `zoom-<name>.png` scaled 2 to 4 times with crisp edges (`scale=` fixes it), `zoom-<region>.json` with each box | `tools/recipe.sh kit sheet zoom C:/refs/fox.png view=front region=hands` (`head`, `hands`, `feet`, or `box=410,520,80,60`) |
| `palette` | `palette-<view>.png` (a tile a colour: swatch, hex, share, and the view with that colour's pixels shown), `palette-<view>.json` | `tools/recipe.sh kit sheet palette C:/refs/fox.png view=front colors=10` |
| `profiles` | `profiles.json`: the torso, legs and loose arms as radii at stations for `tube` | `tools/recipe.sh kit sheet profiles C:/refs/fox.png front=front side=side stations=7 height=1.6` |
| `trace` | `trace-<part>.json`, `trace-<part>.png` (the outline over the view); `part=` is `head`, `ear-left`, `ear-right`, `tail`, `arm-left`, `arm-right` (left and right as the image shows them) or `figure`, or `box=x,y,w,h`; its points are in body units, x from the view's axis, y up from the ground | `tools/recipe.sh kit sheet trace C:/refs/fox.png view=front part=ear-right open=true tolerance=0.004` |

**measure** finds the silhouette, its upright axis (the middle of the head and upper body), its ground (the lowest point once the shadow is split off) and its top, then reads down the axis. One head is the crown to the chin, and `heads` counts them from the ground to the crown. Each row's runs give the width profile, `profile`: the row's outer edges and the body core's, from the axis in body units (image left negative), and how many runs it crosses. Each landmark has its row `y`, its `height` above the ground in body units, `heads` above the ground, `headsDown` from the crown, and a `status`:

| landmark | read as |
| --- | --- |
| `top`, `earTip` | the highest point; the mean top of the ears or tufts standing above the crown |
| `crown` | the top of the skull: where the head first reaches half its widest, raised to where a round head would top out, and never above the first row on the axis; ears, buns and tufts narrower than that, even over the axis, stay above it |
| `chin`, `neck` | where the head closes toward the neck; the neck's narrowest row |
| `shoulder` | where the steep widening below the neck settles |
| `armpit` | the lower row where an arm parts from the torso, either side |
| `waist`, `hip` | the narrowest torso row between the armpits and the crotch; the widest below it |
| `crotch` | the top of the gap between the legs near the axis; with no gap, estimated at 0.55 of the way from the ground to the neck |
| `knee`, `ankle` | the legs' narrowest row above the feet; the knee where the legs' width strays from straight, else halfway up to the hip |
| `handTip`, `tailTop`, `tailBottom` | the lowest point of the arms standing apart from the body; the tail's extent |

`plan` holds every landmark's height for `bodyPlan`; `widths` the core's half widths at the head, neck, chest, waist, hip, thigh, knee and ankle; `pieces` the ears, arms, tail and other parts outside the core, with their tips. `found` means read from the silhouette, `estimated` placed by proportion or read from legs that touch, `given` taken from `landmarks=`. Read the grid before trusting an estimate.

**profiles** takes the front view (`front=`, default the view guessed front, else the first) and, when there is one, a side view (`side=`, a view of this sheet or another image's path; default a view guessed side). The torso runs from the crotch to the neck (`rest: "up"`: `left` and `right` from the front, `front` and `back` from the side; `facing=left|right` when the side view's feet do not tell which way it faces). Each leg runs from the crotch to the ankle (`rest: "down"`: `outer` and `inner`, and `front` and `back` from the side view). Each arm that stands apart from the body runs from where it leaves the outline to its tip, measured across its own axis. Legs and arms are named by the character's side (`leg-l` is the character's left) from the facing guess. Each part has `length`, `at` (stations from its socket end), `heights`, `confidence` and `notes`; `skipped` lists what could not be read.

| function | one line |
| --- | --- |
| `measureSheet` | `const m = measureSheet("C:/refs/fox.png", { view: "front", names: ["front", "side"], height: 1.6 }); bodyPlan({ crotch: m.plan.crotch, neck: m.plan.neck }, { hips: m.widths.hip ?? 0.15 })` |
| `openSheet`, `readSheet` | `const sheet = openSheet("C:/refs/fox.png", { ground: "#ffffff" }); sheet.views[0].guess.facing` |
| `pickView` | `pickView(sheet, "side")`, by number, name or guessed facing |
| `measureView` | `const front = measureView(sheet, "front", { height: 1.6, landmarks: "crotch:251,556" })` is `{ anatomy, measurement }`. |
| `sheetProfiles` | `const p = sheetProfiles(front, measureView(sheet, "side")); tube({ ...p.torso!, closed: true })` |
| `sheetPalette` | `sheetPalette(sheet, pickView(sheet, 1), { colors: 8 }).colors[0].hex` |
| `traceShape` | `traceShape(front.anatomy, front.measurement, { part: "ear-right", open: true }).points`: a few points in body units, x from the axis, y up from the ground |
| `regionBoxes`, `zoomBox` | `zoomBox(sheet, "head", regionBoxes(front.anatomy, "head", sheet)[0].box).image` |
| `viewsOverview`, `viewCrop`, `gridImage`, `paletteImage`, `traceImage` | the images the commands write, as a `Raster`; `writePng("C:/out/grid.png", gridImage(sheet, front.anatomy, front.measurement))` writes one |
| `keyBackground`, `readRaster` | `keyBackground(readRaster("C:/refs/fox.png")).figure`: the figure mask of any PNG or JPEG |

What the silhouette cannot tell is marked rather than guessed away: legs drawn touching, or a tail or far leg filling the gap between them, hide the crotch (estimated, orange on the grid, or the knees where they touch); arms held against the body give no armpit or hand tip; hair as wide as the head reads as skull, so a big hairdo lowers the head count until `landmarks=crown:x,y` sets the skull's top; background a figure encloses with no way out (a curled tail against a leg) stays figure until `holes=` opens it; figures drawn overlapping are one view until `boxes=` splits them; the facing guess reads symmetry and face detail and is unsure on tilted heads and dark hair; thick ink bordering a dark area of the same tone can stay in the palette as a colour.

## Run helpers

`import { buildRecipe, probeRecipe, writeRecipe, recipePathFor } from "squash-kit/run"` for scripts that drive builds themselves: `buildRecipe(path, { render: false })` and `probeRecipe(path, ["part=all"])` resolve to `{ code, output, files }`, the probe's also `report`, a `ProbeReport` (`parseProbe` reads one from text).

## Developing the kit

```
cd kit && npm ci         # the kit's own dependencies
npm test                 # unit tests, the runner on outside files, the example against the schema, types against the schema
npm run typecheck        # the kit's own sources
node tools/gen-types.ts  # src/schema/recipe.ts from schema/recipe.schema.json; ./gradlew generateSchemas runs it too
node tools/gen-types.ts --check   # fails when they differ; :schema-gen:checkKitTypes runs it in check and checkFast
```

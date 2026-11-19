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
| `leaf` | `leaf({ length: 0.3, width: 0.16, base: 0.17 })`, an open ear or fin Top with its tip a corner. |
| `tuft` | `tuft({ spikes: 3, length: 0.08, width: 0.04 })`, an open outline of pointed tips. |
| `furLock` | `const lock = furLock({ length: 0.2, width: 0.03, curl: 0.05 }); outlines: { side: outline(lock.side), top: outline(lock.top) }` |
| geometry | `Point`, `translate`, `scale`, `rotate`, `flipX`, `flipY`, `lerp`, `steps`, `roundTo`, `roundPoints`. |

## Parts

| helper | one line |
| --- | --- |
| `tubePart` | `tubePart({ id: "legs", on: "torso", socket: "socket.3", ...pair("leg"), joints }, { rest: "down", length, profiles })`, a generic tube at `FINE` (96 rings, 32 segments). |
| `FINE`, `SMALL` | `generic` samplings for big and small generic parts. |
| `socket` | `socket(2, torso.u(plan.h("shoulder")), vToward("up", "left"), { mirrored: true })` |
| `joint` | `joint(1, leg.u(plan.h("knee")), { low: 0, high: 140 })` |
| `socketName`, `jointName` | `socketName(2)` is `"socket.2"`. |
| `pair` | `{ ...pair("arm") }` names the sides `arm-l` and `arm-r`. |
| `sides` | `sides("arm").right` is `"arm-r"`. |
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

## Props

| helper | one line |
| --- | --- |
| `prop` | `prop({ id: "heels", on: "feet", socket: 1, garment: "pumps", color: "patent", rest: "down", side, top })` |
| `roundProp` | `roundProp({ id: "button", on: "torso", socket: 5, garment: "coat", color: "button", radius: 0.012, depth: 0.008 })` |

A prop sits on a hand-made socket of the part it is on, usually `socket(n, u, v, { sink: 0 })`.

## Palette

| helper | one line |
| --- | --- |
| `palette` | `const colors = palette({ skin: "#d8b48e", ink: [0.1, 0.08, 0.08] })`; `palette: colors.slots`, `color: colors.slot("skin")` fails on a slot not declared. |
| `mix`, `shade` | `shade("#d8b48e", -0.2)` is darker, `mix(a, b, 0.5)` halfway. |
| `toRgb`, `toHex` | convert between `#rrggbb` and `[r, g, b]`. |

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

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

Options after the file go to `tools/recipe.sh` unchanged: `render=false`, `outfits=all`, `poses=all`, `close=face,hand,foot,joins` (`close=eyes` for both painted eyes close from the front, three-quarter and side; `close=none` for no close-ups), `set=part:name:value`, `reference=...` (with `fit=true` the camera search can land on the far side of the character, as a silhouette does not tell front from back: it prints the far-side camera too, and `eye=`, `target=` and `fov=` fix the one the reference shows), `targets=<file>,<file>` (proportion targets, see Targets). `probe` prints every socket where its part's `socketsAt` put it. `out=<folder>` puts both the recipe JSON and the build there.

The default export is a recipe, or a function returning one (it may be async). Character files use only syntax Node can strip (no `enum`, no `namespace`), and import types with `import type` or `type` in the braces; the typecheck says so when they don't.

## The recipe

| helper | one line |
| --- | --- |
| `recipe` | `export default recipe({ id: "cat", name: "Cat", plan: "blank", parts })` fills `format`. |
| shading | `recipe({ ..., shading: { farSide: 0.3 } })` sets how much darker the far side is (0 to 0.5, 0.18 when left out); `light: "body"` fixes the light in front of the character instead of turning it with the view. |
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
| `orient` | `{ ...orient([1, 0, -1], [0, 1, 0]) }` is the `rest`, `lean`, `outward` and `roll` pointing a part forward and out with its up up; it throws where lean or outward would pass 85 degrees; a direction exactly 90 degrees from every rest, across the body to the character's right for a left part, is refused with that reason. |
| `around` | `around([1, 0, -1], [0, 1, 0], 5)` is five directions evenly round a forward-out facing, the first up: petals. |

## Shapes

Shapes are `{ points, corners, closed }`; `outline(shape)` makes a recipe outline.

| helper | one line |
| --- | --- |
| `tube` | `tube({ rest: "down", length: leg.length, at: stations.map(leg.x), profiles: { front, back, outer, inner } })` gives `{ side, top }`; Side takes the profiles across its y for that rest, Top the others (by rest: up or down, Side `front` and `back`, Top `left` and `right`; forward or back, Side `up` and `down`, Top `left` and `right`; out, Side `up` and `down`, Top `front` and `back`; `outer` and `inner` name a pair's `left` and `right`), a lone Top side is mirrored; `closed: true` rounds both ends (a torso, a head); `tip: 0` ends flat. |
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
| `furLock` | `const lock = furLock({ length: 0.2, width: 0.03, curl: 0.05 }); outlines: { side: outline(lock.side), top: outline(lock.top) }`: a drawn lock for the older `hair` template; hair is built with the Hair helpers below. |
| geometry | `Point`, `translate`, `scale`, `rotate`, `flipX`, `flipY`, `lerp`, `steps`, `roundTo`, `roundPoints`. |

## Parts

A part's `role` is what it is to the body, one of `body`, `spine`, `neck`, `head`, `jaw`, `ear`, `arm`, `hand`, `leg`, `foot`, `tail`, `wing`, `fin`, `horn`, `hair` or `other`; a part's template gives it one when it has none. A hand or foot on an arm or a leg is measured and framed as one whatever its template. `measure.txt` starts with `part ids`, every part's id with its role: the ids `close=`, `only=`, `on:` and a garment's `covers` take (a `close=` or `only=` naming no part fails with the same list).

| helper | one line |
| --- | --- |
| `tubePart` | `tubePart({ id: "legs", on: "torso", socket: "socket.3", ...pair("leg"), joints }, { rest: "down", length, profiles })`, a generic tube at `FINE` (96 rings, 32 segments). |
| `FINE`, `SMALL` | `generic` samplings for big and small generic parts. |
| `socket` | `socket(2, torso.u(plan.h("shoulder")), vToward("up", "left"), { mirrored: true })`; `{ sink: 0, outset: 0.016 }` stands a part just off a shirt of fit 0.012. |
| `digitPatch` | `socket(1, 0.7, 0.25, { patch: digitPatch(2) })` lies on a hand's third finger and rides it, a ring; `digitPatch("thumb")`, `digitPatch(0, "toes")`. Regions and marks take the same `patch`. |
| `ringSocket` | `ringSocket(4, torso.u(plan.h("waist")), 12, { sink: 0, outset: 0.02 })`, twelve sockets round a part: studs round a belt. |
| `spreadSocket` | `spreadSocket(3, { u: [0.1, 0.9], v: [0, 1] }, { count: 200, seed: 4, spacing: 0.03, sizeJitter: 0.2, leanJitter: 12 }, { size: 1, falloff: 0.5 })`, one socket showing copies of the one part on it over an area: wool, spines, tufts, scales. The part on it gives `shades: ["wool-2", "wool-3"]` for copies in a few slots. |
| `pathSocket` | `pathSocket(4, [0.9, 0.7], [0.9, 0.3], { via: [[0.75, 0]], roll: 90, sag: 0.04 })`, copies of the one part on it along a path: beads, studs, buttons; a `ring` part's links hook through each other, every other one turned by `roll: 90`. `between: ["socket.1", "socket.2"]` takes the ends from two sockets. |
| `joint` | `joint(1, leg.u(plan.h("knee")), { low: 0, high: 140 })` |
| `socketName`, `jointName` | `socketName(2)` is `"socket.2"`. |
| `pair` | `{ ...pair("arm") }` names the sides `arm-l` and `arm-r`. |
| `sides` | `sides("arm").right` is `"arm-r"`. |
| `digitCurl` | `digitCurl("hand-l", { count: 3, curl: [-6, -10, -6], thumb: [6, 4, 2] })` poses every joint of a hand's three numbered fingers and its thumb; `row: "toes"` for a foot or paw. |
| `bothSides` | `displayPose: bothSides("arm", { joint: "base", lean: -28 })` |
| `curve`, `spiral` (part fields) | `curve: [[0.4, 0], [0.7, 0.25], [0.55, 0.5, 0.1]]` or `spiral: { turns: -1.5, pitch: -0.5, shrink: 0.5 }` on any part whose template sweeps (`templates.md`, curve column): a hook, a tusk, a coiled horn or trunk. |

## Hair

Hair is one mass in the one surface: a volume over the skull whose hem parts into locks, clumps blended into it and tufts on fur (`docs/hld/parts.md`, Hair; the skill's `head-and-face.md`, Hair). Lengths are body units; a head's own `size` does not scale its hair, so give the head's depth and height as built.

| helper | one line |
| --- | --- |
| `hairVolume` | `const hair = hairVolume({ on: "head", socket: 11, depth: 0.29, height: 0.33, preset: "bob", color: "hair", shine: "hair-light" })`: `hair.part` and `hair.headSocket`, the head's socket at the back of the skull standing off it by `thickness`; its hem parts into locks (`locks`, `lockDepth`, `lockJag`, `lockFlare`), inked as layers (`layers`, `layerGap`), lock lines (`lockLines`) and fine strands gathering into the locks (`strands`); `hem` colours an even band along every lock's end, left out for hair coloured to its tips. |
| `hairClump` | `hairClump({ id: "lock", on: "hair", socket: 3, length: 0.12, preset: "fine", curve: [[0.5, 0.05], [0.95, 0.15]] })`: one curved, tapering lock blended into the volume at its root; `params` sets `width`, `taper`, `tipRound`, `points`, `twist`, `wave` and the rest. |
| `hairTuft` | `hairTuft({ id: "tuft", on: "head", socket: 10, length: 0.06, preset: "crown" })`: a soft tuft blended into what it grows from. |
| `fringe` | `fringe({ id: "fringe", on: "hair", firstSocket: 1, count: 5, lengths: [0.2, 0.17, 0.15, 0.12, 0.1], from: 0.14, to: -0.1, sweep: -0.45 })`: `{ sockets, parts }`, clumps over the brow swept to one side, `fine` clumps unless `preset` says otherwise. |
| `fall` | `fall({ id: "fall", on: "hair", socket: 10, area: { u: [0, 0.78], v: [0.22, 0.78] }, count: 42, length: 0.12, droop: 86, jitter: 0.25 })`: one clump as copies lying down over a part, each its own mesh: for fur and manes rather than hair that merges. |
| `crown` | `crown({ id: "cowlick", on: "hair", firstSocket: 20, count: 2, length: 0.06, turn: 70, rise: 30 })`: clumps round the crown, a swirl lying flat or, rising, a cowlick. |
| `HAIR_LENGTHS` | the templates' own lengths at size 1, which turn a length into `size`. |

Put a set's `sockets` on the volume's `sockets` and its `parts` among the recipe's parts:

```ts
const hair = hairVolume({ on: "head", socket: 11, depth: 0.29, height: 0.33, preset: "bob", color: "hair", shine: "hair-light", params: { strands: 50 } });
const front = fringe({ id: "fringe", on: "hair", firstSocket: 1, count: 5, lengths: [0.18, 0.15, 0.13, 0.11, 0.09], sweep: -0.4, color: "hair", params: { width: 0.3, thickness: 0.2 } });
const lock = hairClump({ id: "lock-1", on: "hair", socket: 20, length: 0.13, preset: "fine", color: "hair", rest: "down", curve: [[0.5, 0.06], [0.95, 0.16]] });
head.sockets = [hair.headSocket];
const parts = [head, { ...hair.part, sockets: [...front.sockets, socket(20, 0.4, 0.3, { sink: 0.5 })] }, ...front.parts, lock];
```

### Lock hair

Lock hair is built from separate swept locks in groups over an inner mass, never in the one surface: its outline breaks where the cut has points or flicks (`docs/hld/parts.md`, Hair locks). It is a recipe's `hair` list, one entry a head. `docs/reference/hair.md` lists every group field with its default, range and units, each kind, section and tip, each preset's groups with their values and flow, the flow's fields and the fitted mass's parameters.

| helper | one line |
| --- | --- |
| `lockMass` | `lockMass()`: the inner mass fitted to the head as built, as the app's preset menu fits one: a `hair-volume` cap with no strands that only hides the scalp, sized by the head's box with its top following the skull's dome, its front above the brow under the fringe's roots and its hem cut into points. Give it as a preset's `mass`; `lockMass({ id, color, params })` names it, colours it or overrides the fit (`params`: `width`, `back`, `sides`, `fringe`, `rise`, `crown`, `locks`, `lockDepth`, `lockJag`, ranges in hair.md, Inner mass). `lockMass({ on, socket, depth, height })` places one by hand instead and returns `{ part, headSocket }`. |
| `shortSpiky`, `bob`, `longStraight`, `ponytail`, `sideSwept`, `curly` | `bob({ on: "head", mass: lockMass(), color: "hair" })`: one `hair` entry of that preset, sized to the head as built (`reach` gives it by hand). `ponytail` takes `band`, the tie's slot; `curly({ ..., tuft: true })` keeps only its crown, a tuft of curls rolling over the brow. |
| `lockHair` | `lockHair("bob", { on: "head", groups: [{ id: "fringe", count: 9, tip: "forked" }, { id: "strays", kind: "strays", count: 3 }] })`: a preset (or `null`) with groups changing the preset's by id (`back`, `left`, `right`, `crown`, `top`, `fringe`, `strays`, `tail`; which a preset holds is in hair.md), adding new ones, which need a `kind`, or leaving one out with `removed: true`. No `groups`, or `groups: []`, keeps the preset's as they are. A group's `locks` set single locks by index: any field, a `guide` of its own, `hidden`. |
| `centrePart`, `sidePart` | `sidePart("right", 0.6)`: a parting for a hair entry's `flow.part`, `at` 0 to 1 of the most a parting sits off the middle (about over the outer corner of the eye); either takes `clean` (0 a soft, wandering split to 1 sharp), `length` (1 to the crown) and `color` (the scalp's slot along it). |
| `whorl`, `cowlick` | `whorl({ around: 170, up: 70, turn: 0.5 })`: the crown point hair spirals out from (`around` degrees from the front, 180 the back; `up` degrees from level; `turn` -1 to 1, clockwise seen from above). `cowlick({ around: 15, up: 48, turn: 0.7, lift: 40 })`: a local swirl (`turn` 1 or -1) or flip (`turn` 0) reaching `radius` degrees, its locks lifted `lift` degrees: a fringe flick at the hairline. |

```ts
const hair = [
  sideSwept({
    on: "head", mass: lockMass(), color: "hair",
    flow: { part: sidePart("left", 0.5), whorl: whorl({ turn: -0.4 }), cowlicks: [cowlick({ around: -15, up: 50, lift: 30 })] },
    groups: [{ id: "fringe", count: 7, sweep: 0.3 }],
  }),
];
// hair is the recipe's hair; the fitted mass is added to the head by the build.
```

A hair entry's `flow` sets which way every lock root grows: `whorl`, `part`, `sweep` (an overall comb toward the character's right, -1 to 1), `sweepBack` (toward the back) and `cowlicks`; each field given replaces the preset's flow's. A group follows the flow as far as its `flow` (1 by default; 0 keeps its kind's own way), its `sweep` adds to it, and a lock's own values win over both. Roots keep off a parting, so the scalp shows along it as a thin line. Each group's `physics` (stiffness, damping, weight, gravity, wind, angleLimit, neighbours) and each lock's chain of 3 to 6 bones are kept for a solver; `colliders: false` leaves out the body's collision shapes.

## Face

Face places are the head seen from straight ahead: x to the character's left, y up, in the head's drawn units from its socket. `tools/recipe.sh kit probe <file> part=head` prints a head's reach.

| helper | one line |
| --- | --- |
| `faceRegion` | `faceRegion("mask", "cream", oval({ center: [0, 0.12], rx: 0.08, ry: 0.06 }))` |
| `uvRegion` | `uvRegion("blaze", "cream", [[0.6, 0.9], [0.9, 0.95], [0.9, 0.05], [0.6, 0.1]], { patch: "body" })` |
| `uvRing` | `uvRing("stripe", "dark", 0.3, (v) => 0.36 + 0.02 * Math.sin(2 * Math.PI * v))`: a region all the way round a part between two edges, each a u, u values evenly round or a function of v, with no seam where its ends meet |
| `faceMark`, `uvMark` | `faceMark("brow", arc({ center: [0.05, 0.2], rx: 0.03, from: 30, to: 150 }), { slot: "brow", mirrored: true })` |
| `smile` | `faceMark("mouth-line", smile({ center: [0, 0.05], width: 0.04, depth: 0.01 }))`; negative depth frowns. |
| `taper` | `width: taper(0.3)` thins a line's ends. |
| `faceEye` | `faceEye("eyes", { on: "head", face: [0.05, 0.14], look: { white: "eye-white" } })`, mirrored unless told. |
| `animeEye` | `look: animeEye({ white: "eye-white", iris: "iris", pupil: "ink" }, { clumps: 2, lidShadow: 0.25 })`: the look with a drawn toon eye's detail on (a heavy upper lid mass tapering to the front corner with a flick past the back one, a light partial lower lid, lash clumps in place of the flicks, an iris with a darker top, a lighter crescent and a rim, a lid shadow on the white, a flatter top over a sharper back corner); each of `lid`, `flick`, `flickLift`, `lower`, `lowerExtent`, `clumps`, `clumpLength`, `shade`, `crescent`, `rim`, `lidShadow`, `flatTop` and `outerCorner` sets one part's strength, 0 turning it off, and the look's own `lidMass`, `lashClumps`, `irisShade` and `lidShadow` (their slots among them) are laid over the preset. |

Head cheats that follow the camera are the recipe's `viewKeys`, typed by the schema with no helper: `viewKeys: { face: "fixed", faceFollow: 0.8, keys: [{ view: "three-quarter", moves: [{ feature: "muzzle", yaw: 12, left: 0.04 }, { part: "ear-l", twist: 20 }] }, { view: "back", moves: [{ feature: "head", width: 1.06 }] }] }`. `face` is `turning` (the face slides part way toward the camera and the head's shape changes by view) or `fixed` (one head shape from front to side, the face kept turned to the viewer); keys are written for the camera on the head's left and mirrored for the right unless a key names `side`. Left out, every head takes its template's defaults. `close=orbit` renders the head round from front to back with and without its keys, and turned on its neck under a fixed camera (docs/reference/face-and-clothes.md, View keys).

## Clothes

| helper | one line |
| --- | --- |
| `cover` | `cover("torso", { from: torso.u(0.9), to: 0.96 })`, hems snapped to the 64 by 32 chart's cell edges. |
| `band` | `band("torso", torso.u(plan.h("waist")), 0.04)`, at least two cells wide. |
| `snapU`, `snapV` | the nearest cell edge. |
| `garment` | `garment({ id: "tee", slot: "top", color: "shirt", fit: 0.008, covers })`; its name from its id; `hemRoll: 0.006` rolls its hems and cuffs, `inkWeight: 1` inks it as heavily as skin (0.7 when left out); `creases`, `wrinkles` and `drape` (0 to 2, 1 when left out) scale its creases, wrinkles and gathers above its hems, which are on unless the recipe's `creases` sets `cloth: 0`. |
| `ease` | `ease([[0, 0.006], [0.7, 0.02], [1, 0.03]])`: a stand-off profile along a part for a garment's `eases: { torso: ..., "arm-l": ... }` (a pair by its left side), each key `[u, ease]` or `[u, front, back]` in body units, in order along u; the cloth follows one smooth curve through them, flat past the ends, over the garment's `fit`. With it a garment takes `hang: { limit: 0.03, flare: 0 }` (cloth over an upright part falls from its highest covered place instead of following the body in, by at most `limit`), `drops: { "arm-l": 0.12 }` (a dropped seam, inked, down the part's u) and, on an area, `gather: 0.005` (a ribbed band pulling the cloth in). Throws on keys out of order or a negative ease. |
| `opening`, `crewNeck`, `vNeck`, `openingBand`, `ribCollar` | `opening("ear-l")` is an `opening` stroke, unpainted by default: the cells round where a part comes out (a neck, an arm, an ear), within `reach` past its root ring, its front dropping `depth` in its `cut` (`"round"` or `"v"`). `crewNeck("neck", { depth: 0.03 })` and `vNeck("neck", { depth: 0.1 })` are necklines for a top's area; `openingBand("neck", { cut: "v", depth: 0.08 }, 0.02)` gives the strokes of a band that wide round the opening (the wider opening painted, the opening unpainted), for an area after the cloth it cuts; `ribCollar(id, neck, shape, { width, gather, color })` is that band as a gathered area. |
| `foldedBand`, `turtleneck`, `ribLines` | `foldedBand("cuff", "leg-l", 0.86, 0.93, { fold: 0.007, color: "denim-light" })`: an area of the hem's rows with `fold`, cloth turned back over the garment, standing out by that thickness with its far edge inked, in the colour of the cloth's back face: a rolled cuff or jeans hem. `turtleneck({ top: 0.7, color: "rib" })` gives a gathered collar up the neck and its top folded back down over it. `ribLines(id, part, from, to, count, slot)` prints a knit band's ribs as garment marks. |
| `crewSweater`, `cuffedJeans` | `crewSweater({ color: "knit", rib: "rib", hem: torso.u(plan.h("hip")), neckline: { cut: "round", depth: 0.03 } })`: a top over the torso from `hem` and the arms, with a gathered rib area holding its hem band, cuffs and a collar band round the neckline (`neckline: { cut: "v", depth }` for a V-neck); `cuffedJeans({ color: "denim", cuffColor: "denim-light", waist, hem, cuff: 0.06, fold: 0.006 })`: jeans to `waist` and down the legs to `hem`, their hem rolled up as a folded cuff. Both take any garment field besides (`eases`, `hang`, `fit`, `id`, ...). |
| `sheerStockings`, `fishnetStockings` | `sheerStockings({ color: "nylon", top: leg.u(plan.h("hip") - 0.2), opacity: 0.25, band: 0.05, toe: 0.25 })`: stockings over the leg pair from `top` and the whole foot, the skin showing through tinted and darker toward the silhouette, under an opaque band (`bandColor`) and, with `toe`, a reinforced toe (`toeColor`), 2.4 times as dense as the leg unless `toeOpacity` says; `fishnetStockings({ color: "black", top, net: { pattern: "diamond", hole: 0.03, thread: 0.004, angle: 40 } })`: the same with a net leg, `pattern: "square"` for a square mesh. Any area takes `opacity` (0 to 1, times its slot's) and `net` on its own. Both take any garment field besides. |
| `through`, `hide`, `redraw`, `swap` | `changes: { neck: through(), "foot-l": swap("blob", { outlines }) }` |
| `outfit` | `outfit("casual", { top: "tee" })`; `outfit("bare", {})` has no garments and renders the bare body (list it first to start on it). |
| `wardrobe` | `wardrobe([tee], [outfit("casual", { top: "tee" })])`, slots read from the garments, the first outfit shown (saved as the character's shown outfit, and the one `measure.txt` marks `(shown)`) unless `wardrobe(garments, outfits, "id")` names another. |
| `slots`, `LAYER` | `slots("top", "feet")`; `layer: LAYER.over`. A slot is any id (`slot: "scarf"` with `wardrobe` reading it from the garment, or `slots("scarf")`); an outfit takes one garment a slot. `LAYER` names layers 1 to 3 counted out from the skin (`under`, `waistband`, `over`); any whole `layer` works, and a higher one lies over every lower one (`layer: 4` over a coat), garments on the same layer and part sharing it. |
| `printOn`, `printAll` | `regions: [printOn("torso", uvRegion("badge", "red", leaf({ ... }), { ink: true }))]`, a region or mark printed on the cloth over a body part. |

## Props

| helper | one line |
| --- | --- |
| `prop` | `prop({ id: "heels", on: "feet", socket: 1, garment: "pumps", color: "patent", rest: "down", side, top })` |
| `roundProp` | `roundProp({ id: "button", on: "torso", socket: 5, garment: "coat", color: "button", radius: 0.012, depth: 0.008 })` |

A prop sits on a hand-made socket of the part it is on, usually `socket(n, u, v, { sink: 0 })`; on cloth add `outset` a little past the garment's `fit`. `prop` takes `lean`, `outward`, `roll` and `topMiddle` (a lens drawn curved in Top wraps the face), and on a `tube` prop `curve` or `spiral` (a bow's loop or a ribbon's end: Side and Top give its width along the curve).

## Palette

| helper | one line |
| --- | --- |
| `palette` | `const colors = palette({ skin: "#d8b48e", ink: [0.1, 0.08, 0.08] })`; `palette: colors.slots`, `color: colors.slot("skin")` fails on a slot not declared. `palette({ lens: "#203040" }, { opacity: { lens: 0.35 } })` makes a part coloured `lens` see-through; `palette({ skin: "#d8b48e" }, { shadow: { skin: "#b08a74" } })` gives a slot its own shadow tone (a warm shift of the colour when left out). |
| `mix`, `shade` | `shade("#d8b48e", -0.2)` is darker, `mix(a, b, 0.5)` halfway. |
| `toRgb`, `toHex` | convert between `#rrggbb` and `[r, g, b]`. |

## Targets and the proportion check

`measure.txt` of every build holds a `proportions` block: the built character's `headHeight`, `headWidth`, `handLength`, `handWidth`, `footLength`, `footWidth`, `eyeWidth`, `eyeSpacing` and `eyeLine`, each as body units, a share of the head's height (crown to chin) and a share of the figure's height (ground to the top of the head). `eyeLine` is a place: its depth down the head from the top as a share of the head, and its height. The hand, foot and head are the bare body's parts at rest (the first hand and foot by their use, a hand's length its longest extent and its width the larger of the other two, a foot's length and width its footprint), the eyes the first painted eye on the head read through the surface under it, so its `look` units are body units (see Eyes in `docs/reference`).

A recipe holds the build to proportions it declares, or to a sheet's:

```ts
export default recipe({ ..., targets: targets({ handLength: "0.38 head", footLength: "0.12 height", eyeLine: "0.55 head" }, { tolerance: 0.1 }) });
```

A target's value is `"<share> head"` or `"<share> height"`, or `{ of: "head", value: 0.4, tolerance: 0.1 }`. `measure.txt` prints each as `ok target ...` or `FLAG target ...` with the built share and how far off it is: more than the tolerance (a share of a size, 0.15 when left out; a share of the head for `eyeLine`, 0.05) is flagged, and so is a target that names no measured proportion or one the build could not measure, with the reason. A reference sheet's measure writes `targets-<view>.json` (`kit sheet measure`, the found readings a build measures; `unsure=true` adds the unsure ones), which a build checks beside the recipe's own: `tools/recipe.sh kit cat.ts targets=C:/out/targets-front.json,C:/out/targets-back.json`.

An eye's `look` lengths are shares of the head's own coordinates at the eye (`across`, `down`), so an eye's width in body units is `2 * look.width * unit across`, and its lid's rise `look.height * unit down`. The unit across at the eye is the head's ring round it (about `pi * (1.5 * (a + b) - sqrt(a * b))` for a ring of half widths `a` and `b`, 0.97 for 0.16 and 0.15), the unit down the head's length from chin to crown (0.4). An eye 0.09 wide on that head takes `width` 0.09 / (2 * 0.97) = 0.046, and one 0.06 high from its middle to its lid's top `height` 0.06 / 0.4 = 0.15. The quicker way is the build's own number: read `eyeWidth` in `measure.txt`, and scale `look.width` and `look.height` by target over built; the `eyeWidth` it prints is `2 * look.width` times the unit across the surface gives there.

`measure.txt` also prints each garment's hems as heights above the ground (`garment tee hem heights above the ground: torso u 0.13 at 0.648, u 1.00 at 1.116`), read off the built body at rest, so a hem placed by u can be checked against the height it was meant for.

## Reading a character sheet

`tools/recipe.sh kit sheet <command> <image> [options]` reads a PNG or JPEG character sheet before any build: where the views are, how tall each part is in heads and body units, the colours, and radii ready for `tube`. Each command writes JSON and images to `out=<folder>` (default `<image>-sheet` beside the image) and prints every path it writes. Look at the images: they are drawn to be read by eye.

The background is keyed from the sheet's border: its dominant colour, a gentle gradient fitted to it, and a flood fill from the edges, so off-white, graded or lightly textured paper works. Pale parts of a figure within the paper's tolerance (white socks, pale hands, a cream sleeve) are kept as figure where they stand clear of the paper at a stricter tolerance, are no cast shadow (which only darkens the paper), are wide enough to survive noise and touch the figure; `pale=false` leaves them to the flood fill. Views are the figures standing on it, found as connected blobs, with thin strokes (hatching, ground lines) cut so they do not join two figures. A small piece near a figure joins it (a detached tail, ear or floating flower); a cluster of small pieces (swatches, text, dots) and anything else small is `other`, with its box. A cast shadow under the feet is split off and reported, so each view stands on its own lowest point. Views are numbered in reading order, rows from the top, and each is measured in its own head units, so views at different scales on one sheet are fine.

Options every command takes:

| option | does |
| --- | --- |
| `out=<folder>` | where the files go |
| `view=2`, `view=<name>`, `view=front` | the view to read: its number, its name, or its guessed facing; the first view when left out |
| `names=front,threeq,back` | names for the views in reading order, used in file names and by `view=` |
| `box=x,y,w,h` | take this box of the sheet as the one view (for `zoom` and `trace`, the region instead) |
| `boxes=x,y,w,h;x,y,w,h` | take these boxes as the views, in this order |
| `ground=#rrggbb`, `groundTolerance=24`, `pale=false` | the background colour, how far (0 to 255 a channel) a pixel may stray from it, and whether pale parts of a figure within that tolerance are kept (`pale=false`: not) |
| `holes=x,y;x,y` | pixels in background a figure closes off, such as the gap a curled tail makes against a leg |
| `height=1.6`, `heightTo=top` | the figure's height in body units, default 1, from the ground to its crown, so ears, buns and hair tufts above the skull do not stretch it; `heightTo=top` measures to its highest point instead |
| `landmarks=crotch:251,556;knee:240,660` | landmarks read off the image, in its pixels: they replace what is found; `wrist:x,y;handTip:x,y` give a hand's length, `heel:x,y;toeTip:x,y` a foot's |

| command | writes | example |
| --- | --- | --- |
| `views` | `views.png` (the whole sheet, each view boxed and numbered with its facing guess, shadows dashed, `other` in grey), `<view>.png` a view (with a margin, other figures painted out), `views.json` (each view's box, pixel height, shadow, and facing guess with its confidence; the `other` boxes) | `tools/recipe.sh kit sheet views C:/refs/fox.png names=front,threeq,back` |
| `measure` | `measure-<view>.json`, `targets-<view>.json` (the found hand, foot and eye readings as recipe `targets`, as shares of the head; `unsure=true` adds the unsure ones), and a summary of every landmark and reading. `views=front,side,back` (or `views=all`) measures each of those views and writes `measure-across.json`: `handLength`, `handWidth`, `footLength` and `footWidth` across them, each from the views that read it truly (a foot's width from the front or back, its length from the side), with every view's share of the head; unsure where two views differ by more than 20% of the larger (the reason names them), or where a width is more than 0.6 of its length for a foot, 0.8 for a hand | `tools/recipe.sh kit sheet measure C:/refs/fox.png views=front,side,back height=1.6` |
| `grid` | `grid-<view>.png`: the view with a line every half head from the crown down (labelled `1.5H` and its sheet row `Y413`) and a line a landmark (labelled with its height in body units and its row; green found, magenta unsure, orange estimated, blue given; the outline points a landmark was read from are crossed), and `measure-<view>.json` | `tools/recipe.sh kit sheet grid C:/refs/fox.png view=side height=1.6` |
| `check` | `check-<view>.png`: one close-up a landmark, side by side, each with its line drawn across in its status colour, the outline points it was read from crossed, and under it the landmark's status and reason; `measure-<view>.json`; the summary | `tools/recipe.sh kit sheet check C:/refs/fox.png view=front` |
| `zoom` | `zoom-<name>.png` scaled 2 to 4 times with crisp edges (`scale=` fixes it), the region's readings drawn on it in their status colours (`hands`: wrist to tip and the width; `feet`: width and length; `head`: eye line, eye width and height, spacing, nose and mouth, with the face check drawn on it: the skin area's outline, the middle line, each eye's box, the muzzle, the nose and the mouth) and printed, `zoom-<region>.json` with each box and reading | `tools/recipe.sh kit sheet zoom C:/refs/fox.png view=front region=hands` (`head`, `hands`, `feet`, or `box=410,520,80,60`) |
| `palette` | `palette-<view>.png` (a tile a colour: swatch, hex, share, and the view with that colour's pixels shown), `palette-<view>.json` | `tools/recipe.sh kit sheet palette C:/refs/fox.png view=front colors=10` |
| `profiles` | `profiles.json`: the torso, legs and loose arms as radii at stations for `tube` | `tools/recipe.sh kit sheet profiles C:/refs/fox.png front=front side=side stations=7 height=1.6` |
| `trace` | `trace-<part>.json`, `trace-<part>.png` (the outline over the view); `part=` is `head`, `ear-left`, `ear-right`, `tail`, `arm-left`, `arm-right`, `foot-left`, `foot-right`, `hand-left`, `hand-right` (left and right as the image shows them) or `figure`, or `box=x,y,w,h`; its points are in body units, x from the view's axis, y up from the ground. A foot or hand with `as=side`, `top` or `front` is also written in its own frame as a recipe outline, printed as a line to paste (`side: { through: [...], closed: true }`) with its size, into `trace-<part>-<as>.json` and `.png` (the socket crossed in blue); see below | `tools/recipe.sh kit sheet trace C:/refs/fox.png view=front part=ear-right open=true tolerance=0.004` |

**measure** finds the silhouette, its upright axis (the middle of the head and upper body), its ground (the lowest point once the shadow is split off) and its top, then reads down the axis. One head is the crown to the chin, and `heads` counts them from the ground to the crown. Each row's runs give the width profile, `profile`: the row's outer edges and the body core's, from the axis in body units (image left negative), and how many runs it crosses. Each landmark has its row `y`, its `height` above the ground in body units, `heads` above the ground, `headsDown` from the crown, and a `status`:

| landmark | read as |
| --- | --- |
| `top`, `earTip` | the highest point; the mean top of the ears or tufts standing above the crown |
| `crown` | the top of the skull: where the head first reaches half its widest, raised to where a round head would top out, and never above the first row on the axis; ears, buns and tufts narrower than that, even over the axis, stay above it |
| `chin`, `neck` | where the head closes toward the neck; the neck's narrowest row |
| `shoulder` | where the silhouette's outline turns from the neck and trapezius into the arm, read on each side from the outline's slope and checked against where the width stops rising; clothes count only by their outline, never a collar or a seam |
| `armpit` | the first row below the shoulder where background parts an arm from the torso, either side; the higher when the sides differ; with no gap (arms against the body) or raised arms, placed 0.3 of the way from the shoulder to the crotch and estimated |
| `waist`, `hip` | the narrowest torso row between the armpits and the crotch; the widest below it |
| `crotch` | the top of the gap between the legs near the axis; with no gap, estimated at 0.55 of the way from the ground to the neck |
| `knee`, `ankle` | the legs' narrowest row above the feet; the knee where the legs' width strays from straight, else halfway up to the hip |
| `handTip`, `tailTop`, `tailBottom` | the lowest point of the arms standing apart from the body; the tail's extent |

Beside the landmarks, `proportions` in the measure file holds the sizes of the hands and feet and the face's key places, each with its value in body units, `ofHead` (a size's share of the head's height, or a place's depth down the head from the crown), `ofHeight`, a `status` (`found`, `unsure`, `estimated`, `given`), a `reason` when it is not found, and the pixel `lines` it was read along; `unread` names each one that could not be read in this view with why. A recipe's `targets` use the same names (the build check above).

| reading | read as |
| --- | --- |
| `handLength`, `handWidth` | a hand told from its sleeve by the face's skin colour (the skin-coloured part at an arm's far end, its length along and its width across its own principal axis), else from the arm's width profile (the wrist the narrowest row in the far 60% of an arm, the hand from there to the tip, its width the widest row beyond). The mean of two hands; unsure where they differ by a quarter, the wrist cannot be told from the hand, or the length is not 0.15 to 0.9 heads, or the hand is wider than 0.8 of its length (read with the other hand or the body, or seen end-on). A hand's skin that runs across the figure's axis (hands touching, a hand on the body) is cut there, unsure. A spread hand is as wide as its spread fingers. Arms against the body give none |
| `footWidth` | each foot's own widest row between 0.35 of the way from the ankle to the ground and the ground, read in a front or back view. The figure below the ankle that stands on the ground is split into two feet at the gap between them, at the deepest notch in their lower half where they touch, or, with no notch, under the gap between the legs (unsure); feet that cannot be told apart are one (unsure). Unsure where the ankle is, the feet differ by 30%, or a given `heel`/`toeTip` length makes the width more than 0.6 of it |
| `footLength` | the longest stretch of the figure on its lowest rows, read in a side view; unsure in a three-quarter view (the foot is foreshortened); a front or back view gives none (give `landmarks=heel:x,y;toeTip:x,y`) |
| `eyeLine`, `eyeWidth`, `eyeHeight`, `eyeSpacing` | the face is the skin area inside the head below the hair (the commonest colour of the head's lower middle, the convex hull of its pieces, hair told by its colour and hue and left out, the head's outline ink left out). Each eye is the outline of lid line, lash and white together, found as the part of the face that stands out from the skin or is whiter and less coloured than it (a pale white in pale skin counts), seeded where it is thick, cut from a brow, muzzle or lash it touches by opening further, and taken from the white alone where a stub of something else came with it. A pair is two eyes about level and mirrored across the face; a turned head allows a narrower far eye and a pair off the middle. `eyeWidth` and `eyeHeight` are the mean of the two eyes (the larger in a turned head or when they differ), `eyeSpacing` middle to middle, `eyeLine` the mean row. Unsure, with the reason, where the two widths or heights differ by a quarter, the eyes sit a twentieth of the head apart in height, hair lies over an eye's top (its height is then read from the other eye), only one eye is found, or the view is three-quarter; each eye's own width and height are in `face.eyes` |
| `nose`, `mouth`, `chinLength` | small marks on the face's middle line below the eyes: the lowest is the mouth (a line or a small shape, also an open mouth), the highest above it the nose (a dot or a shade); a lone mark is a mouth when it is at least an eighth of the head wide, else a nose. A patch of another colour across the line (a muzzle, `face.muzzle`) is looked into against its own colour, so a nose pad and a mouth line inside it are read. Found where the eyes are a front pair and no more than two marks compete; else unsure, and `unread` says why a mark was not found. `nose`, `mouth`, `eyeHeight` and `chinLength` are not recipe targets: a build measures only `eyeLine`, `eyeWidth` and `eyeSpacing` of the face, so `targets-<view>.json` holds those |

`plan` holds every landmark's height for `bodyPlan`; `widths` the core's half widths at the head, neck, chest, waist, hip, thigh, knee and ankle; `pieces` the ears, arms, tail and other parts outside the core, with their tips. Each landmark's `status` is `found` (read from the silhouette), `unsure` (read, but the reading is doubtful), `estimated` (placed by proportion, or read from legs that touch) or `given` (taken from `landmarks=`), and every one that is not found carries a `reason`, printed under it by `measure` and under its tile on the check image: for example `armpit: no gap between an arm and the torso`, `shoulder: two methods disagree by 0.15 heads`. `unsure` is drawn magenta on the grid, `estimated` orange, so a guess is never drawn like a measurement.

Every landmark is also checked against the others and against a standing figure's proportions, and `checks` in the measure file lists each one broken: landmark order down the body, the head's share of the height, the shoulder within a head of the chin, the armpit 0.1 to 0.6 heads (and 0.08 to 0.5 of the way to the crotch) below the shoulder, the waist between armpit and crotch, the crotch at 0.2 to 0.62 of the height, the knee 0.3 to 0.7 of the way from crotch to ankle, the ankle 1 to 20% of the height above the ground. A found landmark a check names becomes `unsure` with the check as its reason; given landmarks are left as given. A waist, hip, knee or ankle read on a clothing edge (the median colour across the body jumps at that row: a sweater's ribbed hem, a waistband, a cuff, a sock's top, within 2% of the height) is `unsure`, saying the body's own may lie under it, and a waist that is not narrower than the chest above it by 8% is `estimated` 0.4 of the way from the armpit (or shoulder) to the crotch, the clothes hiding it. Every landmark below the neck of a view guessed three-quarter is `unsure`, with a `warnings` entry for the view; another warning says the outline rises above the skull (tufts, ears, buns) so the crown is only as sure as the round-skull rule. These are first-draft measurements, not ground truth: look at `check-<view>.png` and fix any wrong landmark with `landmarks=name:x,y` before using `plan`, `widths` or `profiles`.

The facing guess (`views.json`, `guess`) tells a back from a front by how much of the head is one colour (hair against a face: 88% or more is a back), and a side, three-quarter and front by the silhouette's symmetry over the head and upper body; `reason` says what the call rests on. Front against three-quarter is a weak call (a turned body with its face to the viewer reads front), so look at the view; a bald or helmeted front reads as a back.

**trace** with `as=` places a foot or hand in its part frame (`docs/reference/frames.md`), closed, 6 to 14 points unless `tolerance=` is given. A foot is the figure below the ankle row that stands on the ground (socks and shoes included), cut there; its socket is the middle of that row, from the ankle mark or `ankle=x,y`. `as=side` (from a side view): x forward from the ankle, toes positive (the way the foot reaches further, or the side view's facing, or `facing=left|right`), y up from the ankle, the sole negative. `as=top` (from a `box=` crop of a view from above, toes toward the image's top or `tip=x,y`): x forward, y toward the character's right; the ankle a quarter of the way from the heel, or `ankle=`. `as=front` (from a front or back view, touching feet split as `footWidth` splits them): the cut section in -1 to 1, x the character's left, y up. A hand runs from its wrist (where its skin starts, else the arm's narrowest row; `wrist=x,y`) toward its fingertip (`tip=x,y`), the arm cut away: `as=side` y forward (a side view), `as=top` the hand edge-on from the front or back, y toward the character's right; a posed hand is traced from `box=` with `wrist=` and `tip=`. A pair's recipe part is its left: the printout says which side of the sheet was traced, and a character's right foot or hand is mirrored (y for Top, x for Front) onto the left. The printed size (a foot's length, width or height, a hand's length and breadth) checks against `measure`.

**profiles** takes the front view (`front=`, default the view guessed front, else the first) and, when there is one, a side view (`side=`, a view of this sheet or another image's path; default a view guessed side). The torso runs from the crotch to the neck (`rest: "up"`: `left` and `right` from the front, `front` and `back` from the side; `facing=left|right` when the side view's feet do not tell which way it faces). Each leg runs from the crotch to the ankle (`rest: "down"`: `outer` and `inner`, and `front` and `back` from the side view). Each arm that stands apart from the body runs from where it leaves the outline to its tip, measured across its own axis. Legs and arms are named by the character's side (`leg-l` is the character's left) from the facing guess. Each part has `length`, `at` (stations from its socket end), `heights`, `confidence` and `notes`; `skipped` lists what could not be read, and `warnings` says when a view it reads from is guessed three-quarter (its widths and depths are not true ones): every part's `confidence` is then `low`, with the warning in its `notes`.

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
| `checkLandmarks` | `checkLandmarks(marks, headPixels)` is `{ marks, issues }`: the proportion checks on landmarks in pixels, as `measure` runs them |
| `viewsOverview`, `viewCrop`, `gridImage`, `checkImage`, `paletteImage`, `traceImage` | the images the commands write, as a `Raster`; `writePng("C:/out/grid.png", gridImage(sheet, front.anatomy, front.measurement))` writes one |
| `keyBackground`, `readRaster` | `keyBackground(readRaster("C:/refs/fox.png")).figure`: the figure mask of any PNG or JPEG |

What the silhouette cannot tell is marked rather than guessed away: legs drawn touching, or a tail or far leg filling the gap between them, hide the crotch (estimated, orange on the grid, or the knees where they touch); arms held against the body give no armpit (estimated) or hand tip, and an arm that touches the torso lower than the other parts from it makes the armpit unsure; hair as wide as the head reads as skull, so a big hairdo lowers the head count until `landmarks=crown:x,y` sets the skull's top; background a figure encloses with no way out (a curled tail against a leg) stays figure until `holes=` opens it; figures drawn overlapping are one view until `boxes=` splits them; the facing guess reads head colour and silhouette symmetry and is weak between front and three-quarter, and on tilted heads; thick ink bordering a dark area of the same tone can stay in the palette as a colour.

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

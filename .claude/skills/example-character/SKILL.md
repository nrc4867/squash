---
name: example-character
description: Make or improve a Squash character, an example or a test model, dressed or not, by writing a recipe file, building it with one command and judging its renders. Use before adding or reshaping anything under examples/ or recipes/, or when asked for a new test model.
---

# Make a character

A character is a **recipe**: a JSON file (`recipes/<name>.recipe.json`, or anywhere for a private model) that says what the character is in artist terms. `tools/recipe.sh <recipe>` builds it into a character file the app opens, renders it, and measures it. You write the recipe, run the command, read the renders, change one thing, run again. No Kotlin and no source reading: if you find yourself opening a `.kt` file to learn what is possible, stop and log it as a tooling gap (below).

A new character is an original model: draw its outlines and proportions yourself, never reshape another example's data. Use the other recipes for structure only. Template-free characters (the `blank` plan, `tube`, `blob` and `sheet` parts) are fine.

## Before the first recipe line

1. **List the reference's features in plain words**, one line each, and put the list in the recipe as `"x-features"` (keys starting `x-` are notes the build reads past). Cover at least: overall proportions (head to body, leg length, how tall against how long), the silhouette of each part (snout long and pointed or short and round, ear shape, tail shape), the nose point, every colour region on the face and body (a two-tone face, a muzzle patch, a chest blaze, a dark tail tip, socks), the eyes (shape, size, where they sit), mouth and marks, digits (toes, claws), and clothes.
2. Read `docs/work/example-limits.md` for what the engine cannot do yet.
3. Pick the plan: `canine-quadruped`, `biped`, `bird`, `fish` for a templated character, `blank` for a template-free one.
4. Look up what you will use in the reference, `docs/reference/` (generated from the code, so it is current):
   - `templates.md`: each template's rest, the length its outlines run to, which way Side's and Top's y face, what each view sets, its sockets (and which way each faces), joints with hinge, limits and which way positive degrees move them, paintable areas, patches, and every parameter with range, default and meaning.
   - `plans.md`: each plan's root, sockets, slots and palette.
   - `frames.md`: body space; which way u, v and each view's y run for a part resting forward, back, up, down or out; which way `degrees`, `lean`, `turn` and `twist` move a bone.
   - `part-parameters.md`: every dotted part parameter and the recipe field that writes it.
   - `face-and-clothes.md`: mouth group roles and controls; how garment fit, covers, redraws and swaps combine.

When the recipe is done, **go down the feature list and point at the recipe entry that makes each one**. A feature with no entry is not done.

## The loop

```
tools/recipe.sh recipes/<name>.recipe.json
```

From any folder; paths may be relative. It holds the machine-wide build lock (it waits if another build runs). It prints every file it writes:

- `build/recipes/<id>/<id>.json` — the character file; open it in the app with `./gradlew :app:run -Popen=<file>`.
- `build/recipes/<id>/renders/` — `<outfit>.png` (front, three-quarter, side in the display pose), `<outfit>-back-top.png` (back, and from above with the front up), `<outfit>-rest.png` when the display pose differs from rest, `<outfit>-pose-<name>.png` per named pose, `<outfit>-face.png`, `<outfit>-foot.png` (the first foot on a leg), `hand.png` (the first hand or paw on an arm), and `<outfit>-<part>.png` for each part id in `close=`. A character with no outfits is `as-saved`.
- `build/recipes/<id>/measure.txt` — the numbers (below).

About 12 to 20 s a run, most of it Gradle starting. A recipe error stops the run before anything renders and says where, as a JSON path:

```
recipe error at $.parts[3].socket: torso has no socket "nek"; it offers neck, tail, front-leg, back-leg
```

Warnings (`recipe warning at $.parts[2].params.toe: template paw has no parameter "toe"; it has ...`) do not stop the build; read them.

Options, as `key=value` after the recipe:

| Option | Does |
| --- | --- |
| `tools/recipe.sh watch <recipe> [options]` | build, then build again each time the recipe file is saved, in one warm process, printing the same file list each time (about 5 s a rebuild after the first); Ctrl-C stops. Use it for the change-one-thing loop |
| `render=false` | the character file only, a few seconds |
| `measure=false` | skip the measurements |
| `close=face,hand,foot` | close-ups (the default); also part ids (`close=foot-l,hand-l,head`), each framed close per outfit as `<outfit>-<id>.png`; a hand is whatever ends an arm (a paw on an arm too), a foot whatever ends a leg; `hands` and `feet` render the bare hand or paw at rest, curled, open (and spread) from six sides; `joins` the neck, shoulder and hip |
| `only=head,ear-l` | draw and frame just those parts in every view, for a quick look at one part; no close-ups, no measurements unless `measure=true` |
| `around=false` | leave out the back and top views |
| `outfits=a,b` or `all` | which outfits (default: the one the recipe shows) |
| `poses=a,b` or `all` | which named poses (default all) |
| `set=part:name:value,...` | try a parameter without editing the recipe |
| `out=<folder>` | where to write |
| `reference=<png> eye=x,y,z target=x,y,z fov=30` | also render through that camera at the image's size and compare (below) |
| `reference=<png> fit=true` | search the camera that best matches the reference's silhouette (round, up, distance, target, field of view; from `eye`, `target`, `fov` when given), print it as `fit: eye=... target=... fov=...`, then compare through it |
| `tools/recipe.sh crop <sheet.png> [out=<folder>] [ground=#rrggbb]` | split a sheet of several views on one background into `view-1.png`, `view-2.png` ... by the background's gaps, in reading order, each with a margin; use one as a `reference=` |

`tools/recipe.sh export <character.json> [out=<recipe.json>]` writes any character file as a recipe: start from an example this way. It prints `not carried:` for anything a recipe cannot hold.

Paths may hold spaces. A recipe error exits 2 with the recipe's own message and nothing from Gradle after it.

### Probe: answer frame questions without rendering

```
tools/recipe.sh probe recipes/<name>.recipe.json part=head uv=head:0.6,0.2 point=0.9,0.5,-0.1 face=head:0.1,0.1
```

About 10 s, no renders. It builds the recipe and prints, at rest, in body space (x forward, y up, z to the character's right):

| Question | Prints |
| --- | --- |
| `part=<id>[,<id>]`, or `part=all` (the default with no question) | origin and box; which way u runs and Side's +y faces; which way v 0, 0.25, 0.5, 0.75 face; for a head, the reach of its `face` places; each joint with hinge, limits and body point; each socket with u, v, body point and which way it faces |
| `uv=<part>:<u>,<v>[,<patch>]` | the body point there |
| `point=<x>,<y>,<z>` | the part, patch, u and v nearest that body point |
| `face=<head>:<x>,<y>` | the u and v at that place on the head's face |

Use it before placing a socket, region, mark or eye by u and v, before posing a bone, and to find where a feature sits on another part. A question that cannot be answered prints why, the rest are still answered, and the run exits 2.

Change one thing a run. Body first, rendered bare from three sides; fix proportions; then the face close-up; then hands and feet; then one garment at a time.

## Judging

Read every PNG the run printed, with the feature list beside you. For each feature say whether it shows. Then read `measure.txt`:

- `arm volume inside the torso` — should be 0 at rest and as shown.
- `smallest gap between digit tips` — toes or fingers fused if near 0.
- `profile depth brow to chin`, `deepest notch` — how much the face's profile stands out.
- `face in shade` per view — above about 0.3 the face reads dark.
- `terminator ... blotches` — shading blotches; aim for none.
- `one surface ... open edges` — should be 0.
- `owned part vertices under cloth`, `owned parts' drift from rigid` — for props a garment carries.
- `wardrobe:` lines — the slots, each garment with its slot, layer, fit, covered parts (u and v spans), changes, holes and owned parts, and each outfit's picks: check them against the clothes on the feature list.
- `poke-through <inner> through <outer>` per outfit — the body (`body`) or an inner garment drawn outside a garment over it: how many vertices, how far the deepest lies out in body units, and on which parts. `poke-through none` is the aim; raise the outer garment's `fit` (or that part's) past the deepest, or lower the inner one's.

### Matching a reference image

With `reference=<png> eye=x,y,z target=x,y,z fov=<deg>`, or `reference=<png> fit=true` to have the camera found for you (it prints the fitted camera, which later runs can pass as given), the run renders the character through that camera (reference frame: metres, +Y up, the character facing +Z with its left at +X, y = 0 on the soles; `scale=<body units per metre>` for a character drawn at another size) and writes `compare/pair.png` (reference left), `compare/overlay.png` (blue reference only, red Squash only, grey both) and the silhouette IoU. The reference's background is keyed by its top-left pixel, or `ground=#rrggbb`; crop a sheet of views to one view first (`tools/recipe.sh crop`). With a fitted camera the IoU is about the model's shape, not the camera: a low one is a proportion or silhouette to fix. Judge shape and dressing, not tone: light and shading are Squash's own.

## The recipe

The schema is `schema/recipe.schema.json`; put `"$schema": "../schema/recipe.schema.json"` at the top for checking in an editor. Every field has a description there. `recipes/fennec.recipe.json` (templated quadruped, fur, face marks) and `recipes/woman-dressed.recipe.json` (template-free biped, hand-made sockets and joints, outfits) are the worked examples.

To write the recipe as code instead, one TypeScript file anywhere on disk built with `tools/recipe.sh kit <file.ts>`, see `kit/README.md`.

```json
{
  "format": 1, "id": "fennec", "name": "Fennec", "plan": "canine-quadruped",
  "x-features": "tall ears with pink insides; cream muzzle and cheeks; dark tail tip",
  "palette": [{"slot": "fur", "color": "#e6b36e"}, {"slot": "cream", "color": [0.99, 0.94, 0.82]}],
  "parts": [
    {"id": "torso", "template": "torso", "role": "body", "color": "fur",
     "outlines": {"side": {"closed": true, "through": [[0, 0.03], [0.18, 0.22], [0.56, 0.22], [0.88, 0.36], [0.81, -0.05], [0.21, -0.14]]}}},
    {"id": "head", "template": "head", "on": "torso", "socket": "neck", "size": 1.12, "color": "fur",
     "regions": [{"id": "muzzle", "slot": "cream", "patch": "muzzle", "border": [[0.62, 0.31], [0.97, 0.24], [0.99, 0.5], [0.97, 0.76], [0.62, 0.69]]}]},
    {"id": "ears", "template": "ear", "on": "head", "socket": "ear", "pair": {"left": "ear-l", "right": "ear-r"}, "outward": 16,
     "outlines": {"top": {"through": [[0, 0.17], [0.42, 0.16], [0.72, 0], [0.42, -0.16], [0, -0.17]], "corners": [2]}}}
  ],
  "eyes": [{"id": "eyes", "on": "head", "u": 0.6, "v": 0.185, "look": {"white": "cream", "iris": "fur"}}],
  "displayPose": [],
  "poses": {"sit": [{"part": "back-leg-l", "joint": "hip", "degrees": -40}]}
}
```

### Parts

- One entry a part, **after the part it sits on**: `on` names it, `socket` the socket there. A recipe error lists the sockets a part offers.
- A mirrored pair is one entry with `pair` (`{}` names the sides `<id>-l`, `<id>-r`); it is described as its left side and the right mirrors it. A pair on a pair (paws on legs) names the parent pair's id in `on`, and each side sits on its own side. Anywhere else (poses, covers, expressions) a side is named by its own id, and the pair's id means its left side.
- `template` and optionally `version` (newest when left out). `docs/reference/templates.md` lists every template with its sockets, joints, areas and parameters.
- `role` is the template's, or guessed from where the part sits, when left out. Give it for a generic part (`tube`, `blob`) to be sure.
- Shape: `outlines` by view, `size`, `lean` and `outward` (A-pose arms take 45; each under 90, past which take another `rest`), `roll` (any angle about the part's own axis, its up turning toward its v 0.25 side: a sheet resting out and rolled 90 faces forward), `topMiddle: true` to let a Top drawn curved move the middle sideways (a band or lens wrapping a face), `params` by name, `blend` for how far the join with the parent reaches. In the kit, `orient(along, up)` gives the `rest`, `lean`, `outward` and `roll` that point a part along any direction with its up where wanted.
- Hand-made sockets: `sockets: [{"n": 2, "u": 0.83, "v": 0.75, "mirrored": true, "sink": 0}]`, named `socket.<n>`; `count`, `to`, `size` and `falloff` make a row (`socket.<n>.0` and on), as spines down a back. Which way v faces depends on how the part rests (`frames.md`: on a part resting forward v 0.25 is the character's left, on one resting up v 0.75 is), or ask the probe. `sink` 0 seats a child on the surface; once by default (it sinks by the child's depth); a shoulder wants it shallow (0.25).
- Hand-made joints: `joints: [{"n": 1, "u": 0.5, "hinge": "x", "low": -145, "high": 0}]`, posed as `joint.<n>`. Also `rest` (`forward`, `back`, `up`, `down`, `out`) in place of the template's, turned further by `lean`, `outward` and `roll`, `generic: {"section", "views", "rings", "segments"}` for a `tube`, `blob` or `sheet` (rings up to 96 and segments up to 32 make it fine), `landmarks`, `jointsAt` and `socketsAt` to move a template's joints and sockets, and `inkJoin: false` for an unlined join. `part-parameters.md` lists the dotted parameter each writes and the few left for `params`.

### Outlines

- `through`: a few `[x, y]` places a smooth curve passes through; `corners` lists the indices that are sharp (an ear tip, a tail tuft); `closed: true` for a part drawn all round (a torso, a head). Few points: 6 to 14 for a torso or head, 3 to 9 for a limb, ear or tail. `bezier` gives the cubic points themselves (3n + 1) where you need exact handles.
- x runs along the part's axis from its socket end, in body units, not 0 to 1: draw it out to the template's "outline x runs 0 to" length in `templates.md` (an ear 0.3, a leg 0.5) for the template's own length; the drawn length is the part's length (`size` scales it). y is across: Side sets height, Top sets width, Front (where the template's views say `section`) sets the cross-section for the whole part, drawn in -1 to 1.
- Side's y points up for a part resting forward or back, forward for a part resting up or down; `frames.md` and each template's entry say it for every rest. A loop need not be symmetric: front and back are set apart, so a belly, a seat or a bust is drawn in Side.
- Draw a toe or finger run as wide as the palm or foot: digits space themselves by the drawn width. End a templated part's outline at the template's length with a flat end (`foot-2` ends at 0.42).
- `paw` 6, `hand` 6, `foot` 5 and `foot-bird` 4 fit their whole solid to drawn Side and Top outlines; undrawn, their parameters alone shape them. Leave them undrawn unless the parameters cannot get the shape.

### Colour

- `palette`: `{"slot": "fur", "color": "#e6b36e"}` or `[r, g, b]` from 0 to 1. Every colour used anywhere is a slot named here.
- A part's `color` is its base slot; `areas` fill areas its template names (`nail:0`..`nail:4` in `nails`, a paw's `pad` and `claw` slots are filled by naming those slots in the palette).
- `regions`: an area in a slot, its border given one way: `border` as `[u, v]` on the part (`patch` for a template patch such as `muzzle`), **`face` as `[x, y]` on a head seen from straight ahead** (x to the character's left, negative for its right, y up, in the head's drawn units from its socket), or `anchors` across parts. A two-tone face is two or more regions on the head, each drawn in `face` coordinates: draw the light mask, then any darker patch over it, listed in the order they paint. Give regions that meet the same points along their shared border so they meet with no seam, borders are smooth curves through the points (`corners` lists the sharp ones); set `curved: false` for a border straight between points.
- Marks: `marks` with `points`, `face` or `anchors`; `width` stops; `ink: false` for a filled shape; `slot` for a coloured line (a brow); `kind: "fur"` for a fur root. `fur.patches` lays rows of fur roots: `{"name": "chest", "rows": 14, "columns": 36, "u": [0.66, 0.97], "v": [0.2, 0.8]}`, with `fur.spacing` and `fur.length` in pixels.

### Tails and toes

- A long, thin tail that curves (a mouse's, a monkey's, a cat's) is `tail-curve`: name it as the tail's `template`, with `thickness` and `taper` (0.1 to 0.15 for a strong taper). Its resting curve runs through three places (`place1Along` .. `place3Up`), or through the part's own `curve`: any number of `[along, up]` places (2 to 24, `[along, up, side]` to swing sideways) in the part's own axes as fractions of its length, so an S with a hooked end is one tail: `"curve": [[0.22, -0.1], [0.45, 0.05], [0.62, 0.22], [0.78, 0.3], [0.86, 0.42], [0.8, 0.5], [0.72, 0.47]]`. The curve is scaled to the part's length (`size`), and up is its Side's +y (`frames.md` says which way that faces and which sign of `lean` lowers a tail resting back and up). `tail` stays the default for short or thick tails.
- Before sizing a tail, leg or neck, read `proportions.md`: each plan's parts built undrawn at `size` 1, with their boxes and heights above the ground.
- Toes that sit close together: `spread` below 1 on `foot` and `paw` (and `foot-bird`), which brings both their roots and their fan closer.

### Face

- A `face` place must lie on the head as seen from ahead: `tools/recipe.sh probe <recipe> part=<head>` prints the reach of its x and y, which narrows toward the chin and the crown, so keep a mask's corners inside it.
- Eyes are painted: `eyes: [{"id", "on": "<head>", "face": [x, y]}` or `"u"`, `"v"`, with `look` (opening `width`, `height`, `lowerLid`, `frontNarrowing`, `tilt`, `openingTilt`; `irisSize`, `irisRatio`, `irisForward`, `irisUp`; `pupilSize`, `slit`; `highlightSize`; `lid`, `lashes` (a count, 0 to 3), `brow`, `browRaise`, `browTilt`, `browWidth` (1 a line, 3 a thick brow); and the slots `white`, `iris`, `pupil`, `highlight`, `ink`) and optionally a `front` look for front views. `face-and-clothes.md`, Eye look, gives every field's range and meaning and, per head template, how long one unit of a look's width and height is in body units: on an undrawn `head-round` a unit across is about 2.2 times a unit down, so a round iris takes `irisRatio` about 2.2 there. Mirrored across the head unless `mirrored: false`. `flat: true` lays an eye flat across the face instead of following the head's curve: right for a large eye on a small, round, front-facing head, wrong for an eye on the side of a long head, where it stretches.
- A mouth is a mark group on the head with five roles in draw order (`inner`, `tongue`, `teeth`, `lipLine`, `corners`), each naming marks on the head and optionally a `fill` slot and `ink: false`; `face-and-clothes.md` says what each holds and what `controls` do, and the fennec's recipe has one. `controls.open` (1 when left out, so give 0 for a closed mouth) opens it on any head, jaw or none: the lips part on the surface, the `inner` role's `fill` shows between them, tongue marks ride the lower lip and teeth marks (a fang is three places) hang where drawn, both cut at the lips. An expression opens it by group id, `{"id": "open-mouth", "name": "Open mouth", "open": {"mouth": 1}}`, and each expression renders as `<outfit>-face-<expression>.png`; `set=head:mouth.open:1` tries it without editing the recipe, and an unknown `set=` key prints a warning naming what the part has.
- `head-round` version 2 stands upright, its axis from chin to crown; put the neck's end socket at `sink: 0` and draw the head from the neck's top. Side is the profile, forward up in the drawing: up the front from the chin over the lips, nose and brow to the crown, down the back and forward under the jaw. **The nose point is the profile's most forward place**; a bump shorter than about 0.3 of the head's height (nose, lips, chin) stands out as a ridge, a longer one (a muzzle) swells the whole front. Top is the face's width up from the chin; Front the section seen from above.
- Lips, blush and shadow are regions; ink strands on hair are marks. A head's hand-made sockets read v 0 at the face, 0.5 at the back, 0.75 at the left.
- `expressions`: `{"id", "name", "joints": [{"part": "ear-l", "joint": "base", "degrees": 32}], "open": {"<mouth group>": 1}}`.
- A cupped ear is `ear` with `cup` 0.4 to 0.6 and its `inner` area filled; `roundTip: 1` with `taper` 0.5 to 0.7 and `tipReach` about `width` times `taper` (0.2 to 0.3) rounds its tip into a semicircle and `rim` 0.1 to 0.2 gives its edge a rounded thickness. Judge an ear in the three-quarter view as well as front and side: its rim and ink line should run as one smooth curve.

### Poses

- A biped is built in A-pose: arms 45 degrees out (`outward`: 45), legs straight down. `displayPose` brings the arms down: a `lean` on the arm's root bone (`base` on a tube, `shoulder` on the `arm` template), leaving the arm 15 to 28 degrees out from the hips, a free twist at the elbow for the palms, the fingers a little curled (`fingers:<i>.<k>` negative, the thumb `fingers:4.<k>` positive).
- A bone is `{"part", "joint", "degrees", "turn", "lean", "twist", "stretch", "mode": "free"|"limited"|"locked"}`. `frames.md` gives which way each angle moves a bone for every rest and hinge: on a part resting down, hinge x, positive degrees swing it back and `lean` out to its side (past limits with `mode: free`). Bend an elbow by `degrees` and aim it with `lean`; put a palm's roll as `twist` on the elbow, never on the hand's root. `turn` on an upright torso's `base` turns the whole body; on a spine joint the chest against the hips.
- `poses` are named poses the run renders; they are not saved in the character.

### Clothes

- `wardrobe.slots` (`{"id": "top", "name": "Top"}`), `garments`, `outfits` (`{"id", "name", "picks": {"<slot>": "<garment>"}}`), `shown`.
- A garment: `slot`, `layer` (under a waistband 1, a waistband 2, over both 3), `color`, `fit` (how far it stands off, about 0.015) with `partFits` per part, its cloth as `areas` (or `covers`, the simple form), `holes`, `tucks`, `changes` by part, `looseEnds`.
- Cloth is cells of each part's chart, 64 rows along it (u) by 32 columns round it (v), painted as Dress paints them. `areas` are named, in order, each with its own `color` (left out: the garment's) and `paint` strokes: `{"on": "<part>", "shape": "band", "u": [from, to]}` (a ring), `box` (`u`, and `v` round, from below 0 to cross v 0), `ellipse` (inside that box), `lasso` (`points` `[u, v]`, or `face` on a head), `brush` and `line` (`points` or `face`, `size` in body units, reaching the parts beyond theirs). `unpaint: true` takes cells out of every area: a curved hem or a side split is a band, then an `unpaint` ellipse over the hem. `mirrored` paints a middle part's other side; `clean` smooths the edge. A later area's paint takes cells from the earlier ones; where two areas meet the cloth is one piece and the seam is inked.
- One garment, several colours: a tee is one garment whose `body` area bands the torso and `arm-l` (a pair by its left side; both sides take it) and whose `rib` area, `"color": "rib"`, bands the collar and the sleeves' ends. Paint the sleeve from u 0 so it runs on from the shoulder. Shorts are a `body` area over the torso's bottom and the legs' tops and a `waistband` area in a second colour.
- With areas, the parts covered, the parts that come out (a part on a socket inside the painted region that is not painted, as a tail or a neck), the tucked small parts and the inked seams are written from the cells; `joins` and a change on such a part are not used. `holes: [{"part": "tail", "cuff": false}]` lets a part and everything beyond it out at its root, `cuff: true` rings it with a band. `tucks: [{"part": "tail", "kind": "tail", "tucked": false}]` shapes cloth over a small part instead of tucking it.
- Paint under two cells wide drops out of the cloth: make a band at least 2/64 of its part long. Which way v faces depends on how the part rests: `tools/recipe.sh probe <recipe> part=<id>` prints where v 0, 0.25, 0.5 and 0.75 face.
- A `cover` (`{"part", "from", "to", "fromV", "toV", "outlines"}`) is a box of cells painted into the first area: `to` past 1 runs on past the part's end, `from` below 0 before its start; a v span under 1 leaves an opening. Beside areas, a cover on a painted part only carries its `outlines` and `ease`. `tools/recipe.sh export` writes a Dress-painted garment as its areas, each a run of cells a `box`, with its holes and Tuck choices.
- How `partFits`, `fit` and a cover's `ease` combine, and what a redraw replaces, is in `face-and-clothes.md`.
- Changes: `{"type": "through", "cuff": false}` for an armhole or neckline, `{"type": "through"}` for a cuff, `{"type": "hide"}`, `{"type": "redraw", "shape": {...}}` (outlines, params, colour or regions laid over the part: fewer toes, a dark lip), `{"type": "swap", "template": "blob", "shape": {...}}` (a closed shoe).
- A skirt is a cover over the torso with `from` below -0.1, drawn in Side and Top along the torso's own x, negative below it; the legs hang inside it. Do not redraw the torso into a bell.
- Prints on cloth (a chest symbol, a stripe, a pocket, stitching) are the garment's own `regions` and `marks`, written as a part's are but with `"on": "<part>"` naming the body part under the cloth, in its u and v: `{"id": "drop", "on": "torso", "slot": "red", "ink": true, "border": [[u, v], ...], "corners": [4]}`, `{"id": "stitch", "on": "torso", "slot": "thread", "width": [{"at": 0, "scale": 0.4}, {"at": 1, "scale": 0.4}], "points": [[u, v], ...]}`. A region with `ink` is a filled shape with an inked border; `mirrored` shows a print on the other side of the body too (across a middle part, or on a pair's other side). A print shows only on painted cloth and ends where the cloth does; never fake one as an owned part sitting on the cloth. The kit's `printOn("torso", uvRegion(...))` adds `on`.
- Rigid things a garment carries (shades, heels, buttons, a buckle) are parts with `"garment": "<id>"`, on a hand-made socket of the body part they sit on (`sink: 0` to seat them on the surface; on cloth add `outset`, in body units, a little past the garment's `fit`, so they stand on it). A row round a part (`count` with `v: 0`, `toV: 1`) sets studs round a belt. A lens in a palette slot with `opacity` (0.3 to 0.5) shows the eyes through it. Two garments' parts never reuse a socket number on one part.

## Tooling gaps: keep a log

Every time you still have to read code, or guess and render to find out, add a line to `docs/work/recipe-gaps.md` under a heading for the character: the file or the trial, the fact you needed, and what tool or reference would have told you. Reading `docs/reference/`, `templates/*.json`, `schema/*.json`, `docs/hld/` and other recipes is not a gap; a fact the reference should hold and does not is. Report the log's new lines when you finish.

## Examples and goldens

- The shipped examples (`examples/*.json`) are still built from Kotlin fixtures under `screens-core/src/testFixtures/.../screens/<name>/` with golden tests (`./gradlew :screens-core:test -PupdateGoldens`); leave those as they are unless asked. `recipes/fennec.recipe.json` and `recipes/woman-dressed.recipe.json` build those two examples' characters, render for render.
- A new example ships as `recipes/<name>.recipe.json`; the recipe module's tests build every recipe there.

## Gotchas

- A leg swings about its own start, so start it at the hip joint, inside the pelvis (a socket sunk into the torso's side), not where it leaves the pelvis's underside.
- A garment never moves a socket; do not turn limbs per outfit to clear a garment.
- A garment shared between outfits reads u on the part as other garments leave it; a redraw that changes a part's length moves every other garment's range.
- `paw` 5: `toes` 3 to 5, `merge` 0.15 to 0.3 for toes standing clear, about 1 for a mitten; `claw` length, `clawCurl`, `claws` 0 retracts; `pads`, `beans`. `foot` 4 and 5: size 1 is metres; a person's foot is `size` alone (1.18 gives 0.28 m heel to toe).
- A shoe over `foot` 3 is a redraw setting `toes` 0 and `toeBox` 1 plus a cover to u 1. A closed shoe over a later foot is a swap to a `blob`.

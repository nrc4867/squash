# Feet and paws

Build the foot the character means, as a part that stands and poses well. The sheet says what the feet are like: their length against the head, round or long, a smooth sock or toes, claws, pads. The template says how a foot is built so it stands flat and flexes: bones, toes, sole and pad areas. Neither is the answer by itself: a trace of a small, foreshortened or sock-covered foot is a blob, and the template at its defaults is nobody's foot. `templates.md` lists each template's parameters and areas; `docs/hld/parts.md`, Paws and Feet, how they are built.

| template | use for | toes |
| --- | --- | --- |
| `foot` (version 11) | a plantigrade foot: a person's, a mouse's, a bear's, an anthro's, a sock or a shoe-like toon foot; a heel set back behind the ankle, a sole, toes flat underneath | `toes` 0 to 5; `toeLength`, `toeThickness`, `spread`, `merge`, `claw`, `clawCurl`, `pads`, `beans`, `clawParts` |
| `paw` (version 13) | a digitigrade paw: a fox's, a cat's, a dragon's; a wrist rising into the leg, a heel ball and pad, toes in a fan, claws, pads and beans | `toes` 0 to 5; `spread`, `merge`, `claws`, `claw`, `clawCurl`, `pads`, `beans`, `clawParts` |
| `foot-bird` (version 6) | a bird's foot | four toes; `claw`, `clawCurl` |

Both rest forward and stand on the floor rule: whatever ends a leg that bears weight is what the character stands on.

## 1. Measure the proportions first

- **Measure**: `tools/recipe.sh kit sheet measure <sheet> views=front,side,back` reads `footWidth` per foot from the front and back views (touching feet are split at the gap or notch between them) and `footLength` from a side view, checks them across views and prints a table. A reading marked unsure says why: a width more than 0.6 of the length, two views that disagree, a three-quarter view's foreshortening. Socks and shoes read as the foot. Declare the readings you trust as targets (`targets: targets({ footLength: "0.45 head", footWidth: "0.3 head" })`); `measure.txt` then flags any build that misses them.
- **Zoom** each view's feet (`kit sheet zoom ... region=feet`, or `box=x,y,w,h scale=4`) and write down the intent: long or short for the head, round or narrow, what the toe end is (one smooth cap for a sock, a toon shoe or a mitten paw; separate toes, how many), claws, pads. Where the readings are unsure, measure heel to toe and the width on the zooms yourself in head heights and choose one proportion; write it as a target with the readings it came from.

## 2. Choose the construction

Pick what gives a clean foot that stands flat and carries the intent:

- **Drawn outlines** when the sheet shows the foot large and clear in a side view, or when the foot is a feature (a toon shoe, a big round sock foot): trace from a zoomed crop (below) and keep the sole flat and the heel under the ankle.
- **The template with parameters** when the sheet's foot is small, foreshortened or hidden under a trouser cuff: undrawn, `size` for the length (build once, read `footLength`, scale), `heel`, `ballWidth` and the toe parameters for the rest; or draw Side and Top yourself from the chosen length and width with a few places, as below.

## 3. Tracing, where the sheet shows the foot large

```
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=side part=foot-left as=side
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front part=foot-right as=front
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front box=x,y,w,h part=foot-left as=top
```

Each prints an outline in the foot's own frame to paste into the part's `outlines`, its length, width and height, and which sheet side it traced (the recipe's left part takes it; a right foot is mirrored). Look at `trace-<part>-<as>.png` before pasting: the ankle is the blue cross, and anything of the leg or trouser above it must be cut away (`ankle=x,y` moves it). Where the sheet has no true side view (a three-quarter only) or no view from above, draw that outline from the measured length and width with a few places, keeping the traced view's shape: the heel round behind the ankle, the sole flat at the ankle's height below the socket, the instep falling to the toe end; from above, narrower at the heel than across the ball.

- **Side** (`side`): x forward from the ankle (heel negative), y up from the ankle (the sole at minus the plan's `ankle` height).
- **Top** (`top`): x forward, y toward the character's right; the foot's inner edge is +y on the left foot.
- **Front** (`front`): the section in -1 to 1, x to the character's left, y up; it sets the foot's whole section along its length.

## 4. Draw

```ts
import { pair, type RecipePart } from "squash-kit";

export const feet: RecipePart = {
  id: "feet",
  template: "foot",
  on: "legs",
  socket: "socket.1",
  ...pair("foot"),
  role: "foot",
  size: 1,
  inkJoin: false,
  color: "skin",
  params: { toes: 0 },
  outlines: {
    side: { through: [[-0.05, -0.01], [-0.04, 0.012], [-0.012, 0.024], [0.04, 0.012], [0.1, -0.002], [0.15, -0.012], [0.17, -0.026], [0.16, -0.04], [0.1, -0.042], [0, -0.042], [-0.04, -0.036]], closed: true },
    top: { through: [[-0.05, 0], [-0.035, 0.04], [0.03, 0.05], [0.11, 0.06], [0.155, 0.05], [0.17, 0], [0.155, -0.055], [0.11, -0.068], [0.03, -0.052], [-0.035, -0.04]], closed: true },
  },
};
```

- **Size 1.** Outlines are drawing units times `size`; at `size` 1 they are body units, as the trace prints them.
- **The drawing is the solid.** Side and Top are its silhouettes exactly and Front its section (a sole's flat-bottomed one where Front is not drawn), so the foot is as wide from the front as drawn and never narrower than the ankle unless drawn so.
- **The toe end**: `toes: 0` makes it one smooth cap, a sock's or a toon shoe's. With toes, the drawing is cut back at the toes' roots and the toes stand from it; draw the outline round the toes' tips, then set `toeLength`, `toeThickness`, `spread` and `merge` for how they part (`merge` about 1 for lobes joined to the tips). One toe's own shape: `"toes.0.length": 1.1`, `"toes.4.width": 0.8`, `"toes.0.spread": -5` (degrees further out in the fan), counted from the inner toe.
- **The ankle** is the leg's own end: the foot reads the leg's end radius (`limb`) and runs the leg straight into it, so never set it, and never flare or narrow the leg's last stations to meet the foot. The leg ends at the plan's `ankle` height with a socket at u 1 facing down and back (`socket(1, 1, vToward("down", "back"))`); `probe part=foot-l` prints its box, its lowest y the ground.
- **Undrawn**, the parameters shape the foot (`heel`, `ballWidth`, `toeBox` for a shoe): the choice where the sheet's foot is small, foreshortened or hidden, sized from the measured length.

- **Toe-out**: `toeOut` on the foot part (degrees, mirrored on the right, 0 by default; a person's stands 5 to 12) turns the foot about the leg's line at the ankle with the sole flat, leaving the leg and knee facing forward. It holds for paws and bird feet too. Put it on the part, not in `outward`, which leans the part away from the body's plane and is the arm's and leg's own setting.
- A round cartoon foot with no toes: `foot` with `toes` 0, a wide `ballWidth` and a full `heel`. A foot with toes: `toes` 1 to 5, each count laid out across the ball of its own (5 a person's, fewer symmetric and wider); `merge` low for toes standing apart, about 1 to join them to the tips; `spread` below 1 brings them closer.
- An anthro's human-like foot with three wide, short toes, pads and claws: `foot` with `toes: 3, toeLength: 0.75, toeThickness: 1.25, merge: 0.15, claw: 0.5, pads: 1, beans: 0.25`, and a palette slot `pad`. Its claws are its nails, in the slot `nails`, or fill `areas: { "nail:0": "claw", ... }`.
- A hoof: `hoof` on the leg's paw socket, best from a preset (`preset: "horse"`, `"cattle"`, `"goat"` for a goat or sheep, `"deer"`, `"pig"`), then `params` to adjust: `toes: 1` a horse's, `toes: 2` a cloven hoof (a cow, a goat, a deer) with `cleft` for the gap, `flare` for how much wider it is at the sole, `height` for the wall, `width` and `point` for each toe's breadth and point, `splay` for how far the toes part, `dewclaws: 1` for two small dewclaws high behind (`dewLow`, `dewLarge` for a deer's and a pig's), `heels` and `frog` for a horse's. The whole part is keratin in the `claw` slot (fill `hoof`) with its `sole` in `pad`; draw the leg's end thin as the pastern, and the inked join is the coronet.
- A paw: `toes` 1 to 5 (2 a cloven foot, 3 or 4 a cat's or a dog's), `merge` 0.15 to 0.3 for toes standing clear (a fox's), about 1 for a mitten (a house cat's); `claw` for how far claws stand past the toes, `clawCurl`, `claws: 0` to retract; `pads` for the main pad and beans, `beans` to raise them. Pads and claws show in the palette slots `pad` and `claw` when the palette names them.
- Colour: `areas` fill `sole`, `toes` on a foot, `pad`, `sock`, `top`, `toes` on a paw. A paw's `sock` is the whole paw, toes too, under its pads, beans and claws; white socks are `areas: { sock: "white" }` on the paw and on the leg, whose `sock` is its lowest quarter.
- A quadruped's every leg carries a paw: a leg with nothing on its end socket ends in a flat stump.

## 5. Check it as a 3D part, then judge side by side

Before the look: the sole flat on the ground in every pose, the heel under the ankle, the foot no narrower than the ankle from the front, the toes apart (`smallest gap between digit tips` above 0), and the foot's depth sane: no blob swelling above the instep.

Render `close=feet` (the bare foot at rest, curled, open and spread from six sides: front, three-quarter, side, back, below) and put each beside the sheet's crop of the same view at the same scale. Check, in this order:

1. From the front, the foot's width against the ankle and the leg: no bottle neck, no waist above the foot, no collar where they meet.
2. From the side, the heel behind the ankle, the sole flat on the ground, the toe end's height and roundness.
3. From below, the sole's outline is the drawn Top: a heel, an arch, the ball.
4. The toe end's outline is smooth: no ragged edge and no specks. A nail or pad area shows only when the palette names its slot (`nails`, `claw`, `pad`).
5. `footLength` and `footWidth` in `measure.txt` against the targets.

Judge by eye in the rest, display and T-pose renders, bare and dressed: whether it reads as the sheet's foot. Wrong size: `size` or the outline's length. Wrong silhouette on a drawn foot: the outline. Wrong toes: the toe parameters.

## Paws

A paw takes the same steps: measure, then trace its Side and Top where the sheet shows the paw large and clear (front and side views of the hind feet), paste, `size: 1`, or use the template sized from the measured length. Its toes are its own: `toes` 1 to 5 (2 a cloven foot, 3 or 4 a cat's or a dog's), `merge` 0.15 to 0.3 for toes standing clear, about 1 for a mitten, `claw` and `clawCurl` for claws past the tips, `claws: 0` to retract, `pads` and `beans` for the main pad and the beans, in the slots `pad` and `claw` when the palette names them. A quadruped's every leg carries a paw: a leg with nothing on its end socket ends in a flat stump. The same holds for creature feet: a drawn outline round the toes, toes from the parameters.

## Claws: grown or hard

Two kinds, the same on a foot, a paw and a bird foot:

- **Grown claws** (`claw`, `clawCurl`; `claws: 0` hides a paw's) are points grown out of the toe's own surface and painted: one silhouette with the toe, no ink between them, nothing to pose. Enough for a small or soft claw that is part of the toe's shape, a dragon's or a fennec's, and the default.
- **Hard claws** (`clawParts: 1`) are a claw on every toe as its own mesh: its own contour ink, the `claw` slot, a collar where it leaves the toe (area `sheath`, the part's own colour until filled), hooked to a sharp point, and posable: **Retract** slides them back into the toes (0 out to 1 in), the Pose stage's control under Curl, or `clawRetract` in a recipe pose. Use them where the claws are a feature (white claws on a coloured paw, a cat's claws out or sheathed, talons) or where a pose must sheathe them. The grown claws and nails are left out while they are on. Shape them to the character: `clawLength` (a share of the toe's length), `clawThickness` (against the tip's thickness, so the same share reads alike on any size; 0.75 to 1 for chunky toon claws), `clawHook`, `clawSheath`. The template's beans (`pads`, `beans`) are painted on the toes' own surface: where the toes are merged into a drawn foot that surface is a sliver underneath, so turn `pads` off and paint the beans as round `uvRegion` ovals on the `body` patch just behind the toes, sized by the probe's lengths (`uv=foot-l:...` at two places a step apart in u and in v). A retracted claw hides inside its toe only while the toe has room for it: render the claws in and shorten them where a point still shows; a long hooked claw on a foot that stands flat reaches the floor.

```ts
params: { toes: 3, clawParts: 1, clawLength: 0.6, clawThickness: 0.45, clawHook: 0.6, clawSheath: 0.3 },
```

Render `close=feet,foot-l` and poses of the display pose with the claws at 0.5 and 1 (`clawRetract(part, { count: 3, amount })`; `named: ["back"]` for a bird's hind toe): out, each claw clears the toe from the front and the side; in, none shows.

## On one toe

Sockets, regions and marks name a toe's patch, `toes:0` on from the inner toe (`templates.md` lists them), to lie on that toe and ride it: `socket(1, 0.9, 0.25, { patch: digitPatch(0, "toes") })` on the feet. A patch the part does not build warns at build, as does a garment's print on a toe.

**Webbed toes**: `webbing: 1` with `webReach` and `webEdge` (`ears-tails-extras.md`, Webs and membranes).

## Limits

- **Cloth stops at the toes' roots**: a sock or shoe covers the foot and the toes poke through or stay bare, and prints on a toe show nowhere. A closed shoe or a sock is `toes: 0` with its outline drawn.
- **Grown claws are field points.** They are grown in the toe's own surface and painted, and cannot pose or carry sockets; hard claws (`clawParts`) are their own meshes and retract, but carry no sockets either.
- **No toes on paddles.** A paddle foot or flipper is a drawn `blob` or `sheet` with no digits.

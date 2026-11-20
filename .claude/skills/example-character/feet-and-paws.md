# Feet and paws

The sheet is the truth. A foot or paw is drawn from it: trace its silhouettes, paste them as the part's outlines, build, and judge it side by side with the sheet's crops. The template supplies the bones, the toes, the sole and pad areas and sensible defaults; its parameters shape the toes and areas, never the silhouette. `templates.md` lists each template's parameters and areas; `docs/hld/parts.md`, Paws and Feet, how they are built.

| template | use for | toes |
| --- | --- | --- |
| `foot` (version 10) | a plantigrade foot: a person's, a mouse's, a bear's, an anthro's, a sock or a shoe-like toon foot; a heel set back behind the ankle, a sole, toes flat underneath | `toes` 0 to 5; `toeLength`, `toeThickness`, `spread`, `merge`, `claw`, `clawCurl`, `pads`, `beans` |
| `paw` (version 12) | a digitigrade paw: a fox's, a cat's, a dragon's; a wrist rising into the leg, a heel ball and pad, toes in a fan, claws, pads and beans | `toes` 0 to 5; `spread`, `merge`, `claws`, `claw`, `clawCurl`, `pads`, `beans` |
| `foot-bird` (version 4) | a bird's foot | four toes |

Both rest forward and stand on the floor rule: whatever ends a leg that bears weight is what the character stands on.

## 1. Read the sheet

- **Measure**: `tools/recipe.sh kit sheet measure <sheet> views=front,side,back` reads `footWidth` per foot from the front and back views (touching feet are split at the gap or notch between them) and `footLength` from a side view, checks them across views and prints a table. A reading marked unsure says why: a width more than 0.6 of the length, two views that disagree, a three-quarter view's foreshortening. Socks and shoes read as the foot. Declare the readings you trust as targets (`targets: targets({ footLength: "0.45 head", footWidth: "0.3 head" })`); `measure.txt` then flags any build that misses them.
- **Zoom** each view's feet (`kit sheet zoom ... region=feet`) and write down what the toe end is: one smooth cap (a sock, a toon shoe, a mitten paw), separate toes, how many, claws, pads.

## 2. Trace

```
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=side part=foot-left as=side
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front part=foot-right as=front
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front box=x,y,w,h part=foot-left as=top
```

Each prints an outline in the foot's own frame to paste into the part's `outlines`, its length, width and height, and which sheet side it traced (the recipe's left part takes it; a right foot is mirrored). Look at `trace-<part>-<as>.png` before pasting: the ankle is the blue cross, and anything of the leg or trouser above it must be cut away (`ankle=x,y` moves it). Where the sheet has no true side view (a three-quarter only) or no view from above, draw that outline from the measured length and width with a few places, keeping the traced view's shape: the heel round behind the ankle, the sole flat at the ankle's height below the socket, the instep falling to the toe end; from above, narrower at the heel than across the ball.

- **Side** (`side`): x forward from the ankle (heel negative), y up from the ankle (the sole at minus the plan's `ankle` height).
- **Top** (`top`): x forward, y toward the character's right; the foot's inner edge is +y on the left foot.
- **Front** (`front`): the section in -1 to 1, x to the character's left, y up; it sets the foot's whole section along its length.

## 3. Draw

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
- **Undrawn**, the parameters shape a foot as before (`heel`, `ballWidth`, `toeBox` for a shoe), good for a quick stand-in; a character made from a sheet draws its feet.

- A round cartoon foot with no toes: `foot` with `toes` 0, a wide `ballWidth` and a full `heel`. A foot with toes: `toes` 1 to 5, each count laid out across the ball of its own (5 a person's, fewer symmetric and wider); `merge` low for toes standing apart, about 1 to join them to the tips; `spread` below 1 brings them closer.
- An anthro's human-like foot with three wide, short toes, pads and claws: `foot` with `toes: 3, toeLength: 0.75, toeThickness: 1.25, merge: 0.15, claw: 0.5, pads: 1, beans: 0.25`, and a palette slot `pad`. Its claws are its nails, in the slot `nails`, or fill `areas: { "nail:0": "claw", ... }`.
- A hoof: `hoof` on the leg's paw socket, best from a preset (`preset: "horse"`, `"cattle"`, `"goat"` for a goat or sheep, `"deer"`, `"pig"`), then `params` to adjust: `toes: 1` a horse's, `toes: 2` a cloven hoof (a cow, a goat, a deer) with `cleft` for the gap, `flare` for how much wider it is at the sole, `height` for the wall, `width` and `point` for each toe's breadth and point, `splay` for how far the toes part, `dewclaws: 1` for two small dewclaws high behind (`dewLow`, `dewLarge` for a deer's and a pig's), `heels` and `frog` for a horse's. The whole part is keratin in the `claw` slot (fill `hoof`) with its `sole` in `pad`; draw the leg's end thin as the pastern, and the inked join is the coronet.
- A paw: `toes` 1 to 5 (2 a cloven foot, 3 or 4 a cat's or a dog's), `merge` 0.15 to 0.3 for toes standing clear (a fox's), about 1 for a mitten (a house cat's); `claw` for how far claws stand past the toes, `clawCurl`, `claws: 0` to retract; `pads` for the main pad and beans, `beans` to raise them. Pads and claws show in the palette slots `pad` and `claw` when the palette names them.
- Colour: `areas` fill `sole`, `toes` on a foot, `pad`, `sock`, `top`, `toes` on a paw. A paw's `sock` is the whole paw, toes too, under its pads, beans and claws; white socks are `areas: { sock: "white" }` on the paw and on the leg, whose `sock` is its lowest quarter.
- A quadruped's every leg carries a paw: a leg with nothing on its end socket ends in a flat stump.

## 4. Judge side by side

Render `close=feet` (the bare foot at rest, curled, open and spread from six sides: front, three-quarter, side, back, below) and put each beside the sheet's crop of the same view at the same scale. Check, in this order:

1. From the front, the foot's width against the ankle and the leg: no bottle neck, no waist above the foot, no collar where they meet.
2. From the side, the heel behind the ankle, the sole flat on the ground, the toe end's height and roundness.
3. From below, the sole's outline is the drawn Top: a heel, an arch, the ball.
4. The toe end's outline is smooth: no ragged edge and no specks. A nail or pad area shows only when the palette names its slot (`nails`, `claw`, `pad`).
5. `footLength` and `footWidth` in `measure.txt` against the targets.

Wrong silhouette: fix the outline, not a parameter. Wrong toes: fix the toe parameters.

## Paws

A paw takes the same steps: trace its Side and Top (front and side views of the hind feet), paste, `size: 1`. Its toes are its own: `toes` 1 to 5 (2 a cloven foot, 3 or 4 a cat's or a dog's), `merge` 0.15 to 0.3 for toes standing clear, about 1 for a mitten, `claw` and `clawCurl` for claws past the tips, `claws: 0` to retract, `pads` and `beans` for the main pad and the beans, in the slots `pad` and `claw` when the palette names them. A quadruped's every leg carries a paw: a leg with nothing on its end socket ends in a flat stump. The same holds for creature feet: a drawn outline round the toes, toes from the parameters.

## On one toe

Sockets, regions and marks name a toe's patch, `toes:0` on from the inner toe (`templates.md` lists them), to lie on that toe and ride it: `socket(1, 0.9, 0.25, { patch: digitPatch(0, "toes") })` on the feet. A patch the part does not build warns at build, as does a garment's print on a toe.

**Webbed toes**: `webbing: 1` with `webReach` and `webEdge` (`ears-tails-extras.md`, Webs and membranes).

## Limits

- **Cloth stops at the toes' roots**: a sock or shoe covers the foot and the toes poke through or stay bare, and prints on a toe show nowhere. A closed shoe or a sock is `toes: 0` with its outline drawn.
- **Claws are field points.** A paw's claws are grown in its own surface and painted; they cannot be separate hard parts or carry sockets.
- **No toes on paddles.** A paddle foot or flipper is a drawn `blob` or `sheet` with no digits.

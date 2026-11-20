# Hands

The sheet is the truth. A hand is the `hand` template (version 9, the newest) on the arm's end socket, drawn from the sheet: trace the back of the hand and its edge, paste them as outlines, build, and judge it beside the sheet's crops. The template supplies the bones, the digits, the nail areas and sensible defaults; its parameters shape the digits, never the hand's silhouette. `templates.md`, hand, lists its parameters, its digit joints and its `nail:<i>` areas; `docs/hld/parts.md` says how it is built. A front paw on an arm is a `paw` instead (`feet-and-paws.md`).

## 1. Read the sheet

- **Measure**: `tools/recipe.sh kit sheet measure <sheet> views=front,side,back` reads `handLength` (wrist to fingertip) and `handWidth` per hand, splits a hand touching the body or the other hand, checks the views against each other, and marks a width more than 0.8 of the length unsure: a spread hand reads as wide as its spread fingers. Declare the readings you trust as targets (`targets: targets({ handLength: "0.38 head", handWidth: "0.2 head" })`).
- **Zoom** the hands (`kit sheet zoom ... region=hands`) and write down: how many digits, each finger's length against the middle one, how thick, how far they spread, the thumb's place.

## 2. Trace

```
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=side part=hand-right as=side
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front part=hand-left as=top
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front box=x,y,w,h part=hand-right as=side wrist=x,y tip=x,y
```

- **Side** (`side`): the back of the hand as the character's side view shows it, x down from the wrist to the fingertips, y forward (the thumb's side). A posed hand, a wave with the palm to the viewer, is traced from a `box=` with `wrist=` and `tip=` along its middle finger.
- **Top** (`top`): the hand edge-on, as the front view shows a hand hanging with its palm in: x down, y toward the character's right; it sets the hand's thickness.

A spread hand traced whole carries its spread in the outline. For a hand at rest, keep the traced palm and draw the fingers' end closed round their tips at the length the sheet shows, then give the spread with the parameters below.

## 3. Draw

```ts
import { pair, type RecipePart } from "squash-kit";

export const hands: RecipePart = {
  id: "hands",
  template: "hand",
  on: "arms",
  socket: "socket.1",
  ...pair("hand"),
  role: "hand",
  size: 1,
  outward: 45,
  inkJoin: false,
  color: "skin",
  params: { digits: 4, thumb: 1, "fingers.1.length": 1.08, "fingers.2.length": 0.9 },
  outlines: {
    side: { through: [[0, -0.022], [0.035, -0.03], [0.08, -0.033], [0.12, -0.028], [0.15, -0.015], [0.17, 0], [0.16, 0.017], [0.12, 0.03], [0.08, 0.036], [0.045, 0.04], [0.02, 0.028], [0, 0.022]], closed: true },
    top: { through: [[0, -0.014], [0.05, -0.015], [0.1, -0.012], [0.15, -0.008], [0.17, 0], [0.15, 0.008], [0.1, 0.014], [0.05, 0.016], [0, 0.014]], closed: true },
  },
};
```

- **Size 1**: outlines are body units, as the trace prints them.
- **The drawing is the palm.** The drawn solid is cut back at the fingers' knuckles and the fingers stand from it, carried to the drawn length and breadth.
- **One digit's own shape**, read after the drawing fits: `"fingers.2.length": 0.9` (the third finger shorter), `"fingers.0.width": 1.1`, `"fingers.thumb.spread": 10` (degrees further out in the fan), counted from the thumb's side. `fingerLength`, `fingerThickness`, `spread`, `merge` and `knuckles` change them all.
- **The wrist** is the arm's own end: the hand reads the arm's end radius (`limb`) and the arm runs straight into it, so never set it and never flare the arm's last stations.
- **Undrawn**, the parameters shape the hand as before (`palmLength`, `palmWidth`, `palmThickness`), a quick stand-in.

## Digits

| `digits` | `thumb` | builds |
| --- | --- | --- |
| 5 (default) | on | four fingers `fingers:0` to `fingers:3` and `fingers:thumb`: a person's hand |
| 4 | on | three fingers and a thumb: the usual cartoon hand |
| 3 | on | two fingers and a thumb: a robot's or a creature's grip |
| 2 | on | one mitten finger and a thumb |
| 2 to 5 | off | that many fingers, no thumb |

Fewer fingers are thicker and fan wider over the same palm. A cartoon or creature hand is short and thick-fingered: `fingerThickness` 1.1 to 1.6, `knuckles` low. Very thick fingers on four or five digits touch again: thin them or use fewer. A cupped palm: `cup` 0.04 to 0.1. Nails show when the palette names `nails`, or fill `areas: { "nail:0": "claw", "nail:thumb": "claw" }`.

## Which way the palm faces

At rest the palm faces the body's middle and the fingers hang down, the first finger forward beside the thumb. A negative `twist` on the elbow turns the palm forward (the right side mirrors the left); give the twist `mode: "free"`, since an elbow's limits hold it at 0: `{ joint: "joint.2", twist: -70, mode: "free" }`. In A-pose the hand takes the arm's `outward: 45`.

## Poses

Each finger has three joints, `fingers:<i>.1` (the knuckle) to `.3`; the thumb `fingers:thumb.1` to `.3`. Fingers curl with negative degrees and the thumb across the palm with positive. `digitCurl` writes them:

```ts
import { digitCurl, type BonePose } from "squash-kit";

export const relaxedHands: BonePose[] = ["hand-l", "hand-r"].flatMap((part) =>
  digitCurl(part, { count: 3, curl: [-6, -10, -6], each: (i) => -2 * i, thumb: [6, 4, 2] }),
);
```

Put it in the `displayPose`: a hand hanging dead straight reads as a glove. Pose the sheet's own pose (a wave, a hand on a hip) as a named pose and render it beside the sheet.

## 4. Judge side by side

`close=hands` renders the bare hand at rest, curled, open and spread from six sides; put the back, the palm and the side beside the sheet's crops at the same scale, and the sheet's pose beside its view. Check the hand's length against the face's height in the front render, the palm's breadth against the fingers' length, each finger's length against the sheet, the wrist meeting the arm with no step, and `handLength` and `handWidth` in `measure.txt`. Wrong silhouette: fix the outline. Wrong digits: fix the digit parameters.

## On one digit

A socket, region or mark names a digit's patch to lie on that digit and ride it as it curls: a ring on the third finger is `socket(1, 0.7, 0.25, { patch: digitPatch(2), sink: 0.4 })` on the hands. A patch the hand does not build warns at build; a garment's print on a digit warns too, since cloth stops where the fingers leave the palm.

**Webbed fingers**: `webbing: 1` with `webReach` and `webEdge` (`ears-tails-extras.md`, Webs and membranes).

## Limits

- Cloth stops at the fingers' roots: a glove is fingerless, and prints on a finger show nowhere.

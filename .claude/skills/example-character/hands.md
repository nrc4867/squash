# Hands

Build the hand the character means, as a part that poses well. The sheet says what the hands are like: their size against the head, how soft or bony, how long the fingers, where the thumb sits. The `hand` template (version 9, the newest) says how a hand is built so it curls and spreads: bones, digits, nail areas. Neither is the answer by itself. On most sheets a hand is a few dozen pixels, turned, curled or against a sleeve or a thigh: a trace of it is a blob, and the template at its defaults is nobody's hand. `templates.md`, hand, lists its parameters, digit joints and `nail:<i>` areas; `docs/hld/parts.md` says how it is built. A front paw on an arm is a `paw` instead (`feet-and-paws.md`).

## 1. Measure the proportions first

- **Zoom every hand** the sheet draws, in every view: `kit sheet zoom <sheet> view=<view> region=hands`, or `box=x,y,w,h scale=4` for each hand. Write down the intent in words: small or large for the head, soft or bony, finger length against the palm, how thick and how rounded the tips, how far they spread, where the thumb leaves the palm and how far it stands out.
- **Size against the head**: `kit sheet measure <sheet> views=front,side,back` reads `handLength` and `handWidth`, but a hand that is spread, turned, curled or touching something reads unsure, and two hands can differ by half. Where it is unsure, measure wrist to fingertip on the zooms yourself, in head heights, for each hand, and choose one length between them: an open hand seen flat reads longest, a hanging curled one shortest. Write the chosen proportion in the file as a target (`targets: targets({ handLength: "0.35 head" })`) with the readings it came from.

## 2. Choose the construction

Pick what gives a clean, posable hand that carries the intent:

- **The template with parameters** is the usual answer: no outlines, `size` for the length, the digit parameters for the rest. Build once at `size` 1, read `handLength` in `measure.txt`, and set `size` to the target over that length.
- **Drawn outlines** only where the sheet shows the hand large and clear (a big flat hand in a side or front view, a character whose hands are a feature), traced from a zoomed crop (below) and kept to a sane depth: the hand's Top (its thickness) about half the wrist's width or less, never the sheet's flat drawing taken as a thickness.

Then shape it to the intent:

- **Fingers**: `fingerLength` (about 0.8 to 0.9 for short toon fingers, near the palm's own length), `fingerThickness` (1.2 to 1.5 for soft, rounded fingers), `knuckles` low (0.2 to 0.4) for a smooth soft hand, `merge` 0.1 to 0.2 to keep them apart. One digit's own shape: `"fingers.2.length": 0.9`, `"fingers.0.width": 1.1`, `"fingers.thumb.spread": 10` (degrees further out in the fan), counted from the thumb's side; `spread` for the whole fan.
- **The palm**: thinner than the wrist, never a flat card behind a round wrist. Undrawn, `palmThickness` sets its depth (0.1 to 0.13 on a small hand on a round forearm, so the hand does not step down from the wrist like a flipper), `palmWidth` and `palmLength` its breadth and length, `cup` 0.02 to 0.05 a slight hollow.
- **The wrist** is the arm's own end: the hand reads the arm's end radius (`limb`) and runs the arm straight into it, so never set it. When the wrist comes out wider than the hand wants, slim the arm's end: on a figure, narrow the arm's two strokes over its last stretch toward the wrist (a forearm tapers); on a tube, its last stations.

```ts
import { pair, type RecipePart } from "squash-kit";

/** Small soft hands, about 0.35 of a head long: short round fingers, the thumb low and standing out. */
export const hands: RecipePart = {
  id: "hands",
  template: "hand",
  on: "arms",
  socket: "socket.1",
  ...pair("hand"),
  role: "hand",
  size: 0.52,
  outward: 45,
  inkJoin: false,
  color: "skin",
  params: { digits: 5, thumb: 1, fingerLength: 0.9, fingerThickness: 1.35, "fingers.thumb.spread": 10, knuckles: 0.3, merge: 0.15, palmThickness: 0.12, cup: 0.04 },
};
```

## 3. Tracing, where the sheet shows the hand large

```
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=side part=hand-right as=side
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front part=hand-left as=top
tools/recipe.sh kit sheet trace <sheet> names=front,side,back view=front box=x,y,w,h part=hand-right as=side wrist=x,y tip=x,y
```

- **Side** (`side`): the back of the hand as the character's side view shows it, x down from the wrist to the fingertips, y forward (the thumb's side). A posed hand, a wave with the palm to the viewer, is traced from a `box=` with `wrist=` and `tip=` along its middle finger.
- **Top** (`top`): the hand edge-on, as the front view shows a hand hanging with its palm in: x down, y toward the character's right; it sets the hand's thickness.

Paste at `size: 1` (the trace prints body units). The drawing is the palm: the solid is cut back at the fingers' knuckles and the fingers stand from it, carried to the drawn length and breadth. Keep the traced palm, close the fingers' end round their tips at the length the sheet shows, and give the spread with the parameters. Look at the trace before pasting: a trace that takes in a sleeve, a thigh or the other hand, or that comes out deeper than the wrist, is the drawing, not the hand; drop it and use the template.

## 4. Check it as a 3D part

- The palm thinner than the wrist, and the wrist no wider than the forearm's end: no balloon palm, no bulb at the wrist, no flat card.
- The fingers apart at rest and in the T-pose (`smallest gap between digit tips` in `measure.txt` above 0), none crossing another.
- `handLength` and `handWidth` in `measure.txt` against the target.

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

## 5. Judge side by side

Render `close=strip,hands,hand-l` with `poses=` naming a T-pose: `hand-rest.png` (the bare hand at rest, curled, open and spread from six sides), `<outfit>-hand-l.png` in the display pose bare and dressed, `<outfit>-pose-t-hand-l.png`, and the strip. Put each beside the sheet's zoomed crops of the hands at the same scale. Check the hand's length against the face's height in the front render, the palm's breadth against the fingers' length, the thumb's place, and the hand coming out of a sleeve's cuff as the sheet draws it. Judge by eye: whether it reads as the sheet's hand, not whether a number matches. Wrong size: `size`. Wrong digits: the digit parameters. Wrong silhouette on a drawn hand: the outline, kept thin.

## On one digit

A socket, region or mark names a digit's patch to lie on that digit and ride it as it curls: a ring on the third finger is `socket(1, 0.7, 0.25, { patch: digitPatch(2), sink: 0.4 })` on the hands. A patch the hand does not build warns at build; a garment's print on a digit warns too, since prints lie on the cloth of the palm's own run. A glove covers the digits finger by finger: `glove({ digits: "fingers" })` (or `"mitten"`) in the kit.

**Webbed fingers**: `webbing: 1` with `webReach` and `webEdge` (`ears-tails-extras.md`, Webs and membranes).

## Limits

- Cloth stops at the fingers' roots: a glove is fingerless, and prints on a finger show nowhere.

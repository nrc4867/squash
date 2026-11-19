# Hands

A hand is the `hand` template (version 7, the newest) on the arm's end socket: a palm in the surface with a thumb pad and a wrist, and two to five digits joined to it with no line at their roots. `templates.md`, hand, lists its parameters, its digit joints and its `nail:<i>` areas; `docs/hld/parts.md` says how it is built. A front paw on an arm is a `paw` instead (`feet-and-paws.md`).

## Size and place

- **Size it from the sheet, never by default.** Measure the hands (`kit sheet measure` and `kit sheet zoom ... region=hands` print `handLength` and `handWidth` as body units and as shares of the head and of the height, with found, unsure or estimated and the reason; the zoom draws the lines they were read along). A hand too small is the commonest miss: declare the reading you trust as a target (`targets: targets({ handLength: "0.38 head", handWidth: "0.25 head" })`), and after each build read `handLength` and `handWidth` in `measure.txt` and fix any `FLAG` before judging the hand by eye (a spread hand on the sheet reads as wide as its spread fingers, so compare `handWidth` with the pose you build). Read the hand's length, wrist to fingertip, in body units. The hand at `size` 1 is about 0.21 long (`proportions.md`, biped): `size` is the sheet's length over that. A person's hand is about three quarters of the face's height (chin to hairline); a cartoon creature's is whatever the sheet draws, often smaller than it looks.
- It sits on the arm's end socket, at the wrist (`body.md`, joints): give the arm a hand-made socket at u 1 facing down and back (`socket(1, 1, vToward("down", "back"))` on an arm resting down) and put the hand there. In A-pose the hand takes the arm's `outward: 45` so it hangs along the arm.
- **Which way the palm faces.** At rest the palm faces the body's middle and the fingers hang down, the first finger (`fingers:0`) forward beside the thumb. A negative `twist` on the elbow turns the palm forward, on either side of a pair (the right mirrors the left); a positive one turns it back, the nails to the front. An elbow bends one way only, so its limits hold its twist at 0: give the twist `mode: "free"`, or twist the shoulder (`base`) within its limits instead. Turn it there, never on the hand's root: `{ joint: "joint.2", twist: -70, mode: "free" }` shows an open palm to the front.
- Leave it undrawn: version 7 fits its whole solid to drawn Side and Top outlines, but its parameters shape the palm and fingers better than a drawing does. Draw it only when the parameters cannot reach the silhouette.

```ts
import { pair, type RecipePart } from "squash-kit";

export const hands: RecipePart = {
  id: "hands",
  template: "hand",
  on: "arms",
  socket: "socket.1",
  ...pair("hand"),
  role: "hand",
  size: 0.42,
  outward: 45,
  inkJoin: false,
  color: "fur",
  params: { digits: 4, spread: 1.3, cup: 0.04 },
};
```

## Digits

| `digits` | `thumb` | builds |
| --- | --- | --- |
| 5 (default) | on | four fingers `fingers:0` to `fingers:3` and `fingers:thumb`: a person's hand |
| 4 | on | three fingers and a thumb: the usual cartoon hand |
| 3 | on | two fingers and a thumb: a robot's or a creature's grip |
| 2 | on | one mitten finger and a thumb |
| 2 to 5 | off | that many fingers, no thumb |

Each count has its own layout: fewer fingers are thicker and fan wider over the same palm, so three fingers read as drawn for three. Fingers are numbered from the thumb's side; the thumb is always `fingers:thumb`, whatever the count.

## Shape

- At the defaults the fingers part to their roots and taper to soft tips, the palm longer than it is wide. `spread` fans them across the palm (1.2 to 1.6 for an open display hand; 0.5 closes them); `merge` fuses them (about 1 for a mitten).
- A cartoon or creature hand is short and thick-fingered: `fingerLength` 0.8 to 0.95, `fingerThickness` 1.1 to 1.3, `knuckles` low. Very thick fingers on four or five digits touch again: thin them, or use fewer digits.
- A cupped, relaxed palm: `cup` 0.04 to 0.1. `palmWidth` and `palmLength` keep it longer than wide; a palm as wide as it is long reads as a disc.
- Nails: name a palette slot `nails` (the areas show in it), or fill `areas: { "nail:0": "claw", "nail:thumb": "claw" }` per digit.

## Poses

Each finger has three joints, `fingers:<i>.1` (the knuckle) to `.3`; the thumb has `fingers:thumb.1` to `.3`. Fingers curl with negative degrees and the thumb across the palm with positive. `digitCurl` writes them for one hand:

```ts
import { digitCurl, type BonePose } from "squash-kit";

export const relaxedHands: BonePose[] = ["hand-l", "hand-r"].flatMap((part) =>
  digitCurl(part, { count: 3, curl: [-6, -10, -6], each: (i) => -2 * i, thumb: [6, 4, 2] }),
);
```

`count` is the numbered fingers the hand builds (its `digits` less the thumb). Put it in the `displayPose` with the arms' lean and the elbow's twist: a hand hanging dead straight reads as a glove, and a small hand's straight fingers read as spikes. A cartoon hand that should read as a round ball curls harder (-25 on every joint). `wrist` bends the hand on the arm.

## On one digit

A socket, region or mark names a digit's patch to lie on that digit and ride it as it curls: a ring on the third finger is `socket(1, 0.7, 0.25, { patch: digitPatch(2), sink: 0.4 })` on the hands (v 0.25 is the digit's back, where its nail lies), with a part on `socket.1` of `hand-l`. `templates.md` lists each digit's patch. A patch the hand does not build (`fingers:3` on a hand of four digits) warns at build; a garment's print on a digit warns too, since cloth stops where the fingers leave the palm.

## Judging a hand

`close=hands` renders the bare hand at rest, curled, open and spread from six sides; `close=hand-l` frames the hand as shown in each outfit. Check:

- **Oversized hands** are the most common fault: compare the hand's length to the face's height in the front render against the sheet's.
- The fingers part at the tips (`smallest gap between digit tips` in `measure.txt`, near 0 means fused) and the palm reads longer than wide from the front and back.
- The wrist meets the arm with no step: the arm's last radius about the hand's `wrist` times its size.

## Limits

- Cloth stops at the fingers' roots: a glove is fingerless, and prints on a finger show nowhere.
- A palm and the wrist show a slightly stepped outline beside the finer fingers in close-ups.

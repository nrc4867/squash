# Hands

A hand is the `hand` template (version 6, the newest) on the arm's end socket: a palm in the surface with a thumb pad and a wrist, and five digits joined to it with no line at their roots. `templates.md`, hand, lists its parameters (`palmLength`, `palmWidth`, `palmThickness`, `wrist`, `fingerLength`, `fingerThickness`, `cup`, `web`, `knuckles`) and its `nail:0` to `nail:4` areas; `docs/hld/parts.md` says how it is built. A front paw on an arm is a `paw` instead (`feet-and-paws.md`).

## Size and place

- **Size it from the sheet, never by default.** Zoom the hands (`kit sheet zoom ... region=hands`) and read the hand's length, wrist to fingertip, in body units. The hand at `size` 1 is about 0.21 long (`proportions.md`, biped): `size` is the sheet's length over that. A person's hand is about three quarters of the face's height (chin to hairline); a cartoon creature's is whatever the sheet draws, often smaller than it looks.
- It sits on the arm's end socket, at the wrist (`body.md`, joints): give the arm a hand-made socket at u 1 facing down and back (`socket(1, 1, vToward("down", "back"))` on an arm resting down) and put the hand there. In A-pose the hand takes the arm's `outward: 45` so it hangs along the arm.
- It hangs with its palm toward the body's middle and its fingers forward; the display pose turns it with a `twist` on the elbow, never on the hand's root.
- Leave it undrawn: version 6 fits its whole solid to drawn Side and Top outlines, but its parameters shape the palm and fingers better than a drawing does. Draw it only when the parameters cannot reach the silhouette.

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
  params: { fingerLength: 0.8, fingerThickness: 1.3, palmWidth: 0.2, cup: 0.06 },
};
```

## Shape

- A cartoon or creature hand is short, thick-fingered and round: `fingerLength` 0.7 to 0.9, `fingerThickness` 1.2 to 1.5, `knuckles` low, `web` higher.
- A cupped, relaxed palm: `cup` 0.04 to 0.1.
- Nails: name a palette slot `nails` (the areas show in it), or fill `areas: { "nail:0": "claw", ... }` per digit.
- The hand has five digits. A creature with three or four fingers wears a `paw` on its arm (`toes` 3 or 4; `feet-and-paws.md`), turned to hang with `orient`, or a mitten (`toes` 0, or `merge` about 1).

## Poses

Each digit has three joints, `fingers:<i>.1` to `.3`, index to little finger as 0 to 3 and the thumb as 4. Curl the fingers with negative degrees and the thumb with positive:

```ts
import type { BonePose } from "squash-kit";

const curl = [-10, -14, -9];
export const relaxedHand: BonePose[] = ["hand-l", "hand-r"].flatMap((part) => [
  ...[0, 1, 2, 3].flatMap((i) => curl.map((degrees, k) => ({ part, joint: `fingers:${i}.${k + 1}`, degrees: degrees - i * 2 }))),
  ...[1, 2, 3].map((k) => ({ part, joint: `fingers:4.${k}`, degrees: 10 })),
]);
```

Put it in the `displayPose` with the arms' lean: a hand hanging dead straight reads as a glove, and a small hand's straight fingers read as spikes. A cartoon hand that should read as a round ball curls harder (-25 on every joint). `wrist` bends the hand on the arm.

## Judging a hand

`close=hands` renders the bare hand at rest, curled, open and spread from six sides; `close=hand-l` frames the hand as shown in each outfit. Check:

- **Oversized hands** are the most common fault: compare the hand's length to the face's height in the front render against the sheet's.
- The fingers part at the tips (`smallest gap between digit tips` in `measure.txt`, near 0 means fused).
- The wrist meets the arm with no step: the arm's last radius about the hand's `wrist` times its size.

## Limits

- Five digits always; fewer means a paw on the arm.
- A palm and the wrist show a slightly stepped outline beside the finer fingers in close-ups.

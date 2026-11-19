# Ears, tails and extras

## Ears

An ear is the `ear` template, version 3 (the newest): one surface with the head, the skull rising into it over a wide blend with no join line. `templates.md`, ear, lists its parameters and its `inner`, `back` and `tip` areas; `docs/hld/surface.md`, Ears in the field, how it is built.

- It sits on the head's `ear` socket (a pair). Move the socket where the sheet puts the ear with the head part's `socketsAt`: on `head-round`, `{ ear: [u, v] }` with u up the head from the chin (0.5 the middle, 0.8 high on the crown) and v round it (0.75 the left side, toward 1 the face, toward 0.5 the back). `probe part=head` prints where it landed.
- **Size it** from the sheet (`kit sheet trace ... part=ear-left` gives its extent): undrawn, an ear is 0.3 long at `size` 1 and `width` (its half width at the base, a share of its length) wide, so `size` is the sheet's ear length over 0.3.
- **A pointed ear** (a fox's, a cat's) can be drawn: `leaf` makes an ear's Top with its tip a sharp corner, `length` and `width` from the sheet, `base` its width where it meets the head; its outline x runs to its own length in body units. **A round ear** (a mouse's, a bear's) is left undrawn, since a drawn tip corner stays pointed whatever `roundTip` says: `size`, `width` 0.5 to 0.6, `taper` 0.9, `roundTip: 1`, `tipReach` about 0.5.
- **Its depth and cup**: `thickness` 0.6 to 0.9 gives a full round back that reads from behind; `cup` 0.3 to 0.4 dishes a hollow into the front; `rim` 0.15 to 0.25 the rim round it. Fill its `inner` area and the colour follows the hollow.
- **Its tip**: pointed by default; `roundTip: 1` with `taper` 0.6 to 0.8 and `tipReach` about `width` times `taper` rounds it, and a round ear (a mouse's, a bear's) takes `taper` near 1.
- Aim it with `outward` (out from the head's middle) and `lean` (toward its own up, the face on `head-round`): the cup should face the way the sheet's inner ear shows, usually forward and a little out. Judge its splay from above (`-back-top.png`).
- **The blend.** The skull rises into the ear over 0.3 of the ear's length; two big ears close together on a small head fuse across the crown into one shape. Give the ear a `blend` in body units (0.02 to 0.03 on a small head) to keep a dip between them.

```ts
import { leaf, outline, pair, type RecipePart } from "squash-kit";

export const ears: RecipePart = {
  id: "ears",
  template: "ear",
  on: "head",
  socket: "ear",
  ...pair("ear"),
  color: "fur",
  areas: { inner: "inner-ear" },
  outward: 22,
  size: 0.7,
  blend: 0.025,
  params: { width: 0.6, thickness: 0.7, cup: 0.35, rim: 0.2, roundTip: 1, taper: 0.9, tipReach: 0.5 },
};

export const pointedEars: RecipePart = {
  ...ears,
  size: 1,
  outlines: { top: outline(leaf({ length: 0.3, width: 0.16, base: 0.12 })) },
  params: { thickness: 0.6, cup: 0.35, rim: 0.2 },
};
```

Judge an ear from three-quarter and from behind as well as front and side (`close=ear-l`): no line at its base, a full back, the cup's lip one smooth curve. **A wobbly rim in three-quarter** means too few drawn places or a rim too thin: fewer, smoother outline places and `rim` toward 0.25. **Ears reading as flat cards** from the side want more `thickness`.

Versions 1 and 2 are thin separate sheets, kept only for characters that pin them.

## Tails

- **A long, thin tail that curves** (a mouse's, a monkey's, a cat's) is `tail-curve`, with `thickness` (radius at the root, a share of its length) and `taper` (0.1 to 0.15 for a strong taper). Its resting curve runs through the part's own `curve`: 2 to 24 `[along, up]` places (`[along, up, side]` to swing sideways) in the part's own axes as fractions of its length, so an S with a hooked end is one tail. The curve is scaled to the part's length (`size`); up is its Side's +y (`frames.md` gives which way that faces, and which sign of `lean` lowers a tail resting back and up). A hook tighter than the tail is thick narrows the tail a little through the curl and ends in a round tip.
- `tail` stays the default for short or thick tails (a fox's brush, a bear's stub): draw its Side and Top.
- A tail on a `blank` body sits on a hand-made socket on the torso's back, low: on a torso resting up, `vToward("up", "back")` at the seat's height.

```ts
import { socket, vToward, type RecipePart, type RecipeSocket } from "squash-kit";

export const tailSocket: RecipeSocket = socket(4, 0.12, vToward("up", "back"), { sink: 0.5 });
export const tail: RecipePart = {
  id: "tail",
  template: "tail-curve",
  on: "torso",
  socket: "socket.4",
  role: "tail",
  color: "fur",
  size: 0.9,
  inkJoin: false,
  curve: [[0.3, -0.1], [0.6, 0.05], [0.8, 0.3], [0.85, 0.5], [0.72, 0.58], [0.66, 0.48]],
  params: { thickness: 0.035, taper: 0.5 },
};
```

Read the tail's curve off the side view: its root, where it turns, how high the curl rises, which way the hook closes. Judge it from the side and from above (`-back-top.png`): a tail that swings into a leg wants a `side` component or `outward`.

## Horns, spikes, fins, wings

`horn`, `antler`, `spike`, `claw`, `plate`, `fin`, `sheet`, `wing-feathered` and `wing-membrane` are templates (`templates.md`); they sit on a template's sockets or hand-made ones. A row of spines down a back is one socket row: `socket(n, u, v, { count: 6, to: 0.9, size: 1, falloff: 0.5 })`, named `socket.<n>.0` and on, one part on each. `orient` and `around` (`kit/README.md`, Frames) point parts in any direction, as petals round a centre.

## Tufts and locks

`tuft` draws pointed tips for a cheek ruff or a crest; `furLock` a curling lock of fur or hair (`kit/README.md`, Shapes). **Tufts that read as horns** stand too stiff and too far out: make them short and wide at the root, lean them along the surface, and colour them as the fur they grow from.

## Fur

`fur.patches` lays rows of fur stroke roots on a part: `{ name: "chest", rows: 14, columns: 36, u: [0.66, 0.97], v: [0.2, 0.8] }`, with `fur.spacing` and `fur.length` in screen pixels; marks with `kind: "fur"` are single roots. `recipes/fennec.recipe.json` has fur.

## Limits

- **Fur sizes have no guide** for a small character: start from the fennec's spacing and length and halve them, then judge the close-ups.
- **Hair and manes** read as a helmet from the side (`head-and-face.md`, Limits).

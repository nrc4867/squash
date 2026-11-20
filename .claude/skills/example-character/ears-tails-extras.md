# Ears, tails and extras

## Ears

An ear is the `ear` template, version 3 (the newest): one surface with the head, the skull rising into it over a wide blend with no join line. `templates.md`, ear, lists its parameters and its `inner`, `back` and `tip` areas; `docs/hld/surface.md`, Ears in the field, how it is built.

- It sits on the head's `ear` socket (a pair). Move the socket where the sheet puts the ear with the head part's `socketsAt`: on `head-round`, `{ ear: [u, v] }` with u up the head from the chin (0.5 the middle, 0.8 high on the crown) and v round it (0.75 the left side, toward 1 the face, toward 0.5 the back). `probe part=head` prints where it landed.
- **Size it** from the sheet (`kit sheet trace ... part=ear-left` gives its extent): undrawn, an ear is 0.3 long at `size` 1 and `width` (its half width at the base, a share of its length) wide, so `size` is the sheet's ear length over 0.3, from 0.15 up: a toon human's small ear is about 0.2 (0.06 long).
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

- **A long, thin tail that curves** (a mouse's, a monkey's, a cat's) is `tail-curve`, with `thickness` (radius at the root, a share of its length) and `taper` (0.1 to 0.15 for a strong taper). Its resting curve runs through the part's own `curve` (Curves and coils, below), so an S with a hooked end is one tail.
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

## Curves and coils

Any part whose template sweeps along its axis takes a curve: `templates.md`'s curve column says which (`tube`, `horn`, `spike`, `antler`, `tail`, `tail-curve`, `hair`, `claw`, `fin`, `ear`, and tube props). Heads, blobs, hands and paws do not; a curve on them is ignored with a warning.

- **`curve`**: 2 to 24 `[along, up]` or `[along, up, side]` places in the part's own axes as fractions of its length, after its socket point. Up is its Side's +y and side its v 0.25 (`frames.md`). The places may turn back on themselves and leave the plane: a hook, an S, a loop.
- **`spiral`**: `{ turns, pitch, shrink }` in place of places, for a ram's horn or a coiled trunk or tail. It leaves along the rest and coils `turns` times toward up (negative toward the underside) about a line toward the part's side; `pitch` moves the coil that way each turn (a share of its first diameter; 0.3 to 0.6 either way keeps a horn's coil beside the head: judge from above, and flip its sign if the coils run in behind the head), `shrink` is the coil's radius at the tip over its first (0.4 to 0.6 for a horn).
- **The curve is the part's length**, scaled to it, so a coil of more turns is a smaller coil at the same `size`: a ram's horn of 1.5 turns wants `size` 3 to 4 and a thin `girth` (0.06). `horn` takes `size` up to 4.
- **Drawn parts keep their own curve**: Side and Top give the width along the length, the part's `curve` or `spiral` the path, so a tapering tusk or a flat ribbon follows its places. A drawn `tail-curve` with no curve of its own follows its drawing's midline.
- **A warning that the rings narrow** means the curve bends tighter than the part is thick: spread the places, open the coil (less `turns` or more `shrink`), or thin the part. Rings never fold into each other; they narrow there instead.

```ts
export const ramHorns: RecipePart = {
  id: "horns", template: "horn", on: "head", socket: "socket.1", pair: { left: "horn-l", right: "horn-r" }, role: "horn", color: "horn",
  size: 4, outward: 40, lean: 25, params: { girth: 0.06 }, spiral: { turns: -1.5, pitch: -0.5, shrink: 0.5 },
};
export const tusks: RecipePart = {
  id: "tusks", template: "spike", on: "head", socket: "socket.2", pair: { left: "tusk-l", right: "tusk-r" }, role: "horn", color: "tusk",
  rest: "forward", outward: 20, size: 0.9, params: { girth: 0.16 }, curve: [[0.35, 0.02, 0.02], [0.65, 0.18, 0.06], [0.8, 0.45, 0.05], [0.72, 0.7, 0.02]],
};
export const trunk: RecipePart = {
  id: "trunk", template: "tail-curve", on: "head", socket: "socket.3", role: "tail", color: "hide", rest: "down", size: 1.3,
  params: { thickness: 0.1, taper: 0.45 }, curve: [[0.3, 0.04], [0.6, 0.1], [0.82, 0.24], [0.9, 0.44], [0.8, 0.58], [0.66, 0.54], [0.64, 0.42]],
};
```

Judge a curl from the side and three-quarter and from above: a coil that runs into the head or neck wants more `outward` or `pitch`.

## Horns, spikes, fins, wings

`horn`, `antler`, `spike`, `claw`, `plate`, `fin`, `sheet`, `wing-feathered` and `wing-membrane` are templates (`templates.md`); they sit on a template's sockets or hand-made ones. A short crest of a few spikes down a back is one socket row: `socket(n, u, v, { count: 6, to: 0.9, size: 1, falloff: 0.5 })`, named `socket.<n>.0` and on, one part on each, each starting at its socket's size unless the part gives its own. **Antlers branch** by sitting more antlers on an antler's sockets: `tine` partway up, and `tip`, where the child sinks back into the beam's end along the beam and continues it, so a fork at the tip reads as one piece; aim each branch with `lean` and `outward`, and curve the beam with `curve`. `orient` and `around` (`kit/README.md`, Frames) point parts in any direction, as petals round a centre.

## Many copies: spines, wool, manes, scales, tufts

Anything made of many of one part is **one part on one spread socket**, never a part per copy: the socket shows the part as copies scattered over an area of the part it is on, each an instance of the one build, so a few hundred stay cheap and editing the part changes them all.

```ts
import { spreadSocket, type RecipePart } from "squash-kit";

torso.sockets = [spreadSocket(1, { u: [0.12, 1], v: [0.62, 1.38] }, { count: 220, seed: 11, spacing: 0.026, sizeJitter: 0.25, leanJitter: 12, turnJitter: 40 }, { outward: 55, sink: 0.6, falloff: 0.55 })];
export const spines: RecipePart = { id: "spines", template: "spike", on: "torso", socket: "socket.1", color: "spine", shades: ["spine-light", "spine-dark"], outlines };
```

- **The area** is u from `u[0]` to `u[1]` and v from `v[0]` to `v[1]`; v may run past 1 to wrap over the seam (0.62 to 1.38 covers a quadruped torso's back and flanks, v 0 on top), and `v: [0, 1]` is all round. Copies fall where the surface is, by area, so a narrow end gets few.
- **Density**: `count` up to 1000 and `spacing` (body units) so they do not pile up; with a spacing too wide for the count, fewer show. A sheep's fleece is about 150 `blob` lumps at `size` 0.45 with `spacing` 0.055; a hedgehog's back 200 or more spikes at `spacing` 0.025.
- **How they stand**: along the surface's normal; the socket's `outward` tilts them all toward the part's end (negative toward its start), so spines lie back toward the tail and a mane's clumps sweep back. `leanJitter` and `turnJitter` (degrees) break the regularity; `sizeJitter` varies their size; `size` and `falloff` size them and shrink them toward the area's edges over its outer `edge` share. `sink` and `outset` seat them as any socket's.
- **Colour**: the part's `color`, and `shades`, a few more palette slots each copy picks among.
- `seed` picks the scatter; another seed, another scatter with the same look. A mane is a spread of `hair-clump`s round the back of the head and a second one down the chest.
- A part on a spread socket leaves the one surface: field templates (`blob`) build as their own meshes there, each copy inked round.

## Webs and membranes

**Webs between digits** are the hand's, foot's, paw's and bird foot's own: `webbing: 1` on the part's `params` builds a thin skin web between each two neighbouring counted digits (not a thumb or a hind toe), riding both digits as they spread and curl and inked only along its free edge. `webReach` is how far up the digits it reaches from where they leave the palm or sole (1 a full web to the tips, 0.5 a half web), `webEdge` how far its free edge dips between two digits (0 straight, 0.3 to 0.4 scalloped), `webThickness` how thick it is between them. Colour it with the `webbing` area: `areas: { webbing: "web" }`. Check it with `close=feet` or `close=hands`, whose spread and curl views show it bend.

**A membrane between parts** (a glider's patagium, a bat's wing from arm and finger to flank and leg) is a top-level `membranes` entry, not a part: two rails, `first` and `second`, each a list of edges `{ part, from: [u, v], to: [u, v], patch? }` in order from the membrane's root to where its free edge meets that rail. The sheet spans across from one rail to the other; its free edge runs between the rails' last ends and curves in by `edge`; its root runs between their first ends, so start both rails on the body (a flank edge before the limb's) to root it there. `billow` bulges its middle toward the ground where it lies level, out from the body where it hangs; `thickness` and `sink` (how deep the rails sit in their parts) set how it swells into them, which is what hides the join. `mirrored` (on by default) builds the other side on each pair's right part. Name a pair by its left id; a digit by its patch (`fingers:3` is a hand's last finger from the thumb side, `templates.md` lists patches). Pick each edge's v on the side facing the other rail (`docs/reference/frames.md`, Part frame), and check with a pose that spreads and folds the limbs: the sheet rides their bones.

```ts
membranes: [{
  id: "patagium",
  first: [{ part: "torso", from: [0.78, 0.27], to: [0.81, 0.4] }, { part: "front-leg-l", from: [0.12, 0.5], to: [0.95, 0.5] }],
  second: [{ part: "torso", from: [0.22, 0.27], to: [0.19, 0.4] }, { part: "back-leg-l", from: [0.12, 0.0], to: [0.95, 0.0] }],
  edge: 0.3, billow: 0.12, color: "skin",
}],
```

## Tufts and locks

`hairTuft` is a soft tuft blended into the fur it grows from: a crown tuft, cheek fluff, a lynx ear tip (`kit/README.md`, Hair). A mane that reads as one mass is rings of `hair-clump`s on hand-made sockets round the back of the head and the chest, each blended into the head with the `fluff` preset; a spread of clumps is faster to place but its copies are separate meshes. `tuft` draws pointed tips for a cheek ruff or a crest; `furLock` a curling lock for the older `hair` template (`kit/README.md`, Shapes). **Tufts that read as horns** stand too stiff and too far out: make them short and wide at the root, lean them along the surface, and colour them as the fur they grow from.

## Fur

`fur.patches` lays rows of fur stroke roots on a part: `{ name: "chest", rows: 14, columns: 36, u: [0.66, 0.97], v: [0.2, 0.8] }`, with `fur.spacing` and `fur.length` in screen pixels; marks with `kind: "fur"` are single roots. `recipes/fennec.recipe.json` has fur.

## Limits

- **Fur sizes have no guide** for a small character: start from the fennec's spacing and length and halve them, then judge the close-ups.
- **Manes do not collide**: clumps pass through each other; `droop` and `outward` lay them back (`head-and-face.md`, Limits).

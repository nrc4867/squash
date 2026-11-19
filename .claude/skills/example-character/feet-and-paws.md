# Feet and paws

A foot or paw ends a leg on the leg's end socket, at the ankle, and the floor rule stands it on the ground: whatever ends a leg that bears weight is what the character stands on. `templates.md` lists each template's parameters and areas; `docs/hld/parts.md`, Paws and Feet, how they are built.

| template | use for | toes |
| --- | --- | --- |
| `foot` (version 6) | a plantigrade foot: a person's, a mouse's, a bear's; a heel under the ankle, a sole, toes flat underneath | `toes` 0 or 5; `spread`, `merge` |
| `paw` (version 7) | a digitigrade paw: a fox's, a cat's, a dragon's; a heel ball and pad, toes in a fan, claws, pads and beans | `toes` 0, 3, 4 or 5; `spread`, `merge`, `claws`, `claw`, `clawCurl`, `pads`, `beans` |
| `foot-bird` (version 4) | a bird's foot | four toes |

Both rest forward; both fit their whole solid to drawn Side and Top outlines, but their parameters shape them better: leave them undrawn unless the parameters cannot reach the silhouette.

## Size and place

- Zoom the feet (`kit sheet zoom ... region=feet`) in the front and side views and read the foot's length heel to toe, and its width. At `size` 1 a `foot` is about 0.24 long and a `paw` about 0.28 (`proportions.md`): `size` is the sheet's length over that. (`foot` sizes in metres: 1.18 gives a person's 0.28 m.)
- The leg ends at the ankle height in the plan, with a socket at u 1 facing down and back (`socket(1, 1, vToward("down", "back"))`); the foot goes there. The foot's socket sits only a little above its sole (0.025 for a `foot` at `size` 0.72, more as it grows), so the plan's `ankle` is that height: read it from the first `kit heights` run and set it (`body.md`, joints). Its heel sits under the ankle, so a foot whose toes point too far forward or back means the ankle is off, not the foot.
- `probe part=foot-l` prints its box: its lowest y is the ground, 0. A foot floating or sinking means the leg's length or its end socket is wrong (`body.md`, joints).
- **From the front a foot shows its width, not its length**: a cartoon foot that reads as a wide oval from the front wants `ballWidth` and `heel` up, not only `size`.
- The leg's last radius should meet the foot's `ankle` (times its size) with no step: thin legs on big feet read as stilts.

```ts
import { pair, type RecipePart } from "squash-kit";

export const feet: RecipePart = {
  id: "feet",
  template: "foot",
  on: "legs",
  socket: "socket.1",
  ...pair("foot"),
  role: "foot",
  size: 0.42,
  inkJoin: false,
  color: "fur",
  params: { toes: 0, heel: 0.16, ballWidth: 0.2 },
};
```

## Shape

- A round cartoon foot with no toes: `foot` with `toes` 0, a wide `ballWidth` and a full `heel`. A foot with toes: `toes` 5, `merge` low for toes standing apart, about 1 to join them to the tips; `spread` below 1 brings them closer.
- A paw: `toes` 3 to 5, `merge` 0.15 to 0.3 for toes standing clear (a fox's), about 1 for a mitten (a house cat's); `claw` for how far claws stand past the toes, `clawCurl`, `claws: 0` to retract; `pads` for the main pad and beans, `beans` to raise them. Pads and claws show in the palette slots `pad` and `claw` when the palette names them.
- Colour: `areas` fill `sole`, `toes` on a foot, `pad`, `sock`, `top`, `toes` on a paw.
- A quadruped's every leg carries a paw: a leg with nothing on its end socket ends in a flat stump.

## Judging feet

`close=feet` renders the bare foot or paw at rest, curled, open and spread from six sides; `close=foot-l` frames it as shown per outfit. From the front the feet stand apart under the hips; from the side the heel is under the ankle; from behind the heels read; toes part (`smallest gap between digit tips` in `measure.txt`).

## Limits

- **Specks between merged toes.** A `paw` whose base slot differs from the part it sits on can show a speck or two of the parent's colour where three toes meet. Give the paw a slot of its own rather than relying on the leg's.
- **Claws are field points.** A paw's claws are grown in its own surface and painted; they cannot be separate hard parts or carry sockets.
- **No toes on paddles.** A paddle foot or flipper is a drawn `blob` or `sheet` with no digits.

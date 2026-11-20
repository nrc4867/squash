# Feet and paws

A foot or paw ends a leg on the leg's end socket, at the ankle, and the floor rule stands it on the ground: whatever ends a leg that bears weight is what the character stands on. `templates.md` lists each template's parameters and areas; `docs/hld/parts.md`, Paws and Feet, how they are built.

| template | use for | toes |
| --- | --- | --- |
| `foot` (version 8) | a plantigrade foot: a person's, a mouse's, a bear's, an anthro's; an ankle with ankle bones, a heel set back behind it, a sole, toes flat underneath | `toes` 0 to 5; `toeLength`, `toeThickness`, `spread`, `merge`, `claw`, `clawCurl`, `pads`, `beans` |
| `paw` (version 9) | a digitigrade paw: a fox's, a cat's, a dragon's; a wrist rising into the leg, a heel ball and pad, toes in a fan, claws, pads and beans | `toes` 0 to 5; `ankle`, `spread`, `merge`, `claws`, `claw`, `clawCurl`, `pads`, `beans` |
| `foot-bird` (version 4) | a bird's foot | four toes |

Both rest forward; both fit their whole solid to drawn Side and Top outlines, but their parameters shape them better: leave them undrawn unless the parameters cannot reach the silhouette.

## Size and place

- Measure the feet: `kit sheet measure` and `kit sheet zoom ... region=feet` print `footWidth` (from a front or back view) and `footLength` (from a side view; a three-quarter view is foreshortened and unsure) as body units and as shares of the head and of the height, with status and reason, and draw the lines they were read along; socks and shoes read as the foot, so read the ankle and sole by eye on the zoom. A foot too small is the commonest miss: declare the readings you trust as targets (`targets: targets({ footLength: "0.45 head", footWidth: "0.35 head" })`) and fix every `FLAG` on `footLength` and `footWidth` in `measure.txt` before judging the foot by eye. Read the foot's length heel to toe, and its width. At `size` 1 a `foot` is about 0.24 long and a `paw` about 0.28 (`proportions.md`): `size` is the sheet's length over that. (`foot` sizes in metres: 1.18 gives a person's 0.28 m.)
- The leg ends at the ankle height in the plan, with a socket at u 1 facing down and back (`socket(1, 1, vToward("down", "back"))`); the foot goes there. The foot's socket sits only a little above its sole (0.025 for a `foot` at `size` 0.72, more as it grows), so the plan's `ankle` is that height: read it from the first `kit heights` run and set it (`body.md`, joints). Its heel sits under the ankle, so a foot whose toes point too far forward or back means the ankle is off, not the foot.
- `probe part=foot-l` prints its box: its lowest y is the ground, 0. A foot floating or sinking means the leg's length or its end socket is wrong (`body.md`, joints).
- **From the front a foot shows its width, not its length**: a cartoon foot that reads as a wide oval from the front wants `ballWidth` and `heel` up, not only `size`.
- Set the foot's `ankle` (times its size and length) to the leg's last radius, or the paw's `ankle` a little under it: the foot's ankle bones then swell past the leg, its heel sits back behind it by half that radius, and the template's own join blends the leg into it, so the leg needs no narrowing or swelling of its own at the ankle. Thin legs on big feet still read as stilts.

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

- A round cartoon foot with no toes: `foot` with `toes` 0, a wide `ballWidth` and a full `heel`. A foot with toes: `toes` 1 to 5, each count laid out across the ball of its own (5 a person's, fewer symmetric and wider); `merge` low for toes standing apart, about 1 to join them to the tips; `spread` below 1 brings them closer.
- An anthro's human-like foot with three wide, short toes, pads and claws: `foot` with `toes: 3, toeLength: 0.75, toeThickness: 1.25, merge: 0.15, claw: 0.5, pads: 1, beans: 0.25`, and a palette slot `pad`. Its claws are its nails, in the slot `nails`, or fill `areas: { "nail:0": "claw", ... }`.
- A hoof: `hoof` on the leg's paw socket, best from a preset (`preset: "horse"`, `"cattle"`, `"goat"` for a goat or sheep, `"deer"`, `"pig"`), then `params` to adjust: `toes: 1` a horse's, `toes: 2` a cloven hoof (a cow, a goat, a deer) with `cleft` for the gap, `flare` for how much wider it is at the sole, `height` for the wall, `width` and `point` for each toe's breadth and point, `splay` for how far the toes part, `dewclaws: 1` for two small dewclaws high behind (`dewLow`, `dewLarge` for a deer's and a pig's), `heels` and `frog` for a horse's. The whole part is keratin in the `claw` slot (fill `hoof`) with its `sole` in `pad`; draw the leg's end thin as the pastern, and the inked join is the coronet.
- A paw: `toes` 1 to 5 (2 a cloven foot, 3 or 4 a cat's or a dog's), `merge` 0.15 to 0.3 for toes standing clear (a fox's), about 1 for a mitten (a house cat's); `claw` for how far claws stand past the toes, `clawCurl`, `claws: 0` to retract; `pads` for the main pad and beans, `beans` to raise them. Pads and claws show in the palette slots `pad` and `claw` when the palette names them.
- Colour: `areas` fill `sole`, `toes` on a foot, `pad`, `sock`, `top`, `toes` on a paw. A paw's `sock` is the whole paw, toes too, under its pads, beans and claws; white socks are `areas: { sock: "white" }` on the paw and on the leg, whose `sock` is its lowest quarter.
- A quadruped's every leg carries a paw: a leg with nothing on its end socket ends in a flat stump.

## Judging feet

`close=feet` renders the bare foot or paw at rest, curled, open and spread from six sides; `close=foot-l` frames it as shown per outfit. From the front the feet stand apart under the hips; from the side the heel is under the ankle; from behind the heels read; toes part (`smallest gap between digit tips` in `measure.txt`).

## On one toe

Sockets, regions and marks name a toe's patch, `toes:0` on from the inner toe (`templates.md` lists them), to lie on that toe and ride it: `socket(1, 0.9, 0.25, { patch: digitPatch(0, "toes") })` on the feet. A patch the part does not build warns at build, as does a garment's print on a toe.

**Webbed toes**: `webbing: 1` with `webReach` and `webEdge` (`ears-tails-extras.md`, Webs and membranes).

## Limits

- **Cloth stops at the toes' roots**: a sock or shoe covers the foot and the toes poke through or stay bare, and prints on a toe show nowhere. A closed shoe sets `toes: 0` with a `redraw`.
- **Specks between merged toes.** A `paw` whose base slot differs from the part it sits on can show a speck or two of the parent's colour where three toes meet. Give the paw a slot of its own rather than relying on the leg's.
- **Claws are field points.** A paw's claws are grown in its own surface and painted; they cannot be separate hard parts or carry sockets.
- **No toes on paddles.** A paddle foot or flipper is a drawn `blob` or `sheet` with no digits.

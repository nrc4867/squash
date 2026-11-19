# Accessories

Rigid things a garment carries (shades, buttons, a buckle, heels, studs, a brim, a bow) are parts with `garment: "<id>"`: built only while that garment is worn, riding the bone of the part they sit on. Anything soft that lies on the cloth (a pocket, a stripe, a symbol, stitching) is a print instead (`clothing.md`).

## Placing one

- It sits on a **hand-made socket of the body part under it**: `socket(n, u, v, { sink: 0, outset })`. `sink: 0` seats it on the surface; on cloth, `outset` a little past the garment's `fit` stands it on the cloth instead of under it (0.016 for a fit of 0.012). Without the outset an owned part sits under the cloth and does not show.
- An owned part reads its socket on its parent as shown, so a heel's socket on a swapped foot lands on the shoe.
- **Two garments' parts never share a socket number on one part**: number each garment's sockets apart (the tee's 10 to 19, the coat's 20 to 29).
- `prop` and `roundProp` in the kit (`kit/README.md`, Props) make the part: `prop` for a drawn blob or tube with its own `rest`, `side` and `top`, `lean`, `outward`, `roll` and `topMiddle`; `roundProp` for a button or stud from a radius and depth.
- After the first build, `probe part=<id>` prints how long it came out once seated: a brim drawn 0.26 long can stick out much further than meant.

```ts
import { oval, prop, ringSocket, roundProp, socket, vToward } from "squash-kit";

export const buttonSocket = socket(10, 0.55, vToward("up", "forward"), { sink: 0, outset: 0.018, count: 3, to: 0.8 });
export const buttons = [0, 1, 2].map((i) => roundProp({ id: `button-${i}`, on: "torso", socket: `socket.10.${i}`, garment: "coat", color: "button", radius: 0.012, depth: 0.006 }));

export const studSockets = ringSocket(12, 0.42, 10, { sink: 0, outset: 0.024 });

/** One wrapping lens across the face: a socket at the face's middle (v 0 on head-round), at eye height. */
export const lensSocket = socket(20, 0.55, 0, { sink: 0, outset: 0.01 });
export const lens = prop({
  id: "lens",
  on: "head",
  socket: 20,
  garment: "shades",
  color: "lens",
  rest: "out",
  topMiddle: true,
  side: oval({ center: [0.02, 0], rx: 0.02, ry: 0.03 }),
  top: oval({ center: [0.02, 0], rx: 0.02, ry: 0.11 }),
});
```

- **A few in a row** along a part (three buttons down a placket) is one socket with `count` and `to`, named `socket.<n>.0` and on, a part on each; **a few round a part** (studs round a belt) is `ringSocket`, `v` 0 to `toV` 1.
- **Many along a line** (beads, studs, a long button row, chain links) is one part on one path socket, the part shown as copies along it: `pathSocket(n, [u, v], [to, toV], { count, via: [[u, v]], roll, sag })`, its axis along the surface's normal and its up along the path. `between: ["socket.5", "socket.6"]` runs it between two sockets of the part.
- **Rings** are the `ring` template, a closed loop with its hole along its rest axis, hanging from its socket by the inside of its top: an earring hoop is a ring on a socket at the lobe resting `out`; a finger ring a ring `orient`ed with its rest along the finger and its up toward the socket's side, sized so its inside meets the finger (`probe uv=` gives the finger's radius).
- **A chain** is a ring part with `stretch` about 1.6 on a path socket with no count: its links step by their inside length, so each hooks through the next, and `roll: 90` turns every other one. `sag` (body units) droops it between its ends, lying on the body where it would sink in.

```ts
import { pathSocket, socket } from "squash-kit";

torso.sockets.push(socket(5, 0.98, 0.8), socket(6, 0.98, 0.2), pathSocket(7, [0, 0], [0, 0], { between: ["socket.5", "socket.6"], via: [[0.92, 0]], roll: 90, sag: 0.05 }, { outset: 0.002 }));
export const chain = { id: "chain", template: "ring", on: "torso", socket: "socket.7", color: "gold", size: 0.32, params: { stretch: 1.6, thickness: 0.1 } };
```

Links much smaller than about 0.01 body units drown in their ink at full-figure size; judge them close (`close=chain`).
- **A lens** is a part in a palette slot with `opacity` 0.3 to 0.5 (`colour-and-marks.md`), so the eyes show through; `topMiddle: true` lets a Top drawn curved wrap it round the face. Which way Side's and Top's y face for a part resting `out` is in `frames.md`.
- **A bow** is a knot (a small `prop` blob) and loops and tails as `tube` props with a `curve`: draw the ribbon thin in Side and wide in Top with square ends, and run the loop's places out, up and back to the knot in `[along, up]`, so it bends across its thin side; a pair on a mirrored socket makes both loops. A warning that the rings narrow means the loop is tighter than the ribbon is wide that way.
- `roll` turns a part about its own axis (a sheet resting out and rolled 90 faces forward); `orient(along, up)` gives the `rest`, `lean`, `outward` and `roll` for any direction.

## Judging accessories

Close-ups by part id (`close=lens,buckle`) per outfit. **Edge-on accessories** (a badge, a brim, a lens seen as a thin line from the front) mean the part faces the wrong way: check its rest and roll against `frames.md`, and judge it from front, three-quarter and side. Check it stands on the cloth, not under it or floating off it, in a bent pose too.

## Limits

- **Rigid only.** An owned part rides one bone: nothing owned bends with a spine or drapes. A lapel lifts off the chest when the body bends.
- **A brim is a one-sided peak**: a blob resting forward. A brim all round a hat is not built.

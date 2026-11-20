# The body

The body is laid out in this order, and each step is built and read before the next: a height plan, the torso, a real neck, the legs, the arms, then the head, hands and feet on their joints. `kit/examples/mannequin.ts` is a whole biped laid out this way; read it for structure, never for numbers.

## 1. The height plan first

Every height comes from the sheet (`reading-a-reference.md`) into one `bodyPlan`: heights above the ground in body units, and half widths. Nothing below is placed by eye.

```ts
import { bodyPlan, heightMarks } from "squash-kit";

/** Heights above the ground and half widths in body units, from `kit sheet measure` of the front and side views (3 heads tall). */
export const plan = bodyPlan(
  {
    ankle: 0.03, knee: 0.14, crotch: 0.2, hip: 0.22, waist: 0.3, chest: 0.42,
    shoulder: 0.58, torsoTop: 0.62, headSeat: 0.66, chin: 0.67, crown: 1,
  },
  { hips: 0.15, waist: 0.15, shoulders: 0.13 },
);

/** Planned against built: `tools/recipe.sh kit heights <file>`. */
export const heights = heightMarks(plan, {
  hip: { part: "leg-l", joint: "base" },
  knee: { part: "leg-l", joint: "joint.1" },
  ankle: { part: "leg-l", joint: "tip" },
  shoulder: { part: "arm-l", joint: "base" },
  headSeat: { part: "neck", socket: "socket.1" },
});
```

Check the plan before drawing a part. The head, hands and feet are the proportions a toon figure is most often built too small: measure them off the sheet (`reading-a-reference.md`, Measure the hands, feet and face), declare them next to the plan as `targets: targets({ handLength: "0.38 head", footLength: "0.45 head", footWidth: "0.35 head" })` in the recipe, and fix every `FLAG` in `measure.txt` before judging the body by eye:

- **The head's share.** `(crown - chin) / crown` is one over the head count: 0.33 for 3 heads, 0.14 for 7. If the plan's share differs from the sheet's head count, a landmark is wrong.
- **Where the joints fall.** Shoulders sit just below the neck's base, a little under the chin on a short-necked creature; hips (the leg's pivot) a little above the crotch, inside the pelvis; knees about halfway from the hip to the ankle unless the sheet shows otherwise; elbows near the waist with the arms down, wrists near the crotch.
- **Widths.** The hips' half width keeps the thighs apart: the legs' sockets sit about half the hips' width out from the middle, and the thighs' radii must fit inside that, or the thighs touch and fuse.

After the first build, `tools/recipe.sh kit heights <file>` prints each mark planned against built. A difference means a socket sinks (below) or the feet moved the body: fix the socket, not the plan.

## 2. Tubes before templates

Where a tube fits, use a tube: torso, neck, arms, legs, a long tail, a snout. `tubePart` and `tube` (`kit/README.md`, Shapes and Parts) draw it from radii at stations, so its outline is exactly the sheet's widths at exactly the plan's heights. Use a template where the part needs what only a template has: a head with a face (`head-round`, `head`, `head-bird`), hands with fingers, feet or paws with toes, ears with a cup, curled tails (`tail-curve`). `templates.md` says what each holds; `docs/hld/parts.md` what it is built from.

On the `blank` plan the root is a tube resting up (`rest: "up"`): a standing body. Spans turn heights into a part's outline x and its sockets' and joints' u:

```ts
import { bodyPlan, joint, socket, tubePart, vToward } from "squash-kit";

const plan = bodyPlan({ crotch: 0.2, hip: 0.22, waist: 0.3, chest: 0.42, shoulder: 0.58, torsoTop: 0.62 }, { hips: 0.15 });
const torso = plan.upright("crotch", "torsoTop");
const stations = [0.2, 0.22, 0.26, plan.h("waist"), 0.36, plan.h("chest"), 0.5, plan.h("shoulder"), 0.61];

export const torsoPart = tubePart(
  {
    id: "torso",
    role: "body",
    color: "fur",
    sockets: [
      socket(1, 1, vToward("up", "forward"), { sink: 0 }),
      socket(2, torso.u(plan.h("shoulder")), vToward("up", "left"), { mirrored: true, sink: 0 }),
      socket(3, torso.u(plan.h("hip")), vToward("up", "left"), { mirrored: true }),
    ],
    joints: [joint(1, torso.u(plan.h("waist")), { low: -25, high: 25 }), joint(2, torso.u(plan.h("chest")), { low: -25, high: 25 })],
  },
  {
    rest: "up",
    length: torso.length,
    closed: true,
    at: stations.map(torso.x),
    profiles: {
      front: [0.08, 0.11, 0.13, 0.13, 0.135, 0.13, 0.11, 0.09, 0.05],
      back: [0.09, 0.115, 0.125, 0.12, 0.12, 0.12, 0.11, 0.09, 0.05],
      left: [0.1, plan.w("hips"), 0.15, 0.145, 0.15, 0.15, 0.13, 0.12, 0.06],
    },
  },
);
```

## 3. Every part in front and back

A tube's Side outline takes two profiles across its y and Top the other two (`kit/README.md`, `tube`): for a part resting up or down they are `front` and `back` in Side, `left`/`right` (or `outer`/`inner` on a pair) in Top; for one resting forward or back (an arm held out, a tail) `up` and `down` in Side and `left`/`right` in Top; for one resting out, `up` and `down` in Side and `front`/`back` in Top. **Give both sides of every profile**: the belly and the back, the chest and the shoulder blades, the shin and the calf, the seat. Front and back are set apart, so a round belly or a seat is just a larger front or back radius at its stations; a lone Top side is mirrored. Read the side view's grid for the front and back radii and the front view's for the left and right.

- A limb narrows and swells: thigh to knee to calf to ankle, shoulder to elbow to forearm to wrist. **A leg of one constant width with no knee** reads as a pipe; give the knee and ankle their narrowing and the calf its swell at the back.
- Use enough stations to hold the shape: 7 to 11 for a torso or leg, 6 to 9 for an arm or neck. `roundStations` gives stations bunched toward the ends of a round closed part (a head, a belly).
- One front section holds for the whole part (Front, where the template says `section`); a chest cannot have a different section from the waist. Draw the difference with the four profiles instead.

### Outlines drawn by hand

Where no tube fits (a templated torso, a muzzle, a blob), draw the outline yourself (`kit/README.md`, Shapes):

- A few `[x, y]` places a smooth curve passes through, `corners` the sharp ones (an ear tip, a tuft), `closed` for a part drawn all round (a torso, a head): 6 to 14 places for a torso or head, 3 to 9 for a limb, ear or tail. `bezier` gives the cubic points themselves (3n + 1) where exact handles matter.
- x runs along the part from its socket end **in body units, not 0 to 1**: the drawn length is the part's length (`size` scales it), and a templated part's own length is its "outline x runs 0 to" in `templates.md` (an ear 0.3, a leg 0.5). End a templated part's outline at that length with a flat end. y is across: Side sets height, Top width, Front (where the template's views say `section`) the cross-section for the whole part, drawn in -1 to 1.
- Which way Side's y points depends on the rest (`frames.md`): up for a part resting forward or back, forward for one resting up or down.

### Part fields

Besides `outlines`: `size`; `lean` and `outward` (each under 90; past that take another `rest`); `roll` (any angle about the part's own axis); `rest` (`forward`, `back`, `up`, `down`, `out`) in place of the template's; `blend`; `inkJoin`; `generic: { section, views, rings, segments }` for a `tube`, `blob` or `sheet` (`FINE` and `SMALL` in the kit; rings up to 96, segments up to 32); `jointsAt` and `socketsAt` to move a template's own joints and sockets (a socket's `[u, v]`, or `[u, v, sink]` to set how deep a part on it sits, times that part's own sink, 0 to 4); `landmarks`; `params` by name for template parameters and any dotted key without a field (`part-parameters.md`). `role` is the template's or guessed from where the part sits: give it for a generic part. A mirrored pair is one entry with `pair` (`{ ...pair("arm") }` names the sides `arm-l` and `arm-r`), described as its left side; a pair on a pair (hands on arms) names the parent pair's id in `on`, and each side sits on its own side. Elsewhere (poses, covers, expressions) a side is named by its own id.

## 4. A real neck

A neck is its own part between the torso's top and the head, never a head sunk into the shoulders. Even a creature with no visible neck has a short one: it lets the head turn, keeps the chin off the chest, and gives the collar of a garment somewhere to sit.

```ts
import { bodyPlan, socket, tubePart, vToward } from "squash-kit";

const plan = bodyPlan({ torsoTop: 0.62, headSeat: 0.66, neckTop: 0.68 }, { neck: 0.06 });
const neck = plan.upright("torsoTop", "neckTop");

export const neckPart = tubePart(
  {
    id: "neck",
    on: "torso",
    socket: "socket.1",
    role: "neck",
    color: "fur",
    inkJoin: false,
    sockets: [socket(1, neck.u(plan.h("headSeat")), vToward("up", "forward"), { sink: 0 })],
  },
  { rest: "up", length: neck.length, profiles: { front: [0.06, 0.055, 0.055], back: [0.065, 0.06, 0.058], left: [0.065, 0.058, 0.056] } },
);
```

The head goes on the neck's end socket at `sink: 0`, drawn from the neck's top (`head-and-face.md`).

## 5. Sockets, sinks and blends

- A part on a hand-made socket sinks into its parent by its own depth (`sink` 1, the default). `sink: 0` seats it on the surface. **A neck on the torso's top socket at the default sink drops the neck, the head and everything on it by the neck's depth**: give it `sink: 0`, and the head on the neck's end `sink: 0`. Shoulders take 0 to 0.25; hips the default (the leg starts inside the pelvis). `heights` shows every sink that moved a joint off the plan.
- **A leg swings about its own start**, so start it at the hip joint, inside the pelvis (a socket on the torso's side at hip height, sunk), not where the thigh leaves the pelvis's underside.
- Which way v faces depends on the rest (`frames.md`): on a part resting up v 0 faces forward and 0.75 the left; `vToward("up", "left")` says it for you. `probe part=<id>` prints every socket's body point.
- Joins are one surface: each part blends into its parent over its `blend` (body units). Left out, a limb, neck or tail rounds into its parent by 1.4 times its root radius, held back where it hangs close beside the parent so it does not web to it; leave it out on the neck, shoulders and hips. A head, hand or foot takes its template's join or a quarter of its least extent. The blend fills a crease where a part leaves its parent at an angle and leaves the two surfaces as they are where they run alongside each other, so a join never swells. A neck on the torso's closed top sits on the top's middle, rises out of the torso and flows into the shoulders, even where it is wider than the top: draw its front and back as the sheet shows them.
- **Shoulders read best when the torso is not widest exactly at the arm socket.** Put the torso's widest station a little below the shoulder socket (the chest) and let it narrow toward the top. The arm's top then leaves a shoulder that slopes away above it and meets it at a crease the blend rounds into a shoulder. Widest at the socket, the torso's side stands upright there and the arm's top runs alongside it, so nothing rounds it: the arm's width sits on top of the torso's widest point and the outline steps out at the shoulder line. `inkJoin: false` leaves a join unlined, which a body of one fur colour wants at the neck, shoulders and hips; **ink seams at the hips** of a smooth creature mean a join is still inked.
- `orient(along, up)` gives the `rest`, `lean`, `outward` and `roll` that point a part along any direction (a leg splayed out, a tail up and back).

## 6. Joints where the sheet puts them

Hand-made joints (`joint(n, u, { low, high })`) are where the part bends: knee, elbow, spine. Place each by the plan (`leg.u(plan.h("knee"))`), then check after the build:

- `probe part=leg-l,arm-l` prints every joint's body point: the knee at the sheet's knee height, the elbow at the elbow.
- **Hands and feet on their joints.** A hand sits on the arm's end socket at the wrist; a foot on the leg's end socket at the ankle. The foot's socket sits only a little above its sole (`feet-and-paws.md`), and the ground is under the lowest point, so the plan's `ankle` is that height, not where the sheet's leg meets the foot's top: take it from the first `heights` run (`ankle ... built`) and set the plan to it, or every height above lands low by the difference. A hand that starts mid-forearm means the arm's length or its end socket is off.
- Limits: a knee bends back, `low: 0, high: 140`; an elbow forward, `low: -145, high: 0` (signs in `frames.md`, Bone axes); a spine joint ±25.
- Limits also place the creases: a joint that bends much further one way (a knee, an elbow, a shoulder) shows a faint crease on that side at rest, and every joint creases on the side it closes toward as it bends (`clothing.md`, Creases and wrinkles). Draw no neck, collar, elbow, knee or ankle lines as marks: `creases: { skin: 1 }` draws them, and they deepen with the pose.

## 7. Rest and display pose

A biped is built in **A-pose**: arms 45 degrees out (`outward: 45`), legs straight down (`outward` about 1). It rests that way so the arms clear the body while building. The `displayPose` is how it is shown and saved:

```ts
import { bothSides, type BonePose } from "squash-kit";

export const displayPose: BonePose[] = [
  ...bothSides("arm", { joint: "base", lean: -28 }),
  ...bothSides("arm", { joint: "joint.2", degrees: -12 }),
];
export const poses: Record<string, BonePose[]> = {
  wave: [{ part: "arm-r", joint: "base", lean: 70, mode: "free" }, { part: "arm-r", joint: "joint.2", degrees: -90 }],
};
```

- The display pose brings the arms down to 15 to 28 degrees out from the hips with a slight bend at the elbow, the fingers a little curled (`hands.md`). Lean the arm's root bone (`base` on a tube, `shoulder` on the `arm` template). On a round-bellied body the arms hang out round the belly: lean less (-6 to -15) until `arm depth inside the torso as shown` in `measure.txt` is near 0; an arm pressed into the torso also shows as poke-through of the body through a tee's sleeves.
- A named pose (`poses`) is rendered as `<outfit>-pose-<name>.png`: each bone it names replaces the display pose's for that bone; bones it leaves out stay as the display pose holds them. Poses are not saved in the character.
- Which way each angle moves a bone, for every rest and hinge, is `frames.md`, Bone axes. Bend an elbow or knee by `degrees`, aim it with `lean`, roll a forearm with `twist` on the elbow, turn the whole body with `turn` on the root's `base`.
- A quadruped rests standing on its four legs; its display pose is usually empty.

## Limits

- **One hinge a joint.** A joint turns about one axis; `lean` and `turn` reach across it only to the nearer limit unless the bone is `mode: "free"`. A raised arm swings forward across the face, not out to the side, until the bone is freed.
- **One front section a part.** A bust, a pot belly or a broad chest over a narrow waist is drawn with the four profiles, never with a section that varies along the part.
- **Generic parts have no anatomy.** A tube or blob has no face, no digits and no stance: a head, hands, feet and paws stay templates.
- **Joins that run inside their parent** for most of the child's length still overlap: keep a child starting near its parent's surface (a short sink) rather than deep inside it.

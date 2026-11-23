# The body

The body is laid out in this order, and each step is built and read before the next: a height plan, the figure outline with the torso, a real neck, the legs and the arms fitted to it, then the head, hands and feet on their joints. `kit/examples/mannequin.ts` is a whole biped laid out this way; read it for structure, never for numbers.

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

## 2. The figure first

The body's tubes (torso, neck, arms, legs) are drawn as one **figure outline** and fitted, not tuned radius by radius (`kit/README.md`, Figure). Body first means drawing or adjusting the figure:

- **Draw the figure** from the sheet's front and side views: the front's left half and the side from the left, a few strokes in body units on the plan's ground (`readFigureOutline` of a file, or a `FigureOutline` in the character file), stopping at the chin, the wrists and the ankles, corners only where the sheet draws them sharp (a tight armpit), `mirrored` on the inner leg's end at the crotch. Draw it in the display pose the sheet shows, or at rest with `pose: []`.
- **Lay out the skeleton**: each body tube a `figurePart` with its rest, length, `outward` and joints, and its socket's u at the joint's height from the plan (`torso.u(plan.h("shoulder"))`), its v the side it leaves from. Give no profiles, no blend and no `reach` or `sink`: the fit reads the widths, the join's blend and where each root sits off the outline.
- **Read the build**: `figure: fit ... mean miss by round ...` in the run's output, and `renders/figure-front.png` and `figure-side.png`, which mark in red every stretch the built body misses the outline by more than an ink line. A miss is a drawing to change (a stroke, a corner, a place) or a skeleton fact (a joint height, a length, the pose it is drawn in), never a radius to tune.
- **An existing character**: `tools/recipe.sh figure <recipe.json>` traces its body into a figure outline and writes the recipe on it, as a start to draw over.

- **A pose that needs drawing**: where the strip's T-pose (or another pose) shows a shape skinning cannot give, such as a tick or a dip on the shoulder top, a boxy underarm or a deltoid too flat on a raised arm, add a **pose key** (`kit/README.md`, Pose keys) rather than reshaping the rest: `poseKey(name, { pose, outline: {} })`, build, copy the strokes to fix out of `key-<name>-posed.json`, redraw them few-pointed and smooth with only the change you want, read them in with `readFigureOutline`, build again. Check `key <name>: ... mean miss` and `key-<name>.txt`, then by eye: the strip's panel in the key's pose against the build without the key, a sweep of named poses toward it a few degrees apart (no pop), the dressed strip (the clothes follow), and the rest pose unchanged. One key a joint pose: an elbow and a knee bent about 90 degrees take `side` strokes, where their outer points show; an arm raised forward beside the T key's arm out to the side, both on one joint, blend between them, so judge poses between them as well. Start a stroke a little past a join it should leave alone (a shoulder stroke out from the neck), or the fit can leave a fold there. Then the walk (`close=walk`), bare and dressed.

Fall back to the radii below for a tube the figure does not hold: a tail, a snout, a horn.

## 3. Tubes before templates

Where a tube fits, use a tube: torso, neck, arms, legs, a long tail, a snout. `tubePart` and `tube` (`kit/README.md`, Shapes and Parts) draw it from radii at stations, so its outline holds the widths you chose at the plan's heights. Use a template where the part needs what only a template has: a head with a face (`head-round`, `head`, `head-bird`), hands with fingers, feet or paws with toes, ears with a cup, curled tails (`tail-curve`). `templates.md` says what each holds; `docs/hld/parts.md` what it is built from.

On the `blank` plan the root is a tube resting up (`rest: "up"`): a standing body. Spans turn heights into a part's outline x and its sockets' and joints' u:

```ts
import { bodyPlan, joint, socket, tubePart, vToward } from "squash-kit";

const plan = bodyPlan({ crotch: 0.2, hip: 0.22, waist: 0.3, chest: 0.42, shoulder: 0.58, torsoTop: 0.62 }, { hips: 0.15 });
const torso = plan.upright("crotch", "torsoTop");
const stations = [0.21, 0.25, plan.h("waist"), 0.36, plan.h("chest"), 0.5, 0.57];

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
      front: [0.09, 0.12, 0.125, 0.13, 0.135, 0.11, 0.06],
      back: [0.1, 0.12, 0.115, 0.115, 0.12, 0.11, 0.06],
      left: [0.11, plan.w("hips"), 0.14, 0.145, 0.15, 0.13, 0.08],
    },
  },
);
```

## 4. Every part in front and back

A tube's Side outline takes two profiles across its y and Top the other two (`kit/README.md`, `tube`): for a part resting up or down they are `front` and `back` in Side, `left`/`right` (or `outer`/`inner` on a pair) in Top; for one resting forward or back (an arm held out, a tail) `up` and `down` in Side and `left`/`right` in Top; for one resting out, `up` and `down` in Side and `front`/`back` in Top. **Give both sides of every profile**: the belly and the back, the chest and the shoulder blades, the shin and the calf, the seat. Front and back are set apart, so a round belly or a seat is just a larger front or back radius at its stations; a lone Top side is mirrored. Read the side view's grid for the front and back radii and the front view's for the left and right.

- A limb narrows and swells: thigh to knee to calf to ankle, shoulder to elbow to forearm to wrist. **A leg of one constant width with no knee** reads as a pipe; give the knee and ankle their narrowing and the calf its swell at the back.
- One front section holds for the whole part (Front, where the template says `section`); a chest cannot have a different section from the waist. Draw the difference with the four profiles instead.

### Smooth profiles

The outline runs through every station's radius exactly, its direction carried on from the stations either side. So it is only as smooth as the numbers: a radius a little above or below the line its neighbours make puts a bend there, and ten measured radii give a side that wobbles in and out like a hand-traced line. A bare body is smoother than its sheet: the garments carry the folds.

- **Stations go where the shape turns, not at every measurement.** Each profile of a torso holds 5 to 7 stations, a leg 5 to 7, an arm 4 to 6, a neck 3: one at each widest or narrowest place (seat, hip, waist, chest, knee, calf, elbow, wrist) and as few between as keep the gaps even. `roundStations` gives stations bunched toward the ends of a round closed part (a head, a belly); use it only there.
- **Between two features a radius only grows or only shrinks.** From the hip it narrows to the waist, from the waist it widens to the chest, and it narrows from the chest to the top. A radius that turns back in between (0.10, 0.104, 0.096, 0.098, 0.10) is measuring noise or a fold of the clothes: drop it, or set it on the line between its neighbours.
- **Change gently.** Between neighbouring stations a radius changes by no more than about a third. A hip of 0.135 two stations below a waist of 0.088 is a corner, not a curve: move the waist station up, or narrow the hip, until the side reads as one sweep.
- **Even gaps.** No gap between stations under half the gap beside it: a station crowded next to another turns the outline sharply there.
- **Meet the next part's width.** A leg's last radius matches the foot's width where the foot starts, and an arm's the hand's: a foot wider than the ankle shows as a step in the front view. Taper the limb toward that radius, and narrow the foot's heel in its Top outline if it still steps.
- **Every profile, both views.** On a torso resting up, `left` and `right` are its half width (the front view's outline) and `front` and `back` its half depth (the side view's): smooth all four, each with the same stations.
- **Room at a closed end.** On a closed part (a torso, a head) keep the last station well short of the end, about its own radius (0.05 below a top whose last radius is 0.09), so the top rounds over. A last station a hair short of the end makes the top a flat shelf with a corner at the shoulder. The shoulder socket sits at its own height whatever the stations; it may fall on or just below the last station. A short shoulder-to-top span still takes one station below the shoulder and one at it. This is the top only: the torso's first station stays at the crotch, where a skirt or shorts cover starts from it.
- Check the bare front, ¾ and side before any garment: each side of the torso reads as one curve out at the hip, in at the waist, out at the chest and over the shoulder, and each limb tapers with no ripple. A ripple is a station to drop; a corner is two radii too far apart. For a quick look at the profiles alone, build with `outfits=bare close=none measure=false`.

### Outlines drawn by hand

Where no tube fits (a templated torso, a muzzle, a blob), draw the outline yourself (`kit/README.md`, Shapes):

- A few `[x, y]` places a smooth curve passes through, `corners` the sharp ones (an ear tip, a tuft), `closed` for a part drawn all round (a torso, a head): 6 to 14 places for a torso or head, 3 to 9 for a limb, ear or tail. `bezier` gives the cubic points themselves (3n + 1) where exact handles matter.
- x runs along the part from its socket end **in body units, not 0 to 1**: the drawn length is the part's length (`size` scales it), and a templated part's own length is its "outline x runs 0 to" in `templates.md` (an ear 0.3, a leg 0.5). End a templated part's outline at that length with a flat end. y is across: Side sets height, Top width, Front (where the template's views say `section`) the cross-section for the whole part, drawn in -1 to 1.
- Which way Side's y points depends on the rest (`frames.md`): up for a part resting forward or back, forward for one resting up or down.

### Part fields

Besides `outlines`: `size`; `lean` and `outward` (each under 90; past that take another `rest`); `roll` (any angle about the part's own axis); `rest` (`forward`, `back`, `up`, `down`, `out`) in place of the template's; `blend`; `inkJoin`; `generic: { section, views, rings, segments }` for a `tube`, `blob` or `sheet` (`FINE` and `SMALL` in the kit; rings up to 96, segments up to 32); `jointsAt` and `socketsAt` to move a template's own joints and sockets (a socket's `[u, v]`, or `[u, v, sink]` to set how deep a part on it sits, times that part's own sink, 0 to 4); `landmarks`; `params` by name for template parameters and any dotted key without a field (`part-parameters.md`). `role` is the template's or guessed from where the part sits: give it for a generic part. A mirrored pair is one entry with `pair` (`{ ...pair("arm") }` names the sides `arm-l` and `arm-r`), described as its left side; a pair on a pair (hands on arms) names the parent pair's id in `on`, and each side sits on its own side. Elsewhere (poses, covers, expressions) a side is named by its own id.

## 5. A real neck

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

### The neck style

Pick the character's `neck` from the sheet before shaping the neck and the head's underside (`docs/hld/look.md`, Necks):

- **`blended`** (the default) where the sheet shows no neck: the head runs into the shoulders in one curve.
- **`between`** where a short neck shows under a soft jaw line: the jaw's outline flows into the neck's sides and a light shade lies under the chin.
- **`defined`** where the sheet draws the head's outline closed across a slim neck, often with a shade under the chin.

For `between` and `defined`, leave the head's `blend` and `inkJoin` out: the style sets them, and a value of your own wins over it. Then shape it by eye against the sheet's front, three-quarter and back views:

- **Draw the head closed under the jaw.** Its Side and Top outlines start at radius 0 at x 0, so its underside is round, not a stalk that narrows into the neck.
- **Run the neck up into the head.** The neck's top (`neckTop`) sits well above the head's seat, so the neck stays inside the head whichever way it turns; the head's seat is the chin.
- **Show the neck.** The visible neck is the stretch between the chin and the shoulder line on the sheet: raise the head's seat (and the crown with it, keeping the head's size) or lower the shoulders until it shows. A neck the jaw hides shows no jaw line and no shade.
- **Width and set-back.** Read the neck's width off the front view; its socket on the torso and the head's socket on it (v forward, on the neck's front) set how far it sits back under the head. The jaw line across the neck shows only where the head's outline lies in front of the neck.

Judge it in the head close-ups (`close=head`: front, three-quarter, side, back, above, below), the strip, and named poses turning and nodding the head (`{ part: "head", joint: "neck", turn: 40, mode: "free" }`, `degrees: 20` and `-20`): the jaw line crosses the neck in front, the joins never step, the shade under the chin is one clean shape.

## 6. Sockets, sinks and blends

- A part on a hand-made socket sinks into its parent by its own depth (`sink` 1, the default). `sink: 0` seats it on the surface. **A neck on the torso's top socket at the default sink drops the neck, the head and everything on it by the neck's depth**: give it `sink: 0`, and the head on the neck's end `sink: 0`. Shoulders take 0 to 0.25; hips the default (the leg starts inside the pelvis). `heights` shows every sink that moved a joint off the plan.
- **A leg swings about its own start**, so start it at the hip joint, inside the pelvis (a socket on the torso's side at hip height, sunk), not where the thigh leaves the pelvis's underside.
- Which way v faces depends on the rest (`frames.md`): on a part resting up v 0 faces forward and 0.75 the left; `vToward("up", "left")` says it for you. `probe part=<id>` prints every socket's body point.
- Joins are one surface: each part blends into its parent over its `blend` (body units). Left out, a limb, neck or tail rounds into its parent by 1.4 times its root radius, held back where it hangs close beside the parent so it does not web to it; leave it out on the neck, shoulders and hips. A head, hand or foot takes its template's join or a quarter of its least extent. The blend fills a crease where a part leaves its parent at an angle and leaves the two surfaces as they are where they run alongside each other, so a join never swells. A neck on the torso's closed top sits on the top's middle, rises out of the torso and flows into the shoulders, even where it is wider than the top: draw its front and back as the sheet shows them.
- **Shoulders read best when the torso is not widest exactly at the arm socket.** Put the torso's widest station a little below the shoulder socket (the chest) and let it narrow toward the top. The arm's top then leaves a shoulder that slopes away above it and meets it at a crease the blend rounds into a shoulder. Widest at the socket, the torso's side stands upright there and the arm's top runs alongside it, so nothing rounds it: the arm's width sits on top of the torso's widest point and the outline steps out at the shoulder line. An arm on a shoulder socket at `sink: 0` leaves its flat root standing proud of the torso: a nub where the arm's top meets the shoulder line. Three settings together make the shoulder one curve from the neck over the arm: the torso's widest station a little below the shoulder socket and narrowing toward the top, the shoulder socket at `sink` 0.3 to 0.4, and the arm at `blend` 0.05 (or its default). A narrower top pulls the arm sockets inward, since they sit on the surface: keep the shoulders' width by widening the top station rather than sinking the socket less. A larger arm blend (0.08) shelves the shoulder and webs the armpit. `inkJoin: false` leaves a join unlined, which a body of one fur colour wants at the neck, shoulders and hips; **ink seams at the hips** of a smooth creature mean a join is still inked.
- `orient(along, up)` gives the `rest`, `lean`, `outward` and `roll` that point a part along any direction (a leg splayed out, a tail up and back).

## 7. Joints where the sheet puts them

Hand-made joints (`joint(n, u, { low, high })`) are where the part bends: knee, elbow, spine. Place each by the plan (`leg.u(plan.h("knee"))`), then check after the build:

- `probe part=leg-l,arm-l` prints every joint's body point: the knee at the sheet's knee height, the elbow at the elbow.
- **Hands and feet on their joints.** A hand sits on the arm's end socket at the wrist; a foot on the leg's end socket at the ankle. The foot's socket sits a little above its sole (`feet-and-paws.md`: a drawn foot's sole is at minus the plan's `ankle` in its Side), and the ground is under the lowest point, so the plan's `ankle` is that height, not where the sheet's leg meets the foot's top: take it from the first `heights` run (`ankle ... built`) and set the plan to it, or every height above lands low by the difference. A hand that starts mid-forearm means the arm's length or its end socket is off.
- Limits: a knee bends back, `low: 0, high: 140`; an elbow forward, `low: -145, high: 0` (signs in `frames.md`, Bone axes); a spine joint ±25.
- Limits also place the creases: a joint that bends much further one way (a knee, an elbow, a shoulder) shows a faint crease on that side at rest, and every joint creases on the side it closes toward as it bends (`clothing.md`, Creases and wrinkles). Draw no neck, collar, elbow, knee or ankle lines as marks: `creases: { skin: 1 }` draws them, and they deepen with the pose.

## 8. Rest and display pose

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

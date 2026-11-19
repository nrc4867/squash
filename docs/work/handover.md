# Handover: the one surface, where it stands

Every character builds as one surface; `docs/hld/surface.md` is the design. This note says what the examples are, what is open and what comes next. The measurements behind the speed work, and the build rounds' results, are in the git history of `docs/work/one-surface.md`.

## Examples

| File | What |
|---|---|
| `cat.json` | A ginger tabby on all fours on the canine plan, from its first command: mitten paws (`paw` 5, four toes merged at 1.4, claws drawn in, beans), stripes and tail rings as regions, white chest, muzzle and paws, painted green eyes with slit pupils, whiskers and mouth as mirrored marks, the tail curled up in the display pose, a walk in four moments |
| `cat-dressed.json` | The cat in Vampire (a cape drawn high over the shoulders, flaring over the hips and trailing past the rump, open under the belly, a collar the head comes up through, a slit for the tail; fangs inked by a garment that redraws the head's surface) and Hotdog (a round sausage with a mustard stroke painted along its top, in a puffy bun open over the back, the legs coming out under it) |
| `fennec*.json`, `woman*.json` | One surface, painted eyes |
| `koi.json` | One surface: the body in the field, its fins and tail fin thin and separate; painted gold eyes with a dark iris; named poses `swim-0` to `swim-3` and `rise` |
| `lizard.json` | One surface: body, head, legs, paddle feet and tail in the field, the tail's join widened and unlined; painted yellow eyes; named poses `walk-0` to `walk-3` and `bask` |
| `toucan.json` | One surface: torso, head, legs and toes in the field, the beak's halves hard and the tail and wings thin, separate; painted eyes in their pale rings; named poses `walk-0` to `walk-3` and `call` |
| `dragon.json` | One surface: torso, head, tail, legs and clawed paws in the field, the membrane wings thin and the horns and spikes hard, separate; painted amber slit eyes; named poses `walk-0` to `walk-3` and `roar` |
| `woman-twin*` | One surface, painted eyes matched to her drawn ones, fingers and toes at the digit cell; her hair a `hair-cap` in the field and a fall and two locks as `hair` parts, the locks ending above the shoulder ("Twin results") |

Every example builds on one surface.

## Where speed stands

The winter fennec's front-leg move settles in about 78 ms, the woman's arm in about 114, a knee pose drag in about 58. Every dressed character's move waits on the garments' work other than field reads (done about 85 to 88 ms in), so GPU reads cut CPU time by 16 to 25% and leave a move's time as it was; the GPU's renders differ from the CPU's in 87 of 147 images, by a few pixels where ties break apart. The bare cat's front-leg move is about 142 ms against the fennec's 107, and the dragon's about 141, from digits at a quarter cell and, for the cat, a display pose that poses the whole surface again after every edit. Digits at a quarter cell add 43 to 77% to a fresh build of a character with digits and about 37 to 60 ms to a bare front-leg or dressed arm move.

Levers not built:

- Finishing per block: shading smooths over 3 and 16 steps along the quads' sides, so a vertex depends on everything within 19 rings; keeping it per block needs that neighbourhood unchanged, plus the contour edge map and join curves kept per block. The finishing is 7 to 11% of samples.
- Garment vertices placed from the body's placed vertex rather than a line search; with fur left out it brings the winter fennec to 52 ms and leaves the woman at 116.
- Reading less: the channels' exact masks are per sub-block and are what lending to garments and kept channels trust.
- The whole placing on the GPU in one kernel, the CPU's pair grid on the GPU, and reads outside the mesher (coverage for the hidden skin and the cut, anchors), which read one point at a time on the CPU.
- For pose drags, the full posed lines nothing draws while dragging.

## Open items carried

Look:

- The woman's winter scarf rises up the neck as a cowl, a sliver of the top showing under the coat's collar: the scarf's torso branch stands off the coat's and the top's eases as well as its own, 0.092 in all, over the torso's closed end round the neck, where the coat is open and cut short. A stand-off that holds only where the layers under it reach is not built.
- The fennec cap's ear cuff is ragged from the side.
- The woman after a release shows lines across the forehead: not reproduced in `look` renders of an arm's Move drag (each of its moves, the release, at rest, undo and redo, on CPU and GPU reads); not the same as the cat's nose-bridge lines, which were a whisker mark.
- The cat's painted eyes sit near the nose from the side: the eye's anchor lies on the skull's front where the muzzle joins it; moved back it leaves the front view.
- The cat's garments: an open edge is a cut with no rolled rim, so the bun's halves read as one shell cut open; a drawn range running past its drawing leaves a speck; a garment's marks are not drawn; a cape's flanks ride the spine and are not re-hung toward world down when a pose tilts the body.
- Paw specks where three runs meet on the cat (the example-character skill's `feet-and-paws.md`, Limits).
- The lizard has paddle feet with no toes or claws; the koi's tail fin shows two small ink marks near its root; in the lizard's and the dragon's foot close-ups the view from above sits inside the leg and shows its end through it.
- The dragon's claws are field points of `paw` 5, not separate hard parts, which would need a tip socket on it; the roar's open jaw does not show in the renders.
- Hair: the cap is the drawn crown in the field, not grown from the head; the fall has no weight, so it turns with the head rather than hanging; the push reads the parts' own posed meshes, so it is clear of the field within their meshes' error (0.004 in the test). The clumps part a little at the hem. The dressed woman's middle fall clump hangs in front of her face in every outfit, from the front, three-quarter and side; the bare woman's hangs down her back.
- The twin: her compare IoU is reported, not a gate; a hanging skirt shades dark below its upper band, as the woman's does; her painted brows take the ink colour, not her brown brow slot; her locks are shorter than the reference's; a small notch shows under the chin in profile, where the jaw's blend meets the neck; the dress's puffed sleeves are plain.
- A hand's palm and the rest at the body's cell show a slightly stepped outline beside the finer fingers.

Behaviour:

- An edited surface can differ from a fresh one where a field is steeper than `LIPSCHITZ` (no real character is).
- Painted eyes are not driven by the Pose stage's look-at tool; their gaze is set on the eye.
- Posed surface work is carried by the nearest vertex at rest, not by the field in the pose.
- A region whose shapes span several parts is laid as each part's pieces kept to their sections (no example has one).
- A pose that carries skin out through a garment's wall is not tested again beyond what coverage gives.
- Template versions that do not mesh closed and manifold on one surface are listed in `EveryVersionSurfaceTest`, each kept as it ships.
- The Face board needs Nicholas's verification.

## Adaptive cells

Digits (a part's row of toes or fingers) mesh at a quarter of the body's cell through `AdaptiveMesher`, closed and manifold across each change, every other run at the body's cell (`docs/hld/architecture.md`, Cell levels). The fennec's toes and the woman's fingers read as separate digits; the cat's mittens still read as mittens.

## Next, in order

1. Phase 6, joints, only if the walk sheets need it: implicit skinning (`docs/hld/surface.md`, The rig on the surface).

## How rounds are run

- One worktree per round under `.worktrees/`, one agent in it, no sub-agents; land as one squashed commit, never push.
- One Gradle build at a time; the machine ran out of memory with several.
- Light timing while iterating, the full table once at the end; the fast tier of the touched modules while working (`:<module>:test`, or `:<module>:desktopTest` in a multiplatform module, `./gradlew checkFast` for all of them), the full tier, `./gradlew check`, once before landing on main.
- At rest every example must stay identical to main in RGB (`same.py` compares all channels).

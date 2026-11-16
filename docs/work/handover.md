# Handover: one surface, where it stands

The one-surface plan is `docs/work/one-surface.md`; its results sections hold every measurement and open item. This note says what is on main and what comes next.

## On main

| Commit | What |
|---|---|
| 1a58451 | One surface per character: the dual mesher, parts as coordinates, painted eyes, garments as the grown field; the fennec, dressed fennec and winged fennec moved |
| 5f3bd3c | Phase 3: every garment worn on one surface (layers, cuffs, holes, open ends, pieces, hanging flare, posed hiding, owned parts); the person and the woman moved with painted eyes matched to their drawn ones |
| bf70169 | Speed round 2: coverage kept at unchanged vertices, single-threaded steps spread; `measure` prints median, 90th percentile and worst on fresh sizes |
| b01ca55 | The simpler Face eye screen: handles on the panes, Roundness, Pupil and Colors, the rest under More; the Face board (W5-Face in Squash flow of work · restructure) redrawn, state verifiable |
| 047eebb | Drag feel and speed: a drag's whole stack coarse, recent full builds kept for undo, redo and outfit switches; each surface shown only with its own paint and dress |
| 8c603e5 | Pose drags: the coarse surface posed without the posed hiding, lined by outlines only, posing in parallel |
| after 8c603e5 | Field reads through the `FieldReads` seam: `CpuFieldReads`, and `GpuFieldReads` in `field-gpu`; the app reads on the CPU unless launched with `-Preads=gpu` |
| creatures-1 | The koi and the lizard on one surface with painted eyes, `fin` and `sheet` marked separate; `PartRoles.upright` reads the root's Move rest, and a limb's chain ends where its hand or paw hangs, so the woman's Pose rows name her feet and a picked hand curls ("Koi and lizard results") |
| creatures-2 | The toucan and the dragon on one surface with painted eyes; `beak`, `beak-lower`, `tail-feathers`, `wing-membrane`, `horn` and `spike` marked separate; named poses for both ("Toucan and dragon results") |
| hair-shell | Hair as parts: `Role.HAIR`, the `hair-cap` and `hair` templates, hair weighted to its own chain and the head alone, pushed out of the body in each pose; the woman and the dressed woman wear a cap, a fall of five clumps and two locks ("Hair shell results") |
| twin-surface | The twin and the dressed twin on one surface with painted eyes, her hair a cap in the field and a fall and locks as hair parts; every example is now on one surface ("Twin results") |
| garments-2 | Open garment branches read their drawings and a range past their part's ends as shells cut by their v range; a Front outline is a branch's section; a drawn end past a level end closes; cloth hangs and flares only past an end that points down, and open shells are pushed out of legs and tail; a garment's regions and brush strokes are painted. The cat's Vampire (free cape, collar, trail) and Hotdog (round sausage, painted mustard, puffy bun) redone ("Garments 2 results") |

## Examples

| File | What |
|---|---|
| `cat.json` | A ginger tabby on all fours on the canine plan, built as one surface from its first command: mitten paws (`paw` 5, four toes merged at 1.4, claws drawn in, beans), stripes and tail rings as regions, white chest, muzzle and paws, painted green eyes with slit pupils, whiskers and mouth as mirrored marks, the tail curled up in the display pose, a walk in four moments |
| `cat-dressed.json` | The cat in Vampire (a cape drawn high over the shoulders, flaring over the hips and trailing past the rump, open under the belly, a collar the head comes up through, a slit for the tail; fangs inked by a garment that redraws the head's surface) and Hotdog (a round sausage with a mustard stroke painted along its top, in a puffy bun open over the back, the legs coming out under it) |
| `fennec*.json`, `woman*.json` | One surface, painted eyes |
| `koi.json` | One surface: the body in the field, its fins and tail fin thin and separate; painted gold eyes with a dark iris; named poses `swim-0` to `swim-3` and `rise` |
| `lizard.json` | One surface: body, head, legs, paddle feet and tail in the field, the tail's join widened and unlined; painted yellow eyes; named poses `walk-0` to `walk-3` and `bask` |
| `toucan.json` | One surface: torso, head, legs and toes in the field, the beak's halves hard and the tail and wings thin, separate; painted eyes in their pale rings; named poses `walk-0` to `walk-3` and `call` |
| `dragon.json` | One surface: torso, head, tail, legs and clawed paws in the field, the membrane wings thin and the horns and spikes hard, separate; painted amber slit eyes; named poses `walk-0` to `walk-3` and `roar` |
| `woman-twin*` | One surface, painted eyes matched to her drawn ones, fingers and toes at the digit cell; her hair a `hair-cap` in the field and a fall and two locks as `hair` parts, the locks ending above the shoulder ("Twin results") |

Every example builds on one surface.

## Where speed stands

Measurements are in "Drag feel results", "Drag speed results" and "GPU reads results" in `one-surface.md`. The winter fennec's front-leg move settles in about 78 ms, the woman's arm in about 114, a knee pose drag in about 58. Every dressed character's move waits on the garments' work other than field reads (done about 85 to 88 ms in), so GPU reads cut CPU time by 16 to 25% and leave a move's time as it was; the GPU's renders differ from the CPU's in 87 of 147 images, by a few pixels where ties break apart.

## Open items carried

- The woman's winter scarf rises up the neck as a cowl; a sliver under the coat collar.
- The fennec cap's ear cuff is ragged from the side.
- An edited surface can differ from a fresh one where a field is steeper than `LIPSCHITZ` (no real character is).
- A drag's later moves: fur left out and garment vertices placed without a line search together bring the winter fennec to 52 ms and leave the woman at 116 ("Drag speed results"); neither is built.
- Dressed moves are over 50 ms; the levers left are the garments' work other than reads (meshing, finishing, cuts, cuffs, the hanging push) and, for pose drags, the full posed lines nothing draws while dragging.
- The woman after a release shows lines across the forehead.
- Painted eyes are not driven by the Pose stage's look-at tool.
- The Face board needs Nicholas's verification.
- The cat's garments: an open edge is a cut with no rolled rim, so the bun's halves read as one shell cut open; a drawn range running past its drawing leaves a speck; a garment's marks are not drawn; a cape's flanks ride the spine and are not re-hung toward world down when a pose tilts the body ("Garments 2 results").
- The cat's moves are slow for one surface: a front-leg move about 142 ms bare against the fennec's 107, from its mitten paws' quarter-cell digits (about 29 ms more meshing) and its tail-curling display pose, which poses the whole surface again after every edit ("Garments 2 results").
- The dragon's moves are slow for one surface: a front-leg move about 141 ms, a knee pose drag 61; its claws are paw 5's field points, not separate parts ("Toucan and dragon results").
- The lizard has paddle feet with no toes or claws; the koi's tail fin shows two small ink marks near its root ("Koi and lizard results").
- Hair: the cap is drawn, not grown from the head; the fall has no weight, so it turns with the head rather than hanging ("Hair shell results").
- The dressed woman's middle fall clump hangs in front of her face in every outfit, from the front, three-quarter and side; the bare woman's hangs down her back.
- The twin: her compare IoU is reported, not a gate ("Twin results"); a hanging skirt shades dark below its upper band, as the woman's does; her painted brows take the ink colour, not her brown brow slot.

## Adaptive cells

Digits (a part's row of toes or fingers) mesh at a quarter of the body's cell through `AdaptiveMesher`, closed and manifold across each change, every other run at the body's cell (see "Adaptive cells results"). The fennec's toes and the woman's fingers read as separate digits; the cat's mittens still read as mittens. It costs 43 to 77% on a fresh surface build of a character with digits and about 37 to 60 ms on a bare front-leg or dressed arm move.

## Next, in order

1. Phase 5. Every example now builds on one surface, so nothing an example shows still needs the lofted build of body parts. What phase 5 involves:
   - One surface by default: a new character starts on it (the app never sends `SetOneSurface`, so a character made in New character builds as parts), and the switch, `Character.oneSurface`, `asParts()`, `look surface=parts` and the `fennec-one-surface*` fixtures go.
   - Format 15 and its migration ("Migration, format 14 to 15"): field versions of the body templates, `fillet` dropped, `PartInstance.blend` absent, drawn eye groups kept on documents that have them.
   - The examples written again under format 15 from their commands.
   - Retiring what "What retires" lists (strips, socket and garment fillets, the grid-preserving blend, recasting, seated eyeballs, the eye `oval` and `lid` blocks, handed cuts, fixed topology for body parts and its mesh goldens), with the tests that only cover them; the lofted build stays for separate parts.
   - The HLD rewritten ("Docs that change if this is approved"), CLAUDE.md's rules on fixed topology and mesh goldens, and `example-limits.md` brought up to date.
   - Done when no body part builds lofted and `check` and `check -Pslow` pass.
2. Phase 6, joints, only if the walk sheets need it.

## How rounds are run

- One worktree per round under `.worktrees/`, one agent in it, no sub-agents; land as one squashed commit, never push.
- One Gradle build at a time; the machine ran out of memory with several.
- Light timing while iterating, the full table once at the end; module tests while working, `./gradlew check` once at the end.
- At rest every example must stay identical to main in RGB (`same.py` compares all channels).

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
| hair-shell | Hair as parts: `Role.HAIR`, the `hair-cap` and `hair` templates, hair weighted to its own chain and the head alone, pushed out of the body in each pose; the woman and the dressed woman wear a cap, a fall of five clumps and two locks ("Hair shell results") |

## Examples

| File | What |
|---|---|
| `cat.json` | A ginger tabby on all fours on the canine plan, built as one surface from its first command: mitten paws (`paw` 5, four toes merged at 1.4, claws drawn in, beans), stripes and tail rings as regions, white chest, muzzle and paws, painted green eyes with slit pupils, whiskers and mouth as mirrored marks, the tail curled up in the display pose, a walk in four moments |
| `cat-dressed.json` | The cat in Vampire (a cape over the back and flanks, open under the chest, its collar the cuff the head comes up through; fangs inked by a garment that redraws the head's surface) and Hotdog (a sausage over the body, a bun open over the back, a zigzag of mustard beads the sausage owns) |
| `fennec*.json`, `woman*.json` | One surface, painted eyes |
| `koi.json` | One surface: the body in the field, its fins and tail fin thin and separate; painted gold eyes with a dark iris; named poses `swim-0` to `swim-3` and `rise` |
| `lizard.json` | One surface: body, head, legs, paddle feet and tail in the field, the tail's join widened and unlined; painted yellow eyes; named poses `walk-0` to `walk-3` and `bask` |
| `dragon`, `toucan`, `woman-twin*` | Parts |

Dragon, toucan and the twin still build as parts and render as before the one surface landed (checked in RGB against 6fdbc35).

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
- The cat's garments are what a body on all fours can wear today ("Creatures" in `example-limits.md`): no cape hangs free and no bun has its own shape, since an open branch takes neither drawn outlines nor a run past its part's ends, and a hanging branch on a torso resting forward flares into a sheet; the mustard is beads, since a garment's own marks and regions are not painted.
- The cat's moves are slow for one surface: a front-leg move about 140 ms bare, 170 in Vampire and 250 in Hotdog; a knee pose drag 39, 126 and 191.
- The lizard has paddle feet with no toes or claws; the koi's tail fin shows two small ink marks near its root ("Koi and lizard results").
- Hair: the cap is drawn, not grown from the head; the fall has no weight, so it turns with the head rather than hanging; the twin still wears blob and tube hair ("Hair shell results").

## Next, in order

1. Phase 4, creatures: adaptive cells for toes, fingers and claws; the dragon, toucan and the twin onto one surface; the twin's compare IoU must not drop.
2. Phase 5: format 15 migration, the parts build and drawn eyes retired, the HLD rewritten.
3. Phase 6, joints, only if the walk sheets need it.

## How rounds are run

- One worktree per round under `.worktrees/`, one agent in it, no sub-agents; land as one squashed commit, never push.
- One Gradle build at a time; the machine ran out of memory with several.
- Light timing while iterating, the full table once at the end; module tests while working, `./gradlew check` once at the end.
- At rest every example must stay identical to main in RGB (`same.py` compares all channels).

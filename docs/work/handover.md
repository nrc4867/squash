# Handover: one surface, where it stands

The one-surface plan is `docs/work/one-surface.md`; its results sections hold every measurement and open item. This note says what is on main, what is on this branch, and what comes next.

## On main

| Commit | What |
|---|---|
| 1a58451 | One surface per character: the dual mesher, parts as coordinates, painted eyes, garments as the grown field; the fennec, dressed fennec and winged fennec moved |
| 5f3bd3c | Phase 3: every garment worn on one surface (layers, cuffs, holes, open ends, pieces, hanging flare, posed hiding, owned parts); the person and the woman moved with painted eyes matched to their drawn ones |
| bf70169 | Speed round 2: coverage kept at unchanged vertices, single-threaded steps spread; `measure` prints median, 90th percentile and worst on fresh sizes |
| b01ca55 | The simpler Face eye screen: handles on the panes, Roundness, Pupil and Colors, the rest under More; the Face board (W5-Face in Squash flow of work · restructure) redrawn, state verifiable |

Dragon, koi, lizard, toucan and the twin still build as parts and render as before the one surface landed (checked in RGB against 6fdbc35).

## On this branch, `drag-feel`, not landed

- A drag timing harness through the app's graph: each pointer move until settled, release, undo, redo and outfit switches, with the nodes each ran.
- `-Popen` reads a relative path from the project's root.
- A few recent full builds of the stack kept, so undo, redo and an outfit worn again take them as they were.
- Unseen skin of a drag left until its end; the full surface kept through a pose drag; posed hiding found in parallel.
- `look close=release`: a forelimb Move drag rendered open, after release, and as a fresh session builds the released document.

While a drag is open the whole stack is coarse: the body meshes at twice the cell and the garments, paint, eyes, hidden skin and surface work follow its mesh. The measurements, the identity check and the release check are in "Drag feel results" in `one-surface.md`. In short: the winter fennec's front-leg move settles in about 78 ms (310 on main), the woman's arm in about 114 (250 on main), undo, redo and outfit switches in 25 to 125; no dressed character's move is under 50 ms. Every example renders as main does in RGBA, and the release equals a fresh build for the fennec and the woman. `./gradlew check` passes.

Under 50 ms on a dressed character needs a change to the mesher (fewer grid points read, garments placed from the body's vertices, rigidly moved parts not meshed again) or GPU reads; that is Nicholas's decision.

## Open items carried

- The person's jacket sleeves run into the body under the arms.
- The summer person with an arm's socket moved along the torso: the fresh build's whole figure differs from the release in 501 bytes of RGBA, and both show the forearms inside the shirt.
- The woman's winter scarf rises up the neck as a cowl; a sliver under the coat collar.
- The fennec cap's ear cuff is ragged from the side.
- An edited surface can differ from a fresh one where a field is steeper than `LIPSCHITZ` (no real character is).
- A drag's later moves: fur left out and garment vertices placed without a line search together bring the winter fennec to 52 ms and leave the woman at 116 ("Drag speed results"); neither is built.
- Full-detail edits on dressed characters are still well over 50 ms; the levers left are exact reads per grid point, garment points placed from the body's and checked by one read, per-block finishing, then GPU reads (spike on branch `gpu-field-spike`; Stretch v0.22.0 has compute).
- Painted eyes are not driven by the Pose stage's look-at tool.
- The Face board needs Nicholas's verification.

## Next, in order

1. Finish and land `drag-feel`.
2. Phase 4, creatures: adaptive cells for toes, fingers and claws; the dragon, lizard, toucan, koi and the twin onto one surface; the twin's compare IoU must not drop.
3. Phase 5: format 15 migration, the parts build and drawn eyes retired, the HLD rewritten.
4. Phase 6, joints, only if the walk sheets need it.

## How rounds are run

- One worktree per round under `.worktrees/`, one agent in it, no sub-agents; land as one squashed commit, never push.
- One Gradle build at a time; the machine ran out of memory with several.
- Light timing while iterating, the full table once at the end; module tests while working, `./gradlew check` once at the end.
- At rest every example must stay identical to main in RGB (`same.py` compares all channels).

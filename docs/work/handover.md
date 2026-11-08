# Handover: after the restructure, stage 4 and stage 5

Where the app stands, what nobody has looked at yet, and what to do next. Deleted or folded into the HLD once the next sitting has read it.

## What is on `main`

Twenty-five commits since the Build workspace was restructured, none pushed, all landed one per branch with `./gradlew check`, and the whole smoke tier passing on the last of them (`0385245`).

**The restructured Build** (`f52ef62` to `f212a50`). Six stages under Build, each the only thing that decides the strip, the key mode and the one inspector: Shape, Color, Lines, Face, Rig, Pose. Drawers only show and hide. Keys mean one thing per stage; no key caps on the strip. Add part replaces Pick a part type. The reduced palette opens from any colour swatch outside Color. Spec: Squash flow of work · restructure (https://claude.ai/artifact/81F1TgKZKUdhUVPzKZs9zJ), boards W1 to W12.

**Stage 4, the rig and the first pose** (`f07b2f9` to `835621b`, plan in `stage-4.md`). The `rig` module: bones from template joints, weights from part ownership with seam and joint bands, linear blend skinning; the Rig stage with part colours, heat map, wiggle, Reach and seam strokes shared by a pair; the pose as a document value; the Pose stage with Drag (two-bone reach, planted paws, Firm and Stretch, pole targets), Turn, Pin, Look and the Body curve; lines, marks, the face and fur resolved on the posed surface. Templates torso, tail and head are at version 2 (spine and tail hinge across the part, the head has a `neck` joint).

**Stage 5, any shape** (`a5a7e07` to `0385245`, plan in `stage-5.md`). Roles on parts, read by Pose, Add part and the rig instead of template ids. Hand-made sockets, joints and landmarks as part parameters through Move. Generic `tube`, `blob` and `sheet` templates and the Blank plan. Pair with…, Save as template into the app data folder, re-templating, a per-part rest direction. The biped plan with `arm`, `hand`, `foot`, `head-round`, `torso-upright`; the bird plan with `beak` in two parts sharing the jaw, `head-bird` with side-set eyes, `leg-bird`, `foot-bird`, `tail-feathers`, `torso-bird`; `wing-feathered`.

**Example characters** under `examples/`, each built from commands by a fixture under `screens-core/src/testFixtures/` and rewritten by `./gradlew :screens-core:test -PupdateGoldens`: `fennec`, `fennec-winged`, `koi`, `lizard`, `person`, `toucan`.

## What nobody has looked at

Every board touched since the restructure is at "verifiable" in `docs/work/boards.md` with its verify steps written, and not one is verified. No person has run the app since the restructure began; every claim above rests on unit, UI and smoke tests, and on the smoke images the agents and the orchestrator looked at. The first sitting should be a verification pass with the app open, in this order, since each stage builds on the last:

1. Build on the fennec: press 1 to 6, the strips, the inspectors, Tab, Esc, Add part on an open socket. `boards.md` row "Build: stages, tools and keys".
2. Rig and Pose on the fennec: hover and wiggle a joint, paint a seam, drag a paw, Alt and Shift, the Turn rings, a Side curve then a Top curve. Rows 7, 12, 13. Things only a hand will tell: drag feel with the posed lines (about 15 ms settle per step), the Turn rings' direction, whether the Top stroke draws jagged (seen once in a lizard render).
3. Stage 5 on the koi, lizard, person and toucan: Add socket, Add joint, the role chip, Pair with…, Save as template and whether the saved template appears in Add part after a restart. Rows W9 to W12 and row 1.

What is found wrong goes into the differences tables, and the `pose-boards` row of `stage-4.md` (the only row of stages 4 and 5 not built) is that cleanup.

## Known gaps, in the order they will be felt

- **Drag speed.** The posed resolution adds about 12 ms to each drag step on the fennec; with a Top stroke the curve tool evaluates the rig about 5,000 times per move. Unmeasured on screen.
- **Previews on the rest body.** Paint, region and line-weight tools in Full preview on the rest mesh while the character is posed. Locked panes outside Pose and Rig show rest on purpose.
- **Joins are overlaps.** Parts meet by overlapping at their sockets, with ink on the border. What holds the overlap inside now: a socket's `sink` (the plans' limb sockets sink 1.6 to 2.2 times the template's depth), the round head's `mount` under its middle, the `SocketFit` lint that fails a plan whose socket ring would show, and a `flare` on digits where they leave the palm. The lint reads undrawn builds only, so a thin drawn torso under a thick drawn thigh can still show the ring; the next step if the renders still show joins is a geometric blend at the seam, the Implicit Skinning family already planned for pose-time contact, read from the papers first.
- **Fingers have no joints.** The hand's five digits are shape only; a curling hand needs a branching joint tree inside one part (bones, vertex tagging, weights, the Rig stage). The biggest single piece of unbuilt anatomy.
- **Side-set eyes** read angle versions from the head's angle, not the eye's, and the gaze solve has no bias for eyes that look sideways. Written in `parts.md`.
- **The upper beak** hangs from the head's jaw bone, which no pose sets; it holds still, which is right, by accident.
- **The feet slide** about 1.4 cm when the body curve tips an upright pelvis, where a body drag holds them exactly.
- **Saved templates appear after a restart,** because `AppState.templates` is immutable and `AppState.kt` is at its line limit.
- **`check` runs 2.5 to 3.5 minutes** on this machine with compilation, against the two-minute criterion in `stage-3.md` and three in `stage-5.md`.
- **Stretch gaps** worth their own Stretch commits: a disabled state on the labelled button, a per-option tooltip on the segmented control, a chip that presses and dashes (Squash drew `ChoiceChip` in `ui-design` meanwhile).
- **No Update from library** command exists; survival across template versions is tested by a version bump only.

## Decisions waiting on Nicholas

- The open comment on the Face board: when ¾ is unused, borrow from the nearer drawn angle or always from the first drawn. Built as the document falls back.
- Whether the quadruped's front legs showing as "Front leg" and "Front paw" while storing arm and hand is what he wants, or the board's "Foot".
- Whether to fold the restructure canvas back into Squash flow of work now that he has said the new flow is the flow. The old canvas's boards 2, 7, 9 to 13 point at the new one; the rest of the old canvas is still the spec for Home, Sheet, References, Shot and Objects.
- The spike-8 volume reading: Length 0.92 is +3.4% from rest because the bow itself adds +3.0% with the legs sunk deeper; the test measures against the bow at Length 1 and allows 3.5%.

## What to do next, in order

1. **Verify.** The pass above, with the app open, and a look at every example for the joins, the hands and feet and the person's head, which only smoke renders have shown. Half a day. Everything else should wait for it, because three stages of UI have never met a pointer.
2. **`pose-boards`.** The differences tables for boards 7, 12, 13 and W9 to W12 against what the pass found, plus the small fixes it names (the Top stroke's drawing, the Rig inspector's length, whatever drags badly).
3. **Finish stage 5's round 3: the fish plan.** A `fin` template as a named sheet and a fish plan over the koi's parts, with the koi regenerated from its plan. Small, since the koi exists; it closes exit criterion 5.
4. **Fingers.** A branching joint tree inside a part, so hands curl and the toucan's toes grip. Plan it as a short stage note first: it touches `PartJoints`, `boneAt`, the weights, the Rig stage and Turn.
5. **The Shot workspace.** Pose is a stage under Build; the Shot boards (14 settle and touch up, 15 timing, 16 scene, 17 render, and the Shot board for eyes and gaze that `future.md` places before the posing screens) are not drawn. Correct pose and the pose library, out of stage 4's scope, belong here. This is the app's second half as the original plan drew it.
6. **Later anatomy** from `stage-5.md`'s out-of-scope list: `wing-membrane` and the membrane block for dragons and bats, socket rows for spines and frills, tip sockets for antlers, horns and claws.

## How the work was run, for whoever runs the next sitting

One orchestrating session; one Opus agent per branch with a written brief naming the files to read, the boards to build to, the tests and docs owed, and the rule to stop at a clean boundary; boards drawn on the restructure canvas before any UI; a creature per round as the regression. Agents cannot run the app, so each brief asked for a smoke render the agent viewed and described, and the orchestrator re-rendered the creatures in the main checkout to look as well (`./gradlew :app:test --tests '*<Name>Smoke*' -Pslow`, images under `app/build/smoke-images`). The Opus weekly limit stopped one agent mid-branch; its worktree survived and it resumed from the same state.

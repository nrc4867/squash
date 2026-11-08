# Handover: after the restructure, stage 4 and stage 5

Where the app stands, what nobody has looked at yet, and what to do next. Deleted or folded into the HLD once the next sitting has read it.

## What is on `main`

Twenty-five commits since the Build workspace was restructured, none pushed, all landed one per branch with `./gradlew check`, and the whole smoke tier passing on the last of them (`0385245`).

**The restructured Build** (`f52ef62` to `f212a50`). Six stages under Build, each the only thing that decides the strip, the key mode and the one inspector: Shape, Color, Lines, Face, Rig, Pose. Drawers only show and hide. Keys mean one thing per stage; no key caps on the strip. Add part replaces Pick a part type. The reduced palette opens from any colour swatch outside Color. Spec: Squash flow of work · restructure (https://claude.ai/artifact/81F1TgKZKUdhUVPzKZs9zJ), boards W1 to W12.

**Stage 4, the rig and the first pose** (`f07b2f9` to `835621b`, plan in `stage-4.md`). The `rig` module: bones from template joints, weights from part ownership with seam and joint bands, linear blend skinning; the Rig stage with part colours, heat map, wiggle, Reach and seam strokes shared by a pair; the pose as a document value; the Pose stage with Drag (two-bone reach, planted paws, Firm and Stretch, pole targets), Turn, Pin, Look and the Body curve; lines, marks, the face and fur resolved on the posed surface. Templates torso, tail and head are at version 2 (spine and tail hinge across the part, the head has a `neck` joint).

**Stage 5, any shape** (`a5a7e07` to `0385245`, plan in `stage-5.md`). Roles on parts, read by Pose, Add part and the rig instead of template ids. Hand-made sockets, joints and landmarks as part parameters through Move. Generic `tube`, `blob` and `sheet` templates and the Blank plan. Pair with…, Save as template into the app data folder, re-templating, a per-part rest direction. The biped plan with `arm`, `hand`, `foot`, `head-round`, `torso-upright`; the bird plan with `beak` in two parts sharing the jaw, `head-bird` with side-set eyes, `leg-bird`, `foot-bird`, `tail-feathers`, `torso-bird`; `wing-feathered`.

**Example characters** under `examples/`, each built from commands by a fixture under `screens-core/src/testFixtures/` and rewritten by `./gradlew :screens-core:test -PupdateGoldens`: `dragon`, `fennec`, `fennec-winged`, `koi`, `lizard`, `person`, `toucan`.

## What nobody has looked at

Every board touched since the restructure is at "verifiable" in `docs/work/boards.md` with its verify steps written, and not one is verified. No person has run the app since the restructure began; every claim above rests on unit, UI and smoke tests, and on the smoke images the agents and the orchestrator looked at. The first sitting should be a verification pass with the app open, in this order, since each stage builds on the last:

1. Build on the fennec: press 1 to 6, the strips, the inspectors, Tab, Esc, Add part on an open socket. `boards.md` row "Build: stages, tools and keys".
2. Rig and Pose on the fennec: hover and wiggle a joint, paint a seam, drag a paw, Alt and Shift, the Turn rings, a Side curve then a Top curve. Rows 7, 12, 13. Things only a hand will tell: drag feel with the posed lines (about 15 ms settle per step), the Turn rings' direction, whether the Top stroke draws jagged (seen once in a lizard render).
3. Stage 5 on the koi, lizard, person and toucan: Add socket, Add joint, the role chip, Pair with…, Save as template and whether the saved template appears in Add part, at once and after a restart. Rows W9 to W12 and row 1.

What is found wrong goes into the differences tables, and the `pose-boards` row of `stage-4.md` (the only row of stages 4 and 5 not built) is that cleanup.

## Known gaps, in the order they will be felt

- **Drag speed.** The posed resolution adds about 12 ms to each drag step on the fennec; with a Top stroke the curve tool evaluates the rig about 5,000 times per move. Unmeasured on screen.
- **Previews on the rest body.** Paint, region and line-weight tools in Full preview on the rest mesh while the character is posed. Locked panes outside Pose and Rig show rest on purpose.
- **Joins are overlaps.** Parts meet by overlapping at their sockets, with ink on the border. What holds the overlap inside now: a socket's `sink` (the plans' limb sockets sink 1.6 to 2.2 times the template's depth), the round head's `mount` under its middle, the `SocketFit` lint that fails a plan whose socket ring would show, and a `flare` on digits where they leave the palm. The lint reads undrawn builds only, so a thin drawn torso under a thick drawn thigh can still show the ring. The `blend` block's `socket` fillet (`spike-implicit-blend.md`) flares a leg sunk into a torso into a fillet with the torso untouched, the clearest gain of the spike; no shipped template has it yet, and a shipped `leg` version waits on whether the socket's ink should run along the fillet's foot and on caching fields so the fillets (27 to 45 ms a character) stop rerunning in full on every assembly change.
- **Hands, paws and feet read as tubes.** Nicholas's verdict on the renders after the seating work: too tubular, every digit and palm a swept tube with a round or egg section, meshed on its own and overlapped, no web between fingers and no fillet where a finger meets the palm. `stage-7-digit-shape.md` built the two cheap fixes: the `membrane` block, a web between neighbouring digits that follows both digits' bones, and per-digit shape on the toe row (`section`, `width`, `taper`, `knuckle`), in `hand-3`, `paw-3`, `foot-bird-3` and `foot-2`. The close views (`DigitCloseUpSmokeTest`) show the thumb growing out of the palm on a web, fingers tapering with knuckle bumps, the bird's toes as tapering, jointed talons webbed at the base and the paw's toes joined part way up. What is left: the palm itself still reads as a rounded box, the person's fingers still sit side by side as separate tubes along most of their length, a drawn paw keeps its drawn toe sizes so taper and knuckles do not show on the fennec, and the web's edges and its crossings with the fingers draw short crease ticks at the finger roots. The implicit blend as displacement, (3), is built as a spike (`spike-implicit-blend.md`): the thumb's root fillets well into the palm, the four fingers' roots get a small fillet, but the web's crease ticks stay (the web crosses the digits at a grazing angle, and blending it in makes them worse), the palm stays a rounded box (a blend acts only at crossings), and joining neighbouring digits merges them along their whole length into a mitten. No `hand` version takes it. What is left needs shape, not a blend: a palm section that narrows into the wrist and cups, and a web that meets its digits tangentially or lines that skip grazing crossings. Pose-time contact reuses the spike's fields and projection; what it needs on top is in the spike note.
- **The upper beak** hangs from the head's jaw bone, which no pose sets; it holds still, which is right, by accident.
- **`check` runs 2.5 to 3.5 minutes** on this machine with compilation, against the two-minute criterion in `stage-3.md` and three in `stage-5.md`.

## Decisions waiting on Nicholas

- The open comment on the Face board: when ¾ is unused, borrow from the nearer drawn angle or always from the first drawn. Built as the document falls back.
- Whether the quadruped's front legs showing as "Front leg" and "Front paw" while storing arm and hand is what he wants, or the board's "Foot".
- Whether to fold the restructure canvas back into Squash flow of work now that he has said the new flow is the flow. The old canvas's boards 2, 7, 9 to 13 point at the new one; the rest of the old canvas is still the spec for Home, Sheet, References, Shot and Objects.
- Update from library is built for a part's template version; `architecture.md` also names a character-level Update from library, from a character saved in the personal library into a project's copy, which is not built and may want another name. And whether a version that drops a socket a part sits on should be offered at all: it is offered, the part kept on its socket and its row in Parts highlighted, since nothing is lost and undo restores it; no shipped version drops anything.
- The spike-8 volume reading: Length 0.92 is +3.4% from rest because the bow itself adds +3.0% with the legs sunk deeper; the test measures against the bow at Length 1 and allows 3.5%.

## What to do next, in order

1. **Verify.** The pass above, with the app open, and a look at every example for the joins, the hands and feet and the person's head, which only smoke renders have shown. Half a day. Everything else should wait for it, because three stages of UI have never met a pointer.
2. **`pose-boards`.** The differences tables for boards 7, 12, 13 and W9 to W12 against what the pass found, plus the small fixes it names (the Top stroke's drawing, the Rig inspector's length, whatever drags badly).
3. **Finish stage 5's round 3: the fish plan.** A `fin` template as a named sheet and a fish plan over the koi's parts, with the koi regenerated from its plan. Small, since the koi exists; it closes exit criterion 5.
4. **Fingers.** Built on branch `fingers` from `stage-6-fingers.md`: digit chains from the toe row's `joints` (three a finger, two for the thumb, the bird foot's hind toe and a paw's toes), weights per digit, the Rig stage's Bones per digit, Curl on Pose's limb section, `hand`, `foot-bird` and `paw` at version 2. Left for the verify pass: the curl's feel on a real hand, Turn's rings on a finger joint at the panes' size, and whether a fennec's paw reads its toes as First toe on. Tip sockets for claws and nails come with later anatomy.
5. **The Shot workspace.** Pose is a stage under Build; the Shot boards (14 settle and touch up, 15 timing, 16 scene, 17 render, and the Shot board for eyes and gaze that `future.md` places before the posing screens) are not drawn. Correct pose and the pose library, out of stage 4's scope, belong here. This is the app's second half as the original plan drew it.
6. **Hands, paws and feet.** The membrane block and per-digit shape are built (`digit-shape`), and the implicit blend as displacement as a spike (`blend-spike`); next a palm that narrows and cups and a web that meets its digits tangentially, from the known-gaps entry above.
7. **Later anatomy.** `stage-8-later-anatomy.md`, on branch `membrane-wings`: `wing-membrane` (an arm, four long fingers each closing on its own path, webs between them and skin from the last finger back along the arm, folded by Curl), hand-made rows of sockets that size what Add part places on them and fill in one step, the `spike` and `plate` templates, and `horn` and `antler` (an antler chains through a socket at its tip), on the dragon, `examples/dragon.json`. Left from its round 3: a tip socket per digit of a toe row, the `claw` template on it, and claws on the dragon's toes.

## Work order

The steps above split by who can do them. Agents cannot drive the app with a pointer (the devtools endpoint captures, it does not click), so the verify pass and the cleanup that follows it wait for Nicholas; everything else runs as branches in worktrees, one Opus agent per branch, landed on `main` with `check`, rounds serial where they share files.

| Wave | Branch | Delivers | Waits on | Status |
| --- | --- | --- | --- | --- |
| 1 | `fish-plan` | `fin` as a named sheet template, the fish plan, the koi rebuilt from it; stage 5's exit criterion 5 | nothing | landed |
| 1 | `pose-gaps` | Saved templates appear in Add part at once; feet hold under the body curve's pelvis tip; side-set eyes read their own angle and gaze sideways | nothing | landed |
| 1 | Stretch `main` | The labelled button's disabled state, a per-option tooltip on the segmented control, a chip that presses and dashes; Squash moved onto them | nothing | landed |
| 2 | `fingers` | A branching joint tree inside a part: a stage note, then bones, tagging, weights, the Rig stage and Turn, so hands curl and the toucan's toes grip | wave 1 | landed |
| 2 | `update-from-library` | The Update from library command, so survival across template versions is tested through it | wave 1 | landed |
| 3 | `digit-shape` | The membrane block between digits and per-digit shape (sections, taper, knuckles), in hand, paw, bird foot and foot at their next versions; `stage-7-digit-shape.md` | `fingers` | landed |
| 3 | `blend-spike` | The implicit blend as displacement, from the papers, as a spike: the `blend` block (in-part fillets and socket fillets), test-only variants, the close views on and off and a benchmark; `spike-implicit-blend.md` | `digit-shape` | landed |
| 4 | `membrane-wings` | `stage-8-later-anatomy.md`: `wing-membrane` and the dragon (round 1), socket rows (round 2), tip sockets, horns and claws (round 3) | `digit-shape` | rounds 1 and 2 landed, round 3 in part: horns and antlers landed, digit tip sockets and claws waiting |
| 5 | Shot | Boards 14 to 17 and the eyes-and-gaze Shot board drawn, a stage note, then the workspace in rounds | Nicholas's answers to the open questions on the Shot boards (S1 to S6 drawn on the restructure canvas, planned) | waiting |
| Nicholas | none | The verify pass, then `pose-boards` from what it finds, and the decisions above | a sitting with the app open | waiting |

## How the work was run, for whoever runs the next sitting

One orchestrating session; one Opus agent per branch with a written brief naming the files to read, the boards to build to, the tests and docs owed, and the rule to stop at a clean boundary; boards drawn on the restructure canvas before any UI; a creature per round as the regression. Agents cannot run the app, so each brief asked for a smoke render the agent viewed and described, and the orchestrator re-rendered the creatures in the main checkout to look as well (`./gradlew :app:test --tests '*<Name>Smoke*' -Pslow`, images under `app/build/smoke-images`). The Opus weekly limit stopped one agent mid-branch; its worktree survived and it resumed from the same state.

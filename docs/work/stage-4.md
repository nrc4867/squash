# Stage 4: the rig, and the first pose

A working note for the sessions that build stage 4. Folded into the HLD or deleted when the stage lands.

## Goal

The built character gets bones and weights without anyone rigging it, and poses for the first time. The rig comes from the templates: every part already names its joints, their hinge axes, limits and which bone stretches, and the body plan gives the tree. Weights are seeded from part ownership, so only the seams between parts are guessed, and a Rig stage under Build shows the result and lets a seam be painted where the guess is wrong. Posing is the Shot workspace's first screen: drag a paw and the leg follows, planted paws stay put, one stroke bends the spine and tail, and the pose reads from every camera.

That is board 7 and boards 12 and 13, with spikes 4 and 8 behind them; board 14 comes after. Spike 5, contact, waits.

## What the design already holds

`docs/hld/pipeline.md` names the mechanisms: weights seeded from part ownership with both of lego-viewer's tagging systems, stretch weights, lego-viewer's IK with planted paws, the body curve that re-samples the spine's slices by arc length, Correct pose. `docs/hld/data-model.md` has the Rig row (derived) and the Pose row (per-bone transform and stretch, the body curve kept as a curve, the planted set, touch-up deltas). The templates carry the joints: hips to withers on the torso, root to tip on the tail, hip, knee and ankle on a leg with the ankle stretching, the jaw on the head, base, fold and tip on an ear. The Move tool places a knee along its part.

What it does not hold is the flow: how the rig is checked and corrected, and how a pose is made. lego-viewer has both, and they are the model for this stage.

## What carries over from lego-viewer

lego-viewer fits a skeleton to a mesh in four steps (Body, Joints, Check, Roles) and poses it in a Pose mode. Squash needs less of the first half, because a part template is a body type, a joint set and a role in one, and more of the second.

| lego-viewer | In Squash |
| --- | --- |
| Body step: body type tiles, dots guessed | Not needed: the body plan and the templates are the skeleton. |
| Joints step: drag dots, mirrored twin follows, snap to the middle line and level | Already the Move tool, for a knee along a part and a socket on its parent. Nothing new. |
| Roles step: legend of roles, a check stepper for unsure guesses | Not needed: a leg's joints are hip, knee and ankle by template. Blank mode will need it later, when a generic tube is told what it is. |
| Check step: part colours over the model, a heat map on hover, click a joint to wiggle it, Reach per joint, Paint, Erase and Smooth strokes stored and replayed, Tidy stray patches, To twin, Reset, Learn | The whole of the Rig stage. Parts already own their mesh, so the colours are the parts and the seams are the only thing to check. |
| Pose mode's Drag tool: handles on the body centre, each paw, a curl arc per tail and a look-at target; pins on the feet; Alt stops the body where the pinned feet reach, otherwise they slide; Shift stretches the limb; a pole target per bent limb | Board 13, limbs and stretch. The planted set is the pins. |
| Turn tool: rings on a bone, Tip, Turn and Lean, a chain curled in equal shares, Mirror to the twin | Board 13, for the joint-by-joint finish. |
| Role controls: Curl and Spread toes, Mouth open, Heel roll, Toes up, Turn the neck too | From template blocks: the toe row gives Curl and Spread, the jaw block gives Mouth open. Heel roll is in `docs/work/future.md` with walks. |
| Curve handles on a tail; Drape on floor | Board 12, the body curve: one stroke for tail, spine, neck and head, which is lego-viewer's tail curve made general. |
| Limits: Free, Hinge, Ball, Stiff, Locked; elbows and knees one way | Templates set limits; the pose screen shows them and can switch a joint Free or Locked for a shot. |
| Correct pose: settle, balance, relax, volume | Board 14, settle and touch up. |
| Pose library: starters by body shape, flip, a pose put on another model by its aims | Boards 12 to 14 need a Library drawer; the starters come per body plan. |
| Pose bar: Mirror, Limits, Floor, Show | The Shot strip, in the restructure's shape: the armed tool's options first, no key caps. |

## Decided

- **In stage 4:** the Rig stage (the Check step), the Drag tool with pins and pole targets, the Turn rings with the role controls, and the body curve. Correct pose and the pose library wait for a later stage; the data model needs nothing from them now.
- **Posing lives in a Pose stage under Build** until the Shot boards are drawn. A stage is one `Stage` entry and one inspector, so it moves to Shot whole.
- **Seam strokes are shared by a pair with per-side overrides,** the rule surface work follows: one stroke serves both sides mirrored, and painting with This side on writes that side's override.

## Before round 1

The orchestrator:

- Redraws board 7 and boards 12 and 13 in Squash flow of work · restructure, as stages with a strip and one inspector, before any code. Board 14 stays as it is. Nothing else in either artifact changes.
- Writes the verify steps for board 7 into `docs/work/boards.md` at "building".

## Exit criteria

1. **Bones from templates.** Every template joint is a bone boundary, the body plan gives the tree, and the rig is a graph node that follows the assembly. Adding a joint to a template version shifts nothing stored. A golden test holds the fennec's bone count and rest transforms.
2. **Weights from ownership.** Each vertex starts weighted to its part's bone by u along the axis, and only within a seam band at a socket is it blended with the parent. Spike 4 passes on the fennec: a leg and paw bent and stretched with nothing smearing and volume held.
3. **The Rig stage.** Under Build, lit now, with the part colours over the character, a heat map on hover, wiggle on click, Reach per joint, and Paint, Erase and Smooth strokes that are commands, replayed on a rebuild and mirrored across a pair. Tidy and To twin are one undo step each. The stage arms no drawer and no drawer arms it.
4. **A first pose.** Drag a paw and the leg follows through IK; planted paws stay; the body centre moves the character and, with Alt, stops where the planted paws reach; a ring turns one joint within its limits, Mirror turning the twin; the posed character shows its lines, face and fur from every camera. The pose is a document value, one per shot key, and saves and loads.
5. **The body curve.** One stroke in Side poses tail, spine, neck and head while planted paws stay put, and a stroke in Top bends them sideways; spike 8 passes.
6. **Boards match.** Board 7 and boards 12 and 13 match under the Build to the boards rule.
7. **Tiers hold.** `./gradlew check` under two minutes without the smoke tier; the smoke tier renders the Rig stage and a posed fennec.

## How the sessions run

As stage 3: one orchestrating session per sitting, round 0 serial on `main`, later rounds as branches under `.worktrees/`, one per row, landed by rebase, squash and fast-forward with `check` before each landing.

| Round | Branch | Delivers |
| --- | --- | --- |
| 0 | serial | The `rig` module: bones from templates, ownership weights, the seam band, the golden test; spike 4 as a unit test |
| 1 | `rig-stage` | On `main`: the Rig stage (key 5) with Paint weight, Erase weight and Smooth weight, its inspector (tool options, Bones with Reach, Seams with Tidy, To twin and Reset) and status bar; the panes in part colors with seam bands and the heat map as color layers, joint dots, and a frame-driven wiggle through `SessionPose.held`; seam strokes in surface work (`SetSeamStroke`, `ResetSeam`, `SeamToTwin`, `AddSeamStrokes`), replayed by `StrokePaint` into the rig node with reach from `rig.reach.<joint>`; Tidy from `Rig.strayPatches`. Board 7 is verifiable; its differences are in `boards.md` |
| 1 | `pose-data` | On `main`: `Character.pose` with the planted set, the pose commands (`SetBoneAngle`, `SetBoneStretch`, `ResetBone` with `bothSides`, `SetPlanted`, `ResetPose`, `SetPose`), the posed node `SessionPose.posed` skinning positions and normals on the CPU, Full drawing the posed meshes; torso, tail and head version 2 (spine and tail bend across, a `neck` joint at the head's base). Color layers, the face, lines, marks and fur still follow the rest surface and Full leaves them out while posed; `pose-drag` poses them |
| 2 | `pose-drag` | On `main`: the Pose stage (key 6) with Drag, Turn, Pin and Look (D, E, P, L), the body curve's place left on the strip, its handles drawn over every pane and its inspector (Drag's Firm and Stretch, the picked limb, the picked bone's Turn section with Free, Limited and Locked, Planted, Reset bone and Reset pose); `PoseSolver` in `rig` (two-bone reach with stretch, the body moved over planted paws with firm or sliding, pole swing, tail curl, look-at with the neck's share, `holdPaws`, the pin solve the body curve tool chains after its solve); `BonePose` turn, lean, mode and keep volume, `Pose` offset, look-at and still paws, with `SetBoneTurn`, `SetBoneMode`, `SetKeepVolume` and `SetRiding`; every pane posed in Pose with color layers on the posed mesh. Board 13 is verifiable; what still waits on the posed surface (lines, the face, creases, fur) is in its differences row. The body curve's Top stroke can now turn the chain sideways by each bone's `lean` |
| 2 | `body-curve` | On `main`: `Pose.curve` (`BodyCurve`: Side and Top strokes as joined cubics in character space, and the length factor); `solveBodyCurve` in `rig`, laying the tail, torso and head's neck along the Side stroke and leaning them along the Top stroke within each joint's lean reach, the length as a stretch on every spine and tail joint, planted paws reported in `BodyCurveFit.paws` by the joint they hang from; `PoseSolver.bendTo`, the solve chained with `holdPaws`, and `clearCurve`; the Body curve tool (key C, board 12) drawing a stroke in Side or Top, or Full turned square, with handles at its ends and middle, its inspector section (Length with a squash or stretch chip, Keep volume, Clear curve) and the chain's bends; spike 8 as `SpikeEightTest`, the chained solve as `BodyCurvePoseTest`, the tool as `BodyCurveFlowTest`. Board 12 is verifiable; its differences are in `boards.md` |
| 3 | `posed-surface` | On `main`: lines, line weights and creases, inked region borders, surface strokes and marks, the face with its angle versions by the posed head's angle and the gaze handle ahead of the posed head, and fur rooted on the posed body, all resolved on the posed grids through the graph (`CharacterSession.posedBody`, `posedSurfaceWork`, `posedLines`, `posedFur`, each the rest result itself at rest); every pane that shows the pose draws as one at rest does; `PosedSurfaceTest`, `PosedFaceTest`, `PosedSettleTimeTest` and `NearestPointsTest` |
| 3 | `pose-boards` | Every remaining row of the differences table for boards 7, 12 and 13 |

## Stretch

Expected gaps, landed in the Stretch repo first: a ring gizmo drawn by the renderer over the native surface, and a heat map legend if `stretch-ui` lacks one.

## Out of scope

Correct pose and the pose library (board 14), contact and squash against the ground or another body (spike 5), garments, timing and clips, scenes and render to file, Blank mode's role step, foot roll, the idle layer.

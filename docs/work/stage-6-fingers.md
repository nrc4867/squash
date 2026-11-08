# Stage 6: fingers

A working note for the branch that gives digits joints. Folded into the HLD or deleted when it lands.

## Goal

A hand's digits curl and a bird's toes grip. Today a toe row's digits are shape only: every vertex of a digit takes the bone at its u along the part's axis, so the whole hand bends at the wrist and nothing else. A digit gets its own short chain of bones, branching from the part's own chain where the digit leaves the palm, so one part holds a tree of joints rather than a line of them. The rig, the Rig stage and Pose read the tree the way they read a chain today, and one Curl value per hand or foot closes every digit at once.

## What the design already holds

`docs/hld/data-model.md`, The rig: every template joint is a bone named by part and joint, a part's joints own spans of it in u order, the sockets give the tree, weights come from ownership with bands at joints and seams, Reach widens a band, the pose names bones by part and joint, and Turn picks a bone by the weights under the click. The toe row builds one closed tube per digit as its own patch, `name:i`, whose rings carry u along the part's axis, and `digits` moves, spans and angles a digit apart from the fan; a digit turned past a right angle points back, the bird foot's hind toe. `parts.md` defers "three joints a finger and a tip socket per digit" to per-digit joints.

What it does not hold: a joint off the part's axis. A `joint` block sits at one u of the axis and owns the part across it; a digit is one of several patches at the same u, and how many there are is a parameter (the paw's `toes`), so its joints cannot be blocks.

## Decided

- **The toe row carries its digits' joints.** `joints` on a toe row: `at`, where each joint sits along every digit as a share of the digit's span from its root, `hinge` and `limits`. A `digits` override may give its digit its own `at`. A row without `joints` builds digits with no bones, as now. Each digit the row builds gets the joints, so a paw's `toes` parameter decides how many chains there are.
- **How many.** The hand: three a finger (the knuckle where the finger leaves the palm, then two along it) and two for the thumb. The bird foot: three on each front toe, two on the hind toe. The paw: two a toe, so the fennec's toes curl under; a paw with no toes has none. The human foot, a shoe, gets none.
- **The branch point is the joint owning the digit's root.** A digit's first bone hangs from the part's own bone that owns the u where the digit starts, the wrist on a hand, paw or bird foot. The digit up to its first joint follows that bone, as the palm does, and each digit joint owns the digit from its u to the next, the last to the digit's tip. Bones list the part's own chain first, then each digit's in row order, parents first as before.
- **Names.** A digit joint is stored as `<row>:<digit>.<n>`, the digit's patch name and the joint's number from 1 at the root: `fingers:1.2` is the index finger's middle joint. The Rig stage and Pose show the digit's name and the number: on a hand of five, Thumb, Index, Middle, Ring and Little, so Index 2; on any other row First toe to Fifth toe, a digit turned back Hind toe.
- **Hinges curl toward the underside.** A digit joint's hinge is a direction of the part's axes as any joint's, x across the part; a positive tip turns the digit toward the row's underside, the palm or the sole, and a digit turned back takes its hinge reversed so the same angle closes it toward the sole too: the hind toe grips against the front toes. Mirrored parts mirror it as every hinge. Limits are the row's, the thumb's too: the hand -10 to 90, the bird foot -15 to 80, the paw -20 to 60.
- **Weights band per digit joint.** Inside a digit the joint band is the rule a part's joints follow, a smooth step over `JOINT_REACH` of the digit's own radius at the joint either side, held to half the way to the joints around it, the first band blending the branch point's bone into the digit's first. Reach (`rig.reach.<joint>`) scales a digit joint's band as any other. A pad on a row's digit follows that digit. Palm vertices never take a digit's bone: digits overlap the palm, they do not share its vertices.
- **No stretch on a digit.** A stretch scales a joint's distance from the one before it on its own chain only, so a wrist's stretch, if a template offered one, would never pull one finger.
- **Curl is in scope.** One value per hand or foot in Pose, on the picked limb's section when its end part has digits: Curl from 0 to 100%, each digit joint tipped to that share of its upper limit. It is a function of the rig, `Rig.curled`, sent as `SetPose` in one slider transaction, both sides with Mirror on; the slider reads back the mean share the digit joints stand at. Nothing new is stored: a curl is the digit bones' tips, which Turn then finishes.
- **Turn needs nothing new.** It picks the bone whose weights move the skin under a click, so a click on a finger picks that finger's joint, Shift chains joints of one finger to share a turn, and Mirror turns the twin's joint of the same name.
- **Versions.** `hand`, `foot-bird` and `paw` go to version 2 with the joints; version 1 stays shipped, so a stored character keeps its hand, and its bone count, until Update from library. New characters and the example files take version 2, so the fennec, person and toucan goldens change, deliberately.
- **One tree, two readers.** `JointTree`, in `character`, reads a template's chain and its digits' chains from the patches a build made; `PartBuilder.mesh` tags vertices with it and the rig builds bones and weights from it.

## Rows

| Row | Delivers |
| --- | --- |
| Model | `DigitJoints` on `ToeRow` and `at` on `Digit`, lint (shares in 0 to 1, ascending), the schema regenerated; `JointTree`; vertex tagging per digit |
| Rig | Digit bones (pivot at the digit's ring middle, direction to the next joint or the tip, hinge by the rule above), the chain-only stretch, digit joint bands and pads, `PoseSolver.curl` and `curlOf`; unit tests on the bone tree, the weights and a curl |
| Templates | `hand-2`, `foot-bird-2`, `paw-2`; character goldens for them; the rig's fennec golden, the examples rewritten by their fixtures |
| Rig stage | Bones lists a part's own joints, then a row per digit, closed with its joint count, opening to its joints with Reach each; digit bone names in Bones, the Bone chip and the status bar's count |
| Pose | Curl on the limb section; Turn on digit joints, its section naming them; a UI test |
| Smoke | The person with a hand curled and the toucan gripping, rendered and looked at |

## Exit criteria

1. **A hand curls.** On the person, Curl 100% on a hand turns every finger in toward the palm at three joints and the thumb at two; the palm and the wrist's vertices do not move; no finger's vertex follows another finger's bone.
2. **A foot grips.** On the toucan, Curl closes the three front toes and the hind toe toward each other under the foot.
3. **Stored characters survive.** A character holding hand, foot-bird and paw at version 1 builds and rigs with the bone counts it had.
4. **The Rig stage and Turn read the tree.** Bones lists digits per part with Reach on each joint; a hover lights a finger's bone; Turn on a finger turns it and Mirror turns its twin.
5. **Tiers hold.** `./gradlew check` passes; goldens are regenerated on purpose.

## Out of scope

Tip sockets and claws, a Spread control (the fan's angle as a pose), per-digit shape, the membrane between digits, a thumb that opposes across the palm on its own axis, reaching with a fingertip.

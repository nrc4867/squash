# Stage 8: later anatomy

A working note for the branch that builds the anatomy `stage-5.md` left for later: membrane wings, socket rows, tip sockets, horns and claws. Folded into the HLD or deleted when it lands.

## Goal

A dragon or a bat is drawn the way a fox is. A wing can be skin stretched over long fingers that folds when they close; a back can carry a row of spikes or plates sized from big to small; a digit can end in a claw and a head carry horns or branching antlers. Each is data over the blocks that exist, plus the fewest new blocks and fields, read by the rig, Pose, lines and shading per part as everything else is.

## What the design already holds

`parts.md` plans `wing-membrane` as an arm chain, a toe row of long fingers and a `membrane` spanning the digits and back to the body; a socket row as sockets repeated along a curve on a parent; a tip socket on tubes for antlers; `horn` and `claw` as tubes with a pointed cap. `stage-6-fingers.md` gives every digit of a toe row a chain of joints and one Curl value that closes them all; `stage-7-digit-shape.md` builds the `membrane` block between neighbouring digits, whose vertices ride both rails (`Spanned`), and says any swept patch of a part can be a rail. `wing-feathered` shows how a wing sits on a hand-made socket and how Pose drags it by its wrist and never plants it.

What it does not hold: a membrane whose second rail is the part's own run, a digit that closes along its own path (each digit of a row shares one set of limits, so a whole curl turns them all by the same angle and the fan never closes), a socket that repeats, a socket that sizes the part placed on it, and a socket on a digit.

## Decided

- **One creature, grown a round at a time.** A small dragon on the canine plan, invented and generic: membrane wings in round 1, back and tail spikes in round 2, horns in round 3, claws once digits carry tip sockets. `examples/dragon.json`, written by `DragonFixtureTest` from `dragonCommands()`, its golden rewritten by each round on purpose, and `DragonSmokeTest` rendering it at rest and posed.
- **`wing-membrane` is an arm and four long fingers.** One template, role wing, resting out to the side as `wing-feathered` does: a thin arm run to the wrist with shoulder, elbow and wrist joints, a toe row `fingers` of four long thin digits fanned from the wrist, from the leading finger along the arm's line to the last pointing back toward the body, and two membranes: `web` between each finger and the next, reaching their tips with a scalloped free edge, and `skin` from the last finger back along the arm to the shoulder. The skin's edge running from the last fingertip to the shoulder is the wing's trailing edge; the skin meets the body only where the arm does, at the shoulder.
- **A membrane's rail can be the part's own run.** A pair of the `membrane` block names `along`, a span of the part's u from the rail's root to its edge, in place of `second`: the rail is the run covering that span, read root to edge, so `[0.38, 0.03]` runs from the wrist back to the shoulder. Its vertices ride the digit and the run as a web's ride two digits, so the run's own joints (the elbow, the shoulder) move it and the seam band at the shoulder hands its root to the body's bone. A rail on another part is out of scope.
- **Each finger closes on its own path.** A `digits` override may give its digit its own `limits`, so at a whole curl every finger reaches the same direction: the leading finger turns furthest and the last least, and the fan closes to lie back along the flank. Each finger of the wing has one joint, at its root, hinged about the wing's up (`y`), so the fingers fold in the wing's plane; a positive tip swings them back. The webs collapse between closing fingers since every vertex rides both; that is the fold.
- **The fold is Curl.** Pose's limb section shows Curl for a limb whose own part has digits as well as for one ending in a hand or foot, so a membrane wing folds and spreads from the same slider; Drag by the wrist folds the arm. No new string: the row reads Curl.
- **Socket rows are hand-made sockets with a count.** A hand-made socket `socket.<n>` with `.count` above 1 is a row: that many sockets evenly spaced along its part from `.u` to `.to` at one v, named `socket.<n>.<i>` from 0, paired or not as one socket is. Its `.size` is the size a part placed on its first socket starts at and `.falloff` the last's as a share of that, sizes between falling off linearly; a socket carries that `scale`. A row is parameters on the parent, so pairs, overrides, undo, saving and Update from library treat it as any hand-made socket, and any part anywhere takes one. Move's Add socket has Count, and with more than one First size and Last size; a press places the row from the press toward the part's further end. Picking a row's socket picks the row, whose Outward angle and Delete socket act on it whole; its sockets are not dragged one by one. No template ships a row, so the torso and tail keep their versions.
- **Add part fills a row.** Add part on a socket of a row places the template on every empty socket of the row that takes it in one `AddParts`, each part's `size` its socket's scale, so a row of seven spikes is one step and one undo, and each spike's size is then its own to change.
- **Spikes and plates.** `spike`, role horn: a short tube on the `lens` section, flat side to side, tapering to a pointed cap and leaning back, with no joint of its own, so it rides the bone of the part it sits on. `plate`, role fin: a rounded plate on the `sheet`'s grammar, open in v. The template is `spike` and not `spine`, since `spine` is the role of a back's chain of joints.
- **A run's tip socket needs nothing new.** A socket at u 1 with `outward: axis` on a capped run sits at its tip and sinks its part along that part's own axis, so it continues the run: `antler` carries one. A tip socket per digit of a toe row (`tips` on the row, a `patch` on a socket so placement reads the digit's surface, the digit's last joint as the rig's carrier, and Add part filling every tip of the row at once) is designed here and not built; claws wait on it.
- **Horns and antlers.** `horn`, role horn: a tube tapering to a pointed cap, leaning back, with no joint, riding the bone it sits on; a curve comes from Move's lean, since an axis bend on a part resting near vertical bends it forward and moves Start from template's outline past its tolerance. `antler`, role horn: a thinner tine with a round tip, a `tip` socket and a `tine` socket on its front, neither naming what it takes, so no planned antler grows on every tip and Add part offers antler among everything. Role horn gets a tile of its own in Add part.
- **Rounds are serial,** each its own commit with `./gradlew check` passing, a smoke render looked at before it is committed.

## Rounds

| Round | Delivers | Creature |
| --- | --- | --- |
| 1 `wing-membrane` | `along` on a membrane pair, `limits` on a digit override, the schema regenerated; the `wing-membrane` template and its golden; Curl on Pose's limb section for a limb whose own part has digits; unit tests that the skin rides the arm and a whole curl brings the fingers together | the dragon: a canine-plan body drawn long and low, a long tail, a snouted head with small pointed ears, membrane wings on a paired hand-made socket on the shoulders, red with a pale belly and darker wing skin; rendered in Shape, with a wing spread by its wrist in Pose and with a wing folded by Curl |
| 2 socket rows | hand-made rows (`count`, `to`, `size`, `falloff` on a hand-made socket) and `scale` on sockets; Move's Count, First size and Last size; Add part filling a row; `spike` and `plate`; unit tests on the row's sockets, sizes and Add part | the dragon with spikes down its back, large at the shoulders, and smaller down its tail to its tip |
| 3 tip sockets, horns and claws | Built: `horn` and `antler`, an antler chaining through its tip socket, the horn tile art. Not built: `tips` on the toe row, `patch` on a socket, placement and the rig's carrier for a socket on a digit, `claw`, `paw-4` with claw tips | the dragon with a pair of horns on its crown; claws on its toes wait on digit tip sockets |

## Exit criteria

1. **A membrane wing.** The dragon's wings build their webs and skin through the slice build with the template's topology and mesh invariants on every web; they rig, pose by the wrist without planting, take lines and two-tone shading, and fold at a whole Curl with every finger within a few degrees of the others.
2. **A row of spikes.** A row's sockets lie along the parent's top, evenly spaced, each part placed on them sized by the falloff; Add part on one fills the row in one step.
3. **Tips and horns.** Horns sit on the head and turn with it, and an antler on an antler's tip continues it. Still open: a claw on every toe's tip following the toe's curl.
4. **Stored characters survive.** Every earlier template version builds as before; new versions add a row or tips that are off or empty by default.
5. **They read as a dragon** in the smoke renders, judged by eye.
6. **Tiers hold.** `./gradlew check` passes; goldens are regenerated on purpose.

## Out of scope

A membrane rail on another part (a bat's skin running down to its leg), a thumb claw on the wing's leading edge, a mane of hair, a row block in templates and rows on shipped templates, a row along a curve rather than straight along u, a row that resizes parts already placed when its falloff changes, ribbed or veined membranes beyond marks, a bat plan.

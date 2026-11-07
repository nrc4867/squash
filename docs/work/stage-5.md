# Stage 5: any shape

A working note for the sessions that build stage 5. Folded into the HLD or deleted when the stage lands.

## Goal

A character is whatever its parts make it, not one of the shipped body plans. Any part takes an extra part anywhere on its surface, so a fox grows wings. A character starts from nothing when wanted: an empty plan and generic parts whose anatomy is placed by hand, so a fish, a dragon or a creature with no name is drawn the same way as a fox. The species plans are presets over those pieces, and more of them ship: a biped, a bird, a lizard, a fish. Everything downstream, the rig, the pose tools, lines, marks and fur, works per part and per role, never per species.

That is `docs/hld/parts.md`, Blank mode and its "What this adds to the data model", which this note schedules.

## What the design already holds

`parts.md` names the pieces: Blank as a plan with one root part; generic `tube`, `blob` and `sheet` templates over existing blocks; anatomy by hand through Move (joints, sockets, landmarks, pair flags); re-templating that keeps outlines; Save as template; the per-template view remap, the membrane block, the socket row and the tip socket; the later template files (`arm`, `hand`, `wing-feathered`, `wing-membrane`, `beak`, `fin`). `data-model.md` says a patch may be open in v or closed with poles, and that anchors, weights and lines address patches by name, u and v, so a sheet or a blob needs nothing of them. The Move tool already writes `move.` parameters per part and stores a socket's place on its parent, which is where a hand-made socket goes too.

What it does not hold: roles. Stage 4 skipped lego-viewer's Roles step because a template is a role: a `leg` ends in a paw that plants, a `tail` curls, a `head` looks. A hand-placed generic part is nothing until it is told, and the Pose tools (pins, the tail arc, the look-at, the chain the body curve bends) and Add part (which templates a socket takes) read roles today through the plan.

## Decided

- **Roles are a part's own value,** `role` on the part instance, with a closed list in the data model (body, spine, neck, head, jaw, ear, arm, hand, leg, foot, tail, wing, fin, horn, other), set by a command. A typed template sets the role when the part is added. A generic part's role is guessed from where it sits (a tube hanging from the root's rear end is a tail, one reaching the ground is a leg, one on the head's top is an ear or a horn) and shown as a chip in the Shape inspector that opens the list; a guess the user has not confirmed is drawn as a guess. Pose, Add part and the rig read roles, never template ids.
- **A hand-made socket is a part parameter on its parent,** beside the `move.socket.` parameters the Move tool writes, so pairs, overrides, undo and saving treat it like any other; it has no `takes` list, so Add part offers every template.
- **Order:** extra parts on an existing character first, Blank mode second, plans third. Each round delivers one creature as an example file with a fixture and a golden, the fennec's way.
- **Rounds are serial.** Each builds on the last and each touches the Shape stage's inspector and Add part.

## Before each round

The orchestrator draws the round's boards on Squash flow of work · restructure before any code (the status protocol), as stages with a strip and one inspector, and writes the verify steps into `docs/work/boards.md` at "building".

## Rounds

| Round | Branch | Delivers | Creature | Boards |
| --- | --- | --- | --- | --- |
| 1 | `roles` | Built: `role` on parts, the closed list, `SetRole`, the guess for a part whose template gives no role, the chip in Shape's inspector and the guess's chip in Parts; Pose's pins, tail arc, look-at, the body curve's chain, the Face stage's head and Add part's tile art read roles; Add part reads a socket's `takes` or, with none, everything. The fennec's front limbs store arm and hand and read Front leg and Front paw | none; the fennec's roles come from its templates and every pose test still passes | Shape inspector: the role chip |
| 1 | `add-socket` | Built: Add socket on the Move tool, a press on a part's surface placing a socket at (u, v) with an outward angle and a pair flag, stored on the parent as `socket.<n>.` parameters; dots for hand-made sockets like the plan's, on both sides when paired; Add part on one, titled Torso · socket 1, lists every template; Delete socket for a picked one that holds nothing; Pose drags a wing by its wrist and never plants it | wings on the fennec: the `wing-feathered` template (one tube with shoulder, elbow and wrist, the `lens` section, Top setting chord and Side thickness) on a paired hand-made socket on the torso's shoulder, folded along the flanks, fur with a cream tip and feather lines; `examples/fennec-winged.json`, written by `FennecWingedFixtureTest` | W1 Shape with Move's Add socket; W2 Add part on a hand-made socket |
| 2 | `generic-parts` | Built: the `tube`, `blob` and `sheet` templates over existing blocks (a blob a skull run with poles; a sheet a lens tube run open in v, `openV`), each with one bone end to end and no role; the `generic` template field and Shape's Generic section, where a part chooses its section profile, views, rings and segments, each one `SetParameter`; the Blank plan after the species plans in New character, one Body tube whose open end closes on the root; Add part's tiles for the three; the guess says fin for a sheet on the body's top or sides. A generic tube rests forward, so a tube on the rear end guesses other | a koi: a tube body drawn in Side and Top, sheet dorsal, pectoral pair, anal and tail fins and a blob eye pair on hand-made sockets, orange with a white belly, black patches, paler fins and dark pupils; a Side stroke of the body curve tilts the body and lifts the tail fin, one bone each; `examples/koi.json`, written by `KoiFixtureTest` | W10 New character with Blank; W11 Shape's Generic section |
| 2 | `anatomy-by-hand` | Built: Move's modes as Place part · Add socket · Add joint · Add landmark; a joint at a press's u on the axis with hinge, limits and stretch and a landmark at a press's u and v, held as `joint.<n>.` and `landmark.<n>.` parameters (`HandJoints`, `HandLandmarks`) that `asBuilt` makes blocks, with a `tip` at the end, so they are bones the rig, Rig stage and Pose read; a generic part's own rest direction, `move.rest`, so a tube on the rear rests back and is guessed tail; the Anatomy section listing joints and landmarks with deletes, Pair with… (`JoinPair`, the right side's own data kept as overrides) and Unpair; Save as template writing the part into `templates` in the app data folder, loaded after the shipped templates; the Template menu, one `SetTemplate` keeping outlines; Add eyes about a hand-made landmark. The koi's body has three hand-made joints, so the body curve bends it along its length | a lizard: a tube body with four hand-made joints, a tube tail resting back on a rear socket with two, four tube legs on paired side sockets resting down with a knee and a tube foot on an end socket, a blob head with blob eyes and a landmark, green with a pale belly and dark stripes; a leg saves as `lizard-leg` in a test; `examples/lizard.json`, written by `LizardFixtureTest` | W12 Shape with Move's Add joint and the Anatomy section |
| 3 | `biped-plan` | Built: the `arm`, `hand`, `foot`, `head-round` and `torso-upright` templates; the toe row's per-digit override (`digits`: span, place across, angle), the hand's thumb; the `biped` plan over an upright torso, listed between Canine quadruped and Blank as Two legs, two arms, upright with a standing figure; an upright character's arms reach with a dot and no pin, its feet plant, its limbs read Left foot and Right hand and its status counts feet; the body curve keeps a forward-facing head's turn from the spine; Add eyes reads a template's `eye` landmark. Not yet: three joints a finger and a tip socket per digit | a person: a stocky torso drawn in Side and Front, a round head with a chin, arms, legs, hands with a thumb, shoes, hair as a blob on a crown socket placed by hand, two eyes from Add eyes and a smile, a shirt region over trousers; `examples/person.json`, written by `PersonFixtureTest` | boards 1 and 2 as they stand, with the new plan |
| 3 | `bird-plan` | `beak` on the muzzle socket, a bird plan over `wing-feathered`, two legs and a tail | a toucan | the same |
| 3 | `fish-plan` | `fin` as a named sheet template, a fish plan over the koi's parts | the koi again, from its plan | the same |

Later, not in stage 5: `wing-membrane` and the membrane block (dragons and bats), the socket row (spines and frills), the tip socket (antlers), horns and claws as templates.

## Exit criteria

1. **Roles, not species.** No code outside the templates and plans switches on a template id. The pose tools, Add part and the rig read `role`. A test holds it: the fennec with every template id renamed poses the same.
2. **Any part anywhere.** A socket placed by hand on the fennec's torso takes a wing, the wing rigs, poses, takes lines and strokes, mirrors as a pair, and survives Update from library of the torso. The winged fennec is an example file.
3. **From nothing.** A character started from Blank with one tube is drawn, built, rigged and posed with no template but the generic ones. The koi is an example file, and its fins are real sheet meshes through the slice build that take anchors, lines and the two-tone shading.
4. **Anatomy by hand.** A tube given a joint and a socket by Move behaves as a leg: the joint bends in Pose within its limits, the socket takes a part. Save as template writes a file that loads and lints clean.
5. **Plans are presets.** The biped, bird and fish plans are data files over the templates, and the person, toucan and koi are example files, each built from commands by a fixture.
6. **Boards match** under the Build to the boards rule.
7. **Tiers hold.** `./gradlew check` under three minutes on this machine without the smoke tier; the smoke tier renders each example creature posed.

## How the sessions run

As stage 4: one orchestrating session per sitting, boards before code, one worktree branch per row landed by rebase, squash and fast-forward with `check` before each landing, a creature fixture and golden per round. Rounds are serial; the two rows of a round may run in parallel where the table shows them apart.

## Stretch

Nothing expected. A chip that opens a list and a segmented control exist.

## Out of scope

Membrane wings, socket rows, tip sockets, garments, the Shot workspace, Correct pose and the pose library, contact with the ground.

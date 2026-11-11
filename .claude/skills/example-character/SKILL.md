---
name: example-character
description: Make or improve an example character in Squash, dressed or not, from commands in a fixture, with its golden example file and smoke renders to judge it by. Use before adding or reshaping anything under examples/.
---

# Make an example character

An example character is a list of document commands in a test fixture, folded over an empty document, saved as `examples/<name>.json` by a golden test, and judged by smoke renders. Nothing in the engine changes. A new character is an original model: draw its outlines and proportions yourself, never reshape another example's data; use the other examples' files for structure only. Read `docs/work/example-limits.md` first: it lists what Squash cannot do yet, and every place below that still needs a source file (marked **gap**).

## Files

1. A package under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/<name>/`, one responsibility a file, each under about 300 lines. The woman's is the model: `Woman.kt` (empty document, `womanCommands()`, `womanDocument()`, `savedWoman()`), `WomanBody.kt`, `WomanHead.kt`, `WomanColor.kt`, `WomanFace.kt`, `WomanFaceMap.kt`, and for clothes `Wardrobe.kt` (slots and the `garment()`, `owned()`, `ownedPair()` and `propShape()` helpers), `Tops.kt`, `Bottoms.kt`, `Dress.kt`, `Shoes.kt`, `Pumps.kt`, `Accessories.kt`, `Shades.kt`, `Suit.kt`, `Belt.kt`, `WomanDressed.kt` (outfits).
2. Fixture tests under `screens-core/src/test/kotlin/dev/chieppa/squash/screens/<name>/`, modelled on `woman/WomanFixtureTest.kt` and `WomanDressedFixtureTest.kt`: the saved file equals what the commands make, roles are chosen, it assembles and lints, and for clothes what each outfit wears.
3. Smoke tests under `app/src/test/kotlin/dev/chieppa/squash/screens/smoke/`: `WomanSmokeTest.kt` (whole views per outfit, poses, close-ups through `WomanViews` and `CloseUps`) and `WomanShapeSmokeTest.kt` (the four panes in Shape and in Pose).
4. The example files: `examples/<name>.json` (and `-dressed.json`), written by the golden test, never by hand.
5. The `-PupdateGoldens` line in `CLAUDE.md` names every example file; add yours.

## Commands

JDK 25: `JAVA_HOME=~/.jdks/corretto-25.0.1`.

- `./gradlew :screens-core:test --tests '*<Name>*' -PupdateGoldens`: write the example files. About 40 s. Run again without the flag to check them.
- `./gradlew :app:look -Plook="example=<name>"`: render through the real graph with no test runner, every outfit in its display pose (`<outfit>.png`), at rest when that differs (`<outfit>-rest.png`) and in the example's named poses from the front, three-quarter and side, with face, hand and foot close-ups, into `app/build/look/<name>/`. Options: `outfits=a,b`, `poses=a,b`, `close=face,hand,foot`, `file=true` to read `examples/<name>.json` instead of the fixture's commands. About 30 s for the dressed woman's 36 images; needs the GPU. Named poses are listed per example in `screens/look/LookSubjects.kt`; a new example is added to its fixture map.
- `./gradlew :app:measure -Pmeasure="example=<name>"`: print the checks per outfit as numbers: arm volume inside the torso at rest and as shown, hip socket spread, the share of the face in shade per view, the smallest gap between digit tips, socket ring length (all and seen), the head's profile depth brow to chin and its deepest notch, skull vertices seen outside the hair, bare skin seen inside a garment's shell, the vertices of parts a garment owns that lie under cloth, how far each owned part strays from moving rigidly in each named pose, and the shading terminator per view with its blotches. No GPU.
- `./gradlew :app:test --tests '*<Name>SmokeTest*' -Pslow`: the smoke tier's renders, kept for regressions. Images land in `app/build/smoke-images/<image>.png`.
- `./gradlew check`: before landing, 2 to 4 minutes. The smoke tier is not in it.

## Two routes: templated or template-free

Pick per part, with the renders as judge.

- **Template-free** (Blank plan): `tube`, `blob` and `sheet` on hand-made sockets, with hand-made joints and landmarks. Use it wherever a template's built shape fights the figure: torso, limbs, neck, hair. You draw every outline; `generic.rings` up to 96 and `generic.segments` up to 32 make it fine. Costs (see the limits note): no blend, one hinge a joint, the tube's root joint keeps ±60, and `PartRoles.upright` ignores `move.rest`. A hand-made socket sinks once unless its `socket.<n>.sink` says more (`HandSockets.add(..., sink =)`).
- **Templated**: a template earns its place only for blocks no generic part has: `hand-3` and `foot-2` for digits with joints, thumb, webs and toes; `head-round` for a head: version 2 stands upright on the neck's end, faces forward for the face bins, gaze and Look, reads Side as a profile and carries a neck joint and an eye landmark (version 1, resting forward with its face on the pole, still builds the person). The newest version of each template is the highest `version` among `templates/<id>*.part.json`; `ref(id)` in `fennec/FennecBody.kt` picks it.
- Plans: `templates/plans/*.plan.json`. A templated character starts on a species plan (biped, bird, canine, fish); a template-free one on `blank`. Copy the plan's palette into the empty document as `woman/Woman.kt` does.

## Order of commands

1. Root part: `AddParts(PartInstance(id, ref, role = Role.BODY))`. A generic root rests forward; turn it with `RestDirection.restIn(part, UP)`.
2. Sockets on the parent before parts on them: `HandSockets.add(part, n, u, v, outward, paired, sink)`, named `HandSockets.name(n)`. **Gap**: parameter names live in `character/.../build/HandSockets.kt`, `HandJoints.kt`, `HandLandmarks.kt`, `GenericShape.kt`, `Placement.kt`.
3. Children: `AddParts` with `SocketRef(parent, socket)`. A generic part is added with `roleGuessed = true` and its role set after with `SetRole`, as Add part does. A pair is one `AddParts` with both sides (`pair = PairId`, `side`), and every later command names the left side only; the right mirrors it.
4. Shape: `SetOutlines(part, mapOf(View.SIDE to ..., View.TOP to ..., View.FRONT to ...))`, `SetParameter` for `size`, `move.outward`, `move.lean`, `generic.*`, joints with `HandJoints.add`.
5. Color: `SetPaletteSlot`, `SetBaseSlot`, `SetRegion`. Then face and marks (`SetMark`, `SetMarkGroup`). Then the display pose, `SetDisplayPose`. Then the wardrobe.

## A-pose and the display pose

- A biped is built, weighted and dressed in A-pose: arms 45 degrees out (`move.outward` 45; on the biped plan Add part starts them there from the shoulder's `spread`, which a fixture passes as the `AddParts` pair data), legs straight down from the hips. Garments are drawn over that pose.
- The display pose, saved with the character, brings the arms down (`woman/WomanPoses.kt` `WOMAN_DISPLAY`: a lean on the arm's root bone, `base` on a tube, `shoulder` on the `arm` template, and a free turn at the elbow for the palms). The session shows the working pose over it: a pose names only the bones it changes, and naming a display bone replaces it.
- The posed upper arm is pushed out of the torso (not out of garments, which hide what of it lies inside them) and the push lets go toward the elbow, so leave the arm a little out from the hips in the display pose (about 15 to 25 degrees) rather than straight down, or the forearm sinks into the hips.
- Necks, arms, legs, hands and feet are joined to what they sit on by strips: the tube is trimmed at a ring clear of the part it enters and a faired strip bridges to that part's surface, at rest and again in every pose. So draw a limb without a shoulder or hip bulge of its own at its root (the strip makes the shoulder), and a sleeve or trouser leg all the way round follows the strip. `join.ink` 1 on the joined part draws a line round the join; unset, it reads as one surface.
- A garment never moves a socket: a Redraw that reshapes a part leaves what hangs from it where it hangs on the bare part, carried along only by the socket u the Redraw gives. Do not turn limbs in or out per outfit to clear a garment.

## Drawing outlines

- `p(x, y)`, `tuft(x, y)` (a corner), `loop(...)` (closed) and `drawn(...)` (open) are in `fennec/Pen.kt`.
- x runs along the part's axis from its socket end; the drawn length is the part's length (`size` scales it). y is across: Side sets height, Top sets width, Front (where the template's views say `section`) sets the cross-section for the whole part, drawn in -1 to 1.
- Which way Side's y points: up for a part resting forward or back; forward for a part resting up or down (the part frame's up is body forward when its axis is near vertical). v = 0 lies on that up side; a hand-made socket's v reads the left side (the woman's paired sockets sit at v 0.75 on her torso and 0.68 on her head, both resting up). On a part resting out, v = 0 is up, 0.25 back and 0.75 front. **Gap**: `geom/.../PartAxes.kt`.
- A loop's y need not be symmetric: front and back are set apart, so a belly, a seat or a bust is drawn in Side.
- Draw a toe or finger run as wide as the palm or foot: digits space themselves by the drawn width.
- End the outline at the template's length on templated parts with a flat end (`foot-2` ends at 0.42).

## Face

- `head-round` version 2 stands upright, its axis from the chin to the crown; its axis start sits on the socket, so put the neck's end socket at `sink = 0f` and draw the head from the neck's top, with the chin a little below it so the head's underside covers the neck's end.
- Side is the profile, forward up in the drawing: up the front from the chin over the lips, the nose and the brow to the crown, down the back of the skull and forward under the jaw. Each height crosses it twice, so the jaw's underside is a floor from chin to nape. A bump of the profile shorter than about 0.3 of the head's height (a nose, lips, a chin) stands out as a narrow ridge; a longer one (a muzzle) swells the whole front. Draw a nose in the profile, not as a part.
- Top is the face's width up from the chin (the Front pane shows it); Front is the section seen from above (the Top pane shows it), face up: broad across the front, round behind.
- Place marks with `FaceMap.of(head, template, PartData(outlines, size))` (`character/.../build/FaceMap.kt`): `on(x, y)` is the anchor a front view shows at x to her left and y up, in the head's drawn units from the neck's top; `left = false` mirrors it; `ring(...)` a closed ring. `woman/WomanFaceMap.kt` builds hers, and the shades' bridge socket is placed by it too. Build the map from the same outlines the head is drawn with.
- An eye is a group with seven roles (lid, white, iris, pupil, highlight, brow, lashes), each a mark; the lid clips white, iris and pupil; set a role's `fill` to a palette slot. `woman/WomanFace.kt` and `fennec/FennecMarks.kt` (versions per camera bin) are the models. **Gap**: `document/.../MarkRole.kt`, `MarkGroupShapes.kt`.
- Lips, blush and shadow are regions; ink strands on hair are marks.
- Hand-made sockets on the upright head read v = 0 at the face, 0.5 at the back and 0.75 at her left; a socket's `sink` moves a hair part's root in or out along the skull's normal, below 0 standing it off.

## Dress

- `wardrobeSlots()`, then per garment `AddGarment`, `SetGarmentColor`, `CoverPart(garment, GarmentBranch(part, from, to, ease, outlines, fromV = , toV = ))`, `SetBodyPartChange`. The `garment()` helper in `woman/Wardrobe.kt` does all four.
- `from` and `to` are u along the covered part; `to` may run past 1 to continue past the part's far end, `from` below 0 to run on before its start. `fromV` to `toV` spanning less than 1 covers a region (an open front, a sole, a cap), crossing v 0 when `fromV` is negative. `ease` is how far the shell stands off. `layer` counts out from the skin: under the waistband 1, waistband 2, over both 3.
- Changes per covered part: `Through(cuff = false)` for an armhole or neckline, `Through(cuffSize)` for a collar, `Redraw(PartData(...))` laid over the part (fewer toes, a moved arm, a dark lip region by the same region id), `Swap(templateRef, PartData)` for a closed shoe, `Hide`. A part sitting on a socket inside a branch goes through with a cuff unless changed.
- A skirt is a branch over the torso that hangs free: `from` below 0 by more than 0.1 runs it down past the torso's start, and the legs on the hips inside it hang in it, covered with their hip strips, with no hole or cuff. Draw its Side and Top outlines along the torso's own x, negative below the torso, from the hem up to the waist, wide enough to clear the legs at rest; where the drawing lies inside the torso the ease holds. After every pose the hem is pushed out wherever a leg would cut it and the push spreads round and down, so do not redraw the torso into a bell (`woman/Dress.kt`).
- Set the fillet small: the default is 0.35 of each child branch's length. `garment()` sets it to 0.045 body units.
- Rigid things a garment carries (shades, heels, buttons, a buckle, lapels, a brim) are parts it owns: a hand-made socket on the body part they sit on (`HandSockets.add(part, n, u, v, 0f, paired, sink = 0f)`; a sink of 0 seats the part on the surface, the default sinks it along the normal), then `AddParts` with `PartInstance(..., role = Role.OTHER, garment = id)` after the garment exists (`owned()` and `ownedPair()` in `woman/Wardrobe.kt`), then the part's rest, outlines, size and base slot as for any generic part (`propShape()`), and regions for a second colour (the lens inside the frame in `woman/Shades.kt`). A pair on one paired socket gives mirrored halves; parts on an owned part's socket must be owned by the same garment. They build only while the garment is worn, ride the bone they sit on in every pose, are never cut or hidden by garments, and read a socket on a swapped part as the shoe shows (`woman/Pumps.kt`). Side moves a part's middle up and down; Top sets only its width, so draw a lapel's slant with `move.outward` and its stand-off over the bust in Side (`woman/Suit.kt`). Check them with measure's `owned part vertices under cloth` and `owned parts' drift from rigid`.
- `AddOutfit`, `PickGarment(outfit, slot, garment)` per slot, `ShowOutfit`. Share garments between outfits by picking them in several. **Gap**: `document/.../Garment.kt`, `BodyPartChange.kt`, `Worn.kt`.

## Render and look

The look command above is the authoring loop. In a smoke test:

1. `CloseUps.session(EMPTY, commands)` then `SessionPose(session)` builds the rig the posed views need.
2. `WomanViews.keep(session, name)` renders the whole character from the front, three-quarter and side, with face versions from the head's angle; pass `framed = setOf(HEAD, ...)` for a close-up. `CloseUps.keep(session, part, name)` shows one part and its parent from six sides; `keepAround` the whole character framed on a part.
3. `session.send(ShowOutfit(id))` and `session.send(SetPose(Pose(listOf(BonePose(part, bone, degrees)))))` between renders, in one session. Bone names: a tube's root `base`, hand-made `joint.<n>`, templates' own (`neck`, `wrist`).
4. Read every PNG. Compare with `docs/images/fennec.png`.

## Loop that worked

Draw the body first and render bare from three sides; fix proportions; then the face close-up; then hand and foot close-ups; then one garment at a time with the whole views. Change one thing a run. Keep the image you judge by in the same session as the commands that made it.

## Gotchas

- A missing example file fails the golden test: run with `-PupdateGoldens` once, then without.
- `--tests` takes the class's simple name with a wildcard: `'*WomanSmokeTest.poses'`, not `'*WomanSmoke.poses'`.
- A hand-made socket centres the child on the parent's surface, sunk once by default; the strip trims whatever of the child's root stands outside, so no ring shows. `socket.<n>.sink` sets it deeper; an A-pose arm wants it shallow, or its top lies inside the torso. A child that runs inside its parent for more than 0.6 of its rings gets no strip and overlaps.
- A shoe branch over `foot-2` is open at the toe; Swap the foot for a blob. A region on a Swap's data stays where it lies at rest when the foot is posed, so leave a swapped shoe one colour.
- A garment shared between outfits reads u on the part as other garments leave it; a Redraw that changes a part's length moves every other garment's range.
- Wait on the graph, never a timer: `work.settle(session)` (inside `CloseUps`), `settle(work) { ... }` in a UI test.

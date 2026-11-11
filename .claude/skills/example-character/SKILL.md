---
name: example-character
description: Make or improve an example character in Squash, dressed or not, from commands in a fixture, with its golden example file and smoke renders to judge it by. Use before adding or reshaping anything under examples/.
---

# Make an example character

An example character is a list of document commands in a test fixture, folded over an empty document, saved as `examples/<name>.json` by a golden test, and judged by smoke renders. Nothing in the engine changes. A new character is an original model: draw its outlines and proportions yourself, never reshape another example's data; use the other examples' files for structure only. Read `docs/work/example-limits.md` first: it lists what Squash cannot do yet, and every place below that still needs a source file (marked **gap**).

## Files

1. A package under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/<name>/`, one responsibility a file, each under about 300 lines. The woman's is the model: `Woman.kt` (empty document, `womanCommands()`, `womanDocument()`, `savedWoman()`), `WomanBody.kt`, `WomanHead.kt`, `WomanColor.kt`, `WomanFace.kt`, `FacePlane.kt`, and for clothes `Wardrobe.kt` (slots and the `garment()` helper), `Tops.kt`, `Bottoms.kt`, `Dress.kt`, `Shoes.kt`, `Accessories.kt`, `WomanDressed.kt` (outfits).
2. Fixture tests under `screens-core/src/test/kotlin/dev/chieppa/squash/screens/<name>/`, modelled on `woman/WomanFixtureTest.kt` and `WomanDressedFixtureTest.kt`: the saved file equals what the commands make, roles are chosen, it assembles and lints, and for clothes what each outfit wears.
3. Smoke tests under `app/src/test/kotlin/dev/chieppa/squash/screens/smoke/`: `WomanSmokeTest.kt` (whole views per outfit, poses, close-ups through `WomanViews` and `CloseUps`) and `WomanShapeSmokeTest.kt` (the four panes in Shape and in Pose).
4. The example files: `examples/<name>.json` (and `-dressed.json`), written by the golden test, never by hand.
5. The `-PupdateGoldens` line in `CLAUDE.md` names every example file; add yours.

## Commands

JDK 25: `JAVA_HOME=~/.jdks/corretto-25.0.1`.

- `./gradlew :screens-core:test --tests '*<Name>*' -PupdateGoldens`: write the example files. About 40 s. Run again without the flag to check them.
- `./gradlew :app:test --tests '*<Name>SmokeTest*' -Pslow`: render. About 20 to 60 s warm for a dozen images; needs the GPU. Images land in `app/build/smoke-images/<image>.png`.
- `./gradlew check`: before landing, 2 to 4 minutes. The smoke tier is not in it.

## Two routes: templated or template-free

Pick per part, with the renders as judge.

- **Template-free** (Blank plan): `tube`, `blob` and `sheet` on hand-made sockets, with hand-made joints and landmarks. Use it wherever a template's built shape fights the figure: torso, limbs, neck, hair. You draw every outline; `generic.rings` up to 96 and `generic.segments` up to 32 make it fine. Costs (see the limits note): no sink, no blend, one hinge a joint, the tube's root joint keeps ±60, and `PartRoles.upright` ignores `move.rest`.
- **Templated**: a template earns its place only for blocks no generic part has: `hand-3` and `foot-2` for digits with joints, thumb, webs and toes; `head-round` for a head centred on its neck (`mount`), a mid-head neck joint, a jaw and an eye landmark, and a facing the face bins and gaze read. The newest version of each template is the highest `version` among `templates/<id>*.part.json`; `ref(id)` in `fennec/FennecBody.kt` picks it.
- Plans: `templates/plans/*.plan.json`. A templated character starts on a species plan (biped, bird, canine, fish); a template-free one on `blank`. Copy the plan's palette into the empty document as `woman/Woman.kt` does.

## Order of commands

1. Root part: `AddParts(PartInstance(id, ref, role = Role.BODY))`. A generic root rests forward; turn it with `RestDirection.restIn(part, UP)`.
2. Sockets on the parent before parts on them: `HandSockets.add(part, n, u, v, outward, paired)`, named `HandSockets.name(n)`. **Gap**: parameter names live in `character/.../build/HandSockets.kt`, `HandJoints.kt`, `HandLandmarks.kt`, `GenericShape.kt`, `Placement.kt`.
3. Children: `AddParts` with `SocketRef(parent, socket)`. A generic part is added with `roleGuessed = true` and its role set after with `SetRole`, as Add part does. A pair is one `AddParts` with both sides (`pair = PairId`, `side`), and every later command names the left side only; the right mirrors it.
4. Shape: `SetOutlines(part, mapOf(View.SIDE to ..., View.TOP to ..., View.FRONT to ...))`, `SetParameter` for `size`, `move.outward`, `move.lean`, `generic.*`, joints with `HandJoints.add`.
5. Color: `SetPaletteSlot`, `SetBaseSlot`, `SetRegion`. Then face and marks (`SetMark`, `SetMarkGroup`). Then the wardrobe.

## Drawing outlines

- `p(x, y)`, `tuft(x, y)` (a corner), `loop(...)` (closed) and `drawn(...)` (open) are in `fennec/Pen.kt`.
- x runs along the part's axis from its socket end; the drawn length is the part's length (`size` scales it). y is across: Side sets height, Top sets width, Front (where the template's views say `section`) sets the cross-section for the whole part, drawn in -1 to 1.
- Which way Side's y points: up for a part resting forward or back; forward for a part resting up or down (the part frame's up is body forward when its axis is near vertical). v = 0 lies on that up side; a hand-made socket's v reads the left side (the woman's paired sockets sit at v 0.75 on her torso, resting up, and 0.27 on her head, resting forward). **Gap**: `geom/.../PartAxes.kt`.
- A loop's y need not be symmetric: front and back are set apart, so a belly, a seat or a bust is drawn in Side.
- Draw a toe or finger run as wide as the palm or foot: digits space themselves by the drawn width.
- End the outline at the template's length on templated parts with a flat end (`foot-2` ends at 0.42).

## Face

- `head-round` wraps its face round the front pole: v is the angle from up round the nose, u falls away from it. Draw the eyes, brows, lashes, nose, lips and cheeks on a flat face and map them with `woman/FacePlane.kt`.
- An eye is a group with seven roles (lid, white, iris, pupil, highlight, brow, lashes), each a mark; the lid clips white, iris and pupil; set a role's `fill` to a palette slot. `woman/WomanFace.kt` and `fennec/FennecMarks.kt` (versions per camera bin) are the models. **Gap**: `document/.../MarkRole.kt`, `MarkGroupShapes.kt`.
- Lips, blush and shadow are regions; ink strands on hair are marks.

## Dress

- `wardrobeSlots()`, then per garment `AddGarment`, `SetGarmentColor`, `CoverPart(garment, GarmentBranch(part, from, to, ease, outlines, fromV = , toV = ))`, `SetBodyPartChange`. The `garment()` helper in `woman/Wardrobe.kt` does all four.
- `from` and `to` are u along the covered part; `to` may run past 1 to continue past the part's far end, never before its start. `fromV` to `toV` spanning less than 1 covers a region (an open front, a sole, a cap), crossing v 0 when `fromV` is negative. `ease` is how far the shell stands off. `layer` counts out from the skin: under the waistband 1, waistband 2, over both 3.
- Changes per covered part: `Through(cuff = false)` for an armhole or neckline, `Through(cuffSize)` for a collar, `Redraw(PartData(...))` laid over the part (fewer toes, a moved arm, a dark lip region by the same region id), `Swap(templateRef, PartData)` for a closed shoe, `Hide`. A part sitting on a socket inside a branch goes through with a cuff unless changed.
- Set the fillet small: the default is 0.35 of each child branch's length. `garment()` sets it to 0.045 body units.
- `AddOutfit`, `PickGarment(outfit, slot, garment)` per slot, `ShowOutfit`. Share garments between outfits by picking them in several. **Gap**: `document/.../Garment.kt`, `BodyPartChange.kt`, `Worn.kt`.

## Render and look

1. `CloseUps.session(EMPTY, commands)` then `SessionPose(session)` builds the rig the posed views need.
2. `WomanViews.keep(session, name)` renders the whole character from the front, three-quarter and side, with face versions from the head's angle; pass `framed = setOf(HEAD, ...)` for a close-up. `CloseUps.keep(session, part, name)` shows one part and its parent from six sides; `keepAround` the whole character framed on a part.
3. `session.send(ShowOutfit(id))` and `session.send(SetPose(Pose(listOf(BonePose(part, bone, degrees)))))` between renders, in one session. Bone names: a tube's root `base`, hand-made `joint.<n>`, templates' own (`neck`, `wrist`).
4. Read every PNG. Compare with `docs/images/fennec.png`.

## Loop that worked

Draw the body first and render bare from three sides; fix proportions; then the face close-up; then hand and foot close-ups; then one garment at a time with the whole views. Change one thing a run. Keep the image you judge by in the same session as the commands that made it.

## Gotchas

- A missing example file fails the golden test: run with `-PupdateGoldens` once, then without.
- `--tests` takes the class's simple name with a wildcard: `'*WomanSmokeTest.poses'`, not `'*WomanSmoke.poses'`.
- A hand-made socket centres the child on the parent's surface: half a thigh or arm top stands outside, which shows as a ring or pokes through a skirt.
- A shoe branch over `foot-2` is open at the toe; Swap the foot for a blob.
- A garment shared between outfits reads u on the part as other garments leave it; a Redraw that changes a part's length moves every other garment's range.
- Wait on the graph, never a timer: `work.settle(session)` (inside `CloseUps`), `settle(work) { ... }` in a UI test.

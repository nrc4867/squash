# Example limits

What an example character cannot get from Squash as it stands, found by building the woman (`examples/woman.json`, `examples/woman-dressed.json`, fixtures under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/woman/`), and what an author has to read source for while building one. A working note: folded into `future.md` or deleted as features close the gaps.

## Engine limits, by how much they show

Each entry: what falls short, where it comes from, and the feature that would close it.

1. **Skirts and dresses.** A branch over the torso runs on below the torso's start and hangs free over both legs, pushed out of them after every pose, so the woman's skirt is her own torso's branch (`Dress.kt`). An arm whose hand would reach into it turns out at the shoulder until it clears, at most about 20 degrees. Still missing: no sway or follow-through of its own; the push is held at the branch's far ring over its part, so a dress drawn as one branch from the hem to the neck is held at the neck and its waist can be pushed off the body. Fix: a settle for loose cloth.
2. **Joins between body parts.** Necks, shoulders, hips, wrists and ankles are joined by strips and read as one surface, in every outfit and pose. A head on a neck is joined by a strip from the neck's end to just under the jaw, and a limb seated on the surface is joined from its own start. Still overlapping: a head on anything but a neck, and any join where the child runs inside its parent for most of its length. A strip's shape has no setting of its own: how far it reaches up the tube and out over the parent are fixed shares of the tube's radius. The implicit-blend socket fillet is a template block only (`blend`, `BlendSpec`), which no shipped template has and no part can opt into. Fix: reach settings on the join.
3. **Hands.** `hand` 4 builds a blended palm, thumb pad and wrist and five digits placed one by one, apart at the tips, each with three named joints, so a pose curls each finger; the twin wears it. Still missing: the palm's shape is its parameters' and a drawn Side or Top only resizes its rings, so a palm cannot be drawn to cup; the digits' roots show a crossing line where each leaves the palm; no nails. `hand` 3, which the woman keeps, builds a box of a palm unless drawn and spaces its digits by the drawn width. Fix: a cupped palm part; digits filleted into the palm; nail regions on the digits.
4. **Closed shoes.** Over `foot` 3 a shoe is a branch over the foot with a toe box the shoe's Redraw switches on, closing over the toe; a heel pitches the foot with `move.lean` and lays its forefoot flat with `pitch`; soles are owned slabs under the footprint. Still missing: a branch's opening is one v range along its whole length, so a pump's opening is a straight-edged slot and its vamp a second garment in the straps' slot; there is no per-outfit pose, so the pitch rides on the shoe's Redraw. `foot` 2, which the woman keeps, stays open at its flat toe cap and her shoes Swap it for a blob. Fix: an opening whose v range varies along u.
5. **The face.** `head-round` version 2 stands upright with a profile in Side, a nose standing out of it as a narrow ridge, most of its rings over the face, and marks placed on a flat face map (`FaceMap`). Still missing: the head's underside is one floor from chin to nape, since each height crosses the outline twice, so the jaw cannot rise from the chin to the jaw's angle with the neck going up behind it (a strip joins the neck to it just under the jaw); the ridge's width is one share of the ring's half width for nose, lips and chin alike; there is no jaw joint that opens the chin downward, since a joint turns what lies above it on an upright head; no eye sockets, so eyes are marks on the face (an eyeball spike, blobs with lids painted on, read as goggles: the overlap's ink rings each ball). Fix: an outline with a notch from below; a ridge width drawn along the profile; a joint that turns what lies before it.

6. **Hair.** The crown is one closed blob hugging the skull and the fall and the locks are tubes overlapping it: it reads as hair from the front and as a helmet with slabs from the side. Strands are only ink lines on the surface; there is no parting, no clump, no curl. A cap that follows the hair blob reads as a hood or a headband. Fix: a hair template (clumps from a row on the scalp, each a tapering sheet), or a shell part laid over the head.
7. **Accessories with a shape of their own.** Shades, heels, buttons, a buckle and a pendant are parts a garment owns, generic blobs and tubes on hand-made sockets. Still missing: a part's Top outline sets its width only, never moves its middle sideways (Side moves it up and down), and a part rests only along the five axis directions leaned by `move.outward` and `move.lean`, so shades cannot wrap round the face and a lapel cannot curve across the chest; lenses are opaque, so the eyes do not show through them; a part rides one bone rigidly, so nothing owned can bend with a spine or drape. Studs round a belt need a socket row round a part, and a row only runs along it. Fix: Top moving the middle; a twist about the rest axis; a see-through slot; a socket row round v.
8. **Collars, lapels and openings.** The blazer's lapels are flat blobs it owns, leaned in from either side of the neck toward the button, their Side drawn forward over the bust by hand to clear the blouse and blazer. A lapel is rigid, so a spine bend would lift it off the chest. The blazer's opening is one v range the whole length, so the blouse shows as a strip below the button too, and no collar folds over the lapels; a collar is only the cuff a Through change grows round the neck. The coat's padding is one ease all round. Fix: an opening whose v range varies along u; ease that varies round and along a branch; a sheet that lies on the surface under it.
9. **Loose ends.** The scarf's loose end does not show. Its length and width are fixed shares (0.06 of the covered part a bone, a sixth of the hem round), so on the neck it is about 0.04 long; on the shoulders' band it still disappears into the padded coat under it. Fix: a loose end's length and width as its own values, and an end fitted over the layers under it.
10. **Fillets and crotch ink.** A garment's fillet is one share of each child branch's length (0.35 by default), so long sleeves and trouser legs grow fillets 0.2 to 0.3 long that balloon the crotch and the armpits. The woman sets each garment's share so the fillet is 0.045 wide (`Wardrobe.kt`); the crotch still draws a knot of crease ink where both legs' fillets meet the waistband. Fix: a fillet in body units per join; no crease ink at grazing crossings.
11. **Trousers step in under the seat.** Each branch follows only its own part, so trousers cannot fall from the seat to the thigh; the waistband over the seat ends in a step where the leg leaves the torso. A fuller drawn outline at the top of the leg branch opened a gap and a bulge instead. Fix: a garment surface lofted across the pelvis and both legs, weighted by blending their weights.
12. **Curves.** The bust is only the torso's Side outline standing forward across its whole width: no separation, nothing from the front but shading. A front section is one shape for the whole part (`views.front: section`), so the chest cannot have a different section from the waist. Fix: sections drawn at several places along a part.
13. **One hinge a joint.** Every joint turns about one axis (`HandJoints` hinge x, y or z), so a raised arm swings forward across the face rather than out to the side; a pose's lean and turn reach across the hinge only as far as the tip's nearer limit, unless the bone is free, as the woman's display pose frees her elbows to turn her palms in. A tube's own root joint keeps the template's limits (±60), so a hand-made shoulder sits just below it. Fix: ball joints, or two hinges a joint.

14. **Garments on a body on all fours.** Found dressing the cat (`examples/cat-dressed.json`, fixtures under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/cat/`). A branch part of the way round (`fromV` to `toV` under 1) reads neither its drawn Side and Top outlines nor a range past its part's ends: its shell is the grown run at its ease alone, so a cape open under the belly cannot flare or trail past the rump, and a bun open over the back cannot be drawn rounder or longer than the body. A branch the whole way round that runs past the rump of a torso resting forward hangs free, and its hem flare, a cone hinged at the branch's top, spreads into a sheet wider than the character. A branch's own surface work (marks, regions) is not painted, only its base slot, so a mustard squiggle down a sausage cannot be drawn on it. The torso's Front section shapes every garment over it, so a bun takes the body's section. Fix: shells for open branches; hanging read along the body's own down rather than the torso's axis; garment surface work painted; a section drawn per branch.
15. **A paw's toes take the plan's base slot.** On a one surface the `paw` 5 toes' run paints the plan's base palette slot where the part's base slot is another, so a white paw on a ginger cat shows ginger specks between its toes; filling the paw's `toes` area hides the specks and the beans with them. The cat names its white slot `fur`, the canine plan's base, so both agree. Fix: the toes' run reads the part's base slot.

## What the template-free route could not do

Her torso, neck, arms, legs and hair are generic parts on the Blank plan; her head, hands and feet are templates. What generic parts lacked:

- **Forelimbs are read from the template.** `PartRoles.isForelimb` (`character/role/PartRoles.kt`) reads the parent template's rest axis, not a part's `move.rest`, so on a tube body turned by Move the front half is read along the template's rest. Fix: read the placed axis, as `PartRoles.upright` does.
- **No head.** A generic blob faces along its axis (`PlacedPart.facing`, `FaceBins.kt`) and has no `faces`, so a blob resting up faces up to the face bins, gaze and Look, and reads its Side as an outline, not a profile. `head-round` version 2's face direction, profile ridge, ring focus, neck joint and eye landmark are why it stays.
- **No digits.** The toe row, its per-digit shape, joints and webs are template blocks; a generic part has none, so the hands and feet stay `hand-3` and `foot-2`.
- **No blend.** See entry 2. Plan sockets sink 1.6 to 2.2 template depths, hand-made ones 1 unless `socket.<n>.sink` says more.
- **No stance.** The `leg` template's `bearsWeight` and `stance` bend are not on a tube; poses plant by role, which works, but a four-legged reading of `bearsWeight` never sees her legs.

## What an author has to read code for

Each line: the source file opened, the fact needed, and what could have told it instead.

### Command reference and document schema

There is a schema for templates and plans (`schema/`) but none for the document or its commands, and no command reference.

- `screens-core/src/testFixtures/.../fennec/*.kt`, `lizard/*.kt`: how commands are ordered and folded, the empty document, pairs set by their left side only, and roles chosen after a guess. A how-to with one worked example would.
- `document/.../Garment.kt`: what `from`, `to`, `fromV`, `toV`, `ease`, `layer` and `fillet` mean, and their defaults. A document schema with KDoc descriptions, as the template schema has.
- `document/.../BodyPartChange.kt`, `Worn.kt`, `PartData.kt`, `SurfaceWork.kt`: that Redraw lays its data over the part's (outlines and parameters replaced by key, marks and regions by id), Swap replaces it, and the outermost garment wins. A command reference.
- `document/.../GarmentCommands.kt`, `WardrobeCommands.kt`: the list of wardrobe commands. A command reference.
- `document/.../MarkRole.kt`, `MarkGroupShapes.kt`, `Mark.kt`: an eye's seven roles, their draw order, which the lid clips, and a mark's fields. A schema.
- `character/.../face/StarterFace.kt`: what Add eyes makes and where. The Face stage's doc.

### Hand-made parameters

- `character/.../build/HandSockets.kt`, `HandJoints.kt`, `HandLandmarks.kt`, `GenericShape.kt`, `Placement.kt`: every parameter name (`socket.<n>.u`, `joint.<n>.hinge`, `generic.rings`, `move.rest` and so on), the order `RestDirection` and `SectionProfile` count in, the joint gap and the tip joint. A parameter reference generated from these objects.
- `geom/.../ClosedSpline.kt`: the section profiles behind `generic.section`'s index. The same reference.

### Template catalog

- `templates/*.part.json`, `templates/plans/*.plan.json`: sockets, joints, landmarks, axes, lengths and views, readable as JSON, but what a view's `height` or `width` means for a given rest axis, and which template version is newest, need the HLD or the code. A generated catalog: each template's newest version, its axis drawn, its sockets and joints, what its Side and Top set.
- `character/.../build/BlendSpec.kt`: that the blend is a template block a part cannot opt into. The catalog.

### Parts a garment owns

- `character/.../build/Assembly.kt`: that an owned part reads its socket on its parent as shown, so a heel's socket on a swapped foot lands on the shoe, and that a socket `sink` of 0 seats a part on the surface while the default sinks a blob by a third of its template's radius along the normal (with the default a temple on a lens's top edge, where the normal points back, starts a temple's depth in front of the lens). The HLD now says it; a parameter reference would.
- `character/.../build/GarmentBranches.kt`, `geom/.../ViewSizing.kt`: that a part's Top outline sets width but not the middle, so a lens drawn wrapping in Top draws flat. The parameter reference.
- `geom/.../PartAxes.kt`: which way v = 0.25 faces on a part resting out (back) and so where a lens region goes (v = 0.75, front). A headless command that prints the u and v at a body point.

### Orientation and measuring

- `geom/.../PartAxes.kt`: which way a part's up points (forward for a part resting up or down, up for one resting forward), and so where v = 0 lies and which way a Side outline's y runs. A doc of the part frame, or a headless command that prints the u and v at a body point.
- `rig/.../PoseChains.kt`, `character/.../role/PartRoles.kt`: what makes a character upright and which way a hinge's positive angle turns. A pose reference, or a command that prints a rig's bones and limits.
- `screens-surface/.../FaceBins.kt`, `EyeFacing.kt`: that face versions follow the head's axis and eyes more than 60° off it read their own. The Face stage's doc.
- Outline placement inside a part: that the drawn length sets the part's length, that the foot's toe end sits at its template length, that a blob starts at its socket. Only render loops found these; a headless measure of a built part's extents would.

### Rendering

- `screens-core/build.gradle.kts`: that `examples/` is the test fixtures' resource folder and `-PupdateGoldens` writes it. The skill says it now.

### The upright head

- `character/.../build/Assembly.kt` (`originOn`): that a hand-made socket's default sink of 1 carries the head on the neck's end down by the head template's depth along the end's normal, so a head drawn from the neck's top needs the neck's end socket at sink 0; and that a hair part's socket sink moves its root in or out along the skull's normal, by how much. The HLD says sinks scale the template's depth; a number per template would.
- Where parts land: `./gradlew :app:measure -Pmeasure="example=<name> parts=true"` prints each part's origin and box as shown.
- `geom/.../PartAxes.kt`: that on a part resting up v = 0.75 is her left and v = 0 her face, for the hair locks' sockets. The skill says it; a catalog would draw it.
- `character/.../lint/TemplateLint.kt`: that Front alone may set a section, so an upright head's width is drawn under Top (shown in the Front pane) and its section under Front (shown in the Top pane).

### The twin

Read while building the twin (`examples/woman-twin*.json`, fixtures under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/twin/`) from a numeric description of a reference character:

- `document/.../Pose.kt`: a `BonePose`'s fields (`degrees`, `turn`, `lean`, `mode`) and that `JointMode.FREE` lets a lean pass the joint's limits. Which way each turns was then found only by rendering trial poses: a tube's `base` with positive degrees swings an arm or a leg back, `lean` swings it out to the side, and an elbow's `turn` with its bend folds a forearm up from an arm raised to the side. A pose reference with each bone's axes drawn would.
- `templates/foot-2.part.json`, `hand-3.part.json`, `head-round-2.part.json`: the joint names a pose needs (`ankle`, `wrist`, `neck`) and the template lengths a size scales; the fingers' joints sit inside the toe row's block with no names, so no pose curls a finger. The generated catalog would, with joint names a pose can use.
- `character/.../build/FaceMap.kt`: `on`, `ring` and `of`'s signatures, since the face is drawn from numbers about a head centre rather than in steps of a drawn face. The parameter reference.
- `document/.../Mark.kt`, `MarkGroup.kt`: that a mark has its own `slot`, so a brow can be the brow colour rather than ink. A schema.
- `document/.../Palette.kt`: that a slot colour is three sRGB channels 0 to 1, so a reference's hex colour is its bytes over 255. A schema.
- `screens-core/.../woman/Wardrobe.kt`: that `garment()` sizes its fillet from the woman's limb lengths; it now takes the limb lengths. A fillet in body units per join would need none.
- No source was read for where parts land: `measure parts=true` prints each part's origin and box. It showed that a socket's default sink moved the hips 0.05 up and the shoulders 0.035 in from where the drawing put them (the twin seats every body socket with `sink = 0`), and that two garments' parts on one socket number had moved each other.

### Blended hand and foot

Read while putting the twin on `hand` 4 and `foot` 3 and her shoes over the foot:

- `rig/.../BoneTree.kt` (`hingeOf`): which part axis a digit's hinge letter names, to choose the hinge that curls a finger toward the palm. Which way positive degrees turn was found by rendering. The generated catalog with each joint's hinge drawn would.
- `geom/.../ShellBuilder.kt`, `character/.../GarmentBranches.kt`: that a branch carries on past a closed end as a tube (a nub past the toe), and that a garment has one branch a part (`branchPartId`), so a shoe open on top over the heel and closed over the toes is two garments. A garment reference, and an opening whose v range varies along u (round 10), would.
- No code was read for the hand's and foot's shapes: they come from the template's parameters and the reference's numbers, placed by the template's own axes as the HLD gives them.

### Shoulders

Read while joining the twin's shoulders, neck and head and clearing the dress's hands:

- `twin/TwinPoses.kt`, `twin/TwinBodyOutlines.kt`: the display pose's arm lean and the arm's and torso's drawn widths, to see why the posed upper arm stands further out than the reference's. The reference's arm lies partly inside its torso at the armpit; Squash pushes the posed arm out of the torso, so the twin's arm stands out by about the overlap. A print of the posed arm's ring middles beside the reference's joints would have said it.
- `character/.../build/StripPlan.kt`, `SocketStrips.kt`: which joins get a strip, found by printing each plan; the twin's limbs, seated on the surface, had none. A measure line naming each join's strip, its tube, host and ring, would have said it.

### Elbow

Read while smoothing the twin's and the woman's arms through the elbow:

- `rig/.../BoneTurns.kt` (`localTurn`): that a bone's `turn` is taken last, about its rest length, so a turn at a bent elbow swings the bend about the upper arm's line as well as twisting the forearm; the twin had aimed its elbows by a turn of 70 degrees, which twisted the forearm and pinched it at the elbow. A pose reference with each bone's axes and their order drawn would have said it.
- `twin/TwinPoses.kt`, `twin/TwinBody.kt`, `twin/TwinBodyOutlines.kt`: the elbow's pose, the shoulder socket's sink and the arm's radius keys, to tell which made the corner. The keys are the reference's own (it narrows to the elbow too), so they were not the cause. Rendering one change at a time told the rest; a measure line printing each posed arm ring's middle and radius would have said it without renders.
- No source was read for where the shoulder joint lands: `measure parts=true` printed the arm's origin, which put the twin's shoulder 0.012 m further out than the reference's joint.

### Bare body: walk turns, hips and arms

Read while turning the twin's hips and chest for the walk, moving her hip joints and widening the person's arms:

- `rig/.../BoneTurns.kt` (`localTurn`): the order a bone's angles turn it in, to know a `turn` at a bent elbow swings the bend while a roll where the bone points needs a turn taken first (now `twist`), and that a turn on an upright torso's bones is about the vertical. A pose reference with each bone's axes and their order drawn would have said it.
- `twin/TwinBody.kt`, `twin/TwinBodyOutlines.kt`: where the legs' sockets sat and where the leg started, to find why the walk's stride fell short: a tube's root bone pivots at its socket, and the twin's legs started where they leave the pelvis's underside, 0.12 m below the reference's hip joint. `measure parts=true` gave the leg's origin; a measure line printing each bone's pivot beside a reference's joints would have said it without reading.
- `app/.../compare/Silhouette.kt`: how a silhouette is keyed, to find why hand-rest and the lawyer's foot scored low: it keyed each image by its top-left pixel, which in a close-up is cloth or skin. It now keys by the background the shots share.
- No source was read for the person's arms or the woman's armpit: renders of the display pose at several leans told them.

### Joined hand

Read while joining the fingers to the palm and moving the woman to hand 5. Most were engine reads, building the joined digits themselves; the authoring reads are the last two:

- `rig/.../Weights.kt`, `rig/.../DigitCurl.kt`, `rig/.../BoneTree.kt`: how a vertex's bones come from its patch's u and from `Spanned`, to give the joined runs `Rides`, and that Curl closed each joint toward its upper limit only, so fingers whose curl is negative opened; it now closes toward the farther limit.
- `character/.../ColorShapes.kt`, `character/.../SurfaceColor.kt`, `screens-surface/.../PaneColor.kt`, `screens-surface/.../CharacterView.kt`: how an area becomes a colour shape, for the nails' ovals, and why colour layers in `look` renders lay at the parts' rest places; they now lie on the posed parts.
- `geom/.../LineGrid.kt`, `geom/.../MeshInvariants.kt`, `geom/.../RenderMesh.kt`, `character/.../PatchSurface.kt`: where contours and closed meshes are decided, to leave buried cells out of both.
- `woman/WomanBody.kt`, `woman/WomanPoses.kt`: which hand the woman pinned and the palm twist her display pose put at the elbow for hand 4; hand 5 rests palm to the thigh with no twist. The skill now says so, so the next author need not read them.
- No source was read to shape the hand: `close=hands` renders of the display pose, as built, curled and open told it.

### Joined feet and paws

Read while joining the toes to the foot's and paw's fields and moving the twin, the woman, the fennec and the dragon onto them. The engine reads first, then the authoring reads:

- `character/.../block/Digits.kt`, `JoinedDigits.kt`, `Field.kt`, `geom/.../field/BlendedField.kt`, `FieldLoft.kt`, `DigitAxis.kt`: how a joined row builds, to give it counts, claws and pads as parameters, to find that a row building no toes broke the join, and that a claw's point lay past its digit's reach and rode the paw's wrist instead of the toe.
- `geom/.../LineGrid.kt` and a printout of the field run's rings: why an ink tick sat on the heel; rays cast from a jumping middle twisted the cells near the pole into a fold. Rays are now cast again from the middle of each section's box.
- `woman/WomanBody.kt`: the woman's foot size and outlines, to size the blended foot to her. `measure parts=true` gave the box to match (0.28 long); a measure line comparing two templates' extents at a size would have given the size directly.
- `fennec/FennecBody.kt`, `dragon/DragonBody.kt`, `dragon/Dragon.kt`: how the paws were drawn and clawed (outlines on the paw, claw parts on tip sockets), to drop what the joined paw now does itself.
- `character/.../block/Field.kt`: that a joined field's run ignores drawn outlines, so the fennec's paw drawing would have shaped only its booted, toeless paw. The skill now says the paw is shaped by parameters.
- No code was read to find which way a toe's nail faces: renders of `close=feet` told it (a toe's v = 0.25 faces its side until rolled -90 degrees).

### The one-surface fennec's eyes, walk and sweater

Read while giving the one-surface fennec painted eyes, a walk and the winter sweater alone:

- `fennec/FennecMarks.kt`: the drawn three-quarter eye's place, lid, iris, brow and lash numbers, to set the painted eye's defaults to them. A face reference listing each example's eye drawing in numbers would have done.
- `fennec/FennecDressed.kt`: the ids of the body slot and the sweater, to make an outfit of the sweater alone. A `measure` or reference line listing an example's garments, slots and outfits would have done.
- The fennec test's `fennec/FennecPoses.kt`: the walk's bones and angles (the leg pairs' `hip` and `knee`, the tail's `root`, the head's `neck`), spread here over four moments of a stride. A list of each example's bone names in the skill would have done.

### The fennecs on one surface

Read while building the fennec, the dressed fennec and the winged fennec as one surface:

- `fennec/FennecMarks.kt`: the front angle version of the drawn eye (its size, roundness and iris), to set the painted eye's front look to it. The same face reference as above, listing each angle version, would have done.
- `PartTemplate.kt`, `Enums.kt` (`SurfaceMethod`) and `CharacterField.kt`: which templates stay separate pieces (`"surface": "separate"`) and how a template says so, to keep the winged fennec's feathered wings as pieces. A reference line listing each template's surface setting would have done.

### The person and the woman on one surface

Read while building the person and the woman as one surface with painted eyes:

- `character/.../face/StarterFace.kt`: the person's drawn eyes are Add eyes' starter eyes, so their lid's half width and height and the pupil's share are the painted eye's starting numbers. A face reference listing each example's eye drawing in numbers would have done.
- `character/.../surface/SurfaceEyes.kt`, `EyeShapes.kt`: that a painted eye's lengths are shares of the head's u and v at its middle, its width along u and its height along v on a head facing along its axis, and across v and up u on an upright head, so a round iris on the woman's head needs a height about one and a half times its width; that `frontNarrowing` narrows the back corner when positive; and that a shape smaller than about a cell, such as a small pupil or highlight, paints as a jagged patch or not at all. A reference for the eye's parameters, drawn on a head of each kind, would have done.
- `character/.../surface/CharacterSurface.kt`, `build/StripJoin.kt`: that a one-surface join is inked unless its part's `join.ink` parameter is 0, so the woman's neck, wrists, ankles and hips needed it set to draw as her parts did. The parameter reference.
- `character/.../surface/SurfacePaint.kt`, `ChartEasing.kt`, with a printout of the head's shares: why the woman's lips and blush did not paint. Her hair's blob lay over the skull with the default socket blend, so the hair held a third of every face vertex and the face's paint was eased away; a narrow blend at the hairline (`SetSocketBlend`) keeps the face the head's. A measure line printing each part's largest share over its own section would have said it.
- `character/.../surface/Coverage.kt`, `GarmentField.kt`: that a garment reads a part's branch only on its first run, so the sandal over the person's `foot` 2, whose heel block comes before its body, covered only the heel. The first run is now the body run where a part has one.

### The cat

Read while building the cat and dressing it:

- `character/.../surface/GarmentCover.kt`: why the cape's and the bun's drawn outlines changed nothing; a branch's shell solid is built only for a branch the whole way round (entry 14). A garment reference saying which branch fields an open branch reads would have done.
- `character/.../build/Dressing.kt` (`garmentColors`): that a branch's surface work is not painted, only its base slot, so the mustard became owned beads (entry 14). The same reference.
- `fennec/FennecMarks.kt`, `fennec/FennecColor.kt`: the head template's muzzle patch and how marks and regions anchor on it, for the whiskers, mouth and fangs. A reference drawing each template's patches and their u and v would have done.
- No code was read for the eye: renders placed it, after a first try on the skull's side (v 0.20) was not seen from the front; on the canine `head`, an eye seen from the front sits near the muzzle's root (u 0.72, v 0.17).

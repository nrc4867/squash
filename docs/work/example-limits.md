# Example limits

What an example character cannot get from Squash as it stands, found by building the woman (`examples/woman.json`, `examples/woman-dressed.json`, fixtures under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/woman/`), and what an author has to read source for while building one. A working note: folded into `future.md` or deleted as features close the gaps.

## Engine limits, by how much they show

Each entry: what falls short, where it comes from, and the feature that would close it.

1. **Skirts and dresses.** A garment branch follows one part and may only run on past that part's far end (`GarmentBranch.to` past 1); no branch spans two parts, and none runs back before a part's start. The torso's far end is the neck, so nothing hangs from the hips over both legs. The woman's skirt works round it: the skirt's Redraw makes the torso a bell down to above the knee and the branch covers that (`Dress.kt`). Costs: the hip sockets sit on the bell's wider surface, so the legs leave it splayed and have to be turned in, giving an inverted V under the hem; the arms have to be pushed out so the hands clear the flare; the bell rides the torso's bones, so a leg that walks leaves the skirt; garments shared with other outfits would read the redrawn torso's u and land in the wrong place, so the Dress outfit has its own top. Fix: a garment surface lofted across several parts (pelvis and both legs, weighted by blending their weights), or a branch that runs before its part's start.
2. **Joins between body parts are overlaps.** The leg tops draw an ink ring across the lower belly, and the neck reads as a peg between torso and head. Hand-made sockets have no sink (`HandSockets.specs` builds every `SocketSpec` with the default sink of 1; no `socket.<n>.sink` parameter), and the implicit-blend socket fillet is a template block only (`blend`, `BlendSpec`), which no shipped template has and no part can opt into. Fix: sink as a hand-made socket parameter; the blend fillet as a part parameter.
3. **Hands.** `hand-3` builds a box of a palm unless drawn. Drawing its Top narrower toward the tips squeezes the fingers into a mitten, because the digits take their spacing from the drawn width, so the finger run has to stay as wide as the palm. The palm cannot cup or narrow into the wrist on its own. No part turns about its own axis at rest (the Move tool's angles are `move.outward`, `move.lean` and `move.rest`), so the palms face the way the template lays them. Fix: a palm section that narrows and cups; digit spacing independent of the drawn width; a twist about the rest axis.
4. **Closed shoes.** A branch over `foot-2` stays open at the foot's flat toe cap, and a garment cannot close an end (W13's Close end has no model), so a shoe drawn over the foot shows a hole of skin at the toe. Her sneakers, pumps and boots Swap the foot for a blob drawn as the shoe, which loses the foot's own ankle joint and shape. No heel can be raised: a heel needs a part of its own. Fix: Close end on a branch; parts a garment owns for heels.
5. **The face is drawn round a pole.** `head-round` rests forward, so its face is the end cap: v runs round the nose and u falls away from it, and its 40 rings, evenly spaced along the axis, leave the whole face on the last few. Marks have to be placed in polar u and v (`FacePlane.kt` maps a flat face drawing onto it), and no profile can be drawn: the Side outline cannot give a brow, a nose, lips or a chin, since the profile is the end cap. The nose is two ink lines. Fix: a head whose axis runs from chin to crown with its facing set apart from its axis, or a face patch of its own; a nose part on a socket with a fillet.
6. **Hair.** The crown is one closed blob hugging the skull and the fall and the locks are tubes overlapping it: it reads as hair from the front and as a helmet with slabs from the side. Strands are only ink lines on the surface; there is no parting, no clump, no curl. A cap that follows the hair blob reads as a hood or a headband. Fix: a hair template (clumps from a row on the scalp, each a tapering sheet), or a shell part laid over the head.
7. **Accessories with a shape of their own.** Shades can only be a shell over the face: a v-range branch over the head gives a crescent visor over both eyes, with no frame standing off the face and no lens. Brims, pom-poms, buckles and straps are the same: parts a garment owns are designed in `stage-9-garments.md` and not built. Fix: build parts a garment owns.
8. **Collars, lapels and openings.** The blazer opens down the front by a v range, but has no lapel or collar; a collar is only the cuff a Through change grows round the neck. The coat's padding is one ease all round. Fix: parts a garment owns; ease that varies round and along a branch.
9. **Loose ends.** The scarf's loose end does not show. Its length and width are fixed shares (0.06 of the covered part a bone, a sixth of the hem round), so on the neck it is about 0.04 long; on the shoulders' band it still disappears into the padded coat under it. Fix: a loose end's length and width as its own values, and an end fitted over the layers under it.
10. **Fillets and crotch ink.** A garment's fillet is one share of each child branch's length (0.35 by default), so long sleeves and trouser legs grow fillets 0.2 to 0.3 long that balloon the crotch and the armpits. The woman sets each garment's share so the fillet is 0.045 wide (`Wardrobe.kt`); the crotch still draws a knot of crease ink where both legs' fillets meet the waistband. Fix: a fillet in body units per join; no crease ink at grazing crossings.
11. **Trousers step in under the seat.** Each branch follows only its own part, so trousers cannot fall from the seat to the thigh; the waistband over the seat ends in a step where the leg leaves the torso. A fuller drawn outline at the top of the leg branch opened a gap and a bulge instead. Fix: the multi-part surface of entry 1.
12. **Curves.** The bust is only the torso's Side outline standing forward across its whole width: no separation, nothing from the front but shading. A front section is one shape for the whole part (`views.front: section`), so the chest cannot have a different section from the waist. Fix: sections drawn at several places along a part.
13. **One hinge a joint.** Every joint turns about one axis (`HandJoints` hinge x, y or z), so a raised arm swings forward across the face rather than out to the side; a shoulder cannot lift sideways and forward. A tube's own root joint keeps the template's limits (±60), so a hand-made shoulder sits just below it. Fix: ball joints, or two hinges a joint.

## What the template-free route could not do

Her torso, neck, arms, legs and hair are generic parts on the Blank plan; her head, hands and feet are templates. What generic parts lacked:

- **Upright is read from the template.** `PartRoles.upright` (`character/role/PartRoles.kt`) reads the root template's rest axis, not a part's `move.rest`, so a tube torso turned to rest up is not upright to the catalog, though the rig's `isUpright` reads the placed axis and is. `isForelimb` reads the template's rest the same way. Fix: read the placed axis in both.
- **No head.** A generic blob faces along its axis (`PlacedPart.facing`, `FaceBins.kt`), has no mount, so it hangs from its back pole, and its one joint is at that root. A head resting up faces up to the face bins, gaze and Look; one resting forward hangs off the neck by the back of its skull. `head-round`'s mount, mid-head neck joint, jaw and eye landmark are why it stays.
- **No digits.** The toe row, its per-digit shape, joints and webs are template blocks; a generic part has none, so the hands and feet stay `hand-3` and `foot-2`.
- **No sink and no blend.** See entry 2: plan sockets sink 1.6 to 2.2 template depths, hand-made ones 1.
- **No stance.** The `leg` template's `bearsWeight` and `stance` bend are not on a tube; poses plant by role, which works, but a four-legged reading of `bearsWeight` never sees her legs.

## What an author has to read code for

Each line: the source file opened, the fact needed, and what could have told it instead.

### Command reference and document schema

There is a schema for templates and plans (`schema/`) but none for the document or its commands, and no command reference.

- `screens-core/src/testFixtures/.../person/*.kt`, `fennec/*.kt`, `lizard/*.kt`: how commands are ordered and folded, the empty document, pairs set by their left side only, and roles chosen after a guess. A how-to with one worked example would.
- `document/.../Garment.kt`: what `from`, `to`, `fromV`, `toV`, `ease`, `layer` and `fillet` mean, and their defaults. A document schema with KDoc descriptions, as the template schema has.
- `document/.../BodyPartChange.kt`, `Worn.kt`, `PartData.kt`, `SurfaceWork.kt`: that Redraw lays its data over the part's (outlines and parameters replaced by key, marks and regions by id), Swap replaces it, and the outermost garment wins. A command reference.
- `document/.../GarmentCommands.kt`, `WardrobeCommands.kt`: the list of wardrobe commands. A command reference.
- `document/.../MarkRole.kt`, `MarkGroupShapes.kt`, `Mark.kt`: an eye's seven roles, their draw order, which the lid clips, and a mark's fields. A schema.
- `character/.../face/StarterFace.kt`: what Add eyes makes and where. The Face stage's doc.

### Hand-made parameters

- `character/.../build/HandSockets.kt`, `HandJoints.kt`, `HandLandmarks.kt`, `GenericShape.kt`, `Placement.kt`: every parameter name (`socket.<n>.u`, `joint.<n>.hinge`, `generic.rings`, `move.rest` and so on), the order `RestDirection` and `SectionProfile` count in, the joint gap and the tip joint, and that a hand-made socket's sink cannot be set. A parameter reference generated from these objects.
- `geom/.../ClosedSpline.kt`: the section profiles behind `generic.section`'s index. The same reference.

### Template catalog

- `templates/*.part.json`, `templates/plans/*.plan.json`: sockets, joints, landmarks, axes, lengths and views, readable as JSON, but what a view's `height` or `width` means for a given rest axis, and which template version is newest, need the HLD or the code. A generated catalog: each template's newest version, its axis drawn, its sockets and joints, what its Side and Top set.
- `character/.../build/BlendSpec.kt`: that the blend is a template block a part cannot opt into. The catalog.

### Orientation and measuring

- `geom/.../PartAxes.kt`: which way a part's up points (forward for a part resting up or down, up for one resting forward), and so where v = 0 lies and which way a Side outline's y runs. A doc of the part frame, or a headless command that prints the u and v at a body point.
- `rig/.../PoseChains.kt`, `character/.../role/PartRoles.kt`: what makes a character upright and which way a hinge's positive angle turns. A pose reference, or a command that prints a rig's bones and limits.
- `screens-surface/.../FaceBins.kt`, `EyeFacing.kt`: that face versions follow the head's axis and eyes more than 60° off it read their own. The Face stage's doc.
- Outline placement inside a part: that the drawn length sets the part's length, that the foot's toe end sits at its template length, that a blob starts at its socket. Only render loops found these; a headless measure of a built part's extents would.

### Rendering

- `app/src/test/.../smoke/*.kt`, `CloseUps.kt`, `LookProbeSmokeTest.kt`: how to render a character to a PNG from several sides, with its face bins, through the real graph. There is no command-line render; a headless render task taking a document, an outfit and a pose would replace the smoke test as the authoring loop.
- `screens-core/build.gradle.kts`: that `examples/` is the test fixtures' resource folder and `-PupdateGoldens` writes it. The skill says it now.

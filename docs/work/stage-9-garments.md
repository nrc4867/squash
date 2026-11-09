# Stage 9: garments

A working note for the branches that build clothing: garments, layers, outfits and slots, body-part changes, loose ends and the Dress stage. Folded into the HLD or deleted when it lands.

## Goal

Clothing is drawn on each character, starting from the body it covers, with no garment templates. A garment is its own surface, a branch per covered part swept along that part's axis and pushed off it by an ease, reshaped with the outline tools Shape already has, weighted by the body under it so it follows every pose with no rig of its own. Garments stack in layers, change the body parts they cover, and switch by outfit and slot. Dress is a Build stage, boards W13 to W15 on the restructure canvas.

## What the design already holds

The Garments tab of the plan doc, and the notes on W13 to W15. In the code: a part is a row of slices (`Slice`: a frame on the midline, height, width, a closed section) and `SliceBuilder.loft` builds a patch from any row of slices; `PlacedPart` carries every built patch with its slices in the part's own space and places it in body space; the rig weighs every vertex by its part, patch and u (`Patch.boneUOf`), so a vertex that knows the body point it came from can take that point's weights; `socketFilletNode` grows a fillet where one placed part comes out of another with one side still (`ImplicitBlend`'s `moves`), and `PatchSamples` fits each patch a field whose sign is the inside test; `Character.resolved` lays a pair's values, then a side's overrides; anchors name a part and a patch.

What it does not hold: anything worn, a surface that belongs to no part, a part whose template or data changes with what is worn, a vertex hidden by another surface, a document list of named sets with picks.

## Decided

- **The wardrobe is on the character.** `Character.wardrobe` holds `garments`, `slots` (named and ordered, `GarmentSlot`), `outfits` (each a name and at most one garment per slot) and `shown`, the outfit Build shows, or none for the bare body. A garment's slot is its own value, so the outfit's pick for a slot can only name a garment in that slot. A palette slot is `SlotName`; a wardrobe slot is `GarmentSlotId`, so the two never meet.
- **A garment** is an id, a name, a slot, a layer, its branches, join ink switches, hem and cuff ink, its fillet, its colours as palette slots, its body-part changes and its loose ends. A branch names one covered part, the u range it covers, an ease, and its own Side and Top outlines and surface work, the part's panes being where they are drawn.
- **A branch on a paired part covers both sides.** It names the left part; the right side's branch is the same branch mirrored, as a pair's data is shared. Per-side garment overrides are out of scope; Mirror in Dress is how the board draws it and it stays on.
- **Worn is derived.** The garments worn are the shown outfit's picks, inner layer first. Layer is a number per garment, so a jacket stays over a shirt in every outfit; the board's reorder is one command over all layers.
- **A body-part change belongs to the garment.** Per part a garment covers or touches: Hide, Through (cuff on, cuff size, ink cuff), Redraw (a `PartData`: outlines, params and surface work on the part's own template) or Swap (a template ref and its `PartData`). Keyed by the part; on a paired part it holds for both sides. A part with no change is covered (its skin hidden under the garment) or through where it sits on a socket inside the coverage.
- **Resolution order.** Pair, then side, then each worn garment's change, innermost first, the outermost winning (tentative, as the plan says). Hide and Swap are read where parts are placed, Redraw where a part's data is resolved. One function in `document`, `Character.wornPart`, answers all three, so the slice build, the assembly and the rig read one thing.
- **The shell is built from the body's slices.** A branch's rings are its part's slices over the branch's u range, each kept on its frame and section and grown by the ease in height and width. A drawn Side or Top outline then sets the ring's height or width wherever it stands off further than the ease's minimum; where it hugs the body, the minimum holds. Past its part's end a branch continues along the part's last slice frame, at the last slice's size. The branch is lofted with `SliceBuilder.loft` at the part's segment count and its own ring count, so its topology is fixed by the garment, not the drawing.
- **A garment is placed parts.** Each branch is placed in body space as its part is, a `PlacedPart` with no part id of its own but a garment id and its covered part, so lines, shading, colour and picking read garments as they read parts. Branch ids are `garment/<garment>/<part>` wherever a `PartId` is needed (the rig, the mesh's tags); a document part id never starts with `garment/`.
- **Weights from the body.** A branch vertex takes the weights of its body part at the vertex's u, clamped to the part's end past it, so the branch rides the bones its part rides and the joint and seam bands hold. No garment carries bones, except a loose end's.
- **Covered skin and inner layers hidden.** A body vertex is hidden when the garment's field, fitted per branch patch as the blend spike fits it, says it is inside. Hidden vertices drop their faces from the drawn mesh and their lines; the mesh's grid is not changed, so anchors still land. An inner layer's vertex inside an outer garment's field is hidden by the same test. At rest only; pose-time projection between layers is later.
- **Joins by socket fillet.** Where two branches of one garment meet, at a covered part's socket on another covered part, the branch on the child moves onto a socket fillet and the parent's branch stays still, as `withSocketFillets` does for parts. The fillet radius is the garment's Fillet. Ink along each join is a switch per join, off by default; hem and cuff ink are on by default.
- **Holes from the socket tree.** A part on a socket inside a branch's covered u range that the garment does not hide goes Through: the branch's faces inside the through part's field are dropped, and a cuff, a short ring of the garment's section around the through part's root grown by the cuff size, is lofted there.
- **Loose ends are a bone chain.** A loose end is a chain of a few bones from a hem point, posed by hand as a tail is, with Follow-through, Spring and Collide body as stored settings. Simulation is out of scope; Spring and Collide body are kept for Shot's settle.
- **Surface work on garments** lives in each branch, and its anchors name the garment's branch part id and its patch.
- **Dress is stage 6.** The board draws Shape, Color, Lines, Face, Rig, Dress; Pose moves to Shot when Shot is built. Until then Pose stays in Build as stage 7, key 7.
- **Examples.** The fennec in a Winter outfit (hat with the ears through, scarf, sweater over torso and front legs, boots that hide the toes), with Beach and Bare outfits beside it, and a person in a shirt, jacket and sandals with a redrawn foot, each built by a fixture and goldens like the other examples.

## Rounds

Each round is one branch in its own worktree, landed on `main` as one commit with `./gradlew check` passing, a smoke render of the dressed creature looked at before landing once anything is drawn.

| Round | Delivers | Tests owed | Boards | Creature |
| --- | --- | --- | --- | --- |
| 1 `garment-model` | `Wardrobe`, `Garment`, `GarmentBranch`, `GarmentSlot`, `Outfit`, `BodyPartChange`, `LooseEnd` on the character; commands for every change (garments, branches, ease, outlines, layers, slots, outfits, picks, the shown outfit, body-part changes, ink switches, loose ends), each with its undo; `Character.worn` and `Character.wornPart`; serialization and the schema regenerated | command scenarios with undo per command; worn order and outfit picks; resolution order, outermost wins, taking a garment off restores the part exactly; round trip through the file format | none | none drawn: the fennec's Winter wardrobe built by commands in a unit test |
| 2 `garment-shell` | The branch build from the body's slices with ease and drawn outlines and continuation past a part's end; garment branches placed in the assembly and posed; weights from the body; covered skin hidden by the field test; the fennec's sweater and hat in `examples/fennec-dressed.json` with fixture and golden | the shell matches its drawing and the ease's minimum; topology fixed; a posed sweater sleeve follows its leg; no body vertex under a sweater shows at rest | none | the fennec in a sweater and hat, at rest and posed |
| 3 `garment-joins` | Socket fillets between branches, holes and cuffs from sockets, join, hem and cuff ink | the sleeve joins the torso branch with the torso branch still; the ears go through the hat with a cuff; the hem inks by default | none | the fennec's hat with the ears through and a filleted sweater |
| 4 `body-changes` | Hide, Through, Redraw and Swap through `wornPart` in the slice build, the assembly and the rig | boots hide the toe row and taking them off restores it; a redrawn foot under a sandal; with two garments changing one foot the outer wins | none | the fennec in boots |
| 5 `garment-layers` | Fit over the layer below; inner layers hidden inside outer fields | a jacket over a shirt shows nothing of the shirt or skin at rest; the measure of what shows at a deep elbow bend | none | the person in a shirt and jacket, posed with deep bends |
| 6 `dress-stage` | The Dress stage to W13 to W15: the strip, the inspector, the layers list, the outfits and slots band, the slot popover, the body-under section, the Parts rows' notes, and the Cover and Loose end icons | UI tests per board; smoke renders of each board's state | W13, W14, W15 to building, then verifiable | the dressed fennec in Dress |
| 7 `loose-ends` | Loose-end chains with Follow-through, Spring and Collide body stored; posed by hand as a tail | a scarf end's chain poses and stays outside the body at rest | W14 | the fennec's scarf end |

## Exit criteria

1. **A shell from the body.** A sweater on the fennec's torso and front legs and a hat on its head build through the real slice build, match their drawings within the ease's minimum, keep a fixed topology, and follow a pose with the body's weights.
2. **Joins and holes.** Branches meet by fillet with the inner one still, ears come through a hat with a cuff, hems are inked.
3. **Changes come and go.** Hide, Through, Redraw and Swap change the body while worn; taking the garment off gives the body back exactly; the outer garment wins.
4. **Layers hide what they cover.** At rest no inner layer or skin shows through an outer garment.
5. **Outfits switch** by one command that undoes in one step.
6. **Dress is built to W13 to W15.**
7. **They read as clothing** in the smoke renders, judged by eye.
8. **Tiers hold.** `./gradlew check` passes; goldens are regenerated on purpose.

## Out of scope

Garment templates and a garment library, garments moved between characters, per-side garment overrides, pose-time projection between layers, cloth simulation and Shot's settle, a garment on a planned part, per-shot outfits (Shot keys the outfit when Shot is built).

## Open questions for Nicholas

- Two worn garments changing one part: outermost wins, or should the slot rule forbid it? Built as outermost wins.
- Ink at a fillet's foot, for branch joins as for the blend spike's leg join. Built with the join's switch alone.
- Fillet speed, 27 to 45 ms a character before fields are cached; garments add to it on every assembly change.
- Pose's key while Dress is 6 and Shot is not built: built as Pose on 7.
- Whether the outfit Build shows is stored with the character (built so: `Wardrobe.shown`) or is view state.

# Pipeline

The pipeline is fixed by its stages and the data each hands to the next; research only supplies the method inside each stage. Drawing and oversketching loop with building until the shape is right; everything downstream runs by itself from what the user marked earlier.

## Stages and picks

| Stage | Pick | Borrows from | Open |
| --- | --- | --- | --- |
| Draw to part: creatures | Part by part, in place on the body, in the body plan's rest pose. Clean outlines from 1 to 3 views, snapped to the part's own axes, with a hint for missing directions. The outlines size the template's slices directly: Side sets each slice's height along the axis, Top its width, Front the end cap. The template supplies roundness, the open socket end, toes, pads and anything not drawn. No fitting search | Silhouettes (Rivers), applied per part along its own axis instead of by intersection in world axes; Structured Annotations' typed shapes; Yang, Sharon and van de Panne's part recipes | Spike 1. Heads are not a sweep: a skull block plus a muzzle sweep |
| Outline editing | Outlines are vector curves: oversketch a section, drag points and handles. The slices re-sample from the stored curves, never from the on-screen silhouette | Pixar's editable-curve oversketch practice | Read Disney's US8411080 claims first |
| Part templates | JSON files combining building blocks in code (tube, cap, skull, toe row, pad, joint, socket, area, landmark), with parameter ranges, fixed ring and segment counts, and which view sets what | Yang, Sharon and van de Panne's part templates | See [data-model.md](data-model.md) |
| Props | Silhouettes, as published | Silhouettes | After creatures |
| Mirroring | Linked pairs with per-side overrides on top | | Tentative |
| Body | Separate part meshes on one skeleton, overlapping at their sockets | | Socket borders inked by default; spike 3 runs flat and two-tone |
| Rig and weights | lego-viewer's rigging with both tagging systems (weight-derived areas, painted areas), plus stretch weights. Weights are seeded from part ownership, so only seams are guessed | lego-viewer; Stretchable and Twistable Bones | Bounded Biharmonic Weights as a maybe |
| Shape in action | Contact squash against the body itself, the ground, props and other characters; pose-driven fixes as joints bend; per-shot touch-ups by hand | Implicit Skinning for body on body; slice flattening against the ground plane; Pose Space Deformation | Contact with the world goes beyond the paper |
| Pose | One drawn curve sets tail, spine, neck and head. Limbs by hand: planted paws hold (IK), lifted paws ride along. Stretch handles per bone; a longer or shorter curve stretches or squashes the body, keeping volume | Line of Action as an idea; lego-viewer IK; the spine's slices re-sampled by arc length and scaled to keep volume | Pixar's US8890889 claims; making posing less raw |
| Animation | Timing per shot and per character: smooth, on twos or on threes. Drafting motion with one stroke comes later | Space-time Sketching, later | Later |
| Cloth, tails, ears | Garments drawn as outlines over the body. Loose parts posed by hand, with optional springy follow-through that collides with the body | Turquin; SecondSkin as a maybe for armor; Implicit Skinning's volumes for collision | Tentative |
| Look | Shading per object within scene limits: flat, or two-tone with shadows smoothed into clean shapes per part. Far paired parts darker by side, switchable. No textures: painted colors and curve-bordered regions | Guilty Gear's edited normals, supplied automatically by each slice's proxy normal | |
| World | 3D sets and props near the characters, a painted backdrop for the distance. Cast shadows per scene: none, blob or cast | X-Toon as a maybe for world shading | |
| Lines | Thick on the silhouette, thin inside; ink switchable per border; weight adjusted on the body surface | WYSIWYG NPR | |
| Marks | Strokes drawn on the surface, over body and clothing. Fur mostly as surface strokes near the silhouette, big tufts in the outline. Any mark can have per-angle versions that switch; faces come in expression sets | WYSIWYG NPR; Graftals' spacing rule; switch-layer practice | |

Not used, on purpose: trained models anywhere; Teddy-style inflation; sphere-mesh systems; Monster Mash; MIT's 2.5D cartoon method (marks get their 3D position from the body they sit on, never estimated from drawings).

## Spikes

Each spike is small, has no UI beyond what it needs, and ends in a pass or fail by eye. Spikes render in 3D on WebGPU through `stretch-gpu` in a `NativeSurface` inside the Stretch frame, as the real app will. The style test is a whole simple quadruped built with all eight spikes.

1. **Parts from outlines.** Starter template files (torso, leg, paw, head, ear, tail) whose slices are sized from outlines drawn in the Sheet, with its four viewports in one surface; one outline alone also builds. Passes if each part matches the drawing in those views and looks plausible from others, the paw has pads underneath, the leg's open end sits inside the body at its socket, the head reads as skull and muzzle, and the highlight for views that disagree keeps up at stroke rate.
2. **Oversketch.** Redraw part of an outline and drag a handle; the slices re-sample from the curves. Passes if only the touched section changes and surface marks stay attached through the refit.
3. **Overlapping parts.** A leg overlapping the body, flat and two-tone, with ink at the overlap switched on and off. Passes if no seam shows with ink on in either mode; the un-inked two-tone case is judged by eye and may stay flat-only.
4. **Weights and stretch.** lego-viewer's weights on a built leg and paw, seeded from part ownership so only the seam is guessed, one stretching bone, and a body squashed by a scripted shorter curve. Passes if nothing smears and volume holds.
5. **Contact.** A fully bent leg and a body lying on the ground, then Update from library on a posed character with one touch-up. Passes if the skin presses flat instead of passing through and the touch-up survives.
6. **Look.** Painted colors and one curve-bordered region, flat and two-tone with shadows from slice proxy normals, far-side darkening. Passes if edges are crisp and shadows fall as clean shapes.
7. **Marks on the surface.** Tapered strokes and fur strokes anchored in slice coordinates on the character, steady while orbiting and through an oversketch, and an eye with three angle versions that switch. Passes if nothing swims or pops wrongly.
8. **Body curve posing.** One stroke poses tail, spine, neck and head while planted paws stay put, a second stroke in Top tries adding sideways bend, and limbs are then finished by hand. Passes if the pose reads as drawn from every camera and limb posing is quick without throwing the model out of whack.

**Order: 1, 2, 7, 3, 4, 5, 6, 8.** Spike 7 runs third because marks staying attached is a claim about the slice coordinates spike 1 defines, and a flaw there is cheaper to find before weights and contact build on the same mesh. Spikes keep throwaway UI; the real screens come after the style test. The part template format is written and reviewed before spike 1, since spikes 1 through 7 all read it.

**Style test.** One whole simple quadruped: torso, head, two ears, four legs with paws, a tail, a chest ruff and one face set, built only from drawn outlines. Posed standing and lying down, rendered flat with outlines and line weights, turned to side, front and three-quarter views. If it does not look right, the art approach is revisited before anything else.

## Patents

A Google Patents search only, not legal advice; EU and Japanese patents were not checked. Squash is not for sale. The rule is to stay reasonably clean: skip methods whose core is clearly patented, and build differently where a patent sits nearby.

| Method | Patent | Status | What it means |
| --- | --- | --- | --- |
| Geodesic Voxel Binding (lego-viewer's voxel weights are close) | US9836879B2, US10535187B2, Autodesk | Active to about 2034 | Claims weights from shortest paths through interior voxels with a boundary penalty. lego-viewer's distances are plain shortest paths from flood-filled voxels, lacking a step of each claim as summarized; read claim 1 in full. Squash's parts know their bones, so weights come from part ownership and only seams are guessed |
| 2.5D Cartoon Models | US8791942B2, MIT | Active to about 2032 | Claims strokes drawn in key views, anchored in 3D and moved as the view turns. Not used: marks sit on the head's 3D surface with per-angle versions that switch, so no 3D position is estimated from drawings |
| Line of Action, Space-time Sketching | US8890889B1, Pixar (adjacent) | Active to about 2032 | Claims posing a jointed object by pushing it against a path. The body curve re-samples the spine's slices by arc length instead; read the claims before building spike 8 |
| Bounded Biharmonic Weights | US9865073B2, US9858701B2, Adobe | Active to about 2036 | Cover extra steps for 2D artwork only. Plain BBW is clear |
| Dual quaternion skinning | US9613456B2, Disney | Active to about 2035 | Covers a specific rigid and non-rigid split. Plain DQS, as in lego-viewer, is clear; the split is not built |
| Silhouette oversketch | US8411080, Disney | Active to about 2030 | Read the claims before building spike 2; build differently |
| Per-part silhouette sweep (Squash's own draw-to-part method) | Not yet searched | | Search due before spike 1 ends. Generalized cylinders sized from silhouettes go back decades, so prior art is likely and a live claim unlikely |
| Silhouettes, Structured Annotations, Stretchable Bones, Implicit Skinning, Turquin, SecondSkin, Delta Mush, WYSIWYG NPR, inverted-hull outlines | None found | | Clear as far as searched. Teddy's patent and Kalnins' later stroke patent have expired. Implicit Skinning should also be checked on Espacenet |

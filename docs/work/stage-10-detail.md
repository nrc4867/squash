# Stage 10: detail

A working note for the branches that raise a complex character to finished quality: shading, rest pose, joins, free skirts, rigid props, the head and hair, and hands and feet. Folded into the HLD or deleted when it lands.

## Goal

The woman (`examples/woman.json`, `examples/woman-dressed.json`) reaches the quality of the reference renders of the same character built by hand in a general 3D engine: a palm with fingers that stay apart at the tips, arches and heels, a face with depth and a jaw, hair outside the skull, joins that read as one body, a skirt hanging free of straight legs, heels, shades with frames and lenses, lapels, and two-tone shading with no blotches. She stays a drawn character: every change is a command, every shape comes from outlines and settings a person can redraw.

The reference renders, outside the repo, are the bar to compare against side by side:
`C:\Users\nicho\squash-reference\threejs-woman\renders\` (its build log is `log.md` one folder up).

## What stands today

`docs/work/example-limits.md` lists what the woman could not get, ranked, with causes in the code. The causes this stage answers:

| Flaw | Cause |
| --- | --- |
| Blotches on the bust, cheek and jaw | Two-tone shading reads the mesh's own normals, so the terminator follows every small bump |
| A skirt leaves a walking leg | A skirt is only possible as a torso redrawn into a bell, which rides the torso's bones |
| Neck, shoulders and hips read as pegs | Parts meet by overlap; the blend fillet is a template block |
| Flat head, hair through the skull | `head-round` rests forward with a round section, so the face is the sphere's pole; hair is a separate blob |
| Blocky hands and feet, merging tips | The palm is one box and each digit its own tube; digits space by drawn width |
| No heels, shades, buttons or lapels | Garment-owned parts are designed (stage 9) but not built |

## Decided

- **A-pose rest for bipeds.** Bipeds are built, weighted and dressed with the arms about 45 degrees out from the body and the legs a little apart. A character's display pose, saved with it, brings the arms down; the body pushes the posed arm out of the torso where they meet, so the arm lies on the body's surface. The existing biped examples move to it.
- **Skeleton before surface.** A socket and a joint have a position of their own inside the part, not a point on its drawn surface, so a redraw or a garment changes shape only and never moves a hip or a shoulder. Hand-made sockets sink as plan sockets do. The existing examples move to it.
- **`head-round` version 2, face first.** Its axis runs chin to crown; Side draws a profile with brow, nose, lips, chin and jaw; Front draws the face's width; rings are denser across the face. The nose is part of the head's profile, not a part, and the profile is generic, so snouts, muzzles and beaks draw the same way on other heads. Marks are placed on a flat face map.
- **Joins, hands and feet follow the research** in the "Research: hands and joins" tab of the plan doc: bridging strips at socket rings first; then a hand and foot cage with junction cells (B-Mesh, Ji, Liu, Wang 2010; SQM, Bærentzen et al. 2012; box per node, Usai et al. 2015); the implicit blend only as the comparison. Methods from the papers' text only.
- **Parts stay the thing drawn and stored.** The body does not become one mesh; strips and junction cells stitch parts into a surface that reads as one.

## Rounds

Each round is one branch landed on `main` as one commit with `./gradlew check`, and ends with the woman re-rendered beside the reference.

| Round | Branch | Delivers | Done when |
| --- | --- | --- | --- |
| 0 | `fast-look` | A command that renders an example document, an outfit, a pose, the front, three-quarter and side views and close-ups to PNGs through the real graph without a Compose test, and a measure command printing the checks below as numbers | The woman's full set renders in one call, faster than the smoke test |
| 1 | `smooth-shading` | Two-tone shading from a smoothed normal field per part, so the terminator follows the form, not the facets | No blotch on the woman's bust, cheek or jaw in any view |
| 2 | `a-pose` | Sockets and joints with positions inside the part; hand-made sockets sink; the biped plan rests in A-pose; a saved display pose; the arm pushed out of the torso; person and woman moved | Arm volume inside the torso is zero at rest and in the display pose; hip spread is the same in every outfit |
| 3 | `socket-strips` | Each part trimmed at its socket ring and the two rings joined by a fixed strip of quads, faired; the strip carries its own u range so anchors hold; ink at the join switchable | No socket ring shows at neck, shoulder, hip, wrist or ankle; the strip builds in under 5 ms a socket |
| 4 | `free-skirt` | A garment branch that runs past either end of its part, hanging free, pushed out after each pose wherever a posed leg would cut it and the push spread round the hem; the woman's dress built with it over her own torso | Her legs stay straight under the skirt at rest and in a walk |
| 5 | `garment-props` | Parts a garment owns, starting as rigid shapes on a bone (stage 9's design where it agrees): shades with frames and lenses, heels, buttons, buckles, a hat brim; lapels and collars as sheets a garment owns | The beach shades, the office lapels and pumps and the goth buckle read as they do in the reference |
| 6 | `head-v2` | `head-round` version 2 and the flat face map; eyes with depth (a spike of eyeballs with lids painted on, against marks, the better one kept); the woman's head rebuilt | Profile depth from brow to chin shows in Side; the face reads in three-quarter without the disc look |
| 7 | `hair-shell` | The hair cap as a shell lofted from the head's own slices with an ease, as a garment is; volume and the fall as drawn outlines; locks and a ponytail as chains | No skull shows through the hair in any view or pose |
| 8 | `hand-cage` | A spike of the cage on one hand and one foot, then the chosen form as new `hand` and `foot` versions: one surface for palm and digits, edge loops at knuckles, webs, thumb pad, nails, arch, ball and heel, a digit spacing setting that ignores drawn width | Fingertips and toes apart in every close-up; palm, arch and heel shapes drawable; under 50 ms rebuild a hand |

## Checks

Measured by round 0's command and kept as tests where they are cheap: arm volume inside the torso; skull vertices outside the hair; smallest gap between neighbouring digit tips; hip socket spread per outfit; socket ring length shown; profile depth brow to chin; bare skin under garments; shading terminator length per view.

## Round 0: fast look

`./gradlew :app:look` and `./gradlew :app:measure` (see the example-character skill) build an example from its fixture's commands, or its saved file, in one session over the real graph and rig, with no test runner. Look renders on the GPU; measure reads the posed meshes on the CPU. Each check, as measure defines it:

- **Arm volume inside the torso**: grid points 0.012 apart inside both an arm's mesh and its parent's, by winding number, leaving out the fifth of the arm's length at the shoulder.
- **Hip socket spread**: the distance between the legs' roots.
- **Digit tip gap**: the least distance between the last 30% of neighbouring digits' patches.
- **Socket ring length**: the curve where a part's shown faces cross its parent's mesh, all and seen (a point is seen when 10 of 96 rays from it leave the character).
- **Profile notch**: the head cut by its middle plane, chin to three quarters up; the deepest fall of that front curve behind its hull. A sphere reads 0.
- **Skull seen**: the head's shown vertices above its middle and behind its face that are seen.
- **Bare skin**: shown body vertices inside the shell of a garment's branch over that part that are seen.
- **Terminator**: the line on each shown face where the interpolated shading normal turns from the light; its length across each view, the pieces seen, and blotches, pieces shorter than half a turn round their part's thinnest extent.

The woman as the stage starts, undressed (`example=woman-dressed outfits=bare`): arm inside 0.00079 each side; hip spread 0.248 (0.574 in Dress); digit tip gap 0.028 hands, 0.012 feet; socket ring seen neck 0.199, head 0.069, shoulder 1.165, wrist 0.112, hip 0.465, ankle 0.200; profile notch 0.0008; skull seen 83 of 332; terminator front 6.05 in 46 pieces, 2 blotches (torso, head). The full set of 36 images renders in about 25 s, against about 30 s of test time and a minute in all for the smoke test's 15; measure runs every outfit in about 28 s.

## Round 1: smooth shading

Built: `RenderMesh.withSmoothedShading()` in `geom/.../SmoothedShading.kt`, called once in `PartSurface.of`, so every part, garment branch, fillet and loose end gets it, at rest; skinning turns the smoothed normals with the pose, as it did the proxy normals. It rewrites only the proxy channel of the mesh (the third normal, which the fill and colour layers shade by): each vertex takes the mean of the proxy normals within a ball of 0.8 of the part's thinnest box extent (tentative), Gaussian with a spread of half that, weighted by vertex area; neighbours whose normal faces away from the vertex's own are left out, so digits and sheets keep their sides. Positions and mesh normals, which outlines read, are unchanged. Unit tests in `geom/src/commonTest/.../SmoothedShadingTest.kt`.

Measured with `./gradlew :app:measure -Pmeasure="example=woman-dressed poses=none"`; a blotch is a closed loop of terminator shorter than half a turn round its part's thinnest extent (pieces cut short by a hem or hidden faces are not blotches). Before: one blotch on the head in the front and three-quarter views of every outfit, one on the bare torso, two on the winter coat's sleeve in side. After: none on any part in any outfit and view. Terminator length falls 2 to 14% per view (bare front 6.05 to 5.55, three-quarter 6.51 to 6.18). The look renders (`./gradlew :app:look -Plook="example=woman-dressed"`) show the bust's shade wedge gone and the face shaded as one sphere-like curve across the lower cheek and jaw; the fennec, dressed fennec, dressed person, dragon, toucan, lizard and koi render as before, a little cleaner.

For the next rounds: the face's shade is now the head's form, so its shape is round 6's to change, not shading's. The smoothing reads a part's mesh box, so a part whose thinnest extent is tiny relative to its bumps (a flat sheet) smooths little. Any new surface built outside `PartSurface.of` would miss it. Golden digests that hash mesh vertex floats change with it (`SoftBrushCutTest.softnessZeroIsTodaysCutExactly` was refreshed; only the vertex hashes moved). Already on `main` before this round and not caused by it: the dressed woman's hand close-up (`app/build/look/woman-dressed/hand.png`) shows stair-stepped missing faces and broken outline on the hand and forearm, where the undressed woman's (`example=woman`) is clean; something hidden by the wardrobe in the shown outfit is left out of `CloseUps.keep`'s part-only view.

## Round 2: A-pose

Built:

- **Skeleton sockets** (`character/.../build/SliceRowNode.kt` `skeletonRowNode`, `Assembly.kt` `socketPoint`, the session's `skeleton(part)`, `AssemblySource.Skeleton`). While a worn garment reshapes a part (Redraw of outlines or shape, Swap), its sockets are read from the part built bare with the worn `move.` placement kept, and carried from the bare ring's middle to the shown ring's middle at the u the garment gives that socket (`AssemblyPart.wornSockets`, the worn socket parameters that differ from the bare ones). Joints were already inside: a bone pivots at its ring's middle. Outlines the author redraws still carry the sockets, as before.
- **Hand-made socket sink**: `socket.<n>.sink` (`HandSockets.sink`, `add(..., sink =)`), 1 when unset, so nothing that exists moved. The woman leaves it unset; a deeper sink puts more of her arm inside the torso at rest.
- **A-pose**: plan sockets take `spread`, the degrees a part placed there starts leaning out (`PlanSocket.spread`, `SocketSpec.spread`, `OpenSocket.spread`; `placeOnRow` puts `move.outward` in the start data; planned parts build with it through `plannedData`). The biped's shoulders spread 45; its hips spread nothing, since lint holds a weight-bearing leg straight down (`an axis that bears weight rests straight down`).
- **Display pose**: `Character.displayPose`, `SetDisplayPose`, `Pose.over`, `Character.shownPose`; `SessionPose` shows `(held ?: pose).over(displayPose)`. History label `pose_step_display`. No UI sets it yet; there is no board for it.
- **Arms on the body**: `character/.../build/ArmsOnBody.kt` `withArmsOnBody`, run in `posedBodyNode` after `posedBy`. Per ring of the arm's swept patch: one direction (the parent's nearest surface normal made square to its axis, summed over the ring's inside points), carried until no point of the ring lies inside the parent or an all-round branch over it (`ShellInside`, a march of 0.01 then 8 halvings), then dilated and averaged over 1/16 of the rings, eased in over rings 6% to 14% along the arm. Branches over the arm take the shift of the arm vertex under each (`Covering.bodyRings`, `bodyColumns`). Unit test: `character/src/desktopTest/.../APoseTest.kt`, with the plan spread, planned A-pose and the skeleton under a Redraw.
- **Examples**: the woman's arms rest at `move.outward` 45, legs 1 (`WomanBody.kt`); `WOMAN_DISPLAY` (`WomanPoses.kt`) leans each tube's `base` bone -28 and turns each elbow 70 with the bone free, so the palms face the thighs; walk and salute name plain elbows, so they drop that turn. The Dress outfit's leg and arm Redraws and the coat's arm Redraw are gone: the legs hang from her own hips under the bell, the arms come down by the display pose and the push. The person takes the plan's 45 through its `AddParts` pair data, moves its shoulders to u 0.93, and `PERSON_DISPLAY` leans the `shoulder` bones -25.
- **Look and measure**: look renders `<outfit>-rest.png` (display pose cleared) beside the display view whenever the example has one; measure prints the arm volume inside the torso at rest (the assembly) and as shown (the display pose), and `face in shade` per view (`HeadChecks.faceShade`: the share of the face's seen front vertices whose shading normal faces away from the light).
- **Light** (tentative): `PartMaterial.DEFAULT_TO_LIGHT` is (0.7, 0.5, -0.5), lower and more frontal than (0.45, 0.8, -0.5). Face in shade on the woman in Office, front / three-quarter / side: 0.470 / 0.402 / 0.404 before, 0.231 / 0.120 / 0.115 after; the fennec 0.249 / 0.180 / 0.173 before, 0.166 / 0.026 / 0.002 after. The fennec, dragon, toucan, lizard and koi keep their shade on far legs, bellies and under muzzles. A head-only softer terminator was not needed.

Measured (`./gradlew :app:measure -Pmeasure="example=woman-dressed"`): arm volume inside the torso was 0.00078 each side in most outfits (0.00061 Dress, 0.00013 Winter); now 0 at rest and in the display pose in every outfit, and 0 in walk and salute. Hip spread was 0.248 bare and 0.574 in Dress; now 0.2483 in every outfit. The person (`example=person`): 0.0019 before; now 0.00051 at rest and 0.00013 shown, since the plan's shoulder sinks the arm 2.2 times the arm template's undrawn depth, about half the drawn arm's length, more than the fifth of the length at the shoulder that measure leaves out.

For the next rounds:

- The A-pose arm leaves the torso through a ring the shoulder's bend shows: in the display pose the arm's top swings out over the shoulder as a lump (the person most, the woman in Beach and Office), and a pushed arm shows a kink where it meets an all-round top. Round 3's strips at the socket ring are where that should close.
- Hand blotches: the terminator check finds 3 to 5 blotches on the hands in the display pose (none at rest); they come from the posed hand's fingers, not the light.
- The dressed woman's hand close-up no longer shows missing faces. The likely cause: at the old rest her hand hung against her hips inside the shorts' shell, and `GarmentHiding` hid hand faces under a garment covering another part; her hand now hangs clear. Any part reaching into another part's garment shell will show it again.
- The plan lint (`SocketFit`) checks a socket's ring with the part resting along its template's axis, not along its spread, so the biped shoulder could not go shallower than its 2.2.
- The Office blazer sleeves balloon below the elbow as they did before this round.
- Only the four biped example files changed; the creatures' files are unchanged.

## Open

- Whether the display pose is a pose of its own, as built, or the first entry of the pose library Shot will hold; and whether the Pose stage edits it, or shows it under the working pose as it does now.
- The three questions on garment-owned parts in `stage-9-garments.md`; round 5 takes the recommended answers there as tentative and lists them for Nicholas.

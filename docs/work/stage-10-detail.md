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
- **Profile depth brow to chin**: the head cut by its middle plane, chin to three quarters up; for each stretch of that front curve's hull, the deepest fall of the curve behind it, summed, with the deepest alone as the notch. A sphere reads 0 for both.
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

## Round 3: socket strips

Built:

- **Strips** (`character/.../build/SocketStrips.kt`, `StripPlan.kt`, `StripShape.kt`, `StripJoin.kt`; `geom/.../strip/BridgeStrip.kt`, `PatchNearest.kt`): a node after the assembly and its fillets joins each neck, arm, leg, hand and foot to what it sits on. Whichever part runs into the other as a tube (the child from its start; else the parent from its end, as the woman's leg into her foot) is trimmed at its first ring past where every column leaves the other by 0.7 of its radius, at most a third of what it shows past the other; the foot lies 0.6 radii out over the other part's surface, calmed and laid back on it. Nine rings of Hermite columns, tangent at both ends (way reach 0.6 of the gap), faired round by a damped Jacobi bilaplacian with two rings held at each end. The strip is a patch on the tube's surface, u -1 to 0 at a start and 1 to 2 at an end (`Patch.us`), with `Patch.openEnds` so the lines close nothing at its ends; the tube's trimmed rings and the other part's vertices inside the strip (read as a `ShellInside` of the strip) are hidden through `Hiding.fixed` (`alwaysHiding`), so garments and `hiddenAgain` keep them hidden and every vertex and anchor stays. Right sides take their left twin's plan mirrored (v to 1 - v on a shared host). Planning a tube that stays inside the other for more than 0.6 of its rings gives no strip, as does a template with a socket `blend`. The constants are tentative.
- **Posed strips**: `withStripsPosed` in the posed body node, after the arm push, builds every strip whose tube or host the pose carried again from the posed parts. Rig weights (`rig/.../StripWeights.kt`) blend each column from the host's weights under the foot (bilinear between four vertices) to the tube's at the ring.
- **Ink**: `join.ink` on the joined part (`JoinParams`, unset is off): a strip join inks round its ring, or round the foot when the joined part is the host. A garment branch's join switch inks round its own strip's ring (`GarmentEdges`).
- **Garments over strips** (`GarmentStrips.kt` `stripOver`, called in `dressedIn` after the fillet): an all-round branch over a part whose start a strip joins hides its rings over the trimmed rings and grows its own strip from its first kept ring to the body strip's foot lifted by the branch's ease (`StripJoin.lift`), so sleeves and trouser legs follow the shoulder and hip, at rest and in pose.
- **Arm push** (`ArmsOnBody.kt`): pushes the posed arm out of its parent only, not out of all-round garment branches, and lets go from 0.4 to 0.6 of the arm's length, so the forearm hangs as posed. Pushing out of garment shells and down the forearm is what ballooned the Office blazer's sleeves (the trousers' and blouse's shells at the hips pushed the forearm rings 0.08 out, and the sleeves followed) and kinked the arm against the Beach top. A rigid turn of the whole arm at the shoulder was tried instead: it needs 12 to 19 degrees to clear the bust from a pivot sunk in the torso and splays the arms; dropped.
- **Lines**: `LineGrid` draws no contour along the edge where shown cells meet cells with a hidden corner.
- **Woman**: her arm's drawn shoulder bulge is gone from `ARM_SIDE` and `ARM_FRONT` (the strip makes the shoulder); `examples/woman*.json` rewritten.

Measured (`./gradlew :app:measure -Pmeasure="example=woman-dressed"`, display pose): socket ring seen, bare, before / after: neck 0.199 / 0, shoulder 0.452 / 0, wrist 0.202 / 0, hip 0.555 / 0, ankle 0.203 / 0; every outfit is 0 at all five (before: shoulder up to 0.452, hip up to 0.837 in Dress); the head on the neck stays 0.069, not stripped. Arm volume inside the torso 0 at rest and shown in every outfit but Dress (0.00001 shown). The person, before / after: head 0.330 / 0.330, shoulder 0.447 / 0.454, wrist 0.112 / 0, hip 0.549 / 0, ankle 0.444 / 0.275. The shoes are not swept, so the leg is the tube there and the measure takes the leg's crossing of the shoe; what remains at the ankle is likely the shoe's top running up inside the trouser leg past the strip. The shoulder strips are built (at ring 24), and what the measure counts is the hanging arm lying in the torso's side below them (arm volume inside the torso 0.00034 shown), which the push onto the body does not reach in the unposed example. Strip time: about 0.2 ms a socket built again in a pose (`SocketStripBenchTest`, slow, every outfit, 7 to 16 strips), 1 to 2.7 ms a socket at rest with planning, trimming and hiding once warm.

For the next rounds:

- Bare skin seen inside garments rose where sleeves and trouser legs carry strips (casual arms 21 to 63 vertices, legs 31 to 52; beach legs 46 to 60): the body strip and the branch strip are built apart and the body's can stand a hair outside the branch's near the foot. Not visible in the renders; a branch strip read from the body strip's own rows, offset by the ease, would close it.
- The ankle in a closed shoe is not stripped (the shoe Swaps the foot for a blob, which the leg overlaps); its ring is never seen.
- The Office blazer's sleeves are wide because its ease stacks on the blouse's (0.045 a side); that is the fixture's choice, not the strip's.
- The plan's thresholds (0.7 and 0.6 radii, 0.6 of the rings, a third of what shows) and the way reach 0.6 are tuned on the woman and person; creatures took them unchanged.
- The measure's socket ring check takes the strip's tube against its host: where the parent holds the strip (the leg into a shoe), it measures the parent's crossing of the child.
- The dragon's yellow belly diamond shows smaller: its torso's faces under the leg strips' feet are left out, the diamond's paint with them. The fennec's paws show a few white flecks along the strip foot in close-ups.
- The display pose's shoulder: the strip foot stays where the A-pose arm left the torso, so a lowered arm's shoulder is a strip from there; it reads as a rounded shoulder, a little broad on the woman in Beach.

## Round 4: free skirt

Built:

- **Branches that hang free** (`GarmentBranch.from` below 0 runs a branch on before its part's start, as `to` past 1 runs it past the end; `Covering.hangsFree`, `HANG_PAST` 0.1 in u, tentative). The shell past the end is the end slice's frame carried on, sized by the drawn outlines, which read the part's own x, negative below the part. The rig weights it from the end ring, so the skirt rides the pelvis.
- **Parts that hang inside** (`HangingBranches.kt` `leavesByFreeEnd`, `hangingInside`; `Covering.hangsOver`): a part on a socket inside the branch's region whose first ring outside the branch lies past 0.85 of the way to the free end (tentative) hangs inside rather than going through: no hole, no cuff, and it is covered by the branch's inside test like any skin, the hip strip and its foot with it. `throughHoles` leaves such parts out.
- **The push** (`geom/.../HemPush.kt`, `pushedClear`, `withHangingPushed`): in `dressedIn` at rest and in `posedBodyNode` after the posed strips, before `hiddenAgain`. Each ring of the branch is cut against the grids of what hangs inside it (the legs, every part on them, other garments' branches over them): each grid edge crossing the ring's plane gives a section point, and each column, with its two neighbours, is pushed out radially from the ring's middle to clear the farthest point round it by 0.012. The push spreads round the ring (most of every column's push, squared cosine over a quarter turn) and down the branch as a cone hinged at its held ring (another ring's push scaled by their distances from the hinge, at most twice). The held ring never moves; no column moves in. The technique is the reference's idea of a free cone pushed out by leg collision, built from section cuts on Squash's own grids.
- **Woman's dress** (`Dress.kt`): the skirt is a branch over her own torso from 0.40 below its lowest point (u -0.59) to the waist (u 0.32), with Side and Top outlines flaring from the waist to the hem; the redrawn torso, its moved joints and sockets and the redrawn tank top range are gone. The Goth outfit wears black jeans, not a skirt, so it is unchanged.
- **Measure** (`app/.../measure/SkirtChecks.kt`): leg volume outside the skirt, grid points 0.01 apart inside a leg's mesh (winding number), on the inside of the planes of the skirt's waist and hem rings and outside the skirt's shell, per leg; skirt vertices inside a leg; the push's time per pose (median of 21 runs once warm). A skirt to the measure is a branch all round the leg's parent, of a garment with no branch over the leg, reaching below the leg's root by 0.08 of its length.
- Tests: `geom/.../HemPushTest.kt` (a leg inside moves nothing; a stepping leg pushes the hem in front, not behind, the push spreads round and grows down, the waist holds, and the leg lies inside after), `character/.../HangingBranchTest.kt` (the biped's legs in a hanging skirt are covered, not cut, and a leg swung into it pushes it clear).

Measured (`./gradlew :app:measure -Pmeasure="example=woman-dressed outfits=dress poses=all"`), round 3 / round 4:

| Check | Round 3 | Round 4 |
| --- | --- | --- |
| Leg volume outside the skirt, rest and display pose | 0 / 0 | 0 / 0 |
| Leg volume outside the skirt, walk (left / right) | 0.00001 / 0 | 0 / 0 |
| Skirt vertices inside a leg, walk | 2 | 0 |
| Skirt vertices inside a leg, rest, display, salute | 0 | 0 |
| Hip socket spread, every outfit | 0.2483 | 0.2483 |
| Socket ring seen at the hip, Dress | 0 | 0 |
| Push and spread per pose | none | 0.79 ms walk, 0.60 ms salute |

Round 3's numbers are small because its bell was wide and rigid and the walking thigh passed through a hole the leg cut in it: the measure does not see a rigid cone, the renders do. Round 4's walk renders (`dress-pose-walk.png`) show the hem swung forward over the stepping thigh and back over the trailing one, the bell asymmetric, both legs straight under it and coming out below the hem; at rest (`dress-rest.png`) and in the display pose the skirt is an A-line from the waist to above the knee with the legs straight under it. Bare skin seen inside the Dress's garments: torso 69 to 77 vertices, at the skirt's waist ring. Only the Dress images change among all examples (pixel diff against round 3's renders): the person, dressed person, fennec, dressed fennec, dragon, toucan, lizard, koi and the woman's other outfits render the same.

For the next rounds:

- Round 5's props hang on bones as rigid shapes; the push here is for branches only. A buckle or belt on the skirt's waist sits on the held ring, which never moves.
- The push moves positions only; normals keep their skinned values, so a flared hem shades as the cone it was. A short contour shows on the skirt's front in the walk, likely the fold where pushed cloth meets unpushed.
- Arms do not push a hanging branch: a hand lowered against the skirt in the display pose sinks into it from the side (as it did in round 3).
- The Winter coat over the jeans reads to the measure as a skirt (it covers the torso from u 0, below the hips): 0.00056 of each leg lies outside it and 40 of its vertices lie inside the legs, as before. Making it hang free would hide the jeans' tops under it.
- No sway or follow-through: the skirt is placed by the pose, not simulated. A loose end on a hem would be the place for it.
- The thresholds (0.1 in u to hang free, 0.85 of the way to the free end, 0.012 clearance, a quarter turn round, twice down) are tuned on the woman alone.
- Left from round 3: the dragon's belly diamond paint lost under the leg strip feet and the fennec's paw flecks at strip feet are untouched.

## Round 5: garment props

Built:

- **Parts a garment owns** (stage 9's design, `PartInstance.garment`): ordinary parts in `Character.parts` naming the garment that owns them, on hand-made sockets of the part they sit on. `Character` refuses an owned part whose garment the wardrobe lacks and a part on an owned part unless the same garment owns it; `RemoveGarment` removes the garment's parts in the same step (`Together(RemoveParts, SetWardrobe)`). `assemblyParts` leaves out a part whose garment is not worn, with every part on it (`Character.isWorn`, `hiddenUnderGarments`), and carries the owner to `AssemblyPart.owner` and `PlacedPart.owner`. An owned part reads its socket on its parent as the parent shows rather than on the parent's skeleton (`Assembler.skeletonOf(part, owned)`), so a heel's socket on a swapped foot lands on the shoe. `dressedIn` cuts no hole or cuff for an owned part and hides none of it. The rig needs nothing new: a generic part's `base` bone follows its parent's bone at the socket, so props ride rigidly in every pose and in the display pose.
- **Hem push normals** (`MeshShift.kt` `shiftedOnGrid`): after the push each moved vertex's normal and shading normal turn by the turn that carries the shell grid's normal there before the push onto the one after, so the flared hem shades as it stands.
- **Measure** (`app/.../measure/PropChecks.kt`): per owned part, its vertices inside any garment branch's shell, all and seen; in each named pose, how far it strays from moving rigidly (the largest change of any vertex's distance from its first vertex). `isBody` leaves owned parts out of the body checks.
- **Woman** (fixtures `Shades.kt`, `Pumps.kt`, `Suit.kt`, `Belt.kt`, helpers `owned`, `ownedPair`, `propShape` in `Wardrobe.kt`): Beach's shades are two blob halves resting out from a paired socket at the bridge of the nose, each a squarish frame with a bridge, a lens region with a glint region inside it, and a tube temple running back from a socket at its outer end into the hair; the old visor branch is gone. The pumps swap the foot for a slim blob with its heel seat raised and the instep low, and own a tapering tube heel under the seat of each foot; they have no shell, so the ankle shows over the instep as the reference's opening does. The blazer owns two blob lapels from either side of the neck, leaned in 24 degrees to the button and drawn forward over the bust in Side, and a button; its opening narrows from 0.075 to 0.04 in v so the blouse shows as a V between the lapels. Goth adds a belt (a new Belt slot, a narrow band over the jeans' waistband on layer 4) that owns a steel buckle at the front, and the choker owns a small steel pendant. The Dress skirt's drawn flare is toned down to an A-line (hem 0.28 across from 0.32, 0.175 / 0.19 front and back from 0.195 / 0.21).
- Tests: `document/.../OwnedPartTest.kt` (worn only with the garment, removal with undo, the two refusals), `character/.../OwnedPartsTest.kt` (no cuff and no hiding on an owned part inside a cap where a body part gets a cuff; assembled only while worn), `WomanDressedFixtureTest.eachOutfitBuildsThePartsItsGarmentsOwnAndNoOthers`.

Measured (`./gradlew :app:measure -Pmeasure="example=woman-dressed outfits=all poses=all"`, and per outfit with `outfits=office`, `dress` and `goth` for the posed checks):

| Check | Round 4 | Round 5 |
| --- | --- | --- |
| Owned parts' drift from rigid, walk and salute, Beach, Office, Dress and Goth | none | 0.00000 |
| Push and spread per pose, with the normals turned | 0.79 ms walk, 0.60 ms salute | 1.33 ms walk, 0.67 ms salute |
| Owned part vertices under cloth, all / seen: Beach shades and temples | none | 0 / 0 |
| Office: lapels, button, heels | none | lapel-l 104 / 37, lapel-r 103 / 36, button 144 / 69, heels 0 / 0 |
| Goth: buckle, pendant | none | buckle 137 / 5, pendant 28 / 0 |
| Socket ring seen at the ankle, Office / Dress (pumps) | 0 / 0 | 0.056 / 0.099 |
| Hip socket spread, every outfit | 0.2483 | 0.2483 |
| Leg volume outside the skirt and skirt vertices inside a leg, walk | 0 / 0 | 0 / 0 |
| Arm volume inside the torso, every outfit, rest and shown | 0 | 0 |

The lapel and button counts are their buried halves: the button is a stub from the skin out past the blouse, and a lapel's inner edge runs into the blazer's open edge, whose v-range shell the winding test reads loosely; the renders show both faces whole. The ankle ring is the leg end showing over the pumps' low instep, which reads as the reference's opening. Only the Beach, Office, Dress and Goth images change among all examples (pixel diff against round 4's renders); Casual, Winter and Bare, the undressed woman, the person, dressed person, fennec, dressed fennec, dragon, toucan, lizard and koi render the same.

Against the reference: the shades read as square dark frames with grey lenses, a bridge and a glint, riding the head through walk and salute (`beach-face.png`, `beach-pose-salute.png`); the temples run into the hair and do not show over it as the reference's do. The pumps read as pointed pumps on a short heel (`office-foot.png`); the reference's foot points down with a tall heel, which a fixed ankle cannot give. The lapels read as a notchless V-lapel blazer closing at the button (`office.png`); the blouse shows as a strip below the button where the reference's jacket closes. The belt and its square steel buckle read as the reference's (`goth.png`), without its studs.

For the next rounds (aimed at round 6, `head-v2`):

- The shades sit on a paired socket at FacePlane (0, 0.16), u 0.996 on today's `head-round`, with sink 0 and lenses drawn flat 0.006 thick. `head-round` version 2 moves the face off the pole, so the bridge socket, the lens regions' v (0.6 to 0.9 round a half resting out) and the temples' hinge must be placed again on the new head; the face map should give a socket's u and v from a face-plane point, as it will for marks, so props on the face are placed by the same map.
- Eyes with depth will stand forward of today's surface; the lenses stand off the face only by the frame's own depth, so check the lens clears eyeballs and lids, or give the bridge socket a negative sink.
- A head with a profile can take the bridge on the nose: the profile's brow and nose give the shades a seat, and a temple can meet a drawn ear.
- The hair cap (round 7) will cover where the temples end; they run 0.27 head units back and are hidden by the hair today.
- Left from round 4: arms do not push a hanging branch, so a lowered hand still sinks a little into the skirt's side in the display pose; the short ink dash on the skirt's front in the walk is still there, a contour at a ring where the pushed fold turns; the walk's bell is the push's spread, unchanged.
- Regions on a Swap's data do not follow a pose (they stay where they lay at rest), so the pumps show no painted opening.
- A part's Top outline sets width only, not the middle, so no prop wraps round a face or a chest; a lapel is rigid on the chest bone.

## Round 6: head v2

Built:

- **`head-round` version 2** (`templates/head-round-2.part.json`): upright, its axis from the chin to the crown, facing forward (`axis.faces`, `PartBuilder.facing`, read by the face bins, the gaze tools and the look-at solve through `RigPart.facing` and `LookChain.facing`). No mount: its axis start sits on the socket. One joint, `neck`, at u 0.25, owns the whole head and turns it about the middle of the jaw. 72 rings, 0.85 of their spacings over u 0 to 0.72, the face (`topology.focus`, `RingFocus` in `geom/.../SliceBuilder.kt`); 64 segments. The views keep version 1's keys: Side the profile, Top the face's width up from the chin (the Front pane shows it), Front the section from above (the Top pane shows it). Ear sockets and `eye`, `nose` and `mouth` landmarks; no jaw joint, since a joint turns what lies above it. Version 1 ships as it was and the person pins it, its face placed about version 1's eye landmark, so only the woman's two example files change. A new biped from the plan takes version 2.
- **Profile ridge** (`template.profile`, `geom/.../ProfileKeel.kt`, applied in `ViewSizing.slice`): Side's up side is read as a profile. Where it stands out of the base that a ball 0.3 of the drawn length across reaches, rolled along behind it, the ring's section is squeezed into the base and a ridge 0.3 of its half width across rises to the crossing. So the nose, lips and chin stand out narrow, a muzzle longer than the span swells the whole front, and a round face stands out nowhere. A flat window would cut the top off every convex curve and put a faint ridge down any round face; the round ball does not. Tests: `ProfileKeelTest` (a short bump is a narrow ridge, a long plateau is the ring's own swelling, a circle stands out nowhere, the ring keeps its drawn span), `RingFocusTest`, and `character/.../FaceHeadTest.kt` (a nose stands out with the cheek beside it kept back, a creature's muzzle drawn on the same head swells its whole front, the face map lands where the front view shows it, left and right mirror).
- **Face map** (`character/.../build/FaceMap.kt`): built from a head's template and data, it maps a place on the flat face, x to her left and y up in the head's drawn units, to the anchor where a ray from in front first meets the body patch, through the mesh's own triangles. The woman's eyes, brows, lashes, lips, lid shadows, blush and nose lines and the shades' bridge socket are placed by it (`WomanFaceMap.kt`).
- **The woman's head** (`WomanHead.kt`): drawn from the neck's top (the neck's end socket sinks 0), the chin 0.07 below it so the head's underside covers the neck's end. Profile, forward of the neck: chin 0.126, lower lip 0.127, upper lip 0.133, nose tip 0.172, nasion 0.134, brow 0.143; the back of the skull at -0.25, inside the hair. Top widens from a soft chin through the jaw to 0.198 at the temples; Front is broad across the face and round behind. The hair's sockets move to the upright head with sinks that keep each hair part's root within 0.004 of where it was (the crown stands off, sink -0.8). The neck is drawn with no flare at its base (Front 0.08 across there), so the strip makes the base.
- **Measure**: `profile depth brow to chin` (`HeadChecks.profile`): for each stretch of the profile's hull, the deepest fall of the profile behind it, summed; the deepest alone is still printed as the notch.
- **Eyes with depth, spiked and dropped**: each eye a blob on a paired socket at the eye's place on the face map, standing 0.016 out of the face, white, with iris, pupil and glint regions and skin-coloured lid regions painted on it and a lash mark along the upper lid, in place of the mark eyes' lid, white, iris, pupil and highlight. It reads as goggles: the overlap's crease inks a circle round each ball in every view, the ball's round cap shows where the lid's almond should, and the lids painted on the ball meet the face's skin along that inked circle. The mark eyes stay. The spike's renders are kept with the round's look renders under `eyeball-spike/`; its code is not on `main`.

Measured (`./gradlew :app:measure -Pmeasure="example=woman-dressed outfits=all poses=all"`), round 5 / round 6:

| Check | Round 5 | Round 6 |
| --- | --- | --- |
| Profile depth brow to chin, every outfit | 0.0021 | 0.0323 |
| Deepest profile notch | 0.0008 (0.002 of the head's height) | 0.0180 (0.048) |
| Skull vertices seen outside the hair, every outfit but Winter | 83 of 332 | 0 of 1200 |
| The same in Winter, the beanie over the hair | 76 of 332 | 125 of 1200 |
| Face in shade, Office, front / three-quarter / side | 0.231 / 0.120 / 0.115 | 0.060 / 0.000 / 0.012 |
| Head on its neck, socket ring all / seen | 0.118 / 0.069 | 0.393 / 0.126 |
| Owned part vertices under cloth, Beach shades and temples | 0 / 0 | 0 / 0 |
| Owned parts' drift from rigid, walk and salute | 0.00000 | 0.00000 |
| Hip spread, arm volume inside the torso, leg volume outside the skirt | as round 5 | as round 5 |

The fix targets read from round 5's renders:

- **The face as a flat disc with marks on it**: in three-quarter the nose stands out of the cheek line, the jaw runs back from a soft chin and the far cheek turns away; Side shows brow, nasion, nose, lips and chin (`bare-face.png`, `office-face.png`).
- **The beard-like shade band across the lower face and jaw in front**: gone. The face in shade in front falls from 0.23 to 0.06; what is left is a narrow wedge at her right cheek's edge, the side away from the light.
- **The flesh lump behind the head in Side**: it was the skull. Version 1's back of the skull reached 0.033 behind the hair cap's back; version 2's lies 0.026 inside it, and no skull vertex is seen outside the hair in any outfit but Winter. The hair is not rebuilt.
- **Notches at the neck base in front**: they were the neck's round end showing as a small shoulder each side below the round head's disc, and a corner where the neck's drawn flare met its strip. The jaw now covers the neck's end and the flare is gone, so the neck runs from the jaw into the shoulders in one curve.

Only the woman's images change among all examples; the person, dressed person, fennec, dressed fennec, dragon, toucan, lizard and koi render as round 5's (pixel diff).

For the next rounds (aimed at round 7, `hair-shell`):

- The skull lies inside the hair in every outfit but Winter, where the beanie covers the hair blob, not the head. A shell lofted from the head's own slices reads rings that run chin to crown, 72 of them, most over the face: loft it over the top (u above about 0.55) and the back, and leave the face open (the template's `face` area, u 0 to 0.75, v 0.82 round the front to 0.18).
- Rings over the crown are sparse (0.15 of the spacings over the top 0.28 of u); a shell lofted from them is coarse there unless it has rings of its own.
- The hair's sockets: crown at u 0.6, v 0.5, sink -0.8 (standing off); fall at u 0.79, v 0.5, sink 0.3; locks at u 0.61, v 0.68 on her left, sink 0.5. They keep round 5's hair where it was; a shell replaces the crown blob and needs no stand-off.
- The face map gives a hairline: a fringe drawn on the flat face lands on the forehead as marks do.
- The shades' temples run 0.27 head units back from hinges level with the bridge (face map y 0.245) and end in the hair; a shell will cover their ends.
- Left in the head: its underside is one floor from the chin to the nape, since each height crosses the profile twice, and the neck runs into it with no strip (the head's socket ring seen rose to 0.126, under the jaw from the side); the ridge is one width for nose, lips and chin; the upright head has no jaw joint; the eyes are marks, so they have no depth beyond the face's own curve.

## Twin

A woman built to match the reference renders nearly one to one, so the reference can judge Squash's model shape and dressing; light, shading and lines stay Squash's own and are left out of the comparison. She is built from a numeric description of the reference character (body, skeleton, face, hair, outfits, poses and cameras, in metres, +Y up, facing +Z) and its renders, not from its source. The woman stays as she is.

Built:

- **The example** (`examples/woman-twin.json`, `examples/woman-twin-dressed.json`; fixtures under `screens-core/src/testFixtures/kotlin/dev/chieppa/squash/screens/twin/`, tests `TwinFixtureTest`, `TwinDressedFixtureTest`). Drawn in metres on the Blank plan: the torso tube from the pelvis's underside (0.735 m) to the base of the neck, its Side and Top read from the reference's ring table with the bust and seat swellings; spine joints at the reference's bone heights; a neck to the head's origin (1.455 m); `head-round` version 2 at size 1.1, the reference's head scale, its profile, width and section read from the reference's head shape; arms and legs with the reference's radius keys, the shoulder, elbow and knee at its joints; the shipped hand and foot. Body sockets are seated on the surface (`sink = 0`), so the hips land 0.182 apart (0.184 in the reference) and the shoulder joints at 1.318 m. The face is painted from the reference's numbers about its head centre (eyes, lids, brows, wings, nostrils, lips, eyeshadow, blush). The hair is a cap with bangs, a fall to the middle of the back and a curtain each side. She rests in A-pose; the display pose brings the upper arms to 9 degrees out with the elbow bent 5.7, the reference's `rest`. Named poses `walk`, `wave` and `shades-up`. Outfits by the reference's names: `beach` (halter bikini band with cups and straps it owns, denim shorts, cork sandals with a strap, shades), `lawyer` (blouse, open blazer with lapels and a button, wide-leg trousers, pumps with heels, the lawyer face), `dress` (cream top with puffed sleeves, the free teal skirt, a tan belt, flats, the dress face), `goth` (dark top, black jeans, belt with buckle, choker with ring, platform boots, the goth face). The face looks are garments in the Face slot redrawing lips, eyeshadow and blush.
- **Matched cameras** (`app/.../look/shots/`): `look` takes `eye`, `target`, `fov`, `size` and `scale` for one perspective camera in the reference frame, and `shots=reference` for the reference's 24 shots under their own names (`ReferenceShots.kt`). `ReferenceFrame` maps the reference's +Z forward to Squash's +X and its +X left to -Z, and its ground to the posed body's lowest vertex. The shots render on the reference's background colour.
- **Compare** (`app/.../look/compare/`, `./gradlew :app:compare`): per shot a side-by-side, a silhouette overlay (blue reference only, red Squash only, grey both) and the silhouette IoU, whole and in head, torso, arms and legs bands, kept as `iou.txt`.
- **Measure**: `parts=true` prints every part's origin and box from the ground; dressed outfits print bare skin seen in each named pose.

Silhouette IoU, dressed twin against the reference (`./gradlew :app:compare -Pcompare="example=woman-twin-dressed reference=..."`):

| shot | whole | head | torso | arms | legs |
|---|---|---|---|---|---|
| beach-threeq | 0.900 | 0.861 | 0.994 | 0.814 | 0.877 |
| lawyer-threeq | 0.870 | 0.855 | 0.882 | 0.820 | 0.887 |
| dress-threeq | 0.803 | 0.859 | 0.709 | 0.619 | 0.879 |
| goth-threeq | 0.907 | 0.863 | 0.997 | 0.811 | 0.897 |
| beach-front | 0.910 | 0.854 | 0.972 | 0.753 | 0.929 |
| lawyer-side | 0.846 | 0.833 | 0.860 | - | 0.837 |
| dress-side | 0.841 | 0.879 | 0.737 | - | 0.906 |
| goth-front | 0.905 | 0.844 | 0.986 | 0.753 | 0.913 |
| face-natural | 0.868 | | | | |
| face-goth | 0.890 | | | | |
| face-lawyer | 0.805 | | | | |
| face-beach-shades | 0.883 | | | | |
| face-beach-shadesup | 0.878 | | | | |
| hand-rest | 0.233 | | | | |
| foot-beach | 0.771 | | | | |
| foot-lawyer | 0.754 | | | | |
| foot-dress | 0.847 | | | | |
| foot-goth | 0.808 | | | | |
| hand-wave | 0.570 | | | | |
| pose-wave-lawyer | 0.767 | 0.596 | 0.884 | 0.836 | 0.782 |
| pose-walk-dress | 0.630 | 0.783 | 0.631 | 0.353 | 0.713 |
| pose-walk-dress-side | 0.647 | 0.792 | 0.574 | - | 0.661 |
| pose-walk-goth | 0.795 | 0.831 | 0.988 | 0.577 | 0.782 |
| pose-walk-beach | 0.696 | 0.854 | 0.737 | - | 0.615 |
| mean | 0.784 | | | | |

The bands are set at the reference's rest heights, so in the poses they are approximate, and the waving arm lies in the head band. Undressed (`example=woman-twin`) against the dressed reference, the body alone scores 0.912 beach three-quarter, 0.919 beach front and 0.913 goth front.

Largest shape gaps, by how much they cost:

1. **The walk** (0.63 to 0.80). The legs and arms take the reference's angles on the `base`, knee, shoulder and elbow bones, but the hips' and chest's turns about the vertical have no bone (the spine's joints bend about one axis), the planted feet shorten the stride, and the arms swing in a narrower arc (arms band 0.35 to 0.58).
2. **The hand at rest in the dress** (0.23). The reference's dress outfit adds 7.4 degrees of arm abduction for that outfit alone; Squash has one display pose, and arms do not push a hanging skirt, so the hand sinks into the skirt's side. The hand itself is `hand` with straight fingers: its finger joints are unnamed blocks in the template, so no pose gives the reference's relaxed curl.
3. **Hair** (head band 0.83 to 0.88 where the bare head scores 0.88 to 0.90). A blob cap with a solid bang edge, a slab fall and a slab curtain each side: the reference's 23 ribbons, the zigzag hairline and the parting have nothing to be built from, and from the side the hair mass is thinner behind the head (lawyer-side head 0.83).
4. **Shoulders**. The shoulder strip is built where the A-pose arm leaves the torso, so with the arms down the shoulder reads as a squared pad above the arm in every front and three-quarter view (arms band 0.75 to 0.82); the reference's shoulder is a round cap.
5. **Feet and shoes** (0.75 to 0.85). The pump's 24 degree foot pitch is a per-outfit pose in the reference; the twin draws a pitched pump on a flat ankle, so the heel is 0.045 m where the reference's is taller and the instep sits lower. The toes are the template's row, longer and evener than the reference's capsules.

The wave raises the right arm with a free `lean` on its `base` 25 degrees past the reference's 114.6 (the lean reads from where the A-pose leaves the arm, so the reference's number alone lands low) and folds the forearm up with a turn at the elbow; there is no shoulder bone to shrug and no hand twist, so the palm faces in rather than forward (hand-wave 0.57). Shades up turns each half about its own length at the bridge: the reference lifts the whole pair onto the forehead about the head, which a part riding its socket cannot do.

Dressing report (`./gradlew :app:measure -Pmeasure="example=woman-twin-dressed outfits=<outfit> poses=walk,wave"`):

| Outfit | Garment or prop | Built to match | Fit against the reference | Walk and wave |
|---|---|---|---|---|
| Beach | Bikini band and neck tie | Yes, branches on the torso and neck | Band 1.139 to 1.157 at 0.0035, as the reference | Holds |
| Beach | Cups | Partly: owned blobs on the bust | Triangles of the reference's size, but rigid and a little wide; no polka dots | Ride the torso rigidly, drift 0 |
| Beach | Halter straps | Yes, owned tubes | From cup to neck, 0.009 across | Rigid, drift 0 |
| Beach | Shorts | Yes, waist and legs | 0.0085 off, hem at 0.735; the lighter cuff band and the pocket, fly and seam ink need patterns on cloth | Holds; bare skin seen 12 to 17 torso vertices at the waist edge |
| Beach | Sandals | Partly | The sole branch wraps up the foot's sides rather than lying under it (121 foot vertices seen inside its shell), and the toes show over its edge; the strap is a band round the forefoot; the toe post and heel strap are not built | Holds |
| Beach | Shades | Yes, owned halves and temples | Frames, lenses and glint where the reference's are; lenses opaque | Rigid on the head, drift 0 |
| Lawyer | Blouse | Partly | 0.0045 off; its V neck is a plain top edge, since an opening cannot vary along a branch | Holds |
| Lawyer | Blazer | Partly | 0.0135 off, open front of one width the whole length rather than closing to the button | Holds; sleeves follow |
| Lawyer | Lapels, button | Partly, owned blobs | Lapels read; 188 of each lapel's vertices lie under cloth (26 seen); no collar band, no notch | Rigid, drift 0 |
| Lawyer | Trousers | Yes, flared by drawn outlines | Waist 0.009, leg 0.0095 flaring to 0.0275 front and 0.0395 sides at the hem | Bare skin seen 48 to 55 leg vertices at the hip strip; none visible in the renders |
| Lawyer | Pumps and heels | Partly | Swapped foot drawn pitched, heel 0.045 m (the reference's foot pitches 24 degrees on a taller heel); no foot opening paint | Heels rigid, drift 0 |
| Dress | Cream top, puff sleeves | Partly | 0.006 off; scoop neck drawn as a straight top edge; the puff is one ease | Bare skin seen 41 to 58 arm vertices under the sleeves |
| Dress | Skirt | Yes, free skirt | Hem 0.305 below the pelvis, 0.29 wide and 0.19 / 0.24 deep, as the reference's rest radii | Legs stay inside: leg volume outside 0 at rest, in walk and wave; push 1.7 ms in the walk |
| Dress | Belt, flats | Yes | Belt 1.004 to 1.020 at 0.0155; flats a swapped foot, opening not painted | Hold |
| Goth | Top, sleeves | Partly | 0.006 off; scoop neck straight, no lace trim | Bare skin 12 to 20 arm vertices |
| Goth | Jeans | Yes | Waist 0.0085, legs 0.0065 to 0.2 m | Bare skin 2 to 4 leg vertices |
| Goth | Belt, buckle | Yes, owned buckle | Belt 0.961 to 0.983; buckle 0.034 by 0.026 by 0.006; no studs | Rigid, drift 0 |
| Goth | Choker, ring | Yes | Band at the neck's base and a ring at its front | Rigid |
| Goth | Boots | Partly | Swapped foot with a 0.028 platform and a shaft to 0.31 m; a thin ring of skin shows between the boot and the shaft at the ankle; no lacing | Holds |

No skin pokes through in walk or wave beyond what shows at rest: arm volume inside the torso is 0, owned parts drift 0, and the skirt holds the legs. Trouser, jeans and shorts legs run on 0.08 of the leg up into the pelvis (`INTO_PELVIS`): from the leg's own start they left the hip strip bare under the waist in every outfit.

Largest dressing gaps, by how much they hurt the match:

1. **Openings and cut shapes.** A branch's v range is one width along its whole length and its ends are rings, so V necks, scoop necks, the blazer's opening narrowing to its button, the bikini's triangles and the pumps' and flats' foot openings are all straight bands. Fix: an opening whose v range varies along u, or a cut outline drawn on the garment.
2. **Per-outfit pose.** The reference pitches the foot for heels and spreads the arms for the dress; Squash has one display pose, so heels stay short and the dress's hands sink into the skirt (arms do not push a hanging branch either). Fix: a display pose an outfit can add to; arms among what pushes a skirt.
3. **Shoe soles and straps.** A sole is a v range of a shell over the foot, so it wraps up the foot's sides; straps that arc over the foot, a toe post and a heel strap have no part to be built from; a swapped boot meets its shaft with a gap at the ankle. Fix: a sole as a slab under the foot's footprint; a part drawn along a curve on a surface.
4. **One colour per garment.** Cuffs, polka dots, lace, studs, lacing, pockets, seams and creases need patterns or regions on cloth; a two-colour item is two garments in two slots (the sandal's straps take a Straps slot). Fix: regions and ink on a garment's shell.
5. **Rigid owned sheets.** Lapels and cups are rigid blobs that lie partly under cloth and cannot follow the chest; shades turn only about their own socket. Fix: a sheet that lies on the surface under it; a prop pose about another bone.

For the next rounds: round 7 replaces the twin's hair (cap, bangs, fall, curtains) and round 8 its hands and feet; both are judged by `compare` against the same shots.

## After the twin

The twin's gaps set the order of the remaining rounds, model shape and dressing first. Light, shading and lines stay Squash's own; the twin is not a reason to change them. Each round ends with `compare` run on `woman-twin-dressed` beside the woman's own renders, and its numbers in its section.

| Round | Branch | Delivers | Done when |
| --- | --- | --- | --- |
| 9 | `shoulders` | The shoulder strip planned where the arm leaves the torso in the display pose, so a lowered arm meets the body in a round cap; the head joined to the neck by a strip or a floor that wraps the neck; arms among what pushes a hanging skirt | No squared shoulder pad in any front or three-quarter view; arms band 0.88 or more in the twin's rest body shots; the dress hand clear of the skirt; head-on-neck socket ring seen 0 |
| 10 | `garment-cuts` | An opening whose width varies along the branch, or a cut outline drawn on a garment: V and scoop necks, a front closing to a button, bikini triangles, shoe openings; a collar; owned sheets that lie on the surface under them so lapels and cups follow the chest | The lawyer's V and closing front, the dress and goth scoop necks and the bikini triangles read as in the reference; lapel vertices under cloth near 0 |
| 11 | `outfit-pose` | A pose an outfit adds to the display pose (heel pitch, the dress's arm spread); turns of the hips and chest about the vertical; named finger joints and a hand twist on the hand template | Pump heel and instep as in the reference; walk shots 0.80 or more whole; hand-wave palm forward |
| 12 | `hair-shell` | As round 7 in the rounds table: the hair cap as a shell lofted from the head's slices, volume and fall as drawn outlines, locks as chains; the hairline from the face map | Head band 0.90 or more in body shots; no skull through the hair in any view or pose |
| 13 | `hand-cage` | As round 8 in the rounds table | hand-rest 0.70 or more; fingertips and toes apart in every close-up |
| 14 | `soles-straps` | A sole as a slab under the foot's footprint; a part drawn along a curve on a surface (straps, toe post, heel strap); a swapped boot joined to its shaft | Foot shots 0.88 or more; no skin ring at a boot's shaft |
| 15 | `cloth-paint` | Regions and ink on a garment's shell: cuffs, dots, lace, studs, lacing, pockets, seams | The reference's cloth details each have a built counterpart |

## Round 9: shoulders

Built:

- **Seated limbs get strips** (`StripPlan.kt` `planStrip(seated = true)`, `SocketStrips.kt`): a neck, an arm or a leg that runs into its parent with fewer than half its columns, as the twin's do with every body socket seated on the surface (`sink = 0`), is planned from its own start when neither it nor its parent plans otherwise; a column never inside leaves at its first ring. The twin's arms, legs and neck had no strips at all: each limb's flat root showed as a squared pad or a ring, which is what the twin's shoulders were.
- **The foot slides in a pose** (`slidFoot`, `withStripsPosed`, `GarmentHiding.kt` `refixed`): a posed strip keeps its ring and plans its foot again where the posed tube now leaves the host, up to that ring, a column still inside there leaving at the ring. The host's faces under the old foot show again and those under the new one are hidden (`StripJoin.underFoot` holds what the rest foot hid; `hiddenAgain` decides garments' hiding again for a host whose fixed set changed), and a garment's strip over the same join stands on the new foot. A foot the pose leaves within 1e-4 in u and v is kept as it was, so a knee bend changes nothing at the hip.
- **Shoulder caps** (`StripShape.kt`, `StripJoin.capped`): an arm's strip from its start leaves its foot turned out of the body at a slope of 0.5 and is carried 1.0 of its length into the ring (0.6 elsewhere), so the shoulder rises off the torso and rounds over into the arm. Tentative; tuned on the twin and the woman. Other strips are as they were.
- **Head on neck** (`SocketStrips.kt`): a head on a part of role neck is joined by a strip, the neck from its end into the head first, its foot 0.1 of the neck's radius out under the head (`HEAD_REACH`, against 0.6 elsewhere) so the jaw keeps its edge. A head on anything else (the person, the creatures) keeps its overlap.
- **Arms clear a hanging skirt** (`ArmsClearOfSkirts.kt`, in the posed body after the push onto the body): an arm whose vertices, or its hand's, lie inside a branch hanging free from the part it sits on turns out at its shoulder, with everything it carries and the branches over it, by the least turn that clears, found by seven halvings, and a tenth more, at most 0.35 radians. The skirt is not pushed by the arm: an arm lies outside the skirt, and the push only grows a ring outward round what lies inside it. The reference itself spreads the dress's arms 7.4 degrees.
- **Measure**: `arm and hand vertices inside the skirt`, as shown and per pose.
- Tests: `SocketStripsTest` (a pose slides the foot with the leg and shows the torso again under the old foot; a leg seated on the torso's surface is joined from its own start), `HangingBranchTest.anArmLoweredIntoTheSkirtTurnsOutClearOfIt`.

Measured (`./gradlew :app:measure -Pmeasure="example=woman-dressed,woman-twin-dressed,woman,woman-twin,person,person-dressed outfits=all poses=all"`), before / after:

| Check | Before | After |
| --- | --- | --- |
| Head on neck, socket ring all / seen, woman, every outfit | 0.400 / 0.121 to 0.130 | 0 / 0 |
| The same, twin, every outfit | 0.328 / 0.108 | 0 / 0 |
| Twin bare, seen: neck, shoulder, hip | 0.104, 0.186, 0.079 | 0, 0, 0 |
| Arm and hand vertices inside the skirt, Dress, woman and twin | not measured; the hand sank into the skirt | 0 each side |
| Hip spread, woman / twin, every outfit | 0.2483 / 0.1820 | 0.2483 / 0.1820 |
| Arm volume inside the torso, every outfit and pose, woman and twin | 0 | 0 |
| Leg volume outside the skirt, skirt vertices inside a leg | 0 / 0 | 0 / 0 |
| Owned parts' drift from rigid | 0 (shades-up 0.0003, its own turn) | the same |
| Person: head ring seen, shoulder ring seen | 0.330, 0.454 | 0.330, 0.467 |
| Twin profile depth brow to chin | 0.0286 | 0.0234: the strip now forms the chin's underside, which the measure's cut reads |
| Bare skin seen, twin Dress arms under the puff sleeves | 57 / 55 | 104 / 104 |

Strips built again in a pose with the foot slid: 0.38 to 0.64 ms a socket by outfit, median, against about 0.2 ms before the slide (`SocketStripBenchTest`, slow, every outfit of the woman); the round 3 budget is 5 ms.

Silhouette IoU, dressed twin (`./gradlew :app:compare`), twin round / round 9:

| shot | whole | head | torso | arms | legs |
|---|---|---|---|---|---|
| beach-threeq | 0.900 / 0.900 | 0.861 / 0.861 | 0.994 / 0.994 | 0.814 / 0.817 | 0.877 / 0.876 |
| lawyer-threeq | 0.870 / 0.869 | 0.855 / 0.854 | 0.882 / 0.883 | 0.820 / 0.820 | 0.887 / 0.885 |
| dress-threeq | 0.803 / 0.816 | 0.859 / 0.859 | 0.709 / 0.723 | 0.619 / 0.711 | 0.879 / 0.872 |
| goth-threeq | 0.907 / 0.906 | 0.863 / 0.863 | 0.997 / 0.997 | 0.811 / 0.812 | 0.897 / 0.895 |
| beach-front | 0.910 / 0.910 | 0.854 / 0.854 | 0.972 / 0.973 | 0.753 / 0.754 | 0.929 / 0.929 |
| lawyer-side | 0.846 / 0.848 | 0.833 / 0.839 | 0.860 / 0.860 | - | 0.837 / 0.839 |
| dress-side | 0.841 / 0.839 | 0.879 / 0.871 | 0.737 / 0.735 | - | 0.906 / 0.906 |
| goth-front | 0.905 / 0.907 | 0.844 / 0.844 | 0.986 / 0.987 | 0.753 / 0.760 | 0.913 / 0.913 |
| hand-rest | 0.233 / 0.295 | | | | |
| pose-wave-lawyer | 0.767 / 0.767 | 0.596 / 0.596 | 0.884 / 0.885 | 0.836 / 0.828 | 0.782 / 0.784 |
| pose-walk-dress | 0.630 / 0.624 | 0.783 / 0.783 | 0.631 / 0.635 | 0.353 / 0.357 | 0.713 / 0.706 |
| pose-walk-goth | 0.795 / 0.799 | 0.831 / 0.831 | 0.988 / 0.988 | 0.577 / 0.579 | 0.782 / 0.791 |
| mean of all 24 | 0.784 / 0.787 | | | | |

The other shots move by 0.002 or less.

What the renders show: the twin's squared pads and the beach-front's pointed flaps are gone in every view of every outfit, at rest in A-pose and lowered; the shoulder runs from the neck over a round cap into the arm, and a raised arm (wave) meets the body under its own cap. Her neck runs into the shoulders and up under the jaw with no lumps, rings or ink, and her hips join the legs. The woman's head sits on her neck with the jaw's edge and no ring under it; her shoulders read round as before. The Dress hands rest beside the skirt, the twin's arms spread as the reference's do in that outfit. Pixel diff against the round's start: the woman, twin and person change where expected; among the creatures only the joins of forelimbs of role arm move, by a few hundred to two thousand pixels (dragon face 2180, fennec 1085, lizard 152) with no change to read in the images, and the toucan, koi, every foot and hind limb render the same.

The arms band stays at 0.75 to 0.82 in the rest body shots (0.71 in dress-threeq), short of 0.88. Without the push onto the body it reached only 0.80: the reference's upper arm lies about 0.04 m inside its torso at the armpit, Squash pushes the posed arm clear of the torso, so the twin's upper arm stands that much further out and the arm's whole outer edge shows red in the overlays. The rest is the hands (round 13) and the reference's shoulder cap, which stands above its torso's shoulder line where the twin's strip rounds a little below it.

For the next rounds (aimed at round 10, `garment-cuts`):

- Strips at the shoulder now slide: a garment's strip over an arm reads the body strip's slid foot column by column (`footOver`), so an opening or a neckline cut across the shoulder must cut the sleeve's strip as well as its branch, and the body faces a strip hides change with the pose (`StripJoin.underFoot`, `refixed`).
- A host's always-hidden set holds the strips' feet and the `Hider.Over` regions together; freeing a slid foot frees an `Over` vertex under it too until `hiddenAgain` runs. A cut outline built as an `Over` hider near the shoulder should keep a set of its own.
- The twin's puff sleeves show more bare skin seen under them in Dress (57 to 104 vertices an arm), not visible in the renders: the arm turned out beside the skirt, and the capped body strip runs close under the sleeve's strip. A sleeve's strip read from the body strip's rows offset by the ease (round 3's note) would close it.
- Lapels and cups lie on the chest, which the strips do not reach; the shoulder cap rises off the torso where a halter strap or a collar crosses the shoulder.
- The head's strip hides the head's floor near the neck; a collar or a choker on the neck meets the head's underside along the strip, not a ring.
- Left: the arm push against the reference's overlap (above); the person's shoulder ring seen 0.467 is its hanging arm lying in the torso's side below the strip, as round 3 measured.

## Open

- Whether the display pose is a pose of its own, as built, or the first entry of the pose library Shot will hold; and whether the Pose stage edits it, or shows it under the working pose as it does now.
- The three questions on garment-owned parts in `stage-9-garments.md`, built with the tentative answers listed there: owned parts listed in the Parts tree under the part they sit on, worn or not; a body part passing through an owned part overlaps it; owned parts may be pairs.

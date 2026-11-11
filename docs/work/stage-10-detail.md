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

## Open

- Whether the display pose is a pose of its own, as built, or the first entry of the pose library Shot will hold; and whether the Pose stage edits it, or shows it under the working pose as it does now.
- The three questions on garment-owned parts in `stage-9-garments.md`; round 5 takes the recommended answers there as tentative and lists them for Nicholas.

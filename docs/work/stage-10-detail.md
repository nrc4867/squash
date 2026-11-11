# Stage 10: detail

A working note for the branches that raise a complex character to finished quality: shading, rest pose, joins, free skirts, rigid props, the head and hair, and hands and feet. Folded into the HLD or deleted when it lands.

## Goal

The woman (`examples/woman.json`, `examples/woman-dressed.json`) reaches the quality of the reference renders of the same character built by hand in a general 3D engine: a palm with fingers that stay apart at the tips, arches and heels, a face with depth and a jaw, hair outside the skull, joins that read as one body, a skirt hanging free of straight legs, heels, shades with frames and lenses, lapels, and two-tone shading with no blotches. She stays a drawn character: every change is a command, every shape comes from outlines and settings a person can redraw.

The reference renders, outside the repo, are the bar to compare against side by side:
`C:\Users\nicho\AppData\Local\Temp\claude\1ecc764ac6b91634\87b9b827-4a83-48db-812d-f6f72c09da52\scratchpad\threejs-woman\renders\final\`.

## What stands today

`docs/work/example-limits.md` lists what the woman could not get, ranked, with causes in the code. The causes this stage answers:

| Flaw | Cause |
| --- | --- |
| Blotches on the bust, cheek and jaw | Two-tone shading reads the mesh's own normals, so the terminator follows every small bump |
| Arms lie through the body | Arms rest down at a few degrees, on sockets centred on the torso's drawn surface |
| Legs splay under the dress | A skirt is only possible as a torso redrawn into a bell, and hip sockets ride the torso's surface outward |
| Neck, shoulders and hips read as pegs | Parts meet by overlap; hand-made sockets have no sink; the blend fillet is a template block |
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

## Open

- Whether the display pose is a pose of its own or the first entry of the pose library Shot will hold.
- The three questions on garment-owned parts in `stage-9-garments.md`; round 5 takes the recommended answers there as tentative and lists them for Nicholas.

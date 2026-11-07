# Parts

Every body part a cartoon animal or person might have, and the mechanism that makes it. Four mechanisms cover nearly everything: a **template** (an axis spline with section splines and blocks, see [data-model.md](data-model.md)), a **socket** in the body plan or on a parent part, a **slot** that switches a part on or off, and a **mark** (strokes on the surface with angle versions, see [look.md](look.md)). What is not a shape is a mark; what is a shape is a template on a socket.

When: starter is spike 1, first is the first version, later is after it.

| Part | How Squash makes it | When |
| --- | --- | --- |
| Torso, chest | `torso` template. Side gives back and belly, Top gives width, Front sets the section: barrel, keel, flat-sided. Pecs and other detail come from the shape touch-up layer | Starter |
| Neck | Part of the torso's spine chain for short necks; a `neck` template on the neck socket for horses, giraffes, birds | Starter, `neck` first |
| Canine head | `head` template: skull block, muzzle tube, jaw joint, ear sockets, eye and nose landmarks | Starter |
| Horse, deer, fox head | The same `head` template: the muzzle tube's Side outline sets length and droop, its section (round, boxy, tapered to a point) and cap set the breed. Nostrils are marks | Starter |
| Human-like head | `head-round` template: skull with a short face block, a `nose` socket, forward eye landmarks, ear sockets at the sides. Hair is a `hair` shell part with tufts, as a mane is | First |
| Muzzle, snout, pointed nose | The head's muzzle block: length from Side, section from Front, cap shape round, flat (pig) or pointed (fox). A `pad` block at the tip is the nose leather, with its own palette slot | Starter |
| Human nose | `nose` template, a small tube with a rounded cap on the face socket, since it protrudes from a flat face | First |
| Beak | `beak` template on the muzzle socket: upper and lower tubes with pointed caps, sharing the jaw joint | Later |
| Ears, all kinds | One `ear` template: a tube with the `lens` section, an inner area with its own color, a base joint that swivels, and a `cup` parameter. The Side and Front outlines give the shape; the kinds below are drawings and parameter settings, not templates | Starter |
| Cat, fox, dog (pricked) | Triangular outline, cupped section, base swivel. Inner tufts are fur strokes | Starter |
| Dog (floppy), lop bunny | A `fold` joint at a drawn u lets the ear hang; a `floppiness` parameter adds a short bone chain with springy follow-through, as a tail has | First |
| Bunny (upright) | Long narrow outline, two or three bones along the length so it can bend and droop | First |
| Horse, deer | Small cupped outline, swivel at the base | Starter |
| Mouse, bear (round) | Round outline, shallow cup, nearly flat | Starter |
| Human | A small flat `lens` on the head's side socket, the ridges and lobe as marks | First, with `head-round` |
| Elephant | A large sheet with a bone chain, the `lens` section almost flat | Later |
| None (lizards, birds) | No ear part; the opening is a mark | Starter |
| Eyes | A mark group with roles (lid, white, iris, pupil, highlight, brow, lashes) on the skull's eye region, with per-eye open, expression and dilation controls and a gaze system that moves the pupils toward a target. Bulging eyes are an `eye` sphere part carrying the same group. See [look.md](look.md), Eyes and gaze | First (spike 9) |
| Mouth | A mark group with roles (lip line, inner, teeth, tongue, corners), an open control that drives the jaw and a lip morph together, and expression shapes; the line wraps the muzzle so it reads from the side. See [look.md](look.md), Mouth | First (spike 9) |
| Teeth, fangs, tongue | Marks on the mouth line by default. A `fang` template on a lip socket; a `teeth` row along the mouth landmark; a `tongue` template on a socket inside the mouth | Later |
| Legs, digitigrade or plantigrade | One `leg` template. The drawn Side outline bends the axis spline at the hock or not; a `stance` parameter bends the rest axis, and the joints along it, before anything is drawn | Starter |
| Arms | The `leg` template with arm joint names and a hanging rest axis: `arm` is a thin template file over the same blocks | First |
| Paws | `paw` template: tube, a toe row of none or three to five, pads underneath, cap | Starter |
| Hands, fingers, palms, thumbs | `hand` template: the paw's blocks with a toe row of five long digits, three joints each, and a per-digit override that moves and angles the thumb. The palm is the tube and a pad | First |
| Feet, toes | `foot` template: a plantigrade paw with a longer tube and a heel | First |
| Hooves | `hoof` template: a tube with a flat cap and a rigid area; a toe row of two for cloven hooves | First |
| Claws, fingernails | `claw` template, a short tube with a pointed cap, in the paw's claws slot; it attaches at each digit's tip socket, which the toe row exposes. Nails are the same part with a flat section, or a painted region on the digit tip | First |
| Tail | `tail` template: a tube on a bone chain, with an optional tufted tip in the outline | Starter |
| Chest ruff, cheek fluff, tail tip | Outline tufts drawn into the part's outline; a `ruff` shell part on the ruff socket for a big collar | Starter |
| Fur | Three kinds. Silhouette fur is strokes on the surface that show near the silhouette. Big tufts are drawn into the outline. Interior fur is flat color by design, with optional strokes for definition | Starter |
| Mane, long hair | A `hair` shell part, like a garment, with a loose bone chain and tufts in its outline | First |
| Whiskers, eyebrows, cheek marks, blush | Marks | Starter |
| Bird wings | `wing-feathered` template, role wing: one tube with shoulder, elbow and wrist joints along the axis and a pointed tip, the `lens` section, and a view remap so the planform drawn in Top sets each slice's chord and Side its thickness. It sits on a hand-made socket. Primaries are tufts in the outline; feather lines are strokes. Pose drags it by its wrist and never plants it | First |
| Dragon, bat wings | `wing-membrane` template: an arm chain, a toe row of long fingers, and a `membrane` block spanning between the digits and back to a body socket | Later |
| Fins, frills, crests, spines | `lens`-section templates on sockets; a row of them along the back uses a socket row, sockets repeated along a curve on the parent | Later |
| Horns, antlers, tusks | `horn` template, a tube with a pointed cap; antlers chain `horn` parts through a tip socket | Later |
| Shells, armor plates | A rigid garment shell over the torso | Later, with garments |
| Snakes, tentacles, antennae | Tail-like templates; a body plan with no leg sockets | Later |
| Six legs, extra limbs, segmented bodies | Body plan sockets; a second torso chained through a socket | Later |
| Clothing, collars, glasses | Garments and slots | Later, with garments |
| Holding things | A hold socket on the palm of the hand and paw templates, with a grip pose library per template; a held prop attaches to the socket and rides with the hand | First |
| Effects: sweat drops, shock lines, blush, speed lines, tears | Shot-level effect marks, pinned to a landmark or in camera space | First |
| Idle motion: breathing, blinks, ear flicks, tail sway | An automatic layer on the timeline per character, switchable, under the keyed poses | Later |
| Pose-time shape: bulging, contact, squash | Not parts: Implicit Skinning, Pose Space Deformation and touch-ups on whatever parts exist | Spikes 4 and 5 |

## Blank mode

Templates are accelerators, not the only way in. A character can start from an empty body plan and generic parts, with anatomy added by hand.

- **Blank** is a body plan, `blank`, with one root `tube` named Body, no sockets and the default palette, listed in New character after the species plans as One tube, anatomy by hand. Its root has no socket plane, so the tube's open end closes round on it.
- **Generic templates** `tube`, `blob` and `sheet` are data files over existing blocks, each with one joint at its start, so one bone from end to end, and no role: `tube` one tube run with an open start and a round cap, resting forward; `blob` a skull run closed with a pole at each end, a sphere when nothing is drawn; `sheet` a lens-section tube run open in v (`openV`), two faces meeting at an edge, thin by default, resting back along the body. Their `generic` field makes each part choose its section profile, which views set it, and its rings and segments, in the Shape inspector's Generic section, the template's own being the defaults. Move's angles turn a sheet into place: up for a dorsal fin, out for a side fin, down for a fin underneath.
- **Anatomy by hand.** Move adds sockets, joints and landmarks on any part. Add socket places a socket where it is pressed, with an outward angle and a pair flag, and Add part offers every template on it. Add joint places a joint at the pressed u on the part's axis, with a hinge, limits and a stretch flag; it becomes a bone, so a generic tube given a knee and a socket at its end for a foot reaches, bends within its limits and plants as a leg. Add landmark places a named surface point for a face. Pair with… makes two unpaired parts of one template a pair, and Rest in Place part turns a generic part to rest forward, back, up, down or out, so a tube on the body's rear end points back. The Shape inspector's Anatomy section lists a part's joints and landmarks.
- **A generic part's role is guessed.** A generic template gives no role, so a part placed from it has its role guessed from where it sits: a part leaving the root's rear end back along it a tail, one hanging down a leg, a sheet on the body's top or sides a fin, one on the head's top an ear. The Shape inspector's role chip shows the guess dashed until it is chosen. A generic tube rests forward, as Blank's root must, so a tube on the rear end is guessed other until its Rest is set to back, which guesses it again as a tail; the pose tools read the role. See [data-model.md](data-model.md), Roles.
- **Re-templating keeps the drawing.** Outlines live in part axes under the same views grammar, so a generic part can take a typed template later, or a typed part can drop to generic, and the outlines, parameters and surface work stay, through the part section's Template menu, one `SetTemplate`. A part may change to a template whose views set height and width as its own do, so every outline it reads keeps its plane, and that holds every socket of its own a child sits on; an outline of a view the new template does not read stays stored and sets nothing, its row reading off, as a generic part's view turned off does. A typed template gives its role; the root keeps its plan's template.
- **Save as template** turns a hand-built part, with its joints, sockets and landmarks, into a template file in the personal library: the saved templates' folder in the app's data folder, which the catalog loads after the shipped templates, so a part built once by hand is offered by Add part like any other template. See [data-model.md](data-model.md), Save as template.

Face mark groups on a generic head anchor to a landmark placed by hand, which is how the eye region is defined anyway.

## What this adds to the data model

- Built-in section profiles `circle`, `egg`, `keel` and `lens`, which a generic part chooses between; `lens` is the ear's, the wing's and the sheet's.
- A tube run open in v (`openV`), a sheet with two faces and an edge, and the `generic` template field.
- A per-template **view remap**, so a wing's planform drawn in Top can set height along its chord. The `views` map already allows it; the ear and wing templates use it.
- The **toe row** grows per-digit overrides (position, angle, joint count), and exposes a tip socket per digit for claws.
- A **socket row**: sockets repeated along a curve on a parent, for spines and frills.
- A **tip socket** on tubes, for antlers chained from horns.
- A **membrane** block, a surface spanned between digits or parts, for bat wings and webbed feet.
- The skull's jaw block tags the mouth interior as an `inner` area with a color.
- New template files, each a data file over existing blocks: `arm`, `hand`, `foot`, `hoof`, `claw`, `nose`, `eye`, `neck`, `hair`, `head-round` in the first version; `beak`, `fang`, `teeth`, `tongue`, `horn`, `wing-membrane`, `fin` later; `wing-feathered` ships.

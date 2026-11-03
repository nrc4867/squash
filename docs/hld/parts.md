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
| Ears | `ear` template: a tube with the `lens` section (thin, cupped), inner area with its own color, one bone | Starter |
| Eyes | Marks on the skull's eye landmarks with angle versions; pupils, highlights, lids and lashes are marks in expression sets. Bulging cartoon eyes are an `eye` template, a sphere on the eye socket, with the pupil as a mark on it | Starter (marks), `eye` part first |
| Mouth | A mark along the mouth landmark when closed. The jaw joint opens it; the skull template tags the mouth interior as an `inner` area with its own color | Starter (mark), open mouth first |
| Teeth, fangs, tongue | Marks on the mouth line by default. A `fang` template on a lip socket; a `teeth` row along the mouth landmark; a `tongue` template on a socket inside the mouth | Later |
| Legs, digitigrade or plantigrade | One `leg` template. The drawn Side outline bends the axis spline at the hock or not; a `stance` parameter sets the default joint positions and rest axis before anything is drawn | Starter |
| Arms | The `leg` template with arm joint names and a hanging rest axis: `arm` is a thin template file over the same blocks | First |
| Paws | `paw` template: tube, toe row, pads underneath, cap | Starter |
| Hands, fingers, palms, thumbs | `hand` template: the paw's blocks with a toe row of five long digits, three joints each, and a per-digit override that moves and angles the thumb. The palm is the tube and a pad | First |
| Feet, toes | `foot` template: a plantigrade paw with a longer tube and a heel | First |
| Hooves | `hoof` template: a tube with a flat cap and a rigid area; a toe row of two for cloven hooves | First |
| Claws, fingernails | `claw` template, a short tube with a pointed cap, in the paw's claws slot; it attaches at each digit's tip socket, which the toe row exposes. Nails are the same part with a flat section, or a painted region on the digit tip | First |
| Tail | `tail` template: a tube on a bone chain, with an optional tufted tip in the outline | Starter |
| Chest ruff, cheek fluff, tail tip | Outline tufts drawn into the part's outline; a `ruff` shell part on the ruff socket for a big collar | Starter |
| Fur | Three kinds. Silhouette fur is strokes on the surface that show near the silhouette. Big tufts are drawn into the outline. Interior fur is flat color by design, with optional strokes for definition | Starter |
| Mane, long hair | A `hair` shell part, like a garment, with a loose bone chain and tufts in its outline | First |
| Whiskers, eyebrows, cheek marks, blush | Marks | Starter |
| Bird wings | `wing-feathered` template: the leg blocks with shoulder, elbow and wrist joints along the axis, the `lens` section, and a view remap so the planform drawn in Top sets each slice's chord. Primaries are tufts in the outline; feather lines are strokes | Later |
| Dragon, bat wings | `wing-membrane` template: an arm chain, a toe row of long fingers, and a `membrane` block spanning between the digits and back to a body socket | Later |
| Fins, frills, crests, spines | `lens`-section templates on sockets; a row of them along the back uses a socket row, sockets repeated along a curve on the parent | Later |
| Horns, antlers, tusks | `horn` template, a tube with a pointed cap; antlers chain `horn` parts through a tip socket | Later |
| Shells, armor plates | A rigid garment shell over the torso | Later, with garments |
| Snakes, tentacles, antennae | Tail-like templates; a body plan with no leg sockets | Later |
| Six legs, extra limbs, segmented bodies | Body plan sockets; a second torso chained through a socket | Later |
| Clothing, collars, glasses | Garments and slots | Later, with garments |
| Pose-time shape: bulging, contact, squash | Not parts: Implicit Skinning, Pose Space Deformation and touch-ups on whatever parts exist | Spikes 4 and 5 |

## What this adds to the data model

None of it is needed before stage 1 ships except the first two.

- Built-in section profiles `circle`, `egg`, `keel` and `lens`; `lens` is needed by the starter ear.
- A per-template **view remap**, so a wing's planform drawn in Top can set height along its chord. The `views` map already allows it; the ear and wing templates use it.
- The **toe row** grows per-digit overrides (position, angle, joint count), and exposes a tip socket per digit for claws.
- A **socket row**: sockets repeated along a curve on a parent, for spines and frills.
- A **tip socket** on tubes, for antlers chained from horns.
- A **membrane** block, a surface spanned between digits or parts, for bat wings and webbed feet.
- The skull's jaw block tags the mouth interior as an `inner` area with a color.
- New template files, each a data file over existing blocks: `arm`, `hand`, `foot`, `hoof`, `claw`, `nose`, `eye`, `neck`, `hair`, `head-round` in the first version; `beak`, `fang`, `teeth`, `tongue`, `horn`, `wing-feathered`, `wing-membrane`, `fin` later.

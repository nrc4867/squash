# Lock hair

A recipe's `hair` is a list, one entry a head: a `preset` gives a whole hairstyle, `groups` change its groups by id or add new ones, `flow` sets which way the hair grows over the scalp, and `mass` or `fitMass` gives the inner mass the roots sit on. Sizes are in body units; angles in degrees; shares run 0 to 1 unless given otherwise.

## Groups

A group the recipe names by a preset group's id changes only the fields it sets; `groups: []` or no `groups` keeps the preset's groups as they are. A group of any other id is added and needs its `kind`; `removed: true` leaves out a preset group of that id.

| field | default | range | meaning |
| --- | --- | --- | --- |
| `count` | 7 | 1 to 48 | How many locks. |
| `spread` | 1.0 | 0 to 2 | How far round the head its roots reach, a share of its kind's own reach (a fringe's about 96 degrees across the front, a side's about 66, the back's about 116): 1 the kind's own, 0 all at its middle. |
| `around` | 0.0 | -180 to 180 | Degrees its roots turn round the head, positive toward the character's right. |
| `height` | 0.0 | -1 to 1 | How far up the head its roots move, -1 to 1 of about 20 degrees (15 for a crown, strays and the top, whose height moves it forward). |
| `length` | 0.12 | 0.005 to 3 | Each lock's length, in body units. |
| `lengthJitter` | 0.2 | 0 to 1 | How far lengths vary either way, a share of the length. |
| `width` | 0.05 | 0.002 to 0.5 | Each lock's width at its root, in body units. |
| `thickness` | 0.02 | 0.002 to 0.5 | Each lock's thickness at its root, in body units. |
| `taper` | 0.35 | 0 to 1 | What share of its root width a lock keeps toward its tip. |
| `section` | clump | - | - |
| `tip` | pointed | - | - |
| `tipPoints` | 2 | 2 to 3 | How many points a forked tip ends in. |
| `rise` | 10.0 | -30 to 90 | Degrees each lock's outer part lifts off the head, where it lies along the head from its root: 0 lies flat all along, 90 stands its end straight out. A tied lock leaves its tie at this angle. |
| `sweep` | 0.0 | -1 to 1 | How far the locks sweep sideways as they grow, over the way the scalp's flow grows them, -1 to 1: 1 turns a lock 70 degrees round over its length toward the character's right. |
| `fall` | 0.5 | 0 to 1 | How far the locks fall toward the ground along their length, 0 none to 1 hanging. |
| `curl` | 0.0 | -3 to 3 | How many turns each lock curls toward its underside; negative the other way. |
| `wave` | 0.0 | 0 to 2 | How far each lock waves sideways, a share of its width. |
| `twist` | 0.0 | -360 to 360 | How far each lock turns about itself over its length, in degrees. |
| `seed` | 1 | - | Which picks the jitter: another seed, another layout with the same look. |
| `layer` | 0 | 0 to 8 | Its layer: locks of a higher layer lie over those of a lower one. |
| `tie` | 0.0 | 0 to 1 | How far its roots gather toward one point, 0 spread over the head to 1 all at one tie. |
| `band` | none | - | The palette slot of a band round the tie; no band when left out. |
| `color` | none | - | Its palette slot; the inner mass's base colour, else the head's, when left out. |
| `flow` | 1.0 | 0 to 1 | How far its locks grow the way the scalp's flow says, 0 its kind's own way (down the head; a crown's swirling out from its middle) to 1 the flow's. |

| kind | roots and grows |
| --- | --- |
| `fringe` | across the front of the top, high enough that its locks lie down over the mass's hairline; the locks over the eyes are shorter, so the fringe parts to show them; grows down over the brow |
| `left` | along the character's left side in two staggered rows; grows down the head |
| `right` | along the character's right side in two staggered rows; grows down the head |
| `back` | over the back in up to three staggered rows, the lower rows shorter so the rows end together; grows down the head |
| `crown` | over a disc round the crown, laid out by the golden angle; grows out of the crown's middle, swirling round it |
| `strays` | round the top, wandering up and down; grows down the head |
| `top` | in two runs from behind the hairline back to the crown, one either side of the parting (or of the head's middle with none), each in two rows once it holds six locks or more; grows down the head, and away from a parting beside it |

| section | when |
| --- | --- |
| `ribbon` | a flattened lens cupped round the head: smooth, straight or wavy hair, a bob, long hair |
| `clump` | a rounded egg: spiky, short or curly hair, tufts |

| tip | when |
| --- | --- |
| `pointed` | closes to a point over its last 0.3: spikes and ragged ends |
| `round` | a dome whose last rings step evenly round it: a bob's blunt ends, curls |
| `forked` | split into `tipPoints` (2 or 3) points cut back between them: a choppy, layered fringe |
| `flicked` | turns out 50 degrees over its last 0.3: a flicked fringe or a flipped hem |

## Presets

Each preset's groups, their sizes (`length`, `width`, `thickness`) as shares of the head's reach from its middle, which the preset multiplies by the head as built or by `reach`. Every group not listed here is not in the preset: add it with its `kind`.

### shortSpiky

Flow: a crown whorl, no parting.

| group | kind | count | length | width | thickness | rise | fall | sweep | curl | taper | section | tip | layer | tie | flow |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `back` | back | 14 | 0.75 | 0.42 | 0.13 | 28 | 0.15 | 0 | 0 | 0.1 | clump | pointed | 0 | 0 | 1 |
| `left` | left | 5 | 0.4 | 0.4 | 0.12 | 6 | 0.6 | 0 | 0 | 0.1 | clump | pointed | 0 | 0 | 1 |
| `right` | right | 5 | 0.4 | 0.4 | 0.12 | 6 | 0.6 | 0 | 0 | 0.1 | clump | pointed | 0 | 0 | 1 |
| `fringe` | fringe | 9 | 1.15 | 0.5 | 0.12 | 12 | 0.3 | 0.1 | 0 | 0.1 | clump | pointed | 1 | 0 | 1 |
| `strays` | strays | 2 | 0.3 | 0.3 | 0.1 | 12 | 0.1 | 0 | 0 | 0.08 | clump | pointed | 1 | 0 | 1 |

### bob

Flow: a crown whorl, no parting.

| group | kind | count | length | width | thickness | rise | fall | sweep | curl | taper | section | tip | layer | tie | flow |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `back` | back | 9 | 1.55 | 0.85 | 0.12 | 0 | 0.85 | 0 | 0.12 | 0.85 | ribbon | round | 0 | 0 | 1 |
| `left` | left | 5 | 1.3 | 0.8 | 0.12 | 0 | 0.85 | 0 | 0.12 | 0.85 | ribbon | round | 0 | 0 | 1 |
| `right` | right | 5 | 1.3 | 0.8 | 0.12 | 0 | 0.85 | 0 | 0.12 | 0.85 | ribbon | round | 0 | 0 | 1 |
| `fringe` | fringe | 7 | 0.85 | 0.55 | 0.1 | 0 | 0.6 | 0 | 0 | 0.85 | ribbon | round | 1 | 0 | 1 |

### longStraight

Flow: a crown whorl, a centre parting.

| group | kind | count | length | width | thickness | rise | fall | sweep | curl | taper | section | tip | layer | tie | flow |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `back` | back | 16 | 3.6 | 0.6 | 0.14 | 0 | 0.95 | 0 | 0 | 0.45 | ribbon | pointed | 0 | 0 | 1 |
| `left` | left | 9 | 3.2 | 0.8 | 0.12 | 0 | 0.95 | 0 | 0 | 0.45 | ribbon | pointed | 0 | 0 | 1 |
| `right` | right | 9 | 3.2 | 0.8 | 0.12 | 0 | 0.95 | 0 | 0 | 0.45 | ribbon | pointed | 0 | 0 | 1 |
| `crown` | crown | 8 | 1 | 0.55 | 0.12 | 0 | 0.7 | 0 | 0 | 0.6 | ribbon | pointed | 1 | 0 | 1 |
| `top` | top | 12 | 2.4 | 0.6 | 0.1 | 0 | 0.9 | 0 | 0 | 0.45 | ribbon | pointed | 2 | 0 | 1 |
| `fringe` | fringe | 7 | 1.05 | 0.4 | 0.1 | 4 | 0.6 | 0 | 0 | 0.35 | ribbon | pointed | 1 | 0 | 1 |

### ponytail

Flow: a crown whorl, no parting.

| group | kind | count | length | width | thickness | rise | fall | sweep | curl | taper | section | tip | layer | tie | flow |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tail` | back | 7 | 2.2 | 0.45 | 0.15 | 30 | 1 | 0 | 0 | 0.15 | ribbon | pointed | 1 | 0.9 | 0 |
| `fringe` | fringe | 9 | 0.9 | 0.45 | 0.1 | 4 | 0.6 | 0 | 0 | 0.3 | clump | pointed | 1 | 0 | 1 |

### sideSwept

Flow: a crown whorl, a side parting at 0.62, sweep -0.35.

| group | kind | count | length | width | thickness | rise | fall | sweep | curl | taper | section | tip | layer | tie | flow |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `back` | back | 11 | 0.8 | 0.46 | 0.13 | 8 | 0.7 | 0 | 0 | 0.3 | clump | pointed | 0 | 0 | 1 |
| `left` | left | 5 | 0.65 | 0.4 | 0.12 | 4 | 0.75 | 0 | 0 | 0.3 | clump | pointed | 0 | 0 | 1 |
| `right` | right | 5 | 0.65 | 0.4 | 0.12 | 4 | 0.75 | 0 | 0 | 0.3 | clump | pointed | 0 | 0 | 1 |
| `crown` | crown | 8 | 0.8 | 0.46 | 0.12 | 0 | 0.5 | 0 | 0 | 0.3 | clump | pointed | 2 | 0 | 1 |
| `top` | top | 10 | 0.9 | 0.46 | 0.12 | 4 | 0.55 | 0 | 0 | 0.3 | clump | pointed | 1 | 0 | 1 |
| `fringe` | fringe | 7 | 1.25 | 0.42 | 0.12 | 6 | 0.45 | -0.75 | 0 | 0.25 | clump | pointed | 2 | 0 | 1 |

### curly

Flow: a crown whorl, no parting.

| group | kind | count | length | width | thickness | rise | fall | sweep | curl | taper | section | tip | layer | tie | flow |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `back` | back | 8 | 1.3 | 0.55 | 0.26 | 4 | 0.5 | 0 | -1.8 | 0.95 | clump | round | 0 | 0 | 1 |
| `left` | left | 4 | 1.1 | 0.55 | 0.26 | 4 | 0.5 | 0 | -1.8 | 0.95 | clump | round | 0 | 0 | 1 |
| `right` | right | 4 | 1.1 | 0.55 | 0.26 | 4 | 0.5 | 0 | -1.8 | 0.95 | clump | round | 0 | 0 | 1 |
| `crown` | crown | 9 | 1.9 | 0.7 | 0.32 | 40 | 0.4 | 0 | -1.8 | 0.95 | clump | round | 1 | 0.35 | 0 |
| `fringe` | fringe | 4 | 0.9 | 0.55 | 0.26 | 4 | 0.4 | 0 | -1.8 | 0.95 | clump | round | 1 | 0 | 1 |

## Flow

Which way each lock root grows: a background fall down the head, a whorl spiralling out of the crown, a parting the hair lies away from on each side, an overall comb over the top, and cowlicks, each reaching only so far. A group follows it as far as its `flow`, its kind's own way the rest; a group's `sweep` adds to it, and a lock's own values win over both. Roots keep off a parting's line, so the scalp shows along it as a thin line and nowhere else.

| field | default | range | meaning |
| --- | --- | --- | --- |
| `flow.sweep` | 0.0 | -1 to 1 | An overall comb toward one side, -1 to 1: positive toward the character's right; strongest over the top. |
| `flow.sweepBack` | 0.0 | -1 to 1 | An overall comb back over the top, -1 to 1: positive toward the back, negative forward. |

| field | default | range | meaning |
| --- | --- | --- | --- |
| `flow.whorl.around` | 180.0 | -180 to 180 | Degrees round the head from the front, positive toward the character's right: 180 is the back. |
| `flow.whorl.up` | 68.0 | 0 to 90 | Degrees up the head from level: 90 is the top. |
| `flow.whorl.turn` | 0.45 | -1 to 1 | How far hair turns round the whorl as it leaves it, -1 to 1: positive clockwise seen from above, 0 straight out. |
| `flow.whorl.strength` | 1.0 | 0 to 2 | How strongly it rules the hair round it, 0 not at all to 2. |
| `flow.whorl.radius` | 50.0 | 10 to 120 | How far across the head it reaches, in degrees. |

| field | default | range | meaning |
| --- | --- | --- | --- |
| `flow.part.kind` | none | - | none, centre or side. |
| `flow.part.at` | 0.0 | -1 to 1 | How far across the top it runs, -1 to 1 of the most a part sits off the middle (about the outer corner of the eye): positive toward the character's right. |
| `flow.part.length` | 1.0 | 0.2 to 1.5 | How far back from the hairline it runs: 1 reaches the crown. |
| `flow.part.clean` | 0.8 | 0 to 1 | How clean the split is, 0 a soft, wandering split to 1 a sharp, straight line. |
| `flow.part.color` | none | - | The palette slot of the scalp along it; when left out, a scalp tone: the head's colour darkened toward the hair's. |

| field | default | range | meaning |
| --- | --- | --- | --- |
| `flow.cowlicks[].around` | 15.0 | -180 to 180 | Degrees round the head from the front, positive toward the character's right. |
| `flow.cowlicks[].up` | 48.0 | 0 to 90 | Degrees up the head from level. |
| `flow.cowlicks[].radius` | 16.0 | 4 to 60 | How far across the head it reaches, in degrees. |
| `flow.cowlicks[].strength` | 1.0 | 0 to 2 | How strongly it rules the hair over it, 0 not at all to 2. |
| `flow.cowlicks[].turn` | 0.5 | -1 to 1 | 0 flips the hair back against the flow; 1 swirls it clockwise round the point seen from outside, -1 the other way. |
| `flow.cowlicks[].lift` | 20.0 | 0 to 60 | Degrees the locks over it lift off the head, most at its middle. |

## Outline

`outline` draws the hair's silhouette as the sheet does, one closed curve a view, and fits the hair to it: the inner mass's `crown` and `width` sized to the region the front outline covers, each tip of an outline taken by the lock whose tip lands nearest it from a root facing that view, that lock's root moved, its length, rise, sweep, fall, lie and width searched so its tip lands on the outline's tip and its sides follow the outline to the valleys either side, a tip pointing out from the head below the scalp's middle ending in a flick, and every other lock shortened, or laid down, where it would stray outside the outline. The fitted values are the locks' overrides below and the mass's parameters; a fit owns them, so values given for them by hand are replaced. Tied groups and locks with guides of their own are left as they are. The build prints, and writes under its `hair-fit.txt`, how far each outline lies from the hair as built, mean and worst in the head's drawn units, and warns of tips no lock reached. `kit sheet hair` traces an outline from a sheet's view (kit/README.md).

| field | default | range | meaning |
| --- | --- | --- | --- |
| `outline.fit` | true | - | Whether the build fits the hair to the outline: the outer locks' roots, lengths, rise, sweep, fall and widths so their tips land on its tips and their edges follow it, the inner mass's crown and width to the region it covers, and every other lock kept inside it. The fitted values are the locks' overrides and the mass's parameters. |

| field | default | range | meaning |
| --- | --- | --- | --- |
| `outline.views[].view` | front | - | front, three-quarter (the camera 45 degrees round toward the character's left), side (the camera on the character's left) or back. |
| `outline.views[].points` | [] | - | The closed curve round the region the hair covers as the view sees it, each [x, y]: x to the view's right and y up, in the head's drawn units from its origin, the frame face places use seen from the view's camera. 3 to 600 points. |
| `outline.views[].tips` | none | - | The points of its spikes and flicks, by index into points; found from the curve's shape when left out (a turn of 50 degrees or more out of the region within 2% of its length round). |
| `outline.views[].valleys` | none | - | The notches between them, by index into points; found from the curve's shape when left out (a turn of 35 degrees or more into the region). |

The lock overrides a fit sets, which a group's `locks` can also give by hand:

| field | default | range | meaning |
| --- | --- | --- | --- |
| `locks[].length` | none | 0.005 to 3 | Its length, in body units. |
| `locks[].width` | none | 0.002 to 0.5 | Its width at the root, in body units. |
| `locks[].rise` | none | -30 to 90 | Degrees its outer part lifts off the head, where it lies along the head from its root: 0 lies flat all along, 90 stands its end straight out. A tied lock leaves its tie at this angle. |
| `locks[].sweep` | none | -1 to 1 | How far it sweeps sideways as it grows, -1 to 1: positive toward the character's right. |
| `locks[].fall` | none | 0 to 1 | How far it falls toward the ground along its length, 0 none to 1 hanging. |
| `locks[].lie` | none | 0.05 to 0.95 | How much of its length lies along the head before it lifts off by its rise, 0.05 to 0.95: low stands it out from near its root, a spike; 0.55 when left out. |
| `locks[].around` | none | -45 to 45 | Degrees its root moves round the head from where its group lays it, positive toward the character's right. |
| `locks[].up` | none | -45 to 45 | Degrees its root moves up the head from where its group lays it. |
| `locks[].tip` | none | - | - |

## Inner mass

`fitMass` fits a `hair-volume` cap to the head as built, as the app's preset menu does: its size and crown from the head's box, its top following the skull's dome. Its `params` replace the fitted values; most heads need none. The fitted values, with each parameter's range from the template:

| parameter | fitted | range | meaning |
| --- | --- | --- | --- |
| `back` | 0.55 | -0.3 to 3 | How far it hangs below its socket at the back, a fraction of the length: about 0.5 to the nape on a round head. |
| `crown` | by the head | 0.1 to 1.2 | How far its top rises above its socket, a fraction of the length. |
| `flip` | 0 | 0 to 1 | How far its hem flares out, a share of its half width. |
| `fringe` | -0.5 | -0.5 to 1.5 | How far its middle hangs below its socket at the front, a fraction of the length: negative stops it above the socket's height, over the brow. |
| `fullness` | 2.4 | 1.5 to 6 | How full it stays toward its front and back: 2 rounds it as an ellipse, higher squares it. |
| `layers` | 0 | 0 to 4 | How many layers of locks lie over its hem, each inked as a ragged edge higher up with its points over the notches under it. |
| `lockDepth` | 0.12 | 0 to 0.6 | How far up between two locks its hem is cut, a fraction of the length. |
| `lockJag` | 0.5 | 0 to 1 | How much shorter some locks are than others, a share of the cut: a ragged, layered hem. |
| `lockLines` | 0 | 0 to 0.5 | How far up from each notch between two locks a line is inked, where one lock lies over the next, a fraction of the length. |
| `locks` | 24 | 0 to 60 | How many locks its hem parts into round the head, seen from above; 0 for a whole hem. |
| `rise` | 0.25 | -0.5 to 1 | How far its rings' middles rise toward the front, a fraction of the length, so its front closes on the forehead. |
| `sides` | 0.2 | -0.3 to 3 | How far its sides hang below its socket from the middle to the front, a fraction of the length: 0 to the ears, about 0.4 to the jaw. |
| `size` | by the head | 0.2 to 3 | Scales the part: its length, height and width together. Its length runs from the back of the head to the front. |
| `strands` | 0 | 0 to 90 | How many strand lines are inked flowing down over it from below its crown, each gathering into its lock's point toward the hem. |
| `width` | 0.42 | 0.2 to 1.2 | Its half width, a fraction of the length. |

# Head and face

Shape the head first and build it bare; only when its size and profile match the sheet from front, side and three-quarter, add the colour masks, then the eyes, then the nose, mouth and brows. Zoom the head in every view first (`kit sheet zoom ... region=head`).

## Which head

| head | use for | frame |
| --- | --- | --- |
| `head-round` (version 2) | a person, and any creature whose face looks forward from a round skull: a mouse, a bear, a cartoon cat | upright: its axis runs chin to crown, its face looks forward |
| `head` | a long-snouted animal on a body resting forward: a fox, a dog, a horse | rests forward, the muzzle a patch at its front |
| `head-bird` | a bird, with `beak` and `beak-lower` | rests forward |

`templates.md` lists each one's sockets (`ear` on both sides), joints (`neck`), areas (`face`, `crown`, `chin` on `head-round`), patches and landmarks; `docs/hld/parts.md` says how each is built.

## Drawing head-round

- Put the neck's end socket at `sink: 0` and draw the head from the neck's top; the outline's x runs up from the chin end. Its outline length is the head's height: crown minus the head seat from the plan.
- **Side is the profile, forward up in the drawing**: up the front from the chin over the lips, nose and brow to the crown, then down the back of the skull and forward under the jaw. **The nose point is the profile's most forward place.** A bump shorter than about 0.3 of the head's height (a nose, lips, a chin) stands out as a narrow ridge; a longer one (a muzzle) swells the whole front.
- **Top is the face's width** up from the chin, seen from the front: a round head is about as wide as it is tall, a mouse's or bear's cheeks widen it low down.
- Front is the section seen from above (egg by default).
- With the kit, `tube` with `rest: "up"` and `closed: true` draws Side from `front` (the face) and `back` (the skull's back) radii, Top from `left`: give all three, as every part (`body.md`).

```ts
import { roundStations, tube, type RecipePart } from "squash-kit";

const HEAD = 0.34;
const egg = roundStations(HEAD, 9, 0.5);

export const head: RecipePart = {
  id: "head",
  template: "head-round",
  on: "neck",
  socket: "socket.1",
  role: "head",
  color: "fur",
  blend: 0.03,
  inkJoin: false,
  outlines: tube({
    rest: "up",
    length: HEAD,
    closed: true,
    at: egg.at,
    profiles: { front: egg.radii(0.16), back: egg.radii(0.15), left: egg.radii(0.17) },
  }),
};
```

`egg.radii(r)` is a round profile of widest radius `r` at those stations; replace any station's radius with the sheet's to shape cheeks, a brow or a nose (a larger `front` radius a little below the middle stands the nose out).

## Face places

Regions, marks, eyes and paint strokes on a head take `face` places: `[x, y]` on the head seen from straight ahead, x to the character's left (negative its right), y up, in the head's drawn units from its socket (0 at the chin end, the head's length at the crown). Read them straight off the front view's zoom: the sheet's head from chin to crown is 0 to the head's length.

A face place must lie on the head as seen from ahead. `tools/recipe.sh kit probe <file> part=head` prints the reach of its x and y; the face narrows toward the chin and the crown, so keep a mask's corners well inside the box it prints, and `face=head:x,y` answers where one place lands.

## Masks and colour on the face

A muzzle, a two-tone face, a blaze, cheeks, lips and blush are regions on the head (`colour-and-marks.md`):

```ts
import { faceRegion, oval } from "squash-kit";

export const muzzle = faceRegion("muzzle", "cream", oval({ center: [0, 0.1], rx: 0.085, ry: 0.06, points: 8 }));
```

A two-tone face is two or more regions drawn in face places: the light mask first, then any darker patch over it, listed in the order they paint, with the same places along their shared border so they meet with no seam (`face-and-clothes.md`, Regions). Draw the mask in face places, not as a template patch region: a `patch: "muzzle"` region and a `face` region on one head meet with a seam.

## Face places

Measure the face off the sheet's front view before drawing it: `kit sheet measure` and `kit sheet zoom ... region=head` print `eyeLine` (its depth down the head from the crown), `eyeWidth`, `eyeHeight`, `eyeSpacing`, `nose`, `mouth` and `chinLength`, as shares of the head's height, with status and reason, and draw them over the zoom with the skin area, each eye's box, the muzzle, the nose and the mouth; a fringe over an eye's top makes its height unsure, a turned head makes every reading unsure (the far eye is narrower), and the nose and mouth are unsure where several marks compete: confirm each on the zoom. Only `eyeLine`, `eyeWidth` and `eyeSpacing` can be targets. A face that does not seem right is usually the eye line, the eyes' size or spacing, or the mouth's height: declare what you trust (`targets: targets({ eyeLine: "0.57 head", eyeWidth: "0.2 head", eyeSpacing: "0.35 head" })`) and fix every `FLAG` on `eyeLine`, `eyeWidth` and `eyeSpacing` in `measure.txt` (the built eye through its surface) before judging the face by eye.

## Eyes

Eyes are painted on the head (`faceEye`), never parts: `face-and-clothes.md`, Eyes, gives every look field with its range and meaning, and how long one unit across and down is on each head template.

```ts
import { faceEye } from "squash-kit";

export const eyes = faceEye("eyes", {
  on: "head",
  face: [0.075, 0.2],
  look: {
    width: 0.06, height: 0.09, lowerLid: 1, irisSize: 0.055, irisRatio: 2.2, pupilSize: 0.75,
    highlightSize: 0.35, lashes: 0, brow: false,
    white: "eye-white", iris: "ink", pupil: "ink", highlight: "eye-white",
  },
});
```

- Place it at the eye's middle on the front view, on the character's left; it mirrors to the right.
- **Look units to body units.** `look.width` is half the opening's width as a share of the head's own coordinate across at the eye, so the eye is `2 * look.width * unit across` wide; `look.height` is the lid's rise as a share of the coordinate down. On a drawn head the unit across is the ring round the head at the eye (a ring of half widths 0.16 and 0.15 is about 0.97 round) and the unit down the head's length (0.4): a 0.09 wide eye takes `width` 0.09 / (2 * 0.97) = 0.046, a lid rising 0.06 `height` 0.06 / 0.4 = 0.15. Then read `eyeWidth` in `measure.txt` (the built width, through the surface) against the sheet's and scale `look.width` and `look.height` by target over built.
- **Units are not square.** On an undrawn `head-round` a unit across is about 2.2 times a unit down, so a round iris takes `irisRatio` about 2.2 and a round opening a `height` about 1.5 times its `width`; a drawn head scales them with its own shape. Check the face close-up from the front and adjust until round reads round.
- A dark bead eye (a mouse's, a bear's) is an iris filling the opening: `irisSize` near `width`, the iris and pupil one dark slot, a highlight, `lid` and `brow` as the sheet shows.
- `flat: true` lays the eye flat across the face: right for a large eye on a small, round, front-facing head, wrong for an eye on the side of a long head, where it stretches.
- A shape smaller than about one cell of the head's chart (a tiny pupil or highlight) paints jagged or not at all: keep highlights at 0.25 or more of the iris.
- **Eyes reading as stuck-on discs** (a flat sticker with a hard edge, sitting proud of the curve, or staring from the side of the head): move the eye toward the face's front, lower its `lookOut`, drop `flat` on a long head, give it a lid line, and judge it from three-quarter, where the far eye must foreshorten.
- `front` gives a second look for front views, `gazeForward` and `gazeUp` aim the iris.

## Nose and mouth

- A nose is the profile's forward point plus a small region or mark in its colour: `faceRegion("nose", "nose", ...)` with a few places, `corners` at its point.
- A mouth is a mark group on the head with five roles in draw order, `inner`, `tongue`, `teeth`, `lipLine`, `corners`, each naming marks on the head (`face-and-clothes.md`, Mouth group). A closed mouth is a `lipLine` mark alone; give the group `controls: { open: 0 }`, since `open` is 1 when left out.

```ts
import { faceMark, smile, taper, type MarkGroup, type RecipeMark } from "squash-kit";

export const mouthMarks: RecipeMark[] = [faceMark("lip", smile({ center: [0, 0.075], width: 0.03, depth: 0.012 }), { width: taper(0.4) })];
export const mouth: MarkGroup = {
  id: "mouth",
  name: "Mouth",
  controls: { open: 0 },
  roles: [{ role: "inner", ink: false, fill: "mouth" }, { role: "lipLine", marks: ["lip"] }],
};
```

- An open mouth works on any head: the lips part on the surface, the `inner` role's `fill` shows between them, tongue marks ride the lower lip and teeth marks (a fang is three places) hang where drawn, all cut at the lips. Try it with `set=head:mouth.open:1`.
- Brows are marks with their own `slot` (a coloured brow) or the eye look's `brow`; whiskers are thin marks, mirrored; freckles and cheek patches small regions.

## Expressions

`expressions: [{ id: "happy", name: "Happy", joints: [{ part: "ear-l", joint: "base", degrees: 20 }], open: { mouth: 1 } }]`: joint angles of the face's parts and how far each mouth opens. Each renders as `<outfit>-face-<expression>.png`.

## Hair

Hair is one mass: a `hair-volume` over the skull and `hair-clump` locks blended into it, all in the one surface, so it shades and inks as one and only its ends part into locks (`kit/README.md`, Hair; `docs/hld/parts.md`, Hair). A head's hand-made sockets read v 0 at the face, 0.5 at the back, 0.75 at the left.

- **Volume.** `hairVolume({ on: "head", socket, depth, height, preset })` takes a socket at the back of the skull and runs forward to the forehead. Its `preset` (`short`, `bob`, `long`, `ponytail`) gives its shape; `params` tune it: `back` and `sides` how far it hangs, `fringe` where its middle stops over the brow and `sweep` which side it falls longer, `flip` a flared hem, `parting` and `partDepth` the parting.
- **Locks at the hem.** The volume's hem parts into `locks` locks cut `lockDepth` deep; `lockJag` makes it ragged, `lockSkew` leans the points, `lockFlare` splays them. Deeper cuts read better than many shallow ones: 0.25 to 0.35 for a bob, 0.2 to 0.3 for short hair.
- **Size relationship.** Hair parts do not scale with the head's `size`: give `depth` and `height` as the head is built (the drawn depth and height times its size). The volume's length is `depth` plus twice `thickness`; with `height` its crown sits `thickness` over the skull's top. Clump lengths are body units: a fringe lock about half the head's height, a lock over the sides or back about a third.
- **Clumps.** `hairClump` is one curved, tapering lock that blends into the volume where it roots; root it high on the volume and let it hang past the hem, so the mass splits into it toward the tip. Presets `lock`, `spike`, `soft`, `fluff`, `wavy`, `strand`; `params` set `width`, `thickness` (0.2 to 0.4 keeps it a body in the field), `taper`, `tipRound`, `twist`, `wave`, `cup`. A `curve` bends it out over what it hangs over.
- **Layouts.** `fringe` hangs clumps over the brow swept to one side (`sweep` negative toward the character's right): give the volume a higher `fringe` so they hang below it. `crown` roots clumps round the crown, low `rise` for a swirl, high for a cowlick. `fall` spreads one clump as copies, each its own mesh: for fur and manes, not hair meant to merge.
- **Colour.** `hem` fills a band along the lowest edge, following the locks; `parting` a strip along the parting; `shine` a band round the head that runs over every clump and tuft in the mass. A clump's `tip` fills its tip.
- **Ink.** The silhouette and the folds where locks part are inked by the surface; the volume's `strands` draws long lines flowing down from the crown.
- **Tufts.** `hairTuft` is a soft tuft blended into what it grows from: a cat's crown tuft, cheek fluff, an ear tip (presets `crown`, `cheek`, `point`).

```ts
const hair = hairVolume({ on: "head", socket: 11, depth: 0.3, height: 0.42, preset: "bob", color: "hair", hem: "hair-dark", shine: "hair-light", params: { sweep: -0.5, lockDepth: 0.32 } });
const front = fringe({ id: "fringe", on: "hair", firstSocket: 1, count: 2, lengths: [0.19, 0.15], from: 0.1, to: 0.02, sweep: -0.5, preset: "soft", color: "hair", tip: "hair-dark", params: { thickness: 0.14 } });
const side = hairClump({ id: "lock-1", on: "hair", socket: 3, length: 0.14, preset: "soft", color: "hair", tip: "hair-dark", rest: "down", curve: [[0.5, 0.06], [0.95, 0.16]] });
// head.sockets gets hair.headSocket; the volume gets the fringe's sockets and one for each lock; the clumps join the recipe's parts.
```

**Hair that reads as a helmet** has a whole hem: deepen `lockDepth`, add `lockJag`, and hang a few clumps past the hem over the sides and back. **Clumps that do not show** lie inside the volume: root them higher, give them a `curve` out, or raise the volume's hem where they hang. **A fringe that covers the eyes** is too long: shorten it or raise the volume's `fringe`.

## Hand-made sockets on the head

Horns, antennae, a bow and hair locks sit on hand-made sockets of the head: `probe part=head` prints which way v faces, and `face=head:x,y` turns a front-view place into its u and v.

## Limits

- **The jaw cannot rise.** A head's underside is one floor from chin to nape, so a jaw that rises from the chin to its angle with the neck going up behind it is not drawn; a strip joins the neck under the jaw.
- **No jaw joint on `head-round`.** An open mouth parts the lips on the surface; the chin does not drop.
- **One ridge width.** The profile's nose, lips and chin stand out by one share of the head's half width.
- **No eye sockets.** Eyes are paint on the surface; a deep-set eye or a brow ridge over it is drawn in the profile and with a brow mark.
- **The face reach is a box.** The probe prints a box; the face is narrower near the chin and crown.
- **Hair does not move with the head's size.** Give the volume the head's built depth and height.
- **Hair does not collide.** Clumps pass through the face and each other; place and curve them so they hang over the volume.
- **Each clump in the field costs a drag a little**: a head with twenty clumps drags slower than a bare one.

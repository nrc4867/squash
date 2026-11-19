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

Hair is a `hair-cap` on a hand-made socket on the crown and `hair` clumps and locks on sockets round it (`templates.md`, `docs/hld/parts.md`). A head's hand-made sockets read v 0 at the face, 0.5 at the back, 0.75 at the left. Give the hair's blob a narrow `blend` at the hairline: a wide one takes a share of the face and eases its paint (lips, blush) away. Strands are ink marks on the hair.

## Hand-made sockets on the head

Horns, antennae, a bow and hair locks sit on hand-made sockets of the head: `probe part=head` prints which way v faces, and `face=head:x,y` turns a front-view place into its u and v.

## Limits

- **The jaw cannot rise.** A head's underside is one floor from chin to nape, so a jaw that rises from the chin to its angle with the neck going up behind it is not drawn; a strip joins the neck under the jaw.
- **No jaw joint on `head-round`.** An open mouth parts the lips on the surface; the chin does not drop.
- **One ridge width.** The profile's nose, lips and chin stand out by one share of the head's half width.
- **No eye sockets.** Eyes are paint on the surface; a deep-set eye or a brow ridge over it is drawn in the profile and with a brow mark.
- **The face reach is a box.** The probe prints a box; the face is narrower near the chin and crown.
- **Hair reads as a helmet from the side**: the cap is one blob hugging the skull, locks are tubes over it, strands are only ink. There is no parting, clump or curl of its own.

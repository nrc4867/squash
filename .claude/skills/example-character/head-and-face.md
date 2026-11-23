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
- **The underside follows the neck style** (`body.md`, The neck style): on a `between` or `defined` neck the outlines start at radius 0 at x 0, so the head closes round under the jaw over the neck; on a `blended` one it may narrow into the neck.
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
- **A drawn toon eye's detail** is off by default; `animeEye(look, detail)` turns it on: a heavy upper lid mass, even over the top and tapering to the front corner, ending at the lid's shoulder with a short flick out past the back corner, a light partial lower lid, lashes tapering to fine points and curving out and up from the outer third in place of the flicks, an iris with a darker top, a lighter crescent and a rim, a lid shadow on the white, and a flatter top lid over a sharper back corner. Match each to the sheet with `detail` (`lid`, `flick`, `flickLift`, `lower`, `lowerExtent`, `clumps`, `clumpLength`, `shade`, `crescent`, `rim`, `lidShadow`, `flatTop`, `outerCorner`; 0 turns one off), and give the look its own slots where the sheet's tones are not the defaults (`irisShade.upperSlot`, `irisShade.lowerSlot`, `lidShadow.slot`; left out, the shadow is the white shaded toward a cool grey lilac). Keep `lidShadow` near 0.15 so the band hugs the lid. A sheet that inks the whole opening evenly keeps the plain lid; one with a few separate thin lashes sets `lashClumps.width` near 0.03 with a wide `spread`, or keeps `lashes` and sets `clumps: 0`. `close=eyes` renders both eyes close from the front, three-quarter and side to judge them beside the sheet: the lid mass must stay on the head and meet the lashes with no gap.
- **Lids.** Every eye closes: `upperClose` and `lowerRise` on the eye (0 open, 1 shut) stand its lids, and the look's `lids` say where they meet, `closedLids("smile")` for a happy closed eye's arch, `"frown"` for a sag, a tilt besides. A sheet with eyeshadow or a darker lid gives `lids: eyeshadow("lid-slot", { fadeTo, fade, above })`, and a liner wing `marks: [linerWing({ out, up })]`; both ride the lid as it closes. With no `lids.slot` a closing lid takes the colour of the face's painted area the eye sits in, so a cream face over darker fur closes cream. Render `close=eyes lids=open,0.5:0.5,blink` to see the eyes open, half shut and shut: a half-closed eye must read drowsy with the lid mass on its edge and its lashes still rising, a shut one a clean line along the closed curve with short lashes hanging down and out, and no white or iris past a lid. A sheet drawn mid-blink sets the eye's lids with `lidsAt(eye, { upper: 0.4 })`.

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
- **Layers and strands.** The mass stays one surface; its locks read as layers through ink. `layers` (1 to 2) inks ragged edges of upper layers of locks `layerGap` apart over the hem, their points over the notches below; keep `layerGap` near half `lockDepth` or more so the edges clear the hem. `lockLines` inks a line up from each notch where one lock lies over the next (0.1 to 0.2). `strands` (30 to 60) inks fine lines falling from the crown and gathering into each lock's point. The `short`, `bob` and `long` presets set layers and lock lines already.
- **Size relationship.** Hair parts do not scale with the head's `size`: give `depth` and `height` as the head is built (the drawn depth and height times its size). The volume's length is `depth` plus twice `thickness`; with `height` its crown sits `thickness` over the skull's top. Clump lengths are body units: a fringe lock about half the head's height, a lock over the sides or back about a third.
- **Clumps.** `hairClump` is one curved, tapering lock that blends into the volume where it roots; root it high on the volume and let it hang past the hem, so the mass splits into it toward the tip. Presets `lock`, `spike`, `soft`, `fluff`, `wavy`, `strand`, `fine` (narrow and pointed, the fringe's default); `params` set `width`, `thickness` (0.2 to 0.4 keeps it a body in the field), `taper`, `tipRound`, `twist`, `wave`, `cup`. A `curve` bends it out over what it hangs over.
- **Layouts.** `fringe` hangs clumps over the brow swept to one side (`sweep` negative toward the character's right): give the volume a higher `fringe` so they hang below it. `crown` roots clumps round the crown, low `rise` for a swirl, high for a cowlick. `fall` spreads one clump as copies, each its own mesh: for fur and manes, not hair meant to merge.
- **Colour.** Leave `hem` and `tip` unset for hair coloured to its tips; set them only for hair whose ends really change colour, never a dark shade standing in for shadow. `hem` fills an even band along every lock's end; `parting` a strip along the parting; `shine` a band round the head that runs over every clump and tuft in the mass. A clump's `tip` fills its tip.
- **Ink.** The silhouette and the folds where locks part are inked by the surface; the volume's `strands` draws long lines flowing down from the crown.
- **Tufts.** `hairTuft` is a soft tuft blended into what it grows from: a cat's crown tuft, cheek fluff, an ear tip (presets `crown`, `cheek`, `point`).

```ts
const hair = hairVolume({ on: "head", socket: 11, depth: 0.3, height: 0.42, preset: "bob", color: "hair", shine: "hair-light", params: { sweep: -0.5, lockDepth: 0.26, strands: 50 } });
const front = fringe({ id: "fringe", on: "hair", firstSocket: 1, count: 5, lengths: [0.2, 0.17, 0.14, 0.11, 0.09], from: 0.12, to: -0.06, sweep: -0.6, color: "hair", params: { width: 0.3, thickness: 0.2 } });
const side = hairClump({ id: "lock-1", on: "hair", socket: 3, length: 0.14, preset: "fine", color: "hair", rest: "down", params: { width: 0.3, thickness: 0.18 }, curve: [[0.5, 0.06], [0.95, 0.16]] });
// head.sockets gets hair.headSocket; the volume gets the fringe's sockets and one for each lock; the clumps join the recipe's parts.
```

**Hair that reads as a helmet** has a whole hem or no layers: deepen `lockDepth`, add `lockJag` and `layers`, hang a few clumps past the hem over the sides and back, and avoid a wide `flip` on short hair. **Dark patches at the hem or tips** are a dark `hem` or `tip` fill: leave them unset. **Clumps that do not show** lie inside the volume: root them higher, give them a `curve` out, or raise the volume's hem where they hang. **A fringe that covers the eyes** is too long: shorten it or raise the volume's `fringe`.

### Lock hair

Lock hair is the way to a silhouette that breaks: each lock a separate swept part in groups over an inner mass (`kit/README.md`, Lock hair; `docs/hld/parts.md`, Hair locks; every field's range and units and each preset's groups in `docs/reference/hair.md`). Use it for any hair whose outline has points, flicks, parted fringes or curls.

- **Mass first.** `mass: lockMass()` fits the inner mass to the head as built, its top following the skull's dome, the cap the app fits. Build bare and read the head from the back, the three-quarter view and the top: skin showing between locks is the mass too narrow (`lockMass({ params: { width } })`) or its back too short (`back`); ranges are in hair.md, Inner mass. Change one parameter a little at a time: guessing several at once flattens the top. Place a mass by hand (`lockMass({ on, socket, depth, height })`) only to match a sheet exactly.
- **Then a preset.** `bob`, `shortSpiky`, `longStraight`, `ponytail` (with a `band` slot), `sideSwept` or `curly` on the head with `mass` and `color`. It sizes itself to the head; give `reach` only to match a sheet exactly. hair.md lists each preset's groups: a group a preset does not hold (`shortSpiky` has no crown, `bob` no strays) is added with its `kind`.
- **Change groups by id.** A group's `count`, `length`, `spread`, `around`, `height`, `rise`, `fall`, `sweep`, `curl`, `tip` and `section` change one whole group; `lengthJitter` and `seed` its raggedness. No `groups` (or `groups: []`) keeps the preset's. A fringe parts over the eyes by itself; shorten its `length` if it hides them. `locks` pulls single locks out by index.
- **Then the flow.** The sheet's parting first: `flow: { part: sidePart("left", 0.6) }` or `centrePart()`, on the side the sheet's hair splits; the scalp shows as a thin line along it and the hair lies away from it. Read the top view: one clean line, locks lying away on both sides, no other skin. A crown whorl spirals from the back of the crown by default; move it with `whorl({ around, up, turn })` where the sheet's hair spirals. A fringe flick at the hairline is `cowlick({ around, up, lift })`. A group that should ignore the flow takes `flow: 0`.
- **Rise** lifts only a lock's outer part: locks lie along the head from their roots, so `rise` above about 30 on short locks reads as spikes standing out. A tuft or a tail that should stand off the head takes `tie` (its locks then leave the tie at `rise` and hang free).
- **Curls** roll into a spiral from about half a lock's length. The `curly` preset's locks are few, big and round-tipped, rolling outward; its crown is a tuft tied at the front of the top. For a tuft alone (a creature's curled forelock) use `curly({ on, mass, color, tuft: true })`. A curl tighter than its thickness folds into itself.
- **Fit to the sheet's outline.** Once the preset and flow are close, trace the hair from each view the sheet draws: `tools/recipe.sh kit sheet hair <sheet> view=<view> as=<front|three-quarter|side|back>`. It finds the chin, the head's size and the hair's colours on its own, as `sheet measure` reads the head; read `hair-<view>.png`: one region round the hair, its tips on the spikes and flicks, the green cross on the chin. Where it reads wrong, `chin=x,y`, `colors=` and `tolerance=` override it. Give `outline: hairOutline([...views].map(readHairOutline))` to the hair entry and build: it prints, and writes in `hair-fit.txt`, how far each view lies from the hair (`mean` and `worst` in head units, under 0.03 mean reads as a match), and `hair-fit-<head>-<view>.png` show the hair, the outline, its tips and valleys. Each tip no lock reached is warned with why: give the group near it more locks or a group on that side. Hand-written outlines work the same; give `tips` where the found ones miss a spike.
- **Cuts.** Hair cut to the skin is a shell, not locks: `buzzCut`, `fade({ level })`, or any entry's `shell: hairShell({ ... })` under locks. Shape its hairline to the sheet (`hairline: { front, temples, sideburns, ears, nape }`, degrees up the head; raise `temples` above `front` for a receding corner) and read the front and side views: the hairline should sit where the sheet draws it, the fade blend into the skin with no band. An undercut or flat top roots on the head over a shell, with no mass. Blunt ends (a bob's hem, straight bangs, a flat top) are a group's `trim`; a level brush is a low `lie` and a rise near 90 cut by a trim over the head. A braid is a tied group of one lock with `section: "braid"`; a bun a tied group with `coil` about 1.5.
- **Judge** the head beside the sheet's views with `close=hair` (`<outfit>-hair.png`: front, three-quarter, side and back from the outline views' own cameras, the three-quarter from the front as a sheet draws it) and from every side with `close=head`: the outline should break where the cut has points, no scalp shows between locks or anywhere but along a parting, and the back has its own layout.

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

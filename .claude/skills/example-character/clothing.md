# Clothing

Clothes come after the bare body is right; one garment a run. A garment is cloth painted on the body as Dress paints it: cells of each part's chart standing off the body by the garment's fit, inked at its hems and seams. `face-and-clothes.md`, Garments, is the reference for every field and stroke; `recipes/painted-tee.recipe.json` is a worked tee and shorts in JSON; `docs/hld/surface.md`, Garments on the surface, says how cloth is built.

## The wardrobe

Slots are places on the body an outfit fills once (`top`, `bottom`, `feet`, `head`); garments go in slots; outfits pick a garment a slot; `shown` is the outfit the character is saved in. The kit reads the slots from the garments:

```ts
import { garment, LAYER, outfit, wardrobe, type RecipeArea } from "squash-kit";

const teeAreas: RecipeArea[] = [
  {
    id: "body",
    name: "Body",
    paint: [
      { on: "torso", shape: "band", u: [0.4, 0.9] },
      { on: "arm-l", shape: "band", u: [0, 0.3] },
    ],
  },
  {
    id: "rib",
    name: "Ribbing",
    color: "rib",
    paint: [
      { on: "torso", shape: "band", u: [0.86, 0.9] },
      { on: "arm-l", shape: "band", u: [0.25, 0.3] },
    ],
  },
];

const tee = garment({ id: "tee", slot: "top", layer: LAYER.waistband, color: "tee", fit: 0.015, areas: teeAreas });
const shorts = garment({
  id: "shorts",
  slot: "bottom",
  layer: LAYER.under,
  color: "shorts",
  fit: 0.02,
  areas: [
    {
      id: "body",
      paint: [
        { on: "torso", shape: "band", u: [0, 0.45] },
        { on: "leg-l", shape: "band", u: [0, 0.35] },
      ],
    },
  ],
  holes: [{ part: "tail" }],
});

export const clothes = wardrobe([tee, shorts], [outfit("casual", { top: "tee", bottom: "shorts" })]);
```

## Painting the cloth

- **Cells.** A part's chart is 64 rows along it (u, 0 at its root) by 32 columns round it (v). A stroke paints the cells whose middles it holds; the cloth's edge is a smooth curve over their edges. **Paint under two cells wide drops out**: a band is at least 2/64 (0.032) of its part long, so a rib on a short neck or sleeve is wider in u than it looks.
- **Strokes** (`face-and-clothes.md`, stroke): `band` (rows the whole way round), `box` and `ellipse` (u and v spans; `v` from below 0 crosses v 0), `lasso` (a polygon of `[u, v]` points, or `face` places on a head), `brush` and `line` (a path with a `size` in body units, reaching on to the parts beyond: from a sleeve onto the hand). `unpaint: true` takes cells out of every area; `mirrored` paints a middle part's other side; `clean` smooths the edge.
- **u and v from the plan.** A hem's u is a height on the part's span (`torso.u(plan.h("waist"))`); which way v faces depends on the part's rest, so ask `probe part=torso` before a box or lasso. On a torso resting up v 0 is the front.
- **Sleeves and trouser legs from u 0.** Paint the limb from its root: its first rows meet the torso's cells and the cloth runs on from the shoulder or hip in one piece. A pair is painted by its left side; both sides take it.
- **Areas are colours.** Each area has its own `color` (left out: the garment's). A later area's strokes take cells from the earlier ones; where two meet the cloth stays one piece with an inked seam. A collar, cuffs, a waistband, a yoke, a two-tone raglan are areas of one garment, never separate garments.
- **Curved hems and splits**: paint the band, then an `unpaint` ellipse or lasso over the hem where it should curve or open: a shirt tail, a side split, a round or V neck, a jacket's front opening that widens toward the neck. Put the unpaint stroke last in the area it cuts; an area holding only unpaint strokes warns that it holds no cells. A round neckline on a torso resting up: `{ on: "torso", shape: "ellipse", unpaint: true, u: [0.78, 1.02], v: [-0.12, 0.12] }`.

## What comes out, what tucks

With areas, the build reads from the cells what the cloth covers, which parts come out through it, which small parts it tucks and which seams it inks:

- A part on a socket inside the painted region that is not painted itself comes out bare: a neck through a collar, a head and ears under a hat painted on the crown, a tail through the seat.
- `holes: [{ part: "tail", cuff: false }]` lets a part and everything beyond it out at its root; `cuff: true` rings it with a band of the cloth (a sleeve's cuff, a turtleneck).
- `tucks: [{ part: "tail", kind: "tail", tucked: false }]` shapes cloth over a small part instead of tucking it under (`kind`: toes, claws, fingers, ears, tail, horns, spikes, fins). Left out, toes, claws, tails, horns and spikes tuck; the rest are shaped.

## Fit and layers

- `fit` is how far the cloth stands off the body in body units, 0 or more (a negative fit is a recipe error): 0 to 0.005 for leggings, a tight tee or socks, about 0.008 to 0.012 for fitted jeans or a tee, 0.02 to 0.03 for a loose tee or a jacket over another layer on a person-sized body, less on a small one. At 0 the cloth matches the body exactly; a default stand-off reads as a shell, so most everyday clothes want a small fit. `partFits` sets one part's (a pair by its left side): **long sleeves bell out** like a cape when the arm is thin under a flat fit, so give the arm a smaller fit than the torso.
- Every hem stands a hair off the skin by itself, so a hem at fit 0 shows no skin sliver. `hemRoll` (0 to 0.03 body units) rolls the hems, neck opening and cuffs out past the cloth: about 0.004 to 0.008 makes a cuff or a rolled hem read as cloth with a thickness.
- `inkWeight` is the cloth's silhouette and hem ink as a share of the skin's line weight, 0.7 when left out; 1 inks it like skin, lower reads lighter.
- `layer` counts out from the skin: under a waistband 1, a waistband 2, over both 3 (`LAYER.under`, `.waistband`, `.over`). A tee worn over shorts is the higher layer and its band overlaps the shorts' top, so no cloth edge stops bare above the crotch.
- Draw the body under clothes thinner than the sheet's clothed silhouette, by about the fit: the garment stands off it to the silhouette.
- **Boxy clothes** mean the cloth follows a boxy body: shape the torso's profiles (waist, chest, seat; `body.md`), not the garment.

## Prints on the cloth

A chest symbol, a stripe, a pocket, stitching are the garment's own `regions` and `marks`, written as a part's but with `on` naming the body part under the cloth, in its u and v (or face places on a head). They ride the cloth and show only where it is painted:

```ts
import { oval, printOn, taper, uvMark, uvRegion } from "squash-kit";

/** A round badge on the chest: u up the torso, v round it from the front (v 0). */
export const badge = printOn("torso", uvRegion("badge", "badge", oval({ center: [0.72, 0], rx: 0.05, ry: 0.035 }), { ink: true }));
export const stitch = printOn("torso", uvMark("hem-stitch", [[0.44, -0.2], [0.44, 0], [0.44, 0.2]], { slot: "rib", width: taper(0.4) }));
```

- A region with `ink` is a filled shape with an inked border; `mirrored` shows it on the other side of the body too (or a pair's other side).
- A pocket is a region and a thin stitched mark along its edge, never an owned blob on the cloth.
- A print ends where the cloth does; place it well inside the painted cells.

## Changes, covers and swaps

- `changes` by part (a pair by its left side): `through(cuff)` for an armhole or neckline (`cuff` false) or a sleeve cuff, `hide()` for a part the garment hides, `redraw(shape)` for a part reshaped while worn (outlines, params, colour, regions laid over the part's: fewer toes, a closed toe box), `swap(template, shape)` for a part rebuilt from another template (a closed shoe as a `blob`). With areas, a part the cells say something of takes what they say; a change holds on every other part. How redraws and swaps combine is in `face-and-clothes.md`.
- A closed shoe: a `swap` of the foot to a `blob` drawn as the shoe, or on `foot` a `redraw` setting `toes: 0` and `toeBox: 1` with cloth painted over the whole foot.
- `covers` are the simple form, a box of cells a part (`cover`, `band` in the kit, snapped to cell edges), painted into the first area. A **skirt** is a cover over the torso with `from` below -0.1 and Side and Top `outlines` along the torso's own x, negative below it, for its flare; the legs hang inside it. Never redraw the torso into a bell.
- Loose ends: `looseEnds: [{ id: "scarf-end", branch: "torso", v: 0.04 }]` hangs a short chain of bones from the hem of the cloth over `branch` (a body part the garment covers), at `v` round it, `bones` long (5 by default).

## Rules that keep outfits apart

- A garment never moves a socket; never turn or resize limbs per outfit to clear a garment.
- A garment shared between outfits reads u on each part as the other garments leave it; a `redraw` that changes a part's length moves every other garment's range on it.
- Two garments' owned parts never reuse a socket number on one part (`accessories.md`).

## Judging clothes

Build `outfits=all poses=all`. In each outfit's renders check the hems, openings and seams from front, three-quarter, side and back. In `measure.txt`:

- the `wardrobe:` lines list each garment with its slot, layer, fit, covered parts (u and v spans), changes, holes and owned parts, and each outfit's picks: check them against the feature list;
- `poke-through <inner> through <outer>` per outfit, and per outfit and named pose (`<outfit>, <pose>: poke-through ...`): the body or an inner garment drawn outside a garment over it, with how many vertices, how deep and on which parts. `poke-through none` is the aim; raise the outer garment's `fit` (or that part's `partFits`) past the deepest, or lower the inner one's.

Render a bent pose (sitting, an arm raised) for every outfit: hems and openings that hold at rest can open in a pose.

## Limits

- **No loose cloth.** A skirt hangs and flares but has no sway; a dress as one piece from hem to neck is held at its far ring and its waist can stand off the body. A lighter band of shade can show at a skirt's waist where its drawn outline starts.
- **Collars and lapels.** A collar is only the cuff a hole grows round the neck; nothing folds over. Lapels are rigid owned parts (`accessories.md`) that stand off in side view and do not follow a spine bend.
- **Hoods** are a lump behind the head, not a hood framing the face.
- **Open edges are cuts.** An open garment is one sheet: its edge can roll out (`hemRoll`) but has no underside.
- **Flat shading.** Cloth is shaded flat like skin: no folds, bunching or wrinkle shading; creases are marks drawn on the cloth, and they ride the cloth, so they fade as a joint bends instead of deepening.
- **No see-through cloth.** A palette slot's `opacity` on a garment's colour or an area's is not drawn on cloth (the build warns); sheer cloth draws whole.
- **Armpits in tight sleeves.** A raised or bent arm can push skin through a sleeve at fit 0 near the armpit; give the arm a small `partFits`.
- **Loose ends are short flat tabs**, and a neck wrap carrying one flares like a cape collar.
- **Thighs that touch** make trousers read as one tube: keep the legs apart in the body (`body.md`, widths).
- **Garments on all fours**: cloth hangs and flares only past an end that points down; a cape on a body resting forward trails rather than drapes.

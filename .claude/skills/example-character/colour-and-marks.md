# Colour and marks

## The palette

Every colour used anywhere is a slot named in the palette. Take the colours from the sheet (`kit sheet palette`, `reading-a-reference.md`), name each for what it is, and declare them once:

```ts
import { palette, shade } from "squash-kit";

export const colors = palette(
  {
    fur: "#8a97b0",
    cream: "#f0e4cd",
    "inner-ear": "#e8a5a5",
    nose: "#57303c",
    "eye-white": "#ffffff",
    tee: "#5f9e6e",
    rib: shade("#5f9e6e", -0.15),
    lens: "#203040",
    ink: [0.16, 0.13, 0.15],
  },
  { opacity: { lens: 0.35 } },
);
// palette: colors.slots; color: colors.slot("fur") fails to typecheck on a slot not declared.
```

- `ink` is the line colour; give it the sheet's line colour.
- `shade(c, -0.15)` and `mix(a, b, t)` derive trims and shadows from a base colour, so a palette stays one family.
- A slot's `opacity` (0.3 to 0.5) makes a part coloured in it see-through: a lens shows the eyes behind it (`accessories.md`).
- Squash shades every colour two-tone by its own light (`docs/hld/look.md`): give the lit colour the sheet shows, not its shadow. With no `shadow` a saturated colour shades warm and a white, grey or pale one cool, so white socks and shirts stay white in shade; a slot's `shadow` sets its own shadow tone where the default is wrong.
- The light turns with the view, so a back view is lit as the front is. Where the sheet's far legs and far ear are clearly darker, raise the far side's depth: `shading: { farSide: 0.3 }` (0 to 0.5, 0.18 when left out); `shading: { light: "body" }` fixes the light in front of the character instead.

## Base colours and areas

- A part's `color` is its base slot.
- `areas` fill the areas a template names: an ear's `inner`, a paw's `pad`, `sock`, `top` and `toes`, a hand's `nail:0` to `nail:4`, a head-round's `face`, `crown` and `chin`, a tail-curve's `tip`, `base` and `underside`. `templates.md` lists each template's areas and their u and v. Some show in a named slot when the palette has it (`pad`, `claw`, `nails`, `inner-ear`).

## Regions

A region is a patch of colour on a part: a belly, a muzzle, a blaze, socks, a tail tip, lips, blush. Its border is given one way (`face-and-clothes.md`, Regions):

- `face` on a head: `[x, y]` places seen from straight ahead (`faceRegion`; `head-and-face.md`);
- `border` as `[u, v]` on the part (`uvRegion`), with `patch` for a template patch;
- `anchors` across parts.

```ts
import { oval, uvRegion } from "squash-kit";

export const belly = uvRegion("belly", "cream", oval({ center: [0.45, 0], rx: 0.25, ry: 0.14, points: 8 }), { mirrored: true });
```

- Borders are smooth curves through their places: four or five places make a round patch; `curved: false` runs straight between them. Sharp corners come from the shape a kit helper is given: `polygon(points)` makes every place a corner (a triangular nose), `shape(points, [2])` only the ones named; a plain list of points has none.
- On a part in the body's middle (torso, head), v 0 faces forward on a part resting up, so a belly patch round v 0 crosses it: places with v below 0 wrap; `mirrored: true` shows it again at 1 - v. Ask the probe which way v faces before placing one (`probe part=torso`, or `uv=torso:0.45,0`).
- Regions that meet share the places along their border, so they meet with no seam; a later region in the list paints over an earlier one.
- A region on a part joined into the one surface (a belly running onto the neck) ends where that part's share ends: run it across parts with `anchors`, or give each part its own region meeting at the join.

## Marks

Marks are lines on the surface: brows, whiskers, a mouth, stripes, stitches, fur roots (`faceMark`, `uvMark`):

- `width` stops thin or swell a line (`taper(0.3)` thins both ends); `slot` colours it (a brown brow); `ink: false` fills a closed mark as a shape; `kind: "fur"` makes it a fur root.
- `mirrored: true` paints it again on the other side.
- A mark group (a mouth) names marks in roles (`head-and-face.md`).
- `lineWeights` make the outline heavier along a stretch of a part.

## Limits

- **Small shapes paint jagged.** A region or eye shape under about one cell of its part's chart (a part's u split 64 ways by its v 32 ways) paints as a jagged patch or not at all: make spots at least two cells across, or a mark.
- **A template patch region beside a face region** on one head meet with a seam of two shades; draw face colour in face places only.

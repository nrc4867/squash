import { channelDistance } from "./core/color.ts";
import { blankMask, components, dilate, erode, type Mask } from "./core/mask.ts";
import { isShade } from "./shade.ts";
import { pixelAt, type Pixel, type Raster } from "./raster/image.ts";

/** A rescued region is at least this share of the sheet's pixels, so noise in the paper never joins the figure. */
const PALE_MIN_SHARE = 0.0004;
/** The noise a region must survive, as the radius of the opening that cleans it. */
const PALE_OPEN = 2;
/** A region joins the figure only where it touches it for at least this many pixels. */
const PALE_CONTACT = 8;
/** A rescue that would add more than this share of the figure's own area is refused: the background model is off, not the figure pale. */
const PALE_MOST = 0.25;
/** The strict tolerance is this many times the border's noise, plus a floor. */
const STRICT_SPREAD = 2.5;
const STRICT_FLOOR = 4;
const STRICT_LEAST = 8;
/** The strict tolerance stays this far under the normal one, or there is nothing between them to rescue. */
const STRICT_MARGIN = 4;

/**
 * Pale parts of a figure the flood fill took for background because they lie within its tolerance of the paper: white socks, pale
 * hands, a cream sleeve. A part is rescued where it stands clear of the paper at a stricter tolerance, is not a cast shadow (which
 * only darkens the paper), is wide enough to survive an opening, and touches the figure. The figure is returned with them, and the pixels rescued alone.
 */
export function rescuePale(image: Raster, colorAt: (x: number, y: number) => Pixel, tolerance: number, noise: number, figure: Mask): { figure: Mask; pale: Mask } {
  const strict = Math.max(STRICT_LEAST, Math.round(STRICT_SPREAD * noise) + STRICT_FLOOR);
  const none = { figure, pale: blankMask(figure.width, figure.height) };
  if (tolerance - strict < STRICT_MARGIN) return none;
  const { width, height } = figure;
  const model = { colorAt };
  const clear = blankMask(width, height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const at = y * width + x;
      if (figure.data[at]) continue;
      if (channelDistance(pixelAt(image, x, y), colorAt(x, y)) > strict && !isShade(image, model, x, y)) clear.data[at] = 1;
    }
  }
  const opened = dilate(erode(clear, PALE_OPEN), PALE_OPEN);
  for (let i = 0; i < opened.data.length; i++) opened.data[i] &= clear.data[i];
  const near = dilate(figure, 2);
  const keep = blankMask(width, height);
  const least = Math.max(1, PALE_MIN_SHARE * width * height);
  for (const piece of components(opened).pieces) {
    if (piece.area < least) break;
    let contact = 0;
    for (const at of piece.pixels) if (near.data[at]) contact++;
    if (contact >= PALE_CONTACT) for (const at of piece.pixels) keep.data[at] = 1;
  }
  const added = dilate(keep, PALE_OPEN + 1);
  const out = blankMask(width, height);
  const pale = blankMask(width, height);
  let figureArea = 0;
  let addedArea = 0;
  for (let i = 0; i < out.data.length; i++) {
    const on = figure.data[i] || (added.data[i] && clear.data[i]) ? 1 : 0;
    out.data[i] = on;
    pale.data[i] = on - figure.data[i];
    figureArea += figure.data[i];
    addedArea += on - figure.data[i];
  }
  return addedArea > PALE_MOST * figureArea ? none : { figure: out, pale };
}

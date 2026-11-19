// Synthetic sheets for the sheet tools' tests: invented figures drawn from rectangles and ellipses.
import { blankRaster, setPixel, type Pixel, type Raster } from "squash-kit/sheet";

export const PAPER: Pixel = [246, 242, 236];
export const FUR: Pixel = [90, 150, 210];
export const INK: Pixel = [30, 30, 40];

export function sheet(width: number, height: number, ground: Pixel = PAPER): Raster {
  return blankRaster(width, height, ground);
}

export function rect(image: Raster, x0: number, y0: number, x1: number, y1: number, color: Pixel): void {
  for (let y = Math.round(y0); y < Math.round(y1); y++) for (let x = Math.round(x0); x < Math.round(x1); x++) setPixel(image, x, y, color);
}

export function ellipse(image: Raster, cx: number, cy: number, rx: number, ry: number, color: Pixel): void {
  for (let y = Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
    for (let x = Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
      if (((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1) setPixel(image, x, y, color);
    }
  }
}

/** Where a standing figure's landmarks are drawn, as shares of its height from its top. */
export const FIGURE = {
  earTip: 0,
  crown: 0.05,
  chin: 0.27,
  shoulder: 0.3,
  armpit: 0.34,
  handTip: 0.6,
  crotch: 0.62,
  ankle: 0.95,
  ground: 1,
};

/**
 * A standing figure seen from the front, `h` pixels tall with its ear tips at `top`, centred on `cx`: two ears, a round head, a neck, a
 * torso, arms hanging apart from it, legs with a gap between them, and feet wider than the ankles.
 */
export function figure(image: Raster, cx: number, top: number, h: number, color: Pixel = FUR): void {
  const y = (share: number) => top + share * h;
  const x = (share: number) => cx + share * h;
  ellipse(image, x(-0.075), y(0.06), 0.03 * h, 0.06 * h, color);
  ellipse(image, x(0.075), y(0.06), 0.03 * h, 0.06 * h, color);
  ellipse(image, cx, y(0.16), 0.11 * h, 0.11 * h, color);
  rect(image, x(-0.03), y(0.25), x(0.03), y(0.31), color);
  rect(image, x(-0.1), y(0.3), x(0.1), y(0.62), color);
  rect(image, x(-0.16), y(0.3), x(0.16), y(0.34), color);
  rect(image, x(-0.16), y(0.3), x(-0.12), y(0.6), color);
  rect(image, x(0.12), y(0.3), x(0.16), y(0.6), color);
  rect(image, x(-0.09), y(0.6), x(-0.02), y(0.95), color);
  rect(image, x(0.02), y(0.6), x(0.09), y(0.95), color);
  rect(image, x(-0.11), y(0.95), x(-0.01), y(1), color);
  rect(image, x(0.01), y(0.95), x(0.11), y(1), color);
}

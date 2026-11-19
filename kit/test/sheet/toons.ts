// An invented toon child for the proportion tests: a round head with a face, a shirt, trousers, sleeves ending in hands, and feet.
import type { Pixel, Raster } from "squash-kit/sheet";
import { ellipse, INK, rect, sheet } from "./figures.ts";
import { capsule } from "./people.ts";

export const SKIN: Pixel = [236, 190, 150];
export const HAIR: Pixel = [140, 70, 30];
export const SHIRT: Pixel = [60, 140, 90];
export const TROUSERS: Pixel = [50, 70, 120];
export const SOCK: Pixel = [248, 247, 246];
const WHITE: Pixel = [255, 255, 255];

export interface ToonOptions {
  /** The back: hair over the whole head, no face. */
  back?: boolean;
  /** Socks nearly the colour of the paper. */
  socks?: boolean;
  /** A tuft of hair above the head, this many pixels off its middle. */
  tuft?: number;
  /** Draws the head instead of the standard one: hair, face and features, on the sheet, about the middle column `cx`. */
  head?: (image: Raster, cx: number) => void;
  /** Hand ellipse half sizes. */
  hand?: { rx: number; ry: number };
  /** Foot ellipse half sizes. */
  foot?: { rx: number; ry: number };
}

/** Where the drawing puts things, in sheet pixels. */
export const TOON = {
  cx: 200,
  crown: 30,
  chin: 150,
  eyeY: 100,
  eyeRx: 13,
  eyeApart: 60,
  mouthY: 132,
  ground: 560,
  headRadius: 60,
  handRx: 13,
  handRy: 24,
  footRx: 26,
  footRy: 12,
  hemRow: 330,
};

/** A toon child standing on a blank sheet 400 by 600. */
export function toon(options: ToonOptions = {}): { image: Raster; truth: typeof TOON } {
  const image = sheet(400, 600);
  const { cx } = TOON;
  const hand = options.hand ?? { rx: TOON.handRx, ry: TOON.handRy };
  const foot = options.foot ?? { rx: TOON.footRx, ry: TOON.footRy };
  if (options.tuft !== undefined) ellipse(image, cx + options.tuft, TOON.crown - 14, 5, 18, HAIR);
  if (options.head) options.head(image, cx);
  else ellipse(image, cx, 90, TOON.headRadius, TOON.headRadius, HAIR);
  if (!options.back && !options.head) {
    ellipse(image, cx, 104, 54, 46, SKIN);
    for (const side of [-1, 1]) {
      ellipse(image, cx + (side * TOON.eyeApart) / 2, TOON.eyeY, TOON.eyeRx, 17, INK);
      ellipse(image, cx + (side * TOON.eyeApart) / 2, TOON.eyeY, TOON.eyeRx - 4, 12, WHITE);
      ellipse(image, cx + (side * TOON.eyeApart) / 2, TOON.eyeY + 2, 6, 8, INK);
    }
    rect(image, cx - 12, TOON.mouthY, cx + 12, TOON.mouthY + 4, INK);
  }
  rect(image, cx - 14, 148, cx + 14, 190, SKIN);
  rect(image, cx - 60, 180, cx + 60, TOON.hemRow, SHIRT);
  for (const side of [-1, 1]) {
    const from = { x: cx + side * 60, y: 195 };
    const to = { x: cx + side * 120, y: 330 };
    capsule(image, from.x, from.y, to.x, to.y, 14, SHIRT);
    ellipse(image, to.x + side * 4, to.y + hand.ry, hand.rx, hand.ry, SKIN);
  }
  rect(image, cx - 52, TOON.hemRow, cx - 4, TOON.ground - 14, TROUSERS);
  rect(image, cx + 4, TOON.hemRow, cx + 52, TOON.ground - 14, TROUSERS);
  for (const side of [-1, 1]) {
    rect(image, cx + side * 28 - 20, TOON.ground - 40, cx + side * 28 + 20, TOON.ground - 12, options.socks ? SOCK : TROUSERS);
    ellipse(image, cx + side * 30, TOON.ground - foot.ry, foot.rx, foot.ry, options.socks ? SOCK : SKIN);
  }
  return { image, truth: TOON };
}

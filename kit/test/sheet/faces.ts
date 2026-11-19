// Invented heads for the face reader's tests, drawn from ellipses and rectangles, to stand on the toon child's body.
import type { Pixel, Raster } from "squash-kit/sheet";
import { ellipse, INK, rect } from "./figures.ts";
import { TOON } from "./toons.ts";

/** Where the drawings put the features, in sheet pixels: the crown is at TOON.crown and the chin at TOON.chin. */
export const FACE = {
  eyeY: 98,
  noseY: 119,
  mouthY: 134,
};

const SKIN: Pixel = [240, 205, 180];
/** A white so near the skin that only its lack of colour tells it apart. */
const PALE_WHITE: Pixel = [248, 240, 232];
const BLUE_HAIR: Pixel = [40, 60, 150];
const IRIS: Pixel = [50, 140, 80];
const LASH: Pixel = [60, 40, 50];

/** One eye drawn as a lash line round a white with an iris and a pupil, centred on (x, y). */
function eye(image: Raster, x: number, y: number, rx: number, ry: number, white: Pixel, iris: Pixel, irisRx = rx * 0.55, line = LASH): void {
  ellipse(image, x, y, rx, ry, line);
  ellipse(image, x, y, rx - 2, ry - 2, white);
  ellipse(image, x, y + 1, irisRx, ry - 3, iris);
  ellipse(image, x, y + 1, irisRx * 0.5, (ry - 3) * 0.5, INK);
}

/** A toon human's head: skin and a hair cap, eyes whose whites are nearly the skin's colour, a faint shaded nose and a mouth line. */
export function paleEyedHead(image: Raster, cx: number): void {
  ellipse(image, cx, 90, TOON.headRadius, TOON.headRadius, BLUE_HAIR);
  ellipse(image, cx, 104, 54, 46, SKIN);
  for (const side of [-1, 1]) eye(image, cx + side * 20, FACE.eyeY, 12, 10, PALE_WHITE, IRIS);
  ellipse(image, cx, FACE.noseY, 3, 2, [214, 160, 132]);
  rect(image, cx - 9, FACE.mouthY, cx + 9, FACE.mouthY + 2, [150, 60, 70]);
}

/** Big round eyes, each taller than wide, with a fringe of hair lying over both their tops. */
export function fringedHead(image: Raster, cx: number): void {
  ellipse(image, cx, 90, TOON.headRadius, TOON.headRadius, BLUE_HAIR);
  ellipse(image, cx, 104, 54, 46, SKIN);
  for (const side of [-1, 1]) eye(image, cx + side * 22, FACE.eyeY + 2, 15, 19, [255, 255, 255], [150, 70, 170], 11);
  ellipse(image, cx, 62, 56, 38, BLUE_HAIR);
  rect(image, cx - 56, 60, cx + 56, FACE.eyeY - 9, BLUE_HAIR);
  ellipse(image, cx, FACE.noseY + 2, 2.5, 2, [214, 160, 132]);
  rect(image, cx - 7, FACE.mouthY + 2, cx + 7, FACE.mouthY + 4, [150, 60, 70]);
}

const FUR: Pixel = [190, 140, 90];
const MUZZLE: Pixel = [244, 228, 204];

/** A fur head with a lighter muzzle over its lower middle holding a dark nose pad at its top and a mouth line beneath it. */
export function muzzledHead(image: Raster, cx: number): void {
  ellipse(image, cx, 92, TOON.headRadius - 4, 58, FUR);
  for (const side of [-1, 1]) ellipse(image, cx + side * 26, FACE.eyeY - 4, 7, 9, INK);
  ellipse(image, cx, 126, 24, 16, MUZZLE);
  ellipse(image, cx, FACE.noseY - 1, 5, 3, [70, 40, 40]);
  rect(image, cx - 8, FACE.mouthY, cx + 8, FACE.mouthY + 2, [110, 70, 60]);
}

/** The head turned: the face lies toward image right, the near eye wide and the far eye narrow and close to the head's edge. */
export function turnedHead(image: Raster, cx: number): void {
  ellipse(image, cx, 90, TOON.headRadius, TOON.headRadius, BLUE_HAIR);
  ellipse(image, cx + 6, 104, 54, 46, SKIN);
  eye(image, cx - 10, FACE.eyeY, 8, 10, PALE_WHITE, IRIS);
  eye(image, cx + 30, FACE.eyeY, 14, 11, PALE_WHITE, IRIS);
  ellipse(image, cx + 14, FACE.noseY, 3, 2, [214, 160, 132]);
  rect(image, cx + 4, FACE.mouthY, cx + 24, FACE.mouthY + 2, [150, 60, 70]);
}

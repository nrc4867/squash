import { channelDistance, hexToPixel } from "../sheet/core/color.ts";
import { blankMask, components, maskBox, type Mask } from "../sheet/core/mask.ts";
import type { Box } from "../sheet/core/box.ts";
import { cropRaster, pixelAt, type Pixel, type Raster } from "../sheet/raster/image.ts";
import type { ReadingsView } from "./readings.ts";
import type { Px, ViewName } from "./types.ts";

/** How far a channel may stray from the background and still be background. */
const GROUND_TOLERANCE = 40;

/** A piece smaller than this share of the largest is a label or a speck, not the subject. */
const LEAST_PIECE_SHARE = 0.05;

/** An enclosed gap smaller than this share of the subject is filled: a highlight, an eye, a gap in the ink. */
const FILLED_HOLE_SHARE = 0.02;

/** Pixels a march steps by. */
const MARCH_STEP = 0.5;

/** A view's image turned so forward is right (side) or up (top). */
function turned(image: Raster, view: ViewName, forward: ReadingsView["forward"]): Raster {
  const flip = view === "side" && forward === "left";
  const rotate = view === "top" ? (forward === "down" ? 2 : forward === "left" ? 1 : forward === "right" ? 3 : 0) : 0;
  if (!flip && rotate === 0) return image;
  const quarter = rotate % 2 === 1;
  const width = quarter ? image.height : image.width;
  const height = quarter ? image.width : image.height;
  const out: Raster = { width, height, data: new Uint8Array(width * height * 4) };
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let sx = x;
      let sy = y;
      if (flip) sx = image.width - 1 - x;
      if (rotate === 2) [sx, sy] = [image.width - 1 - x, image.height - 1 - y];
      if (rotate === 1) [sx, sy] = [y, image.height - 1 - x];
      if (rotate === 3) [sx, sy] = [image.width - 1 - y, x];
      const i = (sy * image.width + sx) * 4;
      const o = (y * width + x) * 4;
      for (let c = 0; c < 4; c++) out.data[o + c] = image.data[i + c];
    }
  }
  return out;
}

/** The subject's pixels: not the background, the pieces large enough to be it, small enclosed gaps filled. */
function subjectMask(image: Raster, ground: Pixel): Mask {
  const mask = blankMask(image.width, image.height);
  for (let y = 0; y < image.height; y++) {
    for (let x = 0; x < image.width; x++) mask.data[y * image.width + x] = channelDistance(pixelAt(image, x, y), ground) > GROUND_TOLERANCE ? 1 : 0;
  }
  const { pieces } = components(mask);
  const kept = blankMask(image.width, image.height);
  const largest = pieces[0]?.area ?? 0;
  let area = 0;
  for (const piece of pieces) {
    if (piece.area < LEAST_PIECE_SHARE * largest) continue;
    for (const at of piece.pixels) kept.data[at] = 1;
    area += piece.area;
  }
  const gaps = blankMask(image.width, image.height);
  for (let i = 0; i < kept.data.length; i++) gaps.data[i] = kept.data[i] ? 0 : 1;
  for (const gap of components(gaps).pieces) {
    const { box } = gap;
    const enclosed = box.x > 0 && box.y > 0 && box.x + box.w < image.width && box.y + box.h < image.height;
    if (enclosed && gap.area < FILLED_HOLE_SHARE * area) for (const at of gap.pixels) kept.data[at] = 1;
  }
  return kept;
}

/**
 * One view's silhouette, turned so the side view faces right and the top view points up, with the probes a proposer measures by:
 * runs across a row or down a column, and marches from a place to the edge.
 */
export class ViewSilhouette {
  readonly box: Box;
  readonly view: ViewName;
  readonly image: Raster;
  readonly mask: Mask;

  constructor(view: ViewName, image: Raster, mask: Mask) {
    this.view = view;
    this.image = image;
    this.mask = mask;
    const box = maskBox(mask);
    if (!box) throw new Error(`the ${view} view shows nothing on its background`);
    this.box = box;
  }

  inside(x: number, y: number): boolean {
    const ix = Math.round(x);
    const iy = Math.round(y);
    return ix >= 0 && iy >= 0 && ix < this.mask.width && iy < this.mask.height && this.mask.data[iy * this.mask.width + ix] === 1;
  }

  /** The row's runs of the subject, [from, to] in x, left to right. */
  rowRuns(y: number): Array<[number, number]> {
    return runs(this.mask.width, (x) => this.inside(x, y));
  }

  /** The column's runs of the subject, [from, to] in y, top to bottom. */
  columnRuns(x: number): Array<[number, number]> {
    return runs(this.mask.height, (y) => this.inside(x, y));
  }

  /** How far from `from` along the unit direction `dir` the subject's edge lies, up to `most` pixels; 0 from outside. */
  march(from: Px, dir: Px, most: number): number {
    let t = 0;
    while (t < most && this.inside(from[0] + dir[0] * (t + MARCH_STEP), from[1] + dir[1] * (t + MARCH_STEP))) t += MARCH_STEP;
    return this.inside(from[0], from[1]) ? t : 0;
  }

  /** The lowest row the subject reaches: the ground in a front or side view. */
  get ground(): number {
    return this.box.y + this.box.h - 1;
  }

  /** The column the subject is mirrored about, from each row's outermost run ends. */
  get middle(): number {
    let sum = 0;
    let n = 0;
    for (let y = this.box.y; y < this.box.y + this.box.h; y++) {
      const r = this.rowRuns(y);
      if (r.length === 0) continue;
      sum += (r[0][0] + r[r.length - 1][1]) / 2;
      n++;
    }
    return n ? sum / n : this.box.x + this.box.w / 2;
  }
}

function runs(length: number, inside: (i: number) => boolean): Array<[number, number]> {
  const out: Array<[number, number]> = [];
  let start = -1;
  for (let i = 0; i <= length; i++) {
    const on = i < length && inside(i);
    if (on && start < 0) start = i;
    if (!on && start >= 0) {
      out.push([start, i - 1]);
      start = -1;
    }
  }
  return out;
}

/** A view read from an image: cropped to its box, turned to face forward, keyed off its background. */
export function readSilhouette(view: ViewName, image: Raster, given: ReadingsView): ViewSilhouette {
  const [x, y, w, h] = given.box ?? [0, 0, image.width, image.height];
  const cropped = turned(cropRaster(image, x, y, w, h), view, given.forward);
  const ground = given.ground ? hexToPixel(given.ground) : pixelAt(cropped, 0, 0);
  return new ViewSilhouette(view, cropped, subjectMask(cropped, ground));
}

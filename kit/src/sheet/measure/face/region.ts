import type { Box } from "../../core/box.ts";
import { channelDistance } from "../../core/color.ts";
import { blankMask, components, cropMask, dilate, erode, type Mask } from "../../core/mask.ts";
import { convexHull, polygonSpan, type HullPoint } from "../../core/hull.ts";
import { subtract } from "../../core/maskOps.ts";
import { pixelAt, type Pixel, type Raster } from "../../raster/image.ts";
import type { Anatomy } from "../anatomy.ts";
import { headRun } from "../head.ts";
import { dominantColor, hueGap, hueSaturation, maskedPixels } from "./colors.ts";

/** The window on the head reaches this share of the head's width past its widest row on each side. */
const WINDOW_MARGIN = 0.04;
/** The skin is the commonest colour between these shares of the head's length below the crown, either side of the middle by this share of its width. */
const SKIN_ROWS: [number, number] = [0.5, 0.95];
const SKIN_HALF_WIDTH = 0.3;
/** The hair is the commonest colour between these shares of the head's length, either side of the middle by this share of its width. */
const HAIR_ROWS: [number, number] = [0.02, 0.2];
const HAIR_HALF_WIDTH = 0.35;
/** A pixel this far from the skin, in the largest channel, is not skin; this far from the hair colour, it is not hair. */
export const SKIN_TOLERANCE = 34;
const HAIR_TOLERANCE = 60;
/** Coloured hair is told by its hue, within this many degrees, once it is at least this saturated; paler or greyer hair by its colour alone. */
const HAIR_HUE = 30;
const HAIR_SATURATED = 0.25;
/** A skin piece is part of the face when it is at least this share of the head's area. */
const SKIN_PIECE = 0.004;
/** Hair this near the skin's colour is not told from it: the head has no hair to leave out (a fur-coloured crown). */
const HAIR_APART = 45;
/** Hair is the colour match once strokes thinner than twice this share of the head's length are opened away, and gaps narrower than this share closed (highlights between strands). */
const HAIR_OPEN_SHARE = 0.012;
const HAIR_CLOSE_SHARE = 0.03;
/** The head's outline ink, this share of the head's length wide, is left out of the face. */
const OUTLINE_SHARE = 0.03;
/** With hair on the head, skin starting above this share of its length is not the face (a raised hand beside the head). */
const SKIN_TOP = 0.2;
/** Skin specks thinner than twice this share of the head's length are dropped. */
const SKIN_OPEN_SHARE = 0.008;

/** The head as the face reader sees it: the window on it, its colours, and the masks inside the window. */
export interface FaceBase {
  /** The window on the sheet; every mask is in its pixels. */
  win: Box;
  length: number;
  headWidth: number;
  /** The head's middle column in window pixels. */
  centre: number;
  skinColor: Pixel;
  hairColor?: Pixel;
  /** The figure's pixels inside the window. */
  head: Mask;
  skin: Mask;
  hair: Mask;
  /** The skin area's rows from its first to its last, each its span on the convex hull of the skin pieces, so the eyes beside and under a fringe lie inside. */
  face: Mask;
  rows: Array<{ y: number; l: number; r: number }>;
  /** The window's pixel at local (x, y). */
  pixel(x: number, y: number): Pixel;
}

export type FaceBaseResult = { base: FaceBase } | { reason: string };

/** The window on the head, its colours, and the skin area below the hair. */
export function readFaceBase(image: Raster, anatomy: Anatomy): FaceBaseResult {
  const { figure, marks } = anatomy;
  const crown = marks.crown.y;
  const length = marks.chin.y - crown;
  const widest = headRun(figure, anatomy.head.widestRow);
  if (!widest || length < 24) return { reason: "the head is too small or not read" };
  const headWidth = widest.r - widest.l + 1;
  const margin = Math.round(WINDOW_MARGIN * headWidth);
  const x0 = Math.max(0, widest.l - margin);
  const x1 = Math.min(figure.mask.width, widest.r + 1 + margin);
  const win: Box = { x: x0, y: crown, w: x1 - x0, h: marks.chin.y - crown + 1 };
  const head = cropMask(figure.mask, win);
  const centre = Math.round((widest.l + widest.r) / 2) - x0;
  const pixel = (x: number, y: number) => pixelAt(image, win.x + x, win.y + y);
  const sample = (rows: [number, number], half: number) =>
    dominantColor(maskedPixels(image, head, Math.round(centre - half * headWidth), Math.round(rows[0] * length), Math.round(centre + half * headWidth), Math.round(rows[1] * length), win.x, win.y));
  const skinColor = sample(SKIN_ROWS, SKIN_HALF_WIDTH);
  if (!skinColor) return { reason: "no skin colour is read inside the head" };
  let hairColor = sample(HAIR_ROWS, HAIR_HALF_WIDTH);
  if (hairColor && channelDistance(hairColor, skinColor) < HAIR_APART) hairColor = undefined;
  const hair = blankMask(win.w, win.h);
  if (hairColor) {
    const match = blankMask(win.w, win.h);
    const hairHue = hueSaturation(hairColor);
    const byHue = hairHue.saturation >= HAIR_SATURATED;
    for (let y = 0; y < win.h; y++) {
      for (let x = 0; x < win.w; x++) {
        if (!head.data[y * win.w + x]) continue;
        const p = pixel(x, y);
        if (channelDistance(p, skinColor) <= SKIN_TOLERANCE) continue;
        const near = channelDistance(p, hairColor) <= HAIR_TOLERANCE;
        const h = hueSaturation(p);
        if (near || (byHue && h.saturation >= HAIR_SATURATED && hueGap(h.hue, hairHue.hue) <= HAIR_HUE)) match.data[y * win.w + x] = 1;
      }
    }
    const open = Math.max(1, Math.round(HAIR_OPEN_SHARE * length));
    const close = Math.max(2, Math.round(HAIR_CLOSE_SHARE * length));
    const thick = dilate(erode(match, open), open);
    const joined = erode(dilate(thick, close), close);
    for (let y = 0; y < win.h; y++) {
      for (let x = 0; x < win.w; x++) {
        const at = y * win.w + x;
        if (head.data[at] && (thick.data[at] || (joined.data[at] && channelDistance(pixel(x, y), skinColor) > SKIN_TOLERANCE))) hair.data[at] = 1;
      }
    }
  }
  const skinAll = blankMask(win.w, win.h);
  for (let y = 0; y < win.h; y++) for (let x = 0; x < win.w; x++) if (head.data[y * win.w + x] && channelDistance(pixel(x, y), skinColor) <= SKIN_TOLERANCE) skinAll.data[y * win.w + x] = 1;
  const skinOpen = Math.max(1, Math.round(SKIN_OPEN_SHARE * length));
  const skin = subtract(dilate(erode(skinAll, skinOpen), skinOpen), hair);
  const headArea = head.data.reduce((s, v) => s + v, 0);
  const pieces = components(skin)
    .pieces.filter((p) => p.area >= SKIN_PIECE * headArea)
    .filter((p) => !hairColor || p.box.y >= SKIN_TOP * length);
  if (pieces.length === 0) return { reason: "no skin area is read inside the head" };
  const rows: FaceBase["rows"] = [];
  const face = blankMask(win.w, win.h);
  const left = new Int32Array(win.h).fill(win.w);
  const right = new Int32Array(win.h).fill(-1);
  for (const piece of pieces) {
    for (const at of piece.pixels) {
      const x = at % win.w;
      const y = (at - x) / win.w;
      if (x < left[y]) left[y] = x;
      if (x > right[y]) right[y] = x;
    }
  }
  const points: HullPoint[] = [];
  for (let y = 0; y < win.h; y++) if (right[y] >= 0) points.push({ x: left[y], y }, { x: right[y] + 1, y });
  const hull = convexHull(points);
  const inner = erode(head, Math.max(1, Math.round(OUTLINE_SHARE * length)));
  for (let y = 0; y < win.h; y++) {
    const span = polygonSpan(hull, y);
    if (!span) continue;
    rows.push({ y: win.y + y, l: win.x + span.l, r: win.x + span.r });
    for (let x = Math.max(0, span.l); x <= Math.min(win.w - 1, span.r); x++) face.data[y * win.w + x] = inner.data[y * win.w + x];
  }
  return { base: { win, length, headWidth, centre, skinColor, hairColor, head, skin, hair, face, rows, pixel } };
}

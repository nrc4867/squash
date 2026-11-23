import { components, dilate, type Mask } from "../../../sheet/core/mask.ts";
import type { HidingLayer } from "../../../reader/types.ts";
import type { BuiltBody } from "../body.ts";
import { rasterBody } from "../raster.ts";
import type { PlacedViews } from "./views.ts";

/** A part standing off the head, read off the front view, in head units and degrees. */
export interface OffPart {
  kind: string;
  /** From where it leaves the head to its far tip. */
  length: number;
  /** Its widest half width, its half width where it leaves the head, and how far along it is widest (a share of its length). */
  width: number;
  base: number;
  widest: number;
  /** How far it leans out from straight up, toward the body's side. */
  outward: number;
}

/** Pixels the head is grown by before what stands off it is read, so the head's own edge is not read as a part. */
const HEAD_MARGIN = 3;

/** A piece smaller than this share of its box is a speck, not a part. */
const LEAST_SHARE = 0.03;

/** Bands across a part its widths are read in, from its base to its tip. */
const BANDS = 10;

/**
 * The parts a front view's stand-off layers hold (ears in an ears box): what the silhouette shows inside the box past the head as the
 * fit placed it, on the body's left half (the image's right), its largest piece, measured from where it leaves the head to its
 * farthest pixel, and its half width in bands across that line.
 */
export function offParts(placed: PlacedViews, posed: BuiltBody, kinds: readonly string[], headIds: ReadonlySet<string>): OffPart[] {
  const silhouette = placed.views.get("front");
  const place = placed.places.get("front");
  if (!silhouette || !place) return [];
  const { width, height } = silhouette.mask;
  const head = dilate(rasterBody(posed, "front", place, width, height, (owner) => headIds.has(owner)), HEAD_MARGIN);
  const out: OffPart[] = [];
  for (const layer of placed.layers) {
    if (layer.view !== "front" || !kinds.includes(layer.kind)) continue;
    const middle = place.origin[0];
    const inBox = boxed(silhouette.mask, layer, head, middle);
    const piece = components(inBox).pieces.find((p) => p.area >= LEAST_SHARE * layer.box[2] * layer.box[3]);
    if (!piece) continue;
    const near = dilate(head, 2);
    let bx = 0;
    let by = 0;
    let n = 0;
    for (const at of piece.pixels) {
      if (!near.data[at]) continue;
      bx += at % width;
      by += Math.floor(at / width);
      n++;
    }
    if (n === 0) continue;
    bx /= n;
    by /= n;
    let far = 0;
    let tip: [number, number] = [bx, by];
    for (const at of piece.pixels) {
      const x = at % width;
      const y = Math.floor(at / width);
      const d = Math.hypot(x - bx, y - by);
      if (d > far) {
        far = d;
        tip = [x, y];
      }
    }
    if (far < 1) continue;
    const ax = (tip[0] - bx) / far;
    const ay = (tip[1] - by) / far;
    const low = new Array(BANDS).fill(Infinity);
    const high = new Array(BANDS).fill(-Infinity);
    for (const at of piece.pixels) {
      const x = at % width - bx;
      const y = Math.floor(at / width) - by;
      const band = Math.min(BANDS - 1, Math.max(0, Math.floor(((x * ax + y * ay) / far) * BANDS)));
      const across = x * ay - y * ax;
      low[band] = Math.min(low[band], across);
      high[band] = Math.max(high[band], across);
    }
    const halves = low.map((l, i) => (high[i] >= l ? (high[i] - l) / 2 : 0));
    const widestBand = halves.indexOf(Math.max(...halves));
    out.push({
      kind: layer.kind,
      length: far / place.scale,
      width: halves[widestBand] / place.scale,
      base: (halves[0] || halves[widestBand]) / place.scale,
      widest: (widestBand + 0.5) / BANDS,
      outward: (Math.atan2(tip[0] - bx, by - tip[1]) * 180) / Math.PI,
    });
  }
  return out;
}

/** The silhouette's pixels inside a layer's box, outside `head` and right of `middle` (the body's left half). */
function boxed(mask: Mask, layer: HidingLayer, head: Mask, middle: number): Mask {
  const out: Mask = { width: mask.width, height: mask.height, data: new Uint8Array(mask.data.length) };
  const [x0, y0, w, h] = layer.box;
  for (let y = Math.max(0, Math.floor(y0)); y < Math.min(mask.height, Math.ceil(y0 + h)); y++) {
    for (let x = Math.max(Math.ceil(middle), Math.floor(x0)); x < Math.min(mask.width, Math.ceil(x0 + w)); x++) {
      const at = y * mask.width + x;
      out.data[at] = mask.data[at] && !head.data[at] ? 1 : 0;
    }
  }
  return out;
}

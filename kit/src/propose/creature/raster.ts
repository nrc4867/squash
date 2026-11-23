import { convexHull } from "../../sheet/core/hull.ts";
import { blankMask, type Mask } from "../../sheet/core/mask.ts";
import type { V3 } from "../../geom/vec3.ts";
import type { Px, ViewName, ViewPlace } from "../../reader/types.ts";
import { bodyRings, type BuiltBody } from "./body.ts";

export type { ViewPlace };

/** A body place (head units: x forward, y up, z left) in a view's pixels: the front shows the left on the right, the top points forward up. */
export function toPixel(view: ViewName, place: ViewPlace, p: V3): Px {
  const [ox, oy] = place.origin;
  const s = place.scale;
  if (view === "front") return [ox + s * p[2], oy - s * p[1]];
  if (view === "side") return [ox + s * p[0], oy - s * p[1]];
  return [ox - s * p[2], oy - s * p[0]];
}

/** The way a body direction points in a view's pixels, unscaled. */
export function toPixelDirection(view: ViewName, d: V3): Px {
  if (view === "front") return [d[2], -d[1]];
  if (view === "side") return [d[0], -d[1]];
  return [-d[2], -d[0]];
}

/** Fills a convex polygon's pixels in `mask`. */
function fillConvex(mask: Mask, polygon: readonly Px[]): void {
  if (polygon.length < 3) return;
  let y0 = Infinity;
  let y1 = -Infinity;
  for (const [, y] of polygon) {
    y0 = Math.min(y0, y);
    y1 = Math.max(y1, y);
  }
  const from = Math.max(0, Math.ceil(y0 - 0.5));
  const to = Math.min(mask.height - 1, Math.floor(y1 - 0.5));
  for (let row = from; row <= to; row++) {
    const y = row + 0.5;
    let left = Infinity;
    let right = -Infinity;
    for (let i = 0; i < polygon.length; i++) {
      const [ax, ay] = polygon[i];
      const [bx, by] = polygon[(i + 1) % polygon.length];
      if ((ay <= y && by > y) || (by <= y && ay > y)) {
        const x = ax + ((y - ay) / (by - ay)) * (bx - ax);
        left = Math.min(left, x);
        right = Math.max(right, x);
      }
    }
    if (left > right) continue;
    const c0 = Math.max(0, Math.ceil(left - 0.5));
    const c1 = Math.min(mask.width - 1, Math.floor(right - 0.5));
    const base = row * mask.width;
    for (let c = c0; c <= c1; c++) mask.data[base + c] = 1;
  }
}

/**
 * A body's silhouette in a view, `width` by `height` pixels: each pair of neighbouring rings of a part or end filled as the hull of the
 * two, so every part reads end on as well as along. `keep` picks the parts and ends drawn by id, and whether a pair's right side is drawn.
 */
export function rasterBody(body: BuiltBody, view: ViewName, place: ViewPlace, width: number, height: number, keep: (owner: string, mirrored: boolean) => boolean = () => true): Mask {
  const mask = blankMask(width, height);
  for (const rings of bodyRings(body)) {
    if (rings.length === 0 || !keep(rings[0].owner, rings[0].mirrored === true)) continue;
    const projected = rings.map((r) => r.points.map((p) => toPixel(view, place, p)));
    for (let i = 0; i + 1 < projected.length; i++) {
      const hull = convexHull([...projected[i], ...projected[i + 1]].map(([x, y]) => ({ x, y })));
      fillConvex(mask, hull.map((p) => [p.x, p.y] as Px));
    }
  }
  return mask;
}

import { dot, type V3 } from "../../../geom/vec3.ts";
import type { ViewSilhouette } from "../../../reader/silhouette.ts";
import type { HidingLayer, ViewName, ViewPlace } from "../../../reader/types.ts";
import { toPixel, toPixelDirection } from "../raster.ts";

/** Each view's depth: the body direction it looks along. */
export const VIEW_DEPTH: Record<ViewName, V3> = { front: [1, 0, 0], side: [0, 0, 1], top: [0, 1, 0] };

/** A direction lies in a view's plane when it leans off the view's depth by at most this much. */
const IN_PLANE = 0.35;

/** The sheet's views as the fit placed them over the body as the sheet poses it, with the layers that hide its shape. */
export interface PlacedViews {
  views: ReadonlyMap<ViewName, ViewSilhouette>;
  places: ReadonlyMap<ViewName, ViewPlace>;
  layers: readonly HidingLayer[];
}

/** The given view that shows both directions across it, if any. */
export function viewShowing(placed: PlacedViews, a: V3, b: V3): ViewName | undefined {
  return [...placed.places.keys()].find((view) => Math.abs(dot(a, VIEW_DEPTH[view])) < IN_PLANE && Math.abs(dot(b, VIEW_DEPTH[view])) < IN_PLANE);
}

/** Whether a body place lies under a hiding layer's box in a view. */
export function hiddenAt(placed: PlacedViews, view: ViewName, p: V3): boolean {
  const [x, y] = toPixel(view, placed.places.get(view)!, p);
  return placed.layers.some((l) => l.view === view && x >= l.box[0] && y >= l.box[1] && x <= l.box[0] + l.box[2] && y <= l.box[1] + l.box[3]);
}

/**
 * How far from body place `p` toward body direction `d` the view's silhouette reaches, in head units, up to `most`: undefined where the
 * place lies outside the silhouette.
 */
export function reachIn(placed: PlacedViews, view: ViewName, p: V3, d: V3, most: number): number | undefined {
  const silhouette = placed.views.get(view)!;
  const place = placed.places.get(view)!;
  const from = toPixel(view, place, p);
  if (!silhouette.inside(from[0], from[1])) return undefined;
  const [dx, dy] = toPixelDirection(view, d);
  const n = Math.hypot(dx, dy) || 1;
  return silhouette.march(from, [dx / n, dy / n], most * place.scale) / place.scale;
}

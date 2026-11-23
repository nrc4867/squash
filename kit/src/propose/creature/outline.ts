import type { FigureOutline, FigureOutlineStroke } from "../../figure/index.ts";
import { roundTo } from "../../geom/point.ts";
import { cornersOf, signedArea, simplified, traceLoops, type Vertex } from "../../reader/contour.ts";
import { dilate, erode } from "../../sheet/core/mask.ts";
import type { ViewName } from "../../reader/types.ts";
import type { BuiltBody } from "./body.ts";
import { extentOf } from "./fit.ts";
import { rasterBody, toPixel, type ViewPlace } from "./raster.ts";
import { readParts } from "./reads.ts";

/** Pixels across the body's tallest or longest reach when its outline is drawn. */
const OUTLINE_PIXELS = 900;

/** Pixels round the drawn body. */
const MARGIN = 12;

/**
 * How far past an end's edge the outline is cut, as a share of the drawn reach: the stroke stops a little short of where the head, a
 * hand or a foot begins, so the cap a part's open tip shows under it is never read as its width.
 */
const END_CUT = 0.012;

/** The drawn parts are closed by this share of the drawn reach before tracing, so joins meet in a fillet, not a notch. */
const JOIN_FILLET = 0.01;

/** Pixels past a read part's edge its outline is still kept: the body is drawn whole, and only what the view reads becomes strokes. */
const READ_REACH = 2;

/** A stroke stays within this share of the body's height of the traced outline. */
const STROKE_TOLERANCE = 0.0015;

/** A turn sharper than this, in degrees, is a corner of the stroke. */
const CORNER_DEGREES = 55;

/** A stroke shorter than this share of the body's height is left out. */
const SHORTEST_SHARE = 0.03;

/** An end of a stroke this near the middle, in pixels, lies on it. */
const ON_MIDDLE = 2.5;

/**
 * The figure outline of a body at rest: for each view, the silhouette of its parts, traced and kept where it bounds a part the figure
 * fit reads there (a level trunk seen end on is drawn, not read), cut where an end
 * (a head, a hand, a foot) stands over it and, in the front and top views, at the body's middle, each run of it a stroke of few places
 * with the figure on its right, in body units (`unit` of them a head unit).
 */
export function figureOutline(body: BuiltBody, unit: number): FigureOutline {
  const ext = extentOf(body);
  const reach = Math.max(ext.x[1] - ext.x[0], ext.y[1] - ext.y[0], ext.z[1] - ext.z[0]);
  const scale = OUTLINE_PIXELS / reach;
  const out: FigureOutline = { format: "squash-figure-outline/1", front: [], side: [], top: [] };
  for (const view of ["front", "side", "top"] as ViewName[]) {
    const parts = readParts(body, view);
    if (parts.size === 0) continue;
    const [x0, x1, y0, y1] =
      view === "front" ? [ext.z[0], ext.z[1], ext.y[0], ext.y[1]] : view === "side" ? [ext.x[0], ext.x[1], ext.y[0], ext.y[1]] : [ext.z[0], ext.z[1], ext.x[0], ext.x[1]];
    const width = Math.ceil((x1 - x0) * scale) + 2 * MARGIN;
    const height = Math.ceil((y1 - y0) * scale) + 2 * MARGIN;
    const origin: [number, number] =
      view === "top" ? [MARGIN + x1 * scale, MARGIN + y1 * scale] : [MARGIN - x0 * scale, MARGIN + y1 * scale];
    const place: ViewPlace = { origin, scale };
    const fillet = Math.round(JOIN_FILLET * OUTLINE_PIXELS);
    const figureParts = (owner: string) => body.parts.has(owner);
    const mask = erode(dilate(rasterBody(body, view, place, width, height, figureParts), fillet), fillet);
    const readMask = dilate(rasterBody(body, view, place, width, height, (owner) => parts.has(owner)), fillet + READ_REACH);
    const ends = dilate(
      rasterBody(body, view, place, width, height, (owner) => body.ends.some((e) => e.end.id === owner && !e.end.hides)),
      Math.round(END_CUT * OUTLINE_PIXELS),
    );
    const halved = view !== "side";
    const toView = ([px, py]: Vertex): [number, number] =>
      view === "top" ? [(origin[0] - px) / scale, (origin[1] - py) / scale] : [(px - origin[0]) / scale, (origin[1] - py) / scale];
    const tips = openTips(body, view, place, Math.round(END_CUT * OUTLINE_PIXELS));
    const kept = ([px, py]: Vertex) => {
      const at = Math.min(height - 1, Math.max(0, Math.floor(py))) * width + Math.min(width - 1, Math.max(0, Math.floor(px)));
      if (ends.data[at] || !readMask.data[at] || tips.some((under) => under([px, py]))) return false;
      return !halved || toView([px, py])[0] >= -ON_MIDDLE / scale;
    };
    const strokes: FigureOutlineStroke[] = [];
    for (const loop of traceLoops(mask)) {
      const points = loop.map(toView);
      // Traced with the body on its right as the image shows it, y down: with y up the body lies on its left, so walk it back.
      const ordered = signedArea(points) > 0 ? [...points].reverse() : points;
      const flags = (signedArea(points) > 0 ? [...loop].reverse() : loop).map(kept);
      for (const run of runsOf(ordered, flags)) {
        const lengthOf = run.reduce((sum, p, i) => (i === 0 ? 0 : sum + Math.hypot(p[0] - run[i - 1][0], p[1] - run[i - 1][1])), 0);
        if (lengthOf < SHORTEST_SHARE * (y1 - y0)) continue;
        const clipped = halved ? run.map(([x, y]): Vertex => [Math.max(0, x), y]) : run;
        const few = simplified(clipped, STROKE_TOLERANCE * (y1 - y0));
        const first = few[0];
        const last = few[few.length - 1];
        const onMiddle = halved && (Math.abs(first[0]) * scale < ON_MIDDLE || Math.abs(last[0]) * scale < ON_MIDDLE);
        strokes.push({
          id: `${view}-${strokes.length + 1}`,
          points: few.map(([x, y]) => [roundTo(x * unit), roundTo(y * unit)]),
          ...(cornersOf(few, CORNER_DEGREES).length ? { corners: cornersOf(few, CORNER_DEGREES) } : {}),
          ...(onMiddle ? { mirrored: true } : {}),
        });
      }
    }
    if (view === "front") out.front = strokes;
    else if (view === "side") out.side = strokes;
    else out.top = strokes;
  }
  return out;
}

/** How far across a part's tip, as a share of its widest radius there, the cut past its end reaches. */
const TIP_REACH = 1.6;

/**
 * For each part an end sits on at its tip (a neck under a head, an arm under a hand), a test of whether a pixel lies past the part's
 * end, less `back` pixels, and across it within its width: the open end's edge, which no stroke draws.
 */
function openTips(body: BuiltBody, view: ViewName, place: ViewPlace, back: number): Array<(p: Vertex) => boolean> {
  const out: Array<(p: Vertex) => boolean> = [];
  for (const end of body.ends) {
    if (end.end.hides || end.end.at !== undefined) continue;
    const part = body.parts.get(end.end.on);
    if (!part || part.part.closed) continue;
    const tip = toPixel(view, place, part.joints[part.joints.length - 1]);
    const before = toPixel(view, place, part.joints[part.joints.length - 2]);
    const dx = tip[0] - before[0];
    const dy = tip[1] - before[1];
    const length = Math.hypot(dx, dy);
    if (length < 1) continue;
    const last = part.sections[part.sections.length - 1];
    const reach = TIP_REACH * Math.max(...last.round.map((r) => last.radius(r.name))) * place.scale;
    out.push(([x, y]) => {
      const along = ((x - tip[0]) * dx + (y - tip[1]) * dy) / length;
      const across = Math.abs((x - tip[0]) * dy - (y - tip[1]) * dx) / length;
      return along > -back && across < reach;
    });
  }
  return out;
}

/** A closed loop's runs of places that `flags` keep, each in order, a run across the loop's start joined. */
function runsOf(loop: readonly Vertex[], flags: readonly boolean[]): Vertex[][] {
  const n = loop.length;
  const start = flags.indexOf(false);
  if (start < 0) return [[...loop, loop[0]]];
  const runs: Vertex[][] = [];
  let current: Vertex[] = [];
  for (let k = 1; k <= n; k++) {
    const i = (start + k) % n;
    if (flags[i]) current.push(loop[i]);
    else if (current.length > 0) {
      runs.push(current);
      current = [];
    }
  }
  if (current.length > 0) runs.push(current);
  return runs;
}

import { roundTo, type Point } from "../../geom/point.ts";
import type { Box } from "../core/box.ts";
import { blankMask, maskAt, maskOf, type Mask } from "../core/mask.ts";
import { simplifyClosed, simplifyOpen, traceBoundary } from "../core/outline.ts";
import type { Anatomy } from "../measure/anatomy.ts";
import type { Measurement } from "../measure/measurement.ts";

/** The default tolerance, as a share of the figure's height. */
const TOLERANCE_SHARE = 0.004;
const PLACES = 4;

export type TracePart = "head" | "ear-left" | "ear-right" | "tail" | "arm-left" | "arm-right" | "figure";

export interface TraceOptions {
  /** A part of the view, or a box of the sheet whose figure pixels to trace. */
  part?: TracePart;
  box?: Box;
  /** The most a dropped point may stray from the curve, in body units. */
  tolerance?: number;
  /** An open curve: the stretch where the part joins the body is left out. */
  open?: boolean;
}

export interface Trace {
  part: string;
  closed: boolean;
  /** In body units: x across from the axis (image right positive), y up from the ground. */
  points: Point[];
  /** The same points in the image's pixels. */
  pixels: Point[];
  tolerance: number;
}

function partMask(anatomy: Anatomy, options: TraceOptions): Mask {
  const { figure } = anatomy;
  const { width, height } = figure.mask;
  if (options.box) {
    const mask = blankMask(width, height);
    const b = options.box;
    for (let y = b.y; y < b.y + b.h; y++) for (let x = b.x; x < b.x + b.w; x++) if (maskAt(figure.mask, x, y)) mask.data[y * width + x] = 1;
    return mask;
  }
  const part = options.part ?? "head";
  if (part === "figure") return figure.mask;
  if (part === "head") {
    const mask = blankMask(width, height);
    for (let y = figure.top; y <= anatomy.marks.chin.y; y++) {
      for (let x = figure.box.x; x < figure.box.x + figure.box.w; x++) {
        const at = y * width + x;
        if (figure.mask.data[at]) mask.data[at] = 1;
      }
    }
    return mask;
  }
  const [kind, side] = part.split("-") as ["ear" | "tail" | "arm", "left" | "right" | undefined];
  const piece = anatomy.pieces
    .filter((p) => p.kind === kind && (side === undefined || (side === "left") === p.side < 0))
    .sort((a, b) => b.area - a.area)[0];
  if (!piece) throw new Error(`no ${part} stands apart in this view; trace it with box=x,y,w,h`);
  return maskOf(width, height, piece.pixels);
}

/** Splits a loop where it touches the body and returns the longest stretch that does not. */
function openStretch(loop: Point[], core: Mask): Point[] {
  const touches = loop.map(([x, y]) => maskAt(core, x - 1, y) || maskAt(core, x + 1, y) || maskAt(core, x, y - 1) || maskAt(core, x, y + 1));
  if (!touches.some(Boolean)) return loop;
  const start = touches.indexOf(true);
  const rotated = [...loop.slice(start), ...loop.slice(0, start)];
  const flags = [...touches.slice(start), ...touches.slice(0, start)];
  let best: Point[] = [];
  let current: Point[] = [];
  rotated.forEach((p, i) => {
    if (flags[i]) {
      if (current.length > best.length) best = current;
      current = [];
    } else {
      current.push(p);
    }
  });
  return current.length > best.length ? current : best;
}

/** A part's outline as a few points in body units, closed, or open along where it joins the body. */
export function traceShape(anatomy: Anatomy, measurement: Measurement, options: TraceOptions = {}): Trace {
  const mask = partMask(anatomy, options);
  const loop = traceBoundary(mask);
  if (loop.length === 0) throw new Error("nothing to trace there");
  const scale = measurement.bodyPerPixel;
  const tolerancePx = options.tolerance !== undefined ? options.tolerance / scale : TOLERANCE_SHARE * (anatomy.figure.ground - anatomy.figure.top);
  const open = options.open === true;
  const pixels = open ? simplifyOpen(openStretch(loop, anatomy.core), tolerancePx) : simplifyClosed(loop, tolerancePx);
  const points = pixels.map(([x, y]): Point => [roundTo((x + 0.5 - measurement.axis) * scale, PLACES), roundTo((measurement.ground - y - 0.5) * scale, PLACES)]);
  return { part: options.box ? "box" : (options.part ?? "head"), closed: !open, points, pixels, tolerance: roundTo(tolerancePx * scale, PLACES) };
}

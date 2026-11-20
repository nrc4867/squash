import { roundTo, type Point } from "../../geom/point.ts";
import type { Box } from "../core/box.ts";
import { blankMask, maskAt, maskOf, type Mask } from "../core/mask.ts";
import { simplifyClosed, simplifyOpen, traceBoundary } from "../core/outline.ts";
import type { Anatomy } from "../measure/anatomy.ts";
import type { Measurement } from "../measure/measurement.ts";
import { sideFacing } from "./profiles/shared.ts";
import type { Raster } from "../raster/image.ts";
import { extent, fewPoints, inFrame, mirrorX, mirrorY, normalized, outlineLine, type OutlinePane } from "./partFrame.ts";
import { footPlacement } from "./trace-foot.ts";
import { handPlacement } from "./trace-hand.ts";
import type { PartPlacement, SideFacing } from "./trace-part.ts";

/** The default tolerance, as a share of the figure's height. */
const TOLERANCE_SHARE = 0.004;
const PLACES = 4;

export type TracePart = "head" | "ear-left" | "ear-right" | "tail" | "arm-left" | "arm-right" | "foot-left" | "foot-right" | "hand-left" | "hand-right" | "figure";

export interface TraceOptions {
  /** A part of the view, or a box of the sheet whose figure pixels to trace. */
  part?: TracePart;
  box?: Box;
  /** The most a dropped point may stray from the curve, in body units. */
  tolerance?: number;
  /** An open curve: the stretch where the part joins the body is left out. */
  open?: boolean;
  /** A foot or hand written as a recipe outline in its own frame: its Side, Top or Front. */
  as?: OutlinePane;
  /** A foot's ankle, a hand's wrist and its tip (a foot's toe tip seen from above), in the image's pixels. */
  ankle?: [number, number];
  wrist?: [number, number];
  tip?: [number, number];
  /** Which image side a side view faces; read from its feet when left out. */
  facing?: SideFacing;
  /** The sheet's image, for telling a hand from its sleeve by the skin's colour. */
  image?: Raster;
}

/** A foot or hand as a recipe outline in its own frame, ready to paste. */
export interface TraceOutline {
  pane: OutlinePane;
  through: Point[];
  closed: true;
  /** The outline as a line of TypeScript for the part's `outlines`. */
  line: string;
  /** The traced size in body units: a foot's length and height (Side), length and width (Top) or width and height (Front); a hand's length and breadth. */
  size: Record<string, number>;
  /** Which side of the sheet was traced and how it was placed. */
  notes: string[];
}

export interface Trace {
  part: string;
  closed: boolean;
  /** In body units: x across from the axis (image right positive), y up from the ground. */
  points: Point[];
  /** The same points in the image's pixels. */
  pixels: Point[];
  tolerance: number;
  /** A foot's ankle or a hand's wrist, in the image's pixels, where the trace is in a part's frame. */
  socket?: Point;
  outline?: TraceOutline;
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

function isPairPart(part: TracePart | undefined): part is "foot-left" | "foot-right" | "hand-left" | "hand-right" {
  return part !== undefined && /^(foot|hand)-/.test(part);
}

/** A foot or hand cut from the view and placed in its own frame. */
function placement(anatomy: Anatomy, measurement: Measurement, part: "foot-left" | "foot-right" | "hand-left" | "hand-right", options: TraceOptions): PartPlacement {
  const [kind, side] = part.split("-") as ["foot" | "hand", "left" | "right"];
  const pane = options.as ?? "side";
  const facing = () => options.facing ?? sideFacing({ anatomy, measurement });
  if (kind === "foot") return footPlacement(anatomy, { side, pane, box: options.box, ankle: options.ankle, tip: options.tip, facing });
  return handPlacement(anatomy, { side, pane, box: options.box, wrist: options.wrist, tip: options.tip, image: options.image, facing });
}

/** The size a pane's outline shows, named for the part. */
function sizeOf(kind: "foot" | "hand", pane: OutlinePane, span: { x: number; y: number }): Record<string, number> {
  if (kind === "hand") return { length: span.x, breadth: span.y };
  if (pane === "side") return { length: span.x, height: span.y };
  if (pane === "top") return { length: span.x, width: span.y };
  return { width: span.x, height: span.y };
}

/** A foot or hand in its own frame as a recipe outline, mirrored onto the character's left where the view shows its right. */
function partOutline(anatomy: Anatomy, measurement: Measurement, part: "foot-left" | "foot-right" | "hand-left" | "hand-right", options: TraceOptions): Trace {
  const pane = options.as ?? "side";
  const placed = placement(anatomy, measurement, part, options);
  const loop = traceBoundary(placed.mask);
  if (loop.length === 0) throw new Error("nothing to trace there");
  const scale = measurement.bodyPerPixel;
  const simplified = options.tolerance !== undefined ? { points: simplifyClosed(loop, options.tolerance / scale), tolerance: options.tolerance / scale } : fewPoints(loop);
  const pixels = simplified.points;
  const framed = inFrame(pixels, placed.frame, scale);
  const span = extent(framed);
  const kind = part.startsWith("foot") ? "foot" : "hand";
  let through = pane === "front" ? normalized(framed) : framed;
  const notes = [...placed.notes];
  const shown = part.endsWith("left") ? "left" : "right";
  const whose = placed.characterRight === undefined ? "" : placed.characterRight ? " (the character's right)" : " (the character's left)";
  const recipePart = `${kind}-l`;
  if (placed.characterRight && pane === "top") {
    through = mirrorY(through);
    notes.push(`traced the image-${shown} ${kind}${whose}; mirrored in y so the recipe's ${recipePart} takes it`);
  } else if (placed.characterRight && pane === "front") {
    through = mirrorX(through);
    notes.push(`traced the image-${shown} ${kind}${whose}; mirrored in x so the recipe's ${recipePart} takes it`);
  } else {
    notes.push(`traced the image-${shown} ${kind}${whose}; the recipe's ${recipePart} takes it`);
  }
  const points = pixels.map(([x, y]): Point => [roundTo((x + 0.5 - measurement.axis) * scale, PLACES), roundTo((measurement.ground - y - 0.5) * scale, PLACES)]);
  return {
    part,
    closed: true,
    points,
    pixels,
    tolerance: roundTo(simplified.tolerance * scale, PLACES),
    socket: [placed.frame.origin.x, placed.frame.origin.y],
    outline: { pane, through, closed: true, line: outlineLine(pane, through), size: sizeOf(kind, pane, span), notes },
  };
}

/**
 * A part's outline as a few points in body units, closed, or open along where it joins the body. A foot or hand with `as` is also
 * written in its own frame as a recipe outline (`outline`).
 */
export function traceShape(anatomy: Anatomy, measurement: Measurement, options: TraceOptions = {}): Trace {
  if (options.as) {
    if (!isPairPart(options.part)) throw new Error("as= traces a foot or hand: part=foot-left, foot-right, hand-left or hand-right");
    return partOutline(anatomy, measurement, options.part, options);
  }
  const mask = isPairPart(options.part) && !options.box ? placement(anatomy, measurement, options.part, options).mask : partMask(anatomy, options);
  const loop = traceBoundary(mask);
  if (loop.length === 0) throw new Error("nothing to trace there");
  const scale = measurement.bodyPerPixel;
  const tolerancePx = options.tolerance !== undefined ? options.tolerance / scale : TOLERANCE_SHARE * (anatomy.figure.ground - anatomy.figure.top);
  const open = options.open === true;
  const pixels = open ? simplifyOpen(openStretch(loop, anatomy.core), tolerancePx) : simplifyClosed(loop, tolerancePx);
  const points = pixels.map(([x, y]): Point => [roundTo((x + 0.5 - measurement.axis) * scale, PLACES), roundTo((measurement.ground - y - 0.5) * scale, PLACES)]);
  return { part: options.box ? "box" : (options.part ?? "head"), closed: !open, points, pixels, tolerance: roundTo(tolerancePx * scale, PLACES) };
}

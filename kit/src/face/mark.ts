import type { RecipeMark, WidthStop } from "../schema/recipe.ts";
import { roundPoints, type Point } from "../geom/point.ts";
import type { Shape } from "../shapes/shape.ts";
import { arc } from "../shapes/oval.ts";

export interface MarkOptions {
  /** Width along the line as stops; `taper()` thins both ends. */
  width?: WidthStop[];
  /** False fills the shape in place of inking a line. */
  ink?: boolean;
  /** A palette slot for a coloured line, as a brow. */
  slot?: string;
  /** Whether it is painted again on the other side of the head or body. */
  mirrored?: boolean;
  /** A template patch its u and v lie on; for `uvMark` only. */
  patch?: string;
}

function points(drawn: Shape | Point[]): Point[] {
  return roundPoints(Array.isArray(drawn) ? drawn : drawn.points);
}

function mark(id: string, options: MarkOptions): RecipeMark {
  const result: RecipeMark = { id };
  if (options.width) result.width = options.width;
  if (options.ink !== undefined) result.ink = options.ink;
  if (options.slot) result.slot = options.slot;
  if (options.mirrored !== undefined) result.mirrored = options.mirrored;
  return result;
}

/** A mark on a head in `face` places (x to the character's left, y up, from the head's socket). */
export function faceMark(id: string, drawn: Shape | Point[], options: MarkOptions = {}): RecipeMark {
  return { ...mark(id, options), face: points(drawn) };
}

/** A mark on any part as [u, v] places. */
export function uvMark(id: string, drawn: Shape | Point[], options: MarkOptions = {}): RecipeMark {
  const result: RecipeMark = { ...mark(id, options), points: points(drawn) };
  if (options.patch) result.patch = options.patch;
  return result;
}

/** Width stops thin at both ends and full in the middle; `ends` is the ends' share of full width. */
export function taper(ends = 0.3): WidthStop[] {
  return [
    { at: 0, scale: ends },
    { at: 0.5, scale: 1 },
    { at: 1, scale: ends },
  ];
}

export interface SmileSpec {
  /** Its middle, in face places. */
  center: Point;
  /** Half its width. */
  width: number;
  /** How far the corners rise above the middle; negative frowns. */
  depth: number;
  points?: number;
}

/** A mouth line through `center`, its corners `depth` above it: a smile, or with negative depth a frown. */
export function smile(spec: SmileSpec): Shape {
  const sweep = 70;
  const rx = spec.width / Math.sin((sweep * Math.PI) / 180);
  const drop = rx * (1 - Math.cos((sweep * Math.PI) / 180));
  const ry = Math.abs(spec.depth) / Math.max(drop / rx, 1e-6);
  const sign = spec.depth >= 0 ? 1 : -1;
  const center: Point = [spec.center[0], spec.center[1] + sign * ry];
  const drawn = sign > 0
    ? arc({ center, rx, ry, from: 270 - sweep, to: 270 + sweep, points: spec.points ?? 5 })
    : arc({ center, rx, ry, from: 90 + sweep, to: 90 - sweep, points: spec.points ?? 5 });
  return drawn;
}

import type { RecipeOutline } from "../schema/recipe.ts";
import { opposite, pairDirection, restFrames, type Direction, type PairDirection, type Rest } from "../frames/rest.ts";
import { steps, type Point } from "../geom/point.ts";
import { twoSidedLoop, twoSidedOpen } from "./loop.ts";
import { outline, type Shape } from "./shape.ts";

/** Radii by direction, one per station. `front` is `forward`. A pair's left side may name `outer` (its left) and `inner` (its right). */
export type TubeProfiles = Partial<Record<Direction | PairDirection | "front", readonly number[]>>;

export interface TubeSpec {
  /** The way the part rests, which decides which profiles Side and Top take (frames.md). */
  rest: Rest;
  /** Its drawn length: outline x runs 0 to this. */
  length: number;
  /** Radii in body units, a list per direction, all lists the same length. Side takes the two directions across its y, Top the other two; a Top direction left out mirrors the other. */
  profiles: TubeProfiles;
  /** Each station's outline x; evenly from 0 to where the tip begins when left out. */
  at?: readonly number[];
  /** How far the rounded tip runs past the last station, as a share of its radius; 0 ends it flat at `length`. */
  tip?: number;
  /** A closed loop, rounded at the socket end too, as a torso or head: `start` is that end's share like `tip`. */
  closed?: boolean;
  start?: number;
}

export interface TubeOutlines {
  side: RecipeOutline;
  top: RecipeOutline;
}

const DEFAULT_TIP = 1;

function profile(profiles: TubeProfiles, direction: Direction): readonly number[] | undefined {
  const named = profiles[direction];
  if (named) return named;
  if (direction === "forward") return profiles.front;
  if (direction === "left") return profiles.outer;
  if (direction === "right") return profiles.inner;
  return undefined;
}

function stationsX(spec: TubeSpec, count: number, tipRadius: number, startRadius: number): number[] {
  if (spec.at) {
    if (spec.at.length !== count) throw new Error(`tube: ${count} radii a profile but ${spec.at.length} places in at`);
    return [...spec.at];
  }
  const start = spec.closed ? startRadius * (spec.start ?? DEFAULT_TIP) : 0;
  const end = spec.length - tipRadius * (spec.tip ?? DEFAULT_TIP);
  return steps(start, end, count);
}

function view(spec: TubeSpec, plusDirection: Direction, mirrorMissing: boolean): Shape {
  const minusDirection = opposite(plusDirection);
  let plus = profile(spec.profiles, plusDirection);
  let minus = profile(spec.profiles, minusDirection);
  if (mirrorMissing) {
    plus ??= minus;
    minus ??= plus;
  }
  if (!plus || !minus) {
    const missing = [plus ? undefined : plusDirection, minus ? undefined : minusDirection].filter(Boolean).join(" and ");
    throw new Error(`tube resting ${spec.rest}: its Side needs ${missing} radii`);
  }
  if (plus.length !== minus.length) throw new Error(`tube: ${plusDirection} and ${minusDirection} have different station counts`);
  const tipRadius = Math.max(plus[plus.length - 1], minus[minus.length - 1]);
  const startRadius = Math.max(plus[0], minus[0]);
  const xs = stationsX(spec, plus.length, tipRadius, startRadius);
  const plusSide: Point[] = xs.map((x, i) => [x, plus[i]]);
  const minusSide: Point[] = xs.map((x, i) => [x, -minus[i]]);
  const flat = (spec.tip ?? DEFAULT_TIP) === 0;
  if (spec.closed) {
    return twoSidedLoop([[0, 0], ...plusSide, [spec.length, 0]], [[0, 0], ...minusSide, [spec.length, 0]]);
  }
  if (flat) {
    const shapeFlat = twoSidedOpen(plusSide, minusSide, [spec.length, 0]);
    return { ...shapeFlat, corners: [plusSide.length - 1, plusSide.length + 1] };
  }
  return twoSidedOpen(plusSide, minusSide, [spec.length, 0]);
}

/**
 * Side and Top outlines for a part from radius profiles, which names the part's rest decides: resting up or down, Side takes
 * `front` and `back` and Top `left` and `right` (`outer` and `inner` on a pair); resting forward or back, Side takes `up` and `down`
 * and Top `left` and `right`; resting out, Side takes `up` and `down` and Top `front` and `back`.
 */
export function tube(spec: TubeSpec): TubeOutlines {
  const frame = restFrames[spec.rest];
  return { side: outline(view(spec, frame.side, false)), top: outline(view(spec, frame.top, true)) };
}

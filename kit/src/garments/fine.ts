import type { RecipeArea, RecipeGarment, RecipeStroke } from "../schema/recipe.ts";
import { garment, LAYER } from "./wardrobe.ts";

/** A place on a part's chart: [u along it, v round it], v 0 at its front middle on a torso. */
export type Place = [number, number];

/** An exact lasso: the closed shape through `points` on `on`, a smooth curve when `curved`, sharp at `corners`; mirrored across the middle with `mirrored`. */
export function exactLasso(on: string, points: Place[], { curved = false, corners = [], mirrored = false, unpaint = false }: { curved?: boolean; corners?: number[]; mirrored?: boolean; unpaint?: boolean } = {}): RecipeStroke {
  if (points.length < 3) throw new Error(`an exact lasso passes through at least 3 places, not ${points.length}`);
  for (const c of corners) if (c < 0 || c >= points.length) throw new Error(`corner ${c} is not one of the ${points.length} places`);
  const stroke: RecipeStroke = { on, shape: "lasso", exact: true, points };
  if (curved) stroke.curved = true;
  if (corners.length > 0) stroke.corners = corners;
  if (mirrored) stroke.mirrored = true;
  if (unpaint) stroke.unpaint = true;
  return stroke;
}

/** An exact box on `on`: `u` along it and `v` round it, the whole way round when `v` is left out; a band when it is. */
export function exactBox(on: string, u: [number, number], v?: [number, number], { mirrored = false, unpaint = false }: { mirrored?: boolean; unpaint?: boolean } = {}): RecipeStroke {
  if (u[0] >= u[1] || (v && v[0] >= v[1])) throw new Error(`an exact box runs from its from to its to, not u ${u} v ${v}`);
  const stroke: RecipeStroke = v ? { on, shape: "box", exact: true, u, v } : { on, shape: "band", exact: true, u };
  if (mirrored) stroke.mirrored = true;
  if (unpaint) stroke.unpaint = true;
  return stroke;
}

/** An exact ellipse on `on`, inside the box `u` along it by `v` round it. */
export function exactEllipse(on: string, u: [number, number], v: [number, number], { mirrored = false }: { mirrored?: boolean } = {}): RecipeStroke {
  if (u[0] >= u[1] || v[0] >= v[1]) throw new Error(`an exact ellipse lies in a box from its from to its to, not u ${u} v ${v}`);
  const stroke: RecipeStroke = { on, shape: "ellipse", exact: true, u, v };
  if (mirrored) stroke.mirrored = true;
  return stroke;
}

/**
 * A narrow strap on `on` from u `from` to `to`, centred round it at `at`, `width` wide in v: a shoulder strap, a halter or a bikini's
 * band, exact, so it stays however narrow. With `mirrored`, its twin across the middle too.
 */
export function strap(on: string, from: number, to: number, at: number, width: number, { mirrored = false }: { mirrored?: boolean } = {}): RecipeStroke {
  if (width <= 0) throw new Error(`a strap is wider than 0, not ${width}`);
  return exactBox(on, [from, to], [at - width / 2, at + width / 2], { mirrored });
}

/** What a tie is: its knot at the top of `on` and its blade down the front to a point. */
export interface TieSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** The tie's palette slot. */
  color: string;
  /** Where its knot's top sits along the part, in its u. */
  top: number;
  /** Where its point is, in u, below the knot. */
  tip: number;
  /** How wide the knot is round the part, in v. */
  knot?: number;
  /** How wide the blade is at its widest, just above the point, in v. */
  blade?: number;
  /** The part it lies on: the torso. */
  on?: string;
}

/**
 * A tie: one exact curved shape down the front middle of `on`, a knot narrowing into a neck under it, then widening to a blade that ends
 * in a point, on a layer over a shirt. Wear it with a shirt whose collar reaches the knot.
 */
export function tie(spec: TieSpec): RecipeGarment {
  const { color, top, tip, knot = 0.03, blade = 0.06, on = "torso", ...rest } = spec;
  if (tip >= top) throw new Error(`a tie's tip lies below its knot, not ${tip} above ${top}`);
  const length = top - tip;
  const knotBottom = top - length * 0.1;
  const widest = tip + length * 0.1;
  const points: Place[] = [
    [top, knot / 2], [knotBottom, knot * 0.38], [knotBottom - length * 0.04, knot * 0.3],
    [widest, blade / 2], [tip, 0],
    [widest, -blade / 2], [knotBottom - length * 0.04, -knot * 0.3], [knotBottom, -knot * 0.38], [top, -knot / 2],
  ];
  const area: RecipeArea = { id: "tie", name: "Tie", paint: [exactLasso(on, points, { curved: true, corners: [0, 2, 4, 6, 8] })] };
  return garment({ id: "tie", slot: "neck", layer: LAYER.over, color, fit: 0.004, ...rest, areas: [area] });
}

/** What a pointed collar is: its points lying on `on` from the neck's root down the front, either side of the middle. */
export interface PointedCollarSpec {
  /** Where the collar's top edge sits along the part, in its u: near its end at the neck. */
  top: number;
  /** How far down the part, in u, the points reach. */
  drop: number;
  /** Where round the part the collar's outer edge meets the top, in v from the front middle. */
  spread?: number;
  /** How near the front middle, in v, the points lie. */
  point?: number;
  /** The neck, painted as a band up to `stand` of its u. */
  neck?: string;
  /** How far up the neck, in its u, the collar's band stands. */
  stand?: number;
  /** The part the points lie on: the torso. */
  on?: string;
  /** The collar's palette slot; the garment's when left out. */
  color?: string;
}

/**
 * A pointed collar as one area: a stand round the neck and two exact points over the chest, mirrored, each a straight-sided shape
 * sharp at its point. Lay it after the shirt's body so it takes those cells.
 */
export function pointedCollar(spec: PointedCollarSpec): RecipeArea {
  const { top, drop, spread = 0.14, point = 0.012, neck = "neck", stand = 0.22, on = "torso", color } = spec;
  if (drop <= 0) throw new Error(`a collar's points reach down past its top, not by ${drop}`);
  const points: Place[] = [
    [top + 0.1, spread], [top, spread], [top - drop * 0.55, spread * 0.55], [top - drop, point],
    [top - drop * 0.6, point * 0.7], [top - drop * 0.2, spread * 0.25], [top + 0.1, spread * 0.25],
  ];
  const area: RecipeArea = {
    id: "collar",
    name: "Collar",
    paint: [exactLasso(on, points, { mirrored: true }), exactBox(neck, [0, stand])],
  };
  if (color) area.color = color;
  return area;
}

/** What a two-cup bikini top is: its cups on `on`, a band round the back from their sides and straps up from their tops. */
export interface BikiniTopSpec extends Partial<Omit<RecipeGarment, "areas">> {
  /** Its palette slot. */
  color: string;
  /** Where the cups lie along the part, in u, bottom to top. */
  cups: [number, number];
  /** Where each cup lies round the part, in v from the front middle, inner edge to outer. */
  cupV: [number, number];
  /** How tall the band round the back is, in u, at the cups' lower middle. */
  band?: number;
  /** How wide the straps are, in v, and where they end along the part, in u; no straps when `strapWidth` is 0. */
  strapWidth?: number;
  strapTop?: number;
  /** A small gore joining the cups at the middle, in u tall; none when 0. */
  gore?: number;
  on?: string;
}

/**
 * A bikini top: two exact elliptical cups, mirrored, apart at the middle by their inner edge, a narrow band round the back from their
 * sides, narrow straps up from their tops and, with `gore`, a small piece joining them at the middle. Every stroke is exact, so the cups
 * stay apart and the straps and band stay however narrow.
 */
export function bikiniTop(spec: BikiniTopSpec): RecipeGarment {
  const { color, cups, cupV, band = 0.022, strapWidth = 0.012, strapTop = 0.99, gore = 0, on = "torso", ...rest } = spec;
  if (cups[0] >= cups[1] || cupV[0] >= cupV[1]) throw new Error(`a cup lies from its from to its to, not u ${cups} v ${cupV}`);
  const low = cups[0] + (cups[1] - cups[0]) * 0.3;
  const paint: RecipeStroke[] = [
    exactEllipse(on, cups, cupV, { mirrored: true }),
    exactBox(on, [low - band / 2, low + band / 2], [(cupV[0] + cupV[1]) / 2, 0.5], { mirrored: true }),
  ];
  if (strapWidth > 0) paint.push(strap(on, (cups[0] + cups[1]) / 2, strapTop, cupV[0] + (cupV[1] - cupV[0]) * 0.55, strapWidth, { mirrored: true }));
  if (gore > 0) paint.push(exactBox(on, [low - gore / 2, low + gore / 2], [-cupV[0] - 0.01, cupV[0] + 0.01]));
  return garment({ id: "bikini-top", slot: "top", layer: LAYER.under, color, fit: 0.006, ...rest, areas: [{ id: "cups", name: "Cups", paint }] });
}

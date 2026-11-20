import type { RecipeRegion } from "../schema/recipe.ts";
import { roundPoints, type Point } from "../geom/point.ts";
import type { Shape } from "../shapes/shape.ts";

export interface RegionOptions {
  /** Whether its border is inked. */
  ink?: boolean;
  /** False runs the border straight between its places on a part joined into the body's one surface. */
  curved?: boolean;
  /** Whether it is painted again on the other side of the body: across a middle part, or on a pair's other side on a garment. */
  mirrored?: boolean;
  /** A template patch its u and v lie on, as `muzzle`; for `uvRegion` only. */
  patch?: string;
}

function placed(drawn: Shape | Point[]): { points: Point[]; corners: number[] } {
  return Array.isArray(drawn) ? { points: drawn, corners: [] } : { points: drawn.points, corners: drawn.corners ?? [] };
}

function region(id: string, slot: string, options: RegionOptions, corners: number[]): RecipeRegion {
  const result: RecipeRegion = { id, slot };
  if (corners.length > 0) result.corners = corners;
  if (options.ink !== undefined) result.ink = options.ink;
  if (options.curved !== undefined) result.curved = options.curved;
  if (options.mirrored !== undefined) result.mirrored = options.mirrored;
  return result;
}

/**
 * A region on a head drawn in `face` places: the head seen from straight ahead, x to the character's left (negative for
 * its right), y up, in the head's drawn units from its socket. `tools/recipe.sh probe <recipe> part=<head>` prints the reach.
 */
export function faceRegion(id: string, slot: string, drawn: Shape | Point[], options: RegionOptions = {}): RecipeRegion {
  const { points, corners } = placed(drawn);
  return { ...region(id, slot, options, corners), face: roundPoints(points) };
}

/** A region on any part drawn as [u, v] places: u along it from its socket end, v round it (frames.md). */
export function uvRegion(id: string, slot: string, drawn: Shape | Point[], options: RegionOptions = {}): RecipeRegion {
  const { points, corners } = placed(drawn);
  const result: RecipeRegion = { ...region(id, slot, options, corners), border: roundPoints(points) };
  if (options.patch) result.patch = options.patch;
  return result;
}

/** One edge of a ring region: a u all the way round, u values evenly round from the seam, or u as a function of v, read from 0 to just short of 1. */
export type RingEdge = number | readonly number[] | ((v: number) => number);

export interface RingOptions extends RegionOptions {
  /** The v where the ring's two ends meet; 0 when left out. */
  seam?: number;
  /** Places taken round an edge given as a function or a single u; 16 when left out. */
  points?: number;
}

function edgeAt(edge: RingEdge, points: number): number[] {
  if (typeof edge === "number") return Array.from({ length: points + 1 }, () => edge);
  if (typeof edge === "function") return Array.from({ length: points + 1 }, (_, i) => edge((i % points) / points));
  if (edge.length < 2) throw new Error("a ring edge given as a list needs at least two u values");
  return [...edge, edge[0]];
}

/**
 * A region all the way round a part, between edge `from` and edge `to` (each a u, a list of u values evenly round, or a function of v
 * from 0 to 1 round from the seam): one band with no edge where its ends meet, as a stripe round a leg or a tail.
 */
export function uvRing(id: string, slot: string, from: RingEdge, to: RingEdge, options: RingOptions = {}): RecipeRegion {
  const seam = options.seam ?? 0;
  const count = options.points ?? 16;
  const out = edgeAt(from, count);
  const back = edgeAt(to, count).reverse();
  const v = (i: number, n: number) => {
    const around = seam + i / n;
    return around > 1 ? around - 1 : around;
  };
  const points: Point[] = [
    ...out.map((u, i): Point => [u, i === out.length - 1 ? seam + 1 : v(i, out.length - 1)]),
    ...back.map((u, i): Point => [u, i === 0 ? seam + 1 : v(back.length - 1 - i, back.length - 1)]),
  ].map(([u, w]): Point => [u, w > 1 + 1e-9 ? w - 1 : w]);
  const corners = [0, out.length - 1, out.length, out.length + back.length - 1];
  const result: RecipeRegion = { ...region(id, slot, options, corners), border: roundPoints(points) };
  if (options.patch) result.patch = options.patch;
  return result;
}

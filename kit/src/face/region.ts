import type { RecipeRegion } from "../schema/recipe.ts";
import { roundPoints, type Point } from "../geom/point.ts";
import type { Shape } from "../shapes/shape.ts";

export interface RegionOptions {
  /** Whether its border is inked. */
  ink?: boolean;
  /** False runs the border straight between its places on a part joined into the body's one surface. */
  curved?: boolean;
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

import type { RecipeOutline } from "../schema/recipe.ts";
import { roundPoints, type Point } from "../geom/point.ts";

/** A drawn shape: places a smooth curve passes through, the indices of its sharp corners, and whether it closes. */
export interface Shape {
  points: Point[];
  corners?: number[];
  closed?: boolean;
}

export function shape(points: Point[], corners: number[] = [], closed = false): Shape {
  return { points, corners, closed };
}

/** A shape as a recipe outline (`outlines.side`, `.top`, `.front`, or a cover's outlines). */
export function outline(drawn: Shape | Point[]): RecipeOutline {
  const s = Array.isArray(drawn) ? { points: drawn } : drawn;
  const result: RecipeOutline = { through: roundPoints(s.points) };
  if (s.corners && s.corners.length > 0) result.corners = [...s.corners];
  if (s.closed) result.closed = true;
  return result;
}

/** The same shape with its points changed by `map`, corners and closing kept. */
export function mapShape(drawn: Shape, map: (points: Point[]) => Point[]): Shape {
  return { ...drawn, points: map(drawn.points) };
}

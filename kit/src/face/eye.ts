import type { EyeLook, RecipeEye } from "../schema/recipe.ts";
import { roundPoints, type Point } from "../geom/point.ts";

export interface EyeSpec {
  /** The head it is painted on. */
  on: string;
  /** Its middle in face places, on the character's left (x positive); mirrored to the right unless `mirrored` is false. */
  face: Point;
  look?: EyeLook;
  front?: EyeLook;
  flat?: boolean;
  mirrored?: boolean;
}

/** A painted eye placed in face places, mirrored across the head unless told otherwise. */
export function faceEye(id: string, spec: EyeSpec): RecipeEye {
  const eye: RecipeEye = { id, on: spec.on, face: roundPoints([spec.face])[0] };
  if (spec.look) eye.look = spec.look;
  if (spec.front) eye.front = spec.front;
  if (spec.flat !== undefined) eye.flat = spec.flat;
  if (spec.mirrored !== undefined) eye.mirrored = spec.mirrored;
  return eye;
}

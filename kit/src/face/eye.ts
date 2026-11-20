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

/** How strongly `animeEye` draws each part of a toon eye's detail; each left out takes the preset's value, 0 turns it off. */
export interface AnimeEyeDetail {
  /** The upper lid mass's heaviest thickness, a share of the opening's height. */
  lid?: number;
  /** How far the mass runs past the back corner, a share of the opening's half width. */
  flick?: number;
  /** How far the flick's tip rises, a share of the opening's height. */
  flickLift?: number;
  /** The lower lid's weight, a share of the upper's. */
  lower?: number;
  /** How much of the lower lid is drawn from the back corner. */
  lowerExtent?: number;
  /** How many lash wedges grow from the lid's outer part. */
  clumps?: number;
  /** The outermost wedge's length, a share of the opening's height. */
  clumpLength?: number;
  /** How far down the iris's darker top reaches, a share of its height. */
  shade?: number;
  /** How far up the iris's lighter crescent reaches, a share of its height. */
  crescent?: number;
  /** The iris's darker rim, a share of its half width. */
  rim?: number;
  /** The lid's shadow on the white, a share of the opening's height. */
  lidShadow?: number;
  /** How flat the upper lid runs, 0 to 1. */
  flatTop?: number;
  /** How sharp the back corner is, 0 to 1. */
  outerCorner?: number;
}

const ANIME_EYE: Required<AnimeEyeDetail> = {
  lid: 0.32,
  flick: 0.22,
  flickLift: 0.25,
  lower: 0.2,
  lowerExtent: 0.45,
  clumps: 3,
  clumpLength: 0.35,
  shade: 0.5,
  crescent: 0.3,
  rim: 0.12,
  lidShadow: 0.22,
  flatTop: 0.55,
  outerCorner: 0.6,
};

/**
 * [look] with a drawn toon eye's detail turned on: a heavy upper lid tapering to the front corner with a flick past the back one, a light
 * partial lower lid, lash wedges in place of the flicks, an iris with a darker top, a lighter crescent and a rim, a lid shadow on the white
 * and a flatter top lid over a sharper back corner. [detail] sets each part's strength; groups the look gives itself are laid over these.
 */
export function animeEye(look: EyeLook = {}, detail: AnimeEyeDetail = {}): EyeLook {
  const d = { ...ANIME_EYE, ...detail };
  return {
    lashes: 0,
    topFlatness: d.flatTop,
    outerCorner: d.outerCorner,
    ...look,
    lidMass: { weight: d.lid, flick: d.flick, flickLift: d.flickLift, lower: d.lower, lowerExtent: d.lowerExtent, ...look.lidMass },
    lashClumps: { count: d.clumps, length: d.clumpLength, ...look.lashClumps },
    irisShade: { upper: d.shade, lower: d.crescent, rim: d.rim, ...look.irisShade },
    lidShadow: { depth: d.lidShadow, ...look.lidShadow },
  };
}

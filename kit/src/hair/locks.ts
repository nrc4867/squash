// Hair made of locks: each lock a separate swept part, in groups over an inner mass that hides the scalp. See docs/hld/parts.md,
// Hair locks, and the skill's head-and-face.md.

import type { RecipeHair, RecipeLockGroup, RecipePart, RecipeSocket } from "../schema/recipe.ts";
import { socket } from "../parts/rig.ts";
import { roundTo } from "../geom/point.ts";
import { HAIR_LENGTHS } from "./index.ts";

/** The lock presets (`RecipeHair.preset`). */
export type LockPreset = NonNullable<RecipeHair["preset"]>;

export interface LockMassSpec {
  id?: string;
  /** The head it sits on. */
  on: string;
  /** The number of the hand-made socket it takes on the head; `lockMass` returns that socket. */
  socket: number;
  /** The head's depth from the back of the skull to the forehead, in body units. */
  depth: number;
  /** The head's height from chin to crown, in body units. */
  height: number;
  /** How far the mass stands off the skull, in body units: about a lock's thickness. */
  thickness?: number;
  /** Where up the head its socket sits, its u: a little over the middle of a `head-round`, so its front stops above the brow. */
  at?: number;
  /** Its palette slot: the locks' colour. */
  color?: string;
  /** Hair-volume parameters over the mass's own. */
  params?: Record<string, number>;
}

/**
 * The inner mass under lock hair: a smooth `hair-volume` with no locks or strand lines of its own, sized to the head and a little
 * narrower than a full volume, so it only hides the scalp and the locks over it set the outline. Its hairline sits above the brow.
 */
export function lockMass(spec: LockMassSpec): { part: RecipePart; headSocket: RecipeSocket } {
  const { id = "hair-mass", on, socket: n, depth, height, thickness = 0.03, at = 0.55, color, params } = spec;
  const length = depth + 2 * thickness;
  return {
    headSocket: socket(n, at, 0.5, { sink: 0, outset: thickness }),
    part: {
      id,
      template: "hair-volume",
      on,
      socket: `socket.${n}`,
      role: "hair",
      preset: "ponytail",
      size: roundTo(length / HAIR_LENGTHS.volume),
      ...(color ? { color } : {}),
      params: {
        crown: roundTo((height * (1 - at) + thickness) / length),
        width: 0.45,
        back: 0.35,
        sides: -0.05,
        fringe: -0.15,
        rise: 0.35,
        strands: 0,
        lockLines: 0,
        ...(params ?? {}),
      },
    },
  };
}

export interface LockHairSpec {
  /** The head the locks grow on. */
  on: string;
  /** The inner mass's part id, from `lockMass`. */
  mass?: string;
  /** The locks' palette slot. */
  color?: string;
  /** A ponytail band's palette slot. */
  band?: string;
  /** The head's reach from its middle, in body units; measured off the built head when left out. */
  reach?: number;
  /** Groups changing the preset's by id (`back`, `left`, `right`, `crown`, `fringe`, `strays`, `tail`), or new groups with a kind. */
  groups?: RecipeLockGroup[];
  /** Whether to set up the body's collision shapes; on unless false. */
  colliders?: boolean;
}

/** Lock hair of `preset` on a head: one entry of a recipe's `hair`. */
export function lockHair(preset: LockPreset | null, spec: LockHairSpec): RecipeHair {
  const { on, mass, color, band, reach, groups, colliders } = spec;
  return {
    on,
    ...(preset ? { preset } : {}),
    ...(mass ? { mass } : {}),
    ...(color ? { color } : {}),
    ...(band ? { band } : {}),
    ...(reach !== undefined ? { reach } : {}),
    ...(groups && groups.length > 0 ? { groups } : {}),
    ...(colliders === false ? { colliders: false } : {}),
  };
}

/** Short spiky locks: chunky pointed locks lying back over the crown, a few strays breaking the outline. */
export const shortSpiky = (spec: LockHairSpec): RecipeHair => lockHair("shortSpiky", spec);
/** A bob: ribbon locks to the jaw with flicked ends and a fringe parted over the eyes. */
export const bob = (spec: LockHairSpec): RecipeHair => lockHair("bob", spec);
/** Long straight ribbon locks falling past the shoulders. */
export const longStraight = (spec: LockHairSpec): RecipeHair => lockHair("longStraight", spec);
/** A ponytail: locks gathered at one tie at the back, wrapped in a band, with a short fringe and sides. */
export const ponytail = (spec: LockHairSpec): RecipeHair => lockHair("ponytail", spec);
/** A side-swept fringe over short layered hair. */
export const sideSwept = (spec: LockHairSpec): RecipeHair => lockHair("sideSwept", spec);
/** Round curled locks: rounded clumps each rolling into a curl. */
export const curly = (spec: LockHairSpec): RecipeHair => lockHair("curly", spec);

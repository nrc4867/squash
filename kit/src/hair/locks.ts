// Hair made of locks: each lock a separate swept part, in groups over an inner mass that hides the scalp. See docs/hld/parts.md,
// Hair locks, and the skill's head-and-face.md.

import type { Cowlick, RecipeHair, RecipeHairOutline, RecipeLockGroup, RecipeMassFit, RecipePart, RecipeParting, RecipeScalpFlow, RecipeSocket, Whorl } from "../schema/recipe.ts";
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
  /** Hair-volume parameters over the mass's own (docs/reference/hair.md, Inner mass). */
  params?: Record<string, number>;
}

export interface FitMassSpec {
  /** Its part id; the head's id with `-hair` when left out. */
  id?: string;
  /** Its palette slot; the palette's `hair` slot, else the head's colour, when left out. */
  color?: string;
  /**
   * Hair-volume parameters over the fitted ones, each with its range in docs/reference/hair.md (Inner mass): `width` (half width, a
   * share of its length), `back` (how far it hangs at the back), `sides` (how far its sides hang), `fringe` (how far its front hangs;
   * negative stops it above the brow), `rise`, `crown` (how far its top rises), `locks`, `lockDepth`, `lockJag` (its hem's points).
   */
  params?: Record<string, number>;
}

/**
 * The inner mass under lock hair, fitted to the head as built: a `hair-volume` cap with no strand lines of its own, sized by the head's
 * box with its top following the skull's dome, its hairline above the brow under the fringe's roots, its sides to the ears, its back to
 * the nape and its hem cut into points, the cap the app's preset menu fits. Give it as a preset helper's `mass`; `params` override the
 * fit. Given `socket`, `depth` and `height` instead, it is placed by hand: `{ part, headSocket }`, a part and the head's socket for it.
 */
export function lockMass(spec?: FitMassSpec): RecipeMassFit;
export function lockMass(spec: LockMassSpec): { part: RecipePart; headSocket: RecipeSocket };
export function lockMass(spec: FitMassSpec | LockMassSpec = {}): RecipeMassFit | { part: RecipePart; headSocket: RecipeSocket } {
  if (!("depth" in spec)) {
    const { id, color, params } = spec;
    return { ...(id ? { id } : {}), ...(color ? { color } : {}), ...(params && Object.keys(params).length > 0 ? { params } : {}) };
  }
  const { id = "hair-mass", on, socket: n, depth, height, thickness = 0.02, at = 0.62, color, params } = spec;
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
        width: 0.42,
        back: 0.55,
        sides: 0.2,
        fringe: -0.5,
        rise: 0.25,
        locks: 24,
        lockDepth: 0.12,
        lockJag: 0.5,
        strands: 0,
        lockLines: 0,
        ...(params ?? {}),
      },
    },
  };
}

/** A centre parting: the hair splits along the middle of the top and lies away to each side. */
export function centrePart(spec: Omit<RecipeParting, "kind"> = {}): RecipeParting {
  return { kind: "centre", ...spec };
}

/**
 * A side parting on the character's `side`, `at` 0 to 1 of the most a parting sits off the middle (about over the outer corner of the
 * eye; 0.6 when left out).
 */
export function sidePart(side: "left" | "right", at = 0.6, spec: Omit<RecipeParting, "kind" | "at"> = {}): RecipeParting {
  return { kind: "side", at: side === "left" ? -Math.abs(at) : Math.abs(at), ...spec };
}

/** The crown whorl: `around` degrees from the front (180 the back), `up` degrees from level, `turn` -1 to 1 (positive clockwise from above). */
export function whorl(spec: Whorl = {}): Whorl {
  return { ...spec };
}

/**
 * A cowlick at `around` and `up` degrees: `turn` 0 flips the hair back against the flow, 1 or -1 swirls it; `lift` degrees raises the
 * locks over it; `radius` degrees how far it reaches. A fringe flick at the hairline: `cowlick({ around: 15, up: 48, lift: 25 })`.
 */
export function cowlick(spec: Cowlick = {}): Cowlick {
  return { ...spec };
}

export interface LockHairSpec {
  /** The head the locks grow on. */
  on: string;
  /** The inner mass: a fitted one from `lockMass()`, or the id of a mass part placed by hand. */
  mass?: string | RecipeMassFit;
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
  /** Which way the hair grows over the scalp: `whorl`, `part` (`centrePart()`, `sidePart(...)`), `sweep`, `sweepBack`, `cowlicks`; each field given replaces the preset's. */
  flow?: RecipeScalpFlow;
  /** The hair's drawn outline per view, which with `fit` the outer locks are fitted to: `hairOutline([readHairOutline(".../hair-front.json")])`. */
  outline?: RecipeHairOutline;
}

/** Lock hair of `preset` on a head: one entry of a recipe's `hair`. */
export function lockHair(preset: LockPreset | null, spec: LockHairSpec): RecipeHair {
  const { on, mass, color, band, reach, groups, colliders, flow, outline } = spec;
  return {
    on,
    ...(preset ? { preset } : {}),
    ...(typeof mass === "string" ? { mass } : {}),
    ...(mass !== undefined && typeof mass !== "string" ? { fitMass: mass } : {}),
    ...(color ? { color } : {}),
    ...(band ? { band } : {}),
    ...(reach !== undefined ? { reach } : {}),
    ...(groups && groups.length > 0 ? { groups } : {}),
    ...(colliders === false ? { colliders: false } : {}),
    ...(flow ? { flow } : {}),
    ...(outline ? { outline } : {}),
  };
}

/** Short spiky locks: pointed locks lying back over the head, their tips lifting back and up, a spiked fringe and a stray or two. */
export const shortSpiky = (spec: LockHairSpec): RecipeHair => lockHair("shortSpiky", spec);
/** A bob: wide round-tipped ribbon locks of one length to the jaw, their ends turning in, and a full fringe parted over the eyes. */
export const bob = (spec: LockHairSpec): RecipeHair => lockHair("bob", spec);
/** Long straight ribbon locks falling past the shoulders. */
export const longStraight = (spec: LockHairSpec): RecipeHair => lockHair("longStraight", spec);
/** A ponytail: locks gathered at one tie at the back, wrapped in a band, with a fringe. */
export const ponytail = (spec: LockHairSpec): RecipeHair => lockHair("ponytail", spec);
/** A side-swept fringe over short layered hair. */
export const sideSwept = (spec: LockHairSpec): RecipeHair => lockHair("sideSwept", spec);
/** A curly preset's spec: with `tuft`, only its crown, a round mass of curls rising from the top and rolling forward over the brow. */
export interface CurlySpec extends LockHairSpec {
  tuft?: boolean;
}

/** Curly: fewer, bigger locks each rolling outward into a round spiral, a tied crown of curls rolling over the brow. */
export const curly = (spec: CurlySpec): RecipeHair => {
  const { tuft, ...rest } = spec;
  if (!tuft) return lockHair("curly", rest);
  const removed = ["back", "left", "right", "fringe"].map((id) => ({ id, removed: true }));
  return lockHair("curly", { ...rest, groups: [...removed, ...(rest.groups ?? [])] });
};

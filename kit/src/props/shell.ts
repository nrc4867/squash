import type { RecipePart, RecipeSocket } from "../schema/recipe.ts";
import { roundTo, type Point } from "../geom/point.ts";
import { socket, socketName } from "../parts/rig.ts";

/** A limb's drawn radii along its outline x, as `[x, radius]` from its socket end, in order. */
export type LimbProfile = Point[];

/** A hanging limb's shape as drawn: its length and its radii toward the front, the back and the side. */
export interface Limb {
  /** The limb's id, the part the shell sits on; a pair's id for a pair. */
  id: string;
  /** Its drawn length, the outline x its far end reaches. */
  length: number;
  front: LimbProfile;
  back: LimbProfile;
  side: LimbProfile;
}

export interface LimbShellSpec {
  id: string;
  limb: Limb;
  /** The hand-made socket number the shell takes on the limb, at its top in front. */
  socket: number;
  garment: string;
  color: string;
  /** Where along the limb's outline x the shell starts and ends. */
  from: number;
  to: number;
  /** How far it stands off the limb, in body units. */
  ease: number;
  /** How far inside the limb its two ends tuck, so nothing shows past them. 0.01 when left out. */
  tuck?: number;
  /** How many places along it the outline takes between its ends. 10 when left out. */
  steps?: number;
  /** How far along the limb each end closes in from `ease` to `lip`, so its edge hugs the limb. A quarter of its length, at most 0.04, when left out. */
  hug?: number;
  /** How far its edges stand off the limb, in body units. 0.005 when left out, far enough that the skin under it never shows through its edge. */
  lip?: number;
  /** Which bones it moves with; left out, the rule's: a shell round a limb crossing a joint follows the body. */
  bind?: NonNullable<RecipePart["bind"]>;
  /** How rigid it keeps each stretch between joints, 0 to 1. */
  stiff?: number;
  /** Makes it a mirrored pair, `<id>-l` and `<id>-r`, on a limb pair. */
  pair?: boolean;
}

/** The radius `profile` gives at `x`, eased in a line between its places and held past its ends. */
export function radiusAt(profile: LimbProfile, x: number): number {
  if (profile.length === 0) throw new Error("a limb profile needs a place");
  if (x <= profile[0][0]) return profile[0][1];
  for (let i = 1; i < profile.length; i++) {
    const [x0, r0] = profile[i - 1];
    const [x1, r1] = profile[i];
    if (x <= x1) return x1 === x0 ? r1 : r0 + ((r1 - r0) * (x - x0)) / (x1 - x0);
  }
  return profile[profile.length - 1][1];
}

/**
 * A garment-owned tube round a hanging limb from `from` to `to` along its outline x, standing `ease` off it, closing in to `lip` toward
 * each end so its edges hug the limb, and tucked into it at both ends: a stocking shell, a boot shaft, a band. `{ part, socket }`: put the socket on the limb's `sockets`. The shell's axis follows the
 * limb's middle as drawn, its Side outline set against the limb's front where it sits.
 */
export function limbShell(spec: LimbShellSpec): { part: RecipePart; socket: RecipeSocket } {
  const { limb, from, to, ease } = spec;
  if (!(from >= 0 && to > from && to <= limb.length * 1.05)) throw new Error(`a shell runs from 0 or more to past its start, within the limb: ${from} to ${to}`);
  if (ease < 0) throw new Error(`a shell's ease is never below 0, not ${ease}`);
  const tuck = spec.tuck ?? 0.01;
  const steps = spec.steps ?? 10;
  const hug = Math.min(spec.hug ?? Math.min(0.04, (to - from) / 4), (to - from) / 2);
  const lip = Math.min(spec.lip ?? 0.005, ease);
  const rim = Math.min(0.008, hug / 2);
  const inner = Array.from({ length: steps - 1 }, (_, i) => from + ((to - from) * (i + 1)) / steps).filter((x) => x > from + hug && x < to - hug);
  const ends = hug > rim ? [from + (rim + hug) / 2, from + hug] : [];
  const xs = [from, from + rim, ...ends, ...inner, ...ends.map((x) => from + to - x).reverse(), to - rim, to];
  const top = radiusAt(limb.front, from);
  const up: Point[] = [];
  const down: Point[] = [];
  const wide: Point[] = [];
  xs.forEach((x, k) => {
    const near = Math.min(x - from, to - x);
    const t = hug > 0 ? Math.min(1, near / hug) : 1;
    const off = k === 0 || k === xs.length - 1 ? -tuck : lip + (ease - lip) * t * t * (3 - 2 * t);
    const along = roundTo(x - from);
    up.push([along, roundTo(radiusAt(limb.front, x) - top + off)]);
    down.push([along, roundTo(-radiusAt(limb.back, x) - top - off)]);
    wide.push([along, roundTo(radiusAt(limb.side, x) + off)]);
  });
  const part: RecipePart = {
    id: spec.id,
    template: "tube",
    on: limb.id,
    socket: socketName(spec.socket),
    role: "other",
    garment: spec.garment,
    rest: "down",
    generic: { rings: 64, segments: 24 },
    outlines: {
      side: { through: [...up, ...down.reverse()] },
      top: { through: [...wide, ...wide.map(([x, y]): Point => [x, -y]).reverse()] },
    },
    color: spec.color,
  };
  if (spec.pair) part.pair = {};
  if (spec.bind !== undefined) part.bind = spec.bind;
  if (spec.stiff !== undefined) part.stiff = spec.stiff;
  return { part, socket: socket(spec.socket, from / limb.length, 0, { sink: 0 }) };
}

export interface BootShaftSpec extends Omit<LimbShellSpec, "to" | "ease"> {
  /** Where the shaft ends, just inside the limb's far end when left out, so it closes into the boot's foot. */
  to?: number;
  /** 0.02 when left out. */
  ease?: number;
}

/**
 * A boot's shaft: a limb shell from `from` down to just inside the limb's end, standing 0.02 off it, half stiff and bound to follow the
 * body, so it bends at the ankle as leather does and closes into the boot's foot.
 */
export function bootShaft(spec: BootShaftSpec): { part: RecipePart; socket: RecipeSocket } {
  return limbShell({ ...spec, to: spec.to ?? spec.limb.length * 0.98, ease: spec.ease ?? 0.02, stiff: spec.stiff ?? 0.5, bind: spec.bind ?? "follow" });
}

export interface KneeBandSpec extends Omit<LimbShellSpec, "from" | "to" | "ease"> {
  /** Where along the limb's outline x the band's middle sits: the knee. */
  at: number;
  /** How long the band is along the limb. */
  width: number;
  /** 0.014 when left out. */
  ease?: number;
}

/** A band round a limb at `at`, `width` long: a knee band, a garter, an armband; it bends with the joint it sits over. */
export function kneeBand(spec: KneeBandSpec): { part: RecipePart; socket: RecipeSocket } {
  const { at, width, ...rest } = spec;
  return limbShell({ ...rest, from: at - width / 2, to: at + width / 2, ease: spec.ease ?? 0.014, steps: spec.steps ?? 6, hug: spec.hug ?? width / 6 });
}

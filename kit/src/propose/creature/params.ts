import type { ViewName } from "../../reader/types.ts";
import type { CreaturePlan, PlanPart } from "./plan.ts";

/** What a parameter moves: where the views lie, a length or width, an end's size, or how the sheet poses a part. */
export type ParamGroup = "place" | "length" | "width" | "size" | "pose";

/** One number the fit moves: its value, the step it starts trying, its bounds, and how hard it is held to its neutral value. */
export interface Param {
  key: string;
  group: ParamGroup;
  value: number;
  neutral: number;
  step: number;
  min: number;
  max: number;
  /** Weight of the pull toward `neutral`; plan defaults hold where no view reads the parameter. */
  hold: number;
  /** For a width, the part and profile it scales and where along the part (0, 0.5 or 1). */
  part?: string;
  profile?: string;
  at?: number;
}

/** Shares along a part its width scales are set at; between them the scale runs as a smooth curve. */
export const WIDTH_PLACES = [0, 0.5, 1];

const LENGTH = { step: 0.15, min: 0.4, max: 2.5, hold: 0.05 };
const WIDTH = { step: 0.15, min: 0.35, max: 2.8, hold: 0.03 };
const SIZE = { step: 0.15, min: 0.4, max: 2.5, hold: 0.05 };
const POSE = { step: 20, hold: 0.00002 };
/** An end's middle moved forward or up off where the plan puts it, in head units. */
const SHIFT = { step: 0.15, min: -1.5, max: 1.5, hold: 0.05 };
const SPREAD_REACH = 80;
const SWING_REACH = 90;
const BEND_REACH = 120;

function param(key: string, group: ParamGroup, neutral: number, limits: { step: number; min: number; max: number; hold: number }, extra: Partial<Param> = {}): Param {
  return { key, group, value: neutral, neutral, ...limits, ...extra };
}

/** A part's first joint between its ends, which the sheet may bend: a knee, an elbow. */
export function bendJoint(part: PlanPart): number | undefined {
  return part.joints?.find((j) => j.at > 0.15 && j.at < 0.85)?.at;
}

/** Every parameter of a plan's body, at its neutral value: the views' scale and places, then lengths, widths, sizes and the sheet's pose. */
export function planParams(plan: CreaturePlan, views: readonly ViewName[]): Param[] {
  const out: Param[] = [param("scale", "place", 1, { step: 0.08, min: 0.05, max: 50, hold: 0 })];
  if (views.includes("side")) out.push(param("side.x", "place", 0, { step: 0.3, min: -20, max: 20, hold: 0 }));
  if (views.includes("top")) out.push(param("top.y", "place", 0, { step: 0.3, min: -20, max: 20, hold: 0 }));
  // A turn about the body's left axis (a pitch, a swing, a bend) shows only from the side; a spread about its forward axis from the front or above.
  const fromSide = views.includes("side");
  const fromFront = views.includes("front") || views.includes("top");
  if (fromSide) out.push(param("root.pitch", "pose", 0, { ...POSE, min: -60, max: 60 }));
  for (const part of plan.parts) {
    out.push(param(`${part.id}.length`, "length", 1, LENGTH));
    for (const profile of Object.keys(part.radii)) {
      for (const at of WIDTH_PLACES) out.push(param(`${part.id}.${profile}.${at}`, "width", 1, WIDTH, { part: part.id, profile, at }));
    }
    if (!part.on) continue;
    if (fromSide) out.push(param(`${part.id}.swing`, "pose", 0, { ...POSE, min: -SWING_REACH, max: SWING_REACH }));
    if (fromFront && part.kind === "limb") out.push(param(`${part.id}.spread`, "pose", 0, { ...POSE, min: -SPREAD_REACH, max: SPREAD_REACH }));
    if (fromSide && bendJoint(part) !== undefined) out.push(param(`${part.id}.bend`, "pose", 0, { ...POSE, min: -BEND_REACH, max: BEND_REACH }));
  }
  for (const end of plan.ends) {
    for (let axis = 0; axis < 3; axis++) out.push(param(`${end.id}.size.${axis}`, "size", 1, SIZE));
    // A head may sit off its neck's end as drawn; a hand or a foot stays on its limb's end, so the limb reaches it.
    const onTrunk = plan.parts.find((p) => p.id === end.on)?.kind === "trunk";
    if (onTrunk) for (let axis = 0; axis < 2; axis++) out.push(param(`${end.id}.shift.${axis}`, "length", 0, SHIFT));
  }
  return out;
}

/** Parameter values by key. */
export class ParamValues {
  private readonly byKey: Map<string, Param>;
  readonly list: Param[];

  constructor(list: Param[]) {
    this.list = list;
    this.byKey = new Map(list.map((p) => [p.key, p]));
  }

  get(key: string, fallback = 0): number {
    return this.byKey.get(key)?.value ?? fallback;
  }

  has(key: string): boolean {
    return this.byKey.has(key);
  }

  param(key: string): Param | undefined {
    return this.byKey.get(key);
  }

  /** The width scale of a part's profile at a share along it: a smooth curve through its three places. */
  widthScale(part: string, profile: string, t: number): number {
    const a = this.get(`${part}.${profile}.0`, 1);
    const b = this.get(`${part}.${profile}.0.5`, 1);
    const c = this.get(`${part}.${profile}.1`, 1);
    return a * (1 - t) * (1 - 2 * t) + 4 * b * t * (1 - t) + c * t * (2 * t - 1);
  }
}

import type { ParamValues } from "./params.ts";
import type { CreaturePlan } from "./plan.ts";

/** Shares along a part its width scales are set at; between them the scale runs as a smooth curve. */
export const WIDTH_PLACES = [0, 0.25, 0.5, 0.75, 1];

/** How hard a part's width scales are held to a smooth run, per square of each one's bend off its neighbours' mean (in log scale). */
const SMOOTH_WEIGHT = 0.08;

/** A part's width scale at share `t`, through its scales at `WIDTH_PLACES` as a Catmull-Rom curve, its ends carried straight on. */
export function scaleAlong(scales: readonly number[], t: number): number {
  const n = scales.length - 1;
  const x = Math.max(0, Math.min(1, t)) * n;
  const i = Math.min(n - 1, Math.floor(x));
  const u = x - i;
  const p1 = scales[i];
  const p2 = scales[i + 1];
  const p0 = i > 0 ? scales[i - 1] : 2 * p1 - p2;
  const p3 = i + 2 <= n ? scales[i + 2] : 2 * p2 - p1;
  const u2 = u * u;
  const u3 = u2 * u;
  return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
}

/** The width scale parameter key of a part's profile at one of its places. */
export function widthKey(part: string, profile: string, at: number): string {
  return `${part}.${profile}.${at}`;
}

/** A part's profile's width scales at its places. */
export function scalesOf(values: ParamValues, part: string, profile: string): number[] {
  return WIDTH_PLACES.map((at) => values.get(widthKey(part, profile, at), 1));
}

/** How far every profile's width scales bend off a smooth run: the sum of their squared second differences in log scale, weighted. */
export function smoothWidthCost(plan: CreaturePlan, values: ParamValues): number {
  let total = 0;
  for (const part of plan.parts) {
    for (const profile of Object.keys(part.radii)) {
      const logs = scalesOf(values, part.id, profile).map((s) => Math.log(Math.max(1e-6, s)));
      for (let i = 1; i + 1 < logs.length; i++) total += SMOOTH_WEIGHT * (logs[i - 1] - 2 * logs[i] + logs[i + 1]) ** 2;
    }
  }
  return total;
}

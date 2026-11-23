import { atStations, radiusReader } from "./body.ts";
import type { ParamValues } from "./params.ts";
import type { CreaturePlan } from "./plan.ts";

/** How hard a covered part's root standing out past its parent's side is pushed back, per square head unit. */
const COVER_WEIGHT = 30;

/**
 * How far each covered part's root (a thigh's top under the hips) stands out past its parent's side, squared and weighted: the root sits
 * out by its offset scaled with the parent's width, and its outer radius must stay within the parent's half width there.
 */
export function coverCost(plan: CreaturePlan, values: ParamValues): number {
  let total = 0;
  for (const part of plan.parts) {
    if (!part.covered || !part.on || !part.offset) continue;
    const parent = plan.parts.find((p) => p.id === part.on)!;
    const u = part.at ?? 1;
    const side = parent.radii.left ? "left" : "outer";
    const plain = atStations(parent.stations, parent.radii[side], u);
    const width = radiusReader(parent, values)(side, u);
    const out = part.offset[2] * (width / (plain || 1));
    const root = radiusReader(part, values)("outer", 0);
    const past = out + root - width;
    if (past > 0) total += COVER_WEIGHT * past * past;
  }
  return total;
}

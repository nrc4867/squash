import { heightPlan, type HeightPlan } from "./heights.ts";
import { hangingSpan, uprightSpan, type Span } from "./span.ts";

/**
 * A standing body's plan: named heights above the ground, named half widths and depths, and spans between heights.
 * `torso.u(plan.h("shoulder"))` is the shoulder socket's u; `leg.x(plan.h("knee"))` the knee's place in the leg's outline.
 */
export interface BodyPlan<H extends string, W extends string> extends HeightPlan<H> {
  readonly widths: Readonly<Record<W, number>>;
  /** A named half width or depth. */
  w(name: W): number;
  /** A part resting up between two named heights. */
  upright(bottom: H, top: H): Span;
  /** A part hanging down between two named heights. */
  hanging(top: H, bottom: H): Span;
}

export function bodyPlan<const H extends Record<string, number>, const W extends Record<string, number>>(
  heights: H,
  widths: W,
): BodyPlan<keyof H & string, keyof W & string> {
  const plan = heightPlan(heights);
  const w = (name: keyof W & string): number => {
    const value = widths[name];
    if (typeof value !== "number") throw new Error(`the body plan has no width "${name}"`);
    return value;
  };
  return {
    ...plan,
    widths,
    w,
    upright: (bottom, top) => uprightSpan(plan.h(bottom), plan.h(top)),
    hanging: (top, bottom) => hangingSpan(plan.h(top), plan.h(bottom)),
  };
}

import type { RecipeGeneric, RecipePart } from "../schema/recipe.ts";
import { tube, type TubeSpec } from "../shapes/tube.ts";

/** The finest sampling a generic part takes: rings along it and control points round it. */
export const FINE: RecipeGeneric = { rings: 96, segments: 32 };
/** A lighter sampling for small parts: props, buttons, locks. */
export const SMALL: RecipeGeneric = { rings: 24, segments: 16 };

/** A part entry without the fields a shape helper fills. */
export type PartBase = Omit<RecipePart, "template" | "outlines" | "rest">;

/**
 * A generic `tube` part drawn from radius profiles (see `tube`), resting as the spec says, sampled at FINE unless
 * `base.generic` says otherwise.
 */
export function tubePart(base: PartBase, shape: TubeSpec): RecipePart {
  const outlines = tube(shape);
  return {
    template: "tube",
    ...base,
    rest: shape.rest,
    generic: { ...FINE, ...(base.generic ?? {}) },
    outlines: { side: outlines.side, top: outlines.top },
  };
}

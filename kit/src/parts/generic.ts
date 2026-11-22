import type { RecipeGeneric, RecipeOutline, RecipePart, RecipeTube } from "../schema/recipe.ts";
import type { Rest } from "../frames/rest.ts";
import { isTubeCurve, tube, type TubeCurve, type TubeSpec } from "../shapes/tube.ts";

/** The finest sampling a generic part takes: rings along it and control points round it. */
export const FINE: RecipeGeneric = { rings: 96, segments: 32 };
/** A lighter sampling for small parts: props, buttons, locks. */
export const SMALL: RecipeGeneric = { rings: 24, segments: 16 };

/** A part entry without the fields a shape helper fills. */
export type PartBase = Omit<RecipePart, "template" | "outlines" | "rest">;

/**
 * A generic `tube` part drawn from radius profiles (see `tube`), resting as the spec says, sampled at FINE unless
 * `base.generic` says otherwise. Profiles drawn as curves go to the part's `tube` as they are, which the build turns into its outlines.
 */
export function tubePart(base: PartBase, shape: TubeSpec): RecipePart {
  const curves = Object.values(shape.profiles).filter(isTubeCurve);
  if (curves.length > 0) {
    if (curves.length !== Object.keys(shape.profiles).length) throw new Error("tubePart: give every profile as a curve or every one as radii at stations");
    if (shape.at) throw new Error("tubePart: a profile drawn as a curve places its own x; leave at out");
    const profiles: Record<string, RecipeOutline> = {};
    for (const [name, curve] of Object.entries(shape.profiles) as [string, TubeCurve][]) profiles[name] = outlineOf(curve);
    return tubeEntry(base, shape.rest, { length: shape.length, profiles, ...(shape.closed ? { closed: true } : {}), ...(shape.tip === 0 ? { flat: true } : {}) });
  }
  const outlines = tube(shape);
  return {
    template: "tube",
    ...base,
    rest: shape.rest,
    generic: { ...FINE, ...(base.generic ?? {}) },
    outlines: { side: outlines.side, top: outlines.top },
  };
}

function outlineOf(curve: TubeCurve): RecipeOutline {
  if ("bezier" in curve) return { bezier: curve.bezier.map((p) => [...p]) };
  return { through: curve.through.map((p) => [...p]), ...(curve.corners?.length ? { corners: [...curve.corners] } : {}) };
}

function tubeEntry(base: PartBase, rest: Rest, entry: RecipeTube): RecipePart {
  return { template: "tube", ...base, rest, generic: { ...FINE, ...(base.generic ?? {}) }, tube: entry };
}

/** How a part a figure fits is shaped: the way it rests, its length, and whether it is rounded at its socket end too (a trunk). */
export interface FigurePartShape {
  rest: Rest;
  length: number;
  closed?: boolean;
}

/**
 * A generic `tube` part whose widths a figure fits (see `figure`): its profiles, the blend where it joins its parent and, on a hand-made
 * socket that gives no `reach`, where its root sits, all read off the recipe's figure outline.
 */
export function figurePart(base: PartBase, shape: FigurePartShape): RecipePart {
  return tubeEntry(base, shape.rest, { length: shape.length, ...(shape.closed ? { closed: true } : {}) });
}

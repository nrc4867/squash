import type { RecipeArea, RecipeMark, RecipeStroke } from "../schema/recipe.ts";

/** How far past a part's root ring an opening reaches where nothing says, in body units, as the engine's default. */
export const OPENING_REACH = 0.01;

/** An opening's shape: its cut, how far past the part's root ring it reaches and how far its front drops, in body units. */
export interface OpeningShape {
  cut?: "round" | "v";
  reach?: number;
  depth?: number;
}

/**
 * The cells round where `part` comes out of the cloth (a neck, an arm, an ear), within `reach` past its root ring, its front dropping
 * `depth` in its `cut`: unpainted by default, as a neckline, an armhole or an ear hole.
 */
export function opening(part: string, shape: OpeningShape = {}, unpaint = true): RecipeStroke {
  const stroke: RecipeStroke = { on: part, shape: "opening" };
  if (shape.cut && shape.cut !== "round") stroke.cut = shape.cut;
  if (shape.reach !== undefined) stroke.reach = shape.reach;
  if (shape.depth) stroke.depth = shape.depth;
  if (unpaint) stroke.unpaint = true;
  return stroke;
}

/** A crew neckline round `neck`: a round opening a little past the neck, dropping `depth` at the front. */
export function crewNeck(neck = "neck", { reach = OPENING_REACH, depth = 0.025 }: Omit<OpeningShape, "cut"> = {}): RecipeStroke {
  return opening(neck, { cut: "round", reach, depth });
}

/** A V neckline round `neck`: straight sides from the neck to a point `depth` down the front. */
export function vNeck(neck = "neck", { reach = OPENING_REACH, depth = 0.1 }: Omit<OpeningShape, "cut"> = {}): RecipeStroke {
  return opening(neck, { cut: "v", reach, depth });
}

/**
 * The strokes of a band `width` wide round an opening, for an area of its own: the opening grown by `width` painted, the opening itself
 * unpainted, so a rib collar follows a crew or V neckline. Put them in an area after the cloth they cut.
 */
export function openingBand(part: string, shape: OpeningShape, width: number): RecipeStroke[] {
  if (width <= 0) throw new Error(`openingBand: a band is wider than 0, not ${width}`);
  const reach = shape.reach ?? OPENING_REACH;
  const outer: OpeningShape = { cut: shape.cut, reach: reach + width, depth: shape.depth ? shape.depth + width * (shape.cut === "v" ? 2 : 1) : 0 };
  return [opening(part, outer, false), opening(part, { ...shape, reach })];
}

/** A rib collar: an area painting a band round a neckline, gathered in snug, in the rib's colour. */
export function ribCollar(id: string, neck: string, shape: OpeningShape, { width = 0.02, gather = 0.004, color }: { width?: number; gather?: number; color?: string } = {}): RecipeArea {
  const area: RecipeArea = { id, name: "Collar", gather, paint: openingBand(neck, shape, width) };
  if (color) area.color = color;
  return area;
}

/**
 * Cloth turned back over itself at the hem of `part`'s cloth: an area over u `from` to `to` (to at the hem) standing out by `fold`, its
 * colour the cloth's back face: a rolled cuff, a rolled jeans hem, a turtleneck's fold-over.
 */
export function foldedBand(id: string, part: string, from: number, to: number, { fold = 0.006, color, name = "Fold", gather }: { fold?: number; color?: string; name?: string; gather?: number } = {}): RecipeArea {
  if (!(from < to)) throw new Error(`foldedBand: from ${from} lies below to ${to}`);
  if (fold < 0 || fold > 0.03) throw new Error(`foldedBand: a fold stands out 0 to 0.03, not ${fold}`);
  const area: RecipeArea = { id, name, fold, paint: [{ on: part, shape: "band", u: [from, to] }] };
  if (color) area.color = color;
  if (gather !== undefined) area.gather = gather;
  return area;
}

/** Vertical rib lines round `part` over u `from` to `to`, `count` of them evenly round, printed in `slot`: a knit band's ribbing. */
export function ribLines(id: string, part: string, from: number, to: number, count: number, slot: string, width = 0.3): RecipeMark[] {
  if (count < 1) throw new Error(`ribLines: at least one line, not ${count}`);
  return Array.from({ length: count }, (_, i) => {
    const v = (i + 0.5) / count;
    return { id: `${id}-${i + 1}`, on: part, slot, points: [[from, v], [to, v]], width: [{ at: 0, scale: width }, { at: 1, scale: width }] };
  });
}

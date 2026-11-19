import type { RecipeMark, RecipeRegion } from "../schema/recipe.ts";

/**
 * A region or mark printed on a garment's cloth over the body part `on` (a pair's side by its own id): its places stay in that part's
 * u and v, or face places on a head, as `uvRegion`, `faceRegion`, `uvMark` and `faceMark` give them.
 */
export function printOn<T extends RecipeRegion | RecipeMark>(on: string, item: T): T {
  return { ...item, on };
}

/** Each of `items` printed over the body part `on`: `regions: printAll("torso", [badge, stripe])`. */
export function printAll<T extends RecipeRegion | RecipeMark>(on: string, items: T[]): T[] {
  return items.map((item) => printOn(on, item));
}

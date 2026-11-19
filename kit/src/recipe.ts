import type { Recipe } from "./schema/recipe.ts";

/** The recipe grammar version the kit writes. */
export const RECIPE_FORMAT = 1;

/**
 * A character recipe, typed against schema/recipe.schema.json. A character file's default export is one:
 * `export default recipe({ id: "mannequin", name: "Mannequin", plan: "blank", parts: [...] })`.
 */
export function recipe(spec: Omit<Recipe, "format"> & { format?: number }): Recipe {
  return { format: RECIPE_FORMAT, ...spec };
}

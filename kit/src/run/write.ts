import { mkdirSync, writeFileSync } from "node:fs";
import { basename, dirname, extname, join, resolve } from "node:path";
import type { Recipe } from "../schema/recipe.ts";

/** The suffix a written recipe takes. */
export const RECIPE_SUFFIX = ".recipe.json";

/** Where a character file's recipe goes: beside it as <name>.recipe.json, or in `outDir` when given. */
export function recipePathFor(scriptPath: string, outDir?: string): string {
  const name = basename(scriptPath, extname(scriptPath));
  return join(outDir ? resolve(outDir) : dirname(resolve(scriptPath)), name + RECIPE_SUFFIX);
}

/** The recipe as the JSON the kit writes: two-space indented, ending in a newline. */
export function recipeJson(recipe: Recipe): string {
  return JSON.stringify(recipe, null, 2) + "\n";
}

/** Writes the recipe to `path`, making its folder, and returns the path. */
export function writeRecipe(recipe: Recipe, path: string): string {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, recipeJson(recipe));
  return path;
}

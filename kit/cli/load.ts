import { registerHooks } from "node:module";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import type { Recipe } from "../src/schema/recipe.ts";
import type { HeightMarks } from "../src/plan/check.ts";
import { kitEntries } from "./specifiers.ts";

let registered = false;

/** Lets a character file anywhere on disk import the kit by its specifier, with no install of its own. */
export function registerKitResolver(): void {
  if (registered) return;
  registered = true;
  registerHooks({
    resolve(specifier, context, next) {
      const target = kitEntries[specifier];
      if (target) return { url: pathToFileURL(target).href, shortCircuit: true };
      return next(specifier, context);
    },
  });
}

/** What a character file gives the runner: its recipe, and the `heights` it exports, if any. */
export interface Character {
  recipe: Recipe;
  heights?: HeightMarks<string>;
}

function isRecipe(value: unknown): value is Recipe {
  return typeof value === "object" && value !== null && "id" in value && "name" in value;
}

/** Runs a character file: its default export is a recipe, or a function that returns one. */
export async function loadCharacter(path: string): Promise<Character> {
  registerKitResolver();
  const module: { default?: unknown; heights?: HeightMarks<string> } = await import(pathToFileURL(resolve(path)).href);
  const exported = typeof module.default === "function" ? await module.default() : module.default;
  if (!isRecipe(exported)) {
    throw new Error(`${path}: its default export is not a recipe; end it with \`export default recipe({ ... })\``);
  }
  return { recipe: exported, heights: module.heights };
}

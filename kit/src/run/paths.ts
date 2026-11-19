import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

/** The kit's folder. */
export const kitDir = dirname(dirname(dirname(fileURLToPath(import.meta.url))));
/** The repository's root, which holds schema/ and tools/. */
export const repoDir = dirname(kitDir);
export const schemaPath = join(repoDir, "schema", "recipe.schema.json");
export const typesPath = join(kitDir, "src", "schema", "recipe.ts");
export const kitIndexPath = join(kitDir, "src", "index.ts");
export const kitSourceDir = join(kitDir, "src");
export const recipeShPath = join(repoDir, "tools", "recipe.sh");
/** The specifier a character file imports the kit by. */
export const KIT_SPECIFIER = "squash-kit";

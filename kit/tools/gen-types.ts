// Writes kit/src/schema/recipe.ts from schema/recipe.schema.json. With --check, writes nothing and exits 1 when the file is stale.
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { schemaPath, typesPath } from "../src/run/paths.ts";
import { generateTypes } from "./schema-to-ts.ts";

export function expectedTypes(): string {
  return generateTypes(JSON.parse(readFileSync(schemaPath, "utf8")));
}

function currentTypes(): string | undefined {
  try {
    return readFileSync(typesPath, "utf8");
  } catch {
    return undefined;
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const expected = expectedTypes();
  if (process.argv.includes("--check")) {
    if (currentTypes() !== expected) {
      console.error("kit/src/schema/recipe.ts is stale: run `node kit/tools/gen-types.ts` (or `./gradlew generateSchemas`)");
      process.exit(1);
    }
  } else {
    writeFileSync(typesPath, expected);
    console.log(typesPath);
  }
}

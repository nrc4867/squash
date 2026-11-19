import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { expectedTypes } from "../tools/gen-types.ts";
import { typesPath } from "../src/run/paths.ts";

test("the checked-in recipe types match schema/recipe.schema.json", () => {
  assert.equal(
    readFileSync(typesPath, "utf8"),
    expectedTypes(),
    "kit/src/schema/recipe.ts is stale: run `node kit/tools/gen-types.ts`",
  );
});

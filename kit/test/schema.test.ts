import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { Ajv2020 } from "ajv/dist/2020.js";
import mannequin from "../examples/mannequin.ts";
import { recipeJson } from "../src/run/write.ts";
import { schemaPath } from "../src/run/paths.ts";

test("the example's recipe JSON validates against the recipe schema", () => {
  const validate = new Ajv2020({ allErrors: true, strict: false }).compile(JSON.parse(readFileSync(schemaPath, "utf8")));
  const ok = validate(JSON.parse(recipeJson(mannequin)));
  assert.ok(ok, JSON.stringify(validate.errors, null, 2));
});

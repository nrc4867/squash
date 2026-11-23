import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { Ajv2020 } from "ajv/dist/2020.js";
import { bothSides, poseKey, recipe } from "squash-kit";
import { recipeJson } from "../src/run/write.ts";
import { schemaPath } from "../src/run/paths.ts";

const raised = bothSides("arm", { joint: "base", lean: 45, mode: "free" });

test("a key drawn as an outline carries its strokes, its pose and its options", () => {
  const key = poseKey("t", {
    pose: raised,
    outline: { front: [{ id: "shoulder", points: [[0.04, 0.72], [0.1, 0.7], [0.2, 0.68]], corners: [1] }] },
    rounds: 2,
    joins: false,
  });
  assert.equal(key.name, "t");
  assert.deepEqual(key.pose, raised);
  assert.deepEqual(key.outline, { front: [{ id: "shoulder", through: [[0.04, 0.72], [0.1, 0.7], [0.2, 0.68]], corners: [1] }], side: [], rounds: 2, joins: false });
  assert.equal(key.changes, undefined);
});

test("a key with an empty outline asks only for the posed body's trace", () => {
  assert.deepEqual(poseKey("t", { pose: raised, outline: {} }).outline, { front: [], side: [] });
});

test("a recipe's keys pass the schema", () => {
  const validate = new Ajv2020({ allErrors: true, strict: false }).compile(JSON.parse(readFileSync(schemaPath, "utf8")));
  const made = recipe({
    id: "toy",
    name: "Toy",
    keys: [
      poseKey("t", { pose: raised, outline: { front: [{ id: "shoulder", points: [[0.04, 0.72], [0.2, 0.68]] }] } }),
      poseKey("bent", { pose: [{ part: "arm-l", joint: "joint.1", degrees: -90 }], changes: [{ part: "arms", widths: { outer: [[0, 1], [0.1, 1.1]] }, blend: 1.2 }] }),
    ],
  });
  assert.ok(validate(JSON.parse(recipeJson(made))), JSON.stringify(validate.errors));
});

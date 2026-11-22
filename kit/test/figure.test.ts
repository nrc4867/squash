import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { Ajv2020 } from "ajv/dist/2020.js";
import { figure, figurePart, recipe, socket, tube, tubePart, type FigureOutline } from "squash-kit";
import { recipeJson } from "../src/run/write.ts";
import { schemaPath } from "../src/run/paths.ts";

const outline: FigureOutline = {
  format: "squash-figure-outline/1",
  front: [{ id: "side", points: [[0.03, 0.9], [0.1, 0.7], [0.1, 0.4]] }, { id: "inner-leg", points: [[0.03, 0.05], [0.02, 0.3], [0, 0.36]], mirrored: true }],
  side: [{ id: "front", points: [[0.05, 0.9], [0.07, 0.5], [0.04, 0.05]], corners: [1] }],
};

test("a tube part drawn with curves goes to the part's tube as drawn", () => {
  const part = tubePart(
    { id: "arm" },
    { rest: "down", length: 0.3, profiles: { front: { through: [[0, 0.03], [0.25, 0.02]] }, back: { through: [[0, 0.03], [0.25, 0.02]] }, outer: { through: [[0, 0.03], [0.1, 0.028], [0.25, 0.02]], corners: [1] } } },
  );
  assert.equal(part.outlines, undefined);
  assert.deepEqual(part.tube?.profiles?.outer, { through: [[0, 0.03], [0.1, 0.028], [0.25, 0.02]], corners: [1] });
  assert.equal(part.tube?.length, 0.3);
  assert.equal(part.rest, "down");
});

test("a tube part takes every profile one way", () => {
  assert.throws(() => tubePart({ id: "arm" }, { rest: "down", length: 0.3, profiles: { front: { through: [[0, 0.03], [0.25, 0.02]] }, back: [0.03, 0.02] } }));
  assert.throws(() => tube({ rest: "down", length: 0.3, profiles: { front: { through: [[0, 0.03], [0.25, 0.02]] } } }));
});

test("a figure carries its strokes, and its parts and a socket's reach validate against the schema", () => {
  const torso = figurePart({ id: "torso", sockets: [socket(1, 0.1, 0.75, { mirrored: true, reach: 0.05 })] }, { rest: "up", length: 0.4, closed: true });
  const legs = figurePart({ id: "legs", on: "torso", socket: "socket.1", pair: {} }, { rest: "down", length: 0.36 });
  const made = figure(outline, { rootHeight: 0.36, pose: [] });
  assert.deepEqual(made.front?.[1], { id: "inner-leg", through: [[0.03, 0.05], [0.02, 0.3], [0, 0.36]], mirrored: true });
  assert.deepEqual(made.side?.[0].corners, [1]);
  assert.deepEqual(torso.tube, { length: 0.4, closed: true });
  const validate = new Ajv2020({ allErrors: true, strict: false }).compile(JSON.parse(readFileSync(schemaPath, "utf8")));
  const ok = validate(JSON.parse(recipeJson(recipe({ id: "fig", name: "Fig", figure: made, parts: [torso, legs] }))));
  assert.ok(ok, JSON.stringify(validate.errors, null, 2));
});

import { test } from "node:test";
import assert from "node:assert/strict";
import { around, orient } from "squash-kit";

test("a direction on a rest needs no turn", () => {
  assert.deepEqual(orient([0, 1, 0]), { rest: "up", lean: 0, outward: 0, roll: 0 });
});

test("forward and out takes outward 45 off forward", () => {
  const o = orient([1, 0, -1]);
  assert.equal(o.rest, "forward");
  assert.equal(o.outward, 45);
  assert.equal(o.lean, 0);
});

test("roll turns the up toward v 0.25: a forward part with its up to the left rolls 90", () => {
  assert.equal(orient([1, 0, 0], [0, 0, -1]).roll, 90);
  assert.equal(orient([1, 0, 0], [0, 0, 1]).roll, -90);
});

test("a direction at exactly 90 degrees from every rest is refused, saying why", () => {
  assert.throws(() => orient([0, 0, 1]), /90 degrees from the nearest rest.*character's right/);
});

test("a left part cannot point straight right", () => {
  assert.throws(() => orient([0, 0, 1]));
});

test("around spaces directions evenly round an axis", () => {
  const petals = around([1, 0, -1], [0, 1, 0], 5);
  assert.equal(petals.length, 5);
  for (const p of petals) assert.ok(Math.abs(p[0] - p[2]) < 1e-9, "square to the axis");
  assert.ok(Math.abs(petals[0][1] - 1) < 1e-9);
});

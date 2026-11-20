import { test } from "node:test";
import assert from "node:assert/strict";
import { bob, curly, lockHair, lockMass, ponytail } from "squash-kit";

test("lockMass seats a smooth mass with no locks or strand lines of its own, sized to the head", () => {
  const { part, headSocket } = lockMass({ on: "head", socket: 7, depth: 0.29, height: 0.377, color: "hair" });
  assert.equal(part.template, "hair-volume");
  assert.equal(part.preset, "ponytail");
  assert.equal(part.size, 1.1667);
  assert.equal(part.params?.strands, 0);
  assert.equal(part.params?.lockLines, 0);
  assert.deepEqual(headSocket, { n: 7, u: 0.55, v: 0.5, sink: 0, outset: 0.03 });
});

test("each preset helper names its preset and passes the head, mass and colours on", () => {
  assert.deepEqual(bob({ on: "head", mass: "hair-mass", color: "hair" }), { on: "head", preset: "bob", mass: "hair-mass", color: "hair" });
  assert.equal(curly({ on: "head" }).preset, "curly");
  assert.equal(ponytail({ on: "head", band: "ribbon" }).band, "ribbon");
});

test("lockHair keeps groups and leaves the colliders on unless told", () => {
  const hair = lockHair(null, { on: "head", groups: [{ id: "fringe", kind: "fringe", count: 9 }] });
  assert.equal(hair.preset, undefined);
  assert.equal(hair.groups?.[0].count, 9);
  assert.equal(hair.colliders, undefined);
  assert.equal(lockHair("bob", { on: "head", colliders: false }).colliders, false);
});

import { test } from "node:test";
import assert from "node:assert/strict";
import { bob, centrePart, cowlick, curly, lockHair, lockMass, longStraight, ponytail, sidePart, whorl } from "squash-kit";

test("lockMass seats a smooth mass with no locks or strand lines of its own, sized to the head", () => {
  const { part, headSocket } = lockMass({ on: "head", socket: 7, depth: 0.29, height: 0.377, color: "hair" });
  assert.equal(part.template, "hair-volume");
  assert.equal(part.preset, "ponytail");
  assert.equal(part.size, 1.1);
  assert.equal(part.params?.strands, 0);
  assert.equal(part.params?.lockLines, 0);
  assert.deepEqual(headSocket, { n: 7, u: 0.62, v: 0.5, sink: 0, outset: 0.02 });
});

test("each preset helper names its preset and passes the head, mass and colours on", () => {
  assert.deepEqual(bob({ on: "head", mass: "hair-mass", color: "hair" }), { on: "head", preset: "bob", mass: "hair-mass", color: "hair" });
  assert.equal(curly({ on: "head" }).preset, "curly");
  assert.deepEqual(curly({ on: "head", tuft: true }).groups?.filter((g) => g.removed).map((g) => g.id), ["back", "left", "right", "fringe"]);
  assert.equal(curly({ on: "head" }).groups, undefined);
  assert.equal(ponytail({ on: "head", band: "ribbon" }).band, "ribbon");
});

test("lockHair keeps groups and leaves the colliders on unless told", () => {
  const hair = lockHair(null, { on: "head", groups: [{ id: "fringe", kind: "fringe", count: 9 }] });
  assert.equal(hair.preset, undefined);
  assert.equal(hair.groups?.[0].count, 9);
  assert.equal(hair.colliders, undefined);
  assert.equal(lockHair("bob", { on: "head", colliders: false }).colliders, false);
});

test("lockMass with no sizes asks the build to fit the mass to the head, its params over the fit", () => {
  assert.deepEqual(lockMass(), {});
  assert.deepEqual(lockMass({ color: "hair", params: { back: 0.6 } }), { color: "hair", params: { back: 0.6 } });
  const hair = bob({ on: "head", mass: lockMass({ id: "cap" }) });
  assert.deepEqual(hair.fitMass, { id: "cap" });
  assert.equal(hair.mass, undefined);
  assert.equal(bob({ on: "head", mass: "hair-mass" }).mass, "hair-mass");
});

test("the flow helpers write a recipe's flow: partings, a whorl and cowlicks", () => {
  assert.deepEqual(centrePart(), { kind: "centre" });
  assert.deepEqual(sidePart("left", 0.5), { kind: "side", at: -0.5 });
  assert.deepEqual(sidePart("right"), { kind: "side", at: 0.6 });
  const flow = { part: sidePart("right", 0.7, { clean: 0.6 }), whorl: whorl({ around: 170, turn: -0.4 }), cowlicks: [cowlick({ around: 15, up: 48, lift: 25 })] };
  const hair = longStraight({ on: "head", flow });
  assert.deepEqual(hair.flow, flow);
  assert.equal(bob({ on: "head" }).flow, undefined);
});

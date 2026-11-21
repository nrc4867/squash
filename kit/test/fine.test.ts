import { test } from "node:test";
import assert from "node:assert/strict";
import { bikiniTop, exactBox, exactLasso, pointedCollar, strap, tie } from "squash-kit";

test("exact strokes are kept as shapes: a band, a box and a lasso", () => {
  assert.deepEqual(exactBox("torso", [0.4, 1]), { on: "torso", shape: "band", exact: true, u: [0.4, 1] });
  assert.deepEqual(exactBox("torso", [0.4, 0.6], [0.1, 0.2], { mirrored: true }), { on: "torso", shape: "box", exact: true, u: [0.4, 0.6], v: [0.1, 0.2], mirrored: true });
  assert.deepEqual(exactLasso("torso", [[0.5, 0], [0.6, 0.1], [0.6, -0.1]], { curved: true, corners: [0] }), {
    on: "torso", shape: "lasso", exact: true, points: [[0.5, 0], [0.6, 0.1], [0.6, -0.1]], curved: true, corners: [0],
  });
  assert.throws(() => exactLasso("torso", [[0.5, 0], [0.6, 0.1]]));
  assert.throws(() => exactLasso("torso", [[0.5, 0], [0.6, 0.1], [0.6, -0.1]], { corners: [3] }));
});

test("a strap is an exact box centred round its place, however narrow", () => {
  assert.deepEqual(strap("torso", 0.8, 0.99, 0.125, 0.015625), { on: "torso", shape: "box", exact: true, u: [0.8, 0.99], v: [0.1171875, 0.1328125] });
  assert.throws(() => strap("torso", 0.8, 0.99, 0.1, 0));
});

test("a tie is one curved exact shape from its knot to a sharp point on the front middle", () => {
  const made = tie({ color: "red", top: 0.98, tip: 0.6 });
  assert.equal(made.slot, "neck");
  const [stroke] = made.areas?.[0].paint ?? [];
  assert.equal(stroke.exact, true);
  assert.equal(stroke.curved, true);
  const tipAt = stroke.points?.findIndex(([u, v]) => u === 0.6 && v === 0) ?? -1;
  assert.ok(stroke.corners?.includes(tipAt));
  assert.throws(() => tie({ color: "red", top: 0.6, tip: 0.9 }));
});

test("a pointed collar mirrors its points and stands up the neck", () => {
  const collar = pointedCollar({ top: 0.97, drop: 0.08, color: "white" });
  assert.deepEqual(collar.paint?.map((s) => [s.on, s.shape, s.mirrored ?? false]), [["torso", "lasso", true], ["neck", "band", false]]);
  assert.ok(collar.paint?.every((s) => s.exact));
  assert.throws(() => pointedCollar({ top: 0.97, drop: 0 }));
});

test("a bikini top has two mirrored cups, a back band and straps, all exact", () => {
  const top = bikiniTop({ color: "swim", cups: [0.7, 0.84], cupV: [0.01, 0.14] });
  const paint = top.areas?.[0].paint ?? [];
  assert.deepEqual(paint.map((s) => s.shape), ["ellipse", "box", "box"]);
  assert.ok(paint.every((s) => s.exact && s.mirrored));
  assert.equal(bikiniTop({ color: "swim", cups: [0.7, 0.84], cupV: [0.01, 0.14], strapWidth: 0 }).areas?.[0].paint?.length, 2);
  assert.throws(() => bikiniTop({ color: "swim", cups: [0.84, 0.7], cupV: [0.01, 0.14] }));
});

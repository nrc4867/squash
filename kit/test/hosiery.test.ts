import { test } from "node:test";
import assert from "node:assert/strict";
import { fishnetStockings, sheerStockings } from "squash-kit";

test("sheer stockings are a sheer leg and foot under an opaque band and a denser toe", () => {
  const stockings = sheerStockings({ color: "nylon", top: 0.3, band: 0.06, bandColor: "lace", toe: 0.2, opacity: 0.3, fit: 0.003 });
  assert.equal(stockings.color, "nylon");
  assert.equal(stockings.fit, 0.003);
  const [leg, band, toe] = stockings.areas ?? [];
  assert.equal(leg.opacity, 0.3);
  assert.deepEqual(leg.paint, [{ on: "leg-l", shape: "band", u: [0.3, 1] }, { on: "foot-l", shape: "band", u: [0, 1] }]);
  assert.equal(band.opacity, undefined);
  assert.equal(band.color, "lace");
  assert.deepEqual(band.paint, [{ on: "leg-l", shape: "band", u: [0.3, 0.36] }]);
  assert.equal(toe.color, "lace");
  assert.deepEqual(toe.paint, [{ on: "foot-l", shape: "band", u: [0.8, 1] }]);
  assert.ok(Math.abs((toe.opacity ?? 1) - 0.72) < 1e-9, "the toe is reinforced, denser than the leg");
  assert.equal("top" in stockings, false, "stocking fields stay off the garment");
  assert.equal("toeOpacity" in stockings, false);
  const whole = sheerStockings({ color: "nylon", top: 0.3, toe: 0.2, toeOpacity: 1 }).areas ?? [];
  assert.equal(whole[2].opacity, undefined, "a toe as dense as whole is whole");
});

test("fishnets are a diamond net unless told otherwise, with no band when it is 0", () => {
  const fishnets = fishnetStockings({ color: "black", top: 0.25, band: 0, net: { hole: 0.04 } });
  assert.equal(fishnets.areas?.length, 1);
  assert.deepEqual(fishnets.areas?.[0].net, { pattern: "diamond", hole: 0.04, thread: 0.004, angle: 40 });
  const mesh = fishnetStockings({ color: "black", top: 0.25, net: { pattern: "square", angle: 0 } });
  assert.equal(mesh.areas?.[0].net?.pattern, "square");
  assert.equal(mesh.areas?.[1].id, "band");
  assert.equal("net" in mesh, false);
});

import { test } from "node:test";
import assert from "node:assert/strict";
import { garment, printAll, printOn, teardrop, uvMark, uvRegion } from "squash-kit";

test("a print names the body part under the cloth and keeps its places in that part's u and v", () => {
  const drop = printOn("torso", uvRegion("drop", "red", teardrop({ length: 0.1, width: 0.04 }), { ink: true, mirrored: true }));
  assert.equal(drop.on, "torso");
  assert.equal(drop.slot, "red");
  assert.equal(drop.ink, true);
  assert.equal(drop.mirrored, true);
  assert.deepEqual(drop.corners, [4]);
  assert.equal(drop.border?.length, 8);
});

test("printAll prints every item over one part, leaving the items given as they were", () => {
  const stitch = uvMark("stitch", [[0.2, 0.4], [0.3, 0.4]], { width: [{ at: 0, scale: 0.4 }, { at: 1, scale: 0.4 }] });
  const [printed] = printAll("leg-l", [stitch]);
  assert.equal(printed.on, "leg-l");
  assert.equal(stitch.on, undefined);
  const tee = garment({ id: "tee", marks: [printed] });
  assert.equal(tee.marks?.[0].on, "leg-l");
});

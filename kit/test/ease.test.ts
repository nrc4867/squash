import { test } from "node:test";
import assert from "node:assert/strict";
import { ease, garment } from "squash-kit";

test("ease keeps its keys as a garment's profile, front and back", () => {
  const sleeve = ease([[0, 0.006], [0.7, 0.02], [1, 0.03, 0.04]]);
  assert.deepEqual(sleeve, [[0, 0.006], [0.7, 0.02], [1, 0.03, 0.04]]);
  const tee = garment({ id: "tee", eases: { "arm-l": sleeve }, hang: { limit: 0.02 }, drops: { "arm-l": 0.12 } });
  assert.deepEqual(tee.eases?.["arm-l"], sleeve);
});

test("ease refuses keys out of order and a negative ease", () => {
  assert.throws(() => ease([]));
  assert.throws(() => ease([[0.5, 0.01], [0.2, 0.02]]));
  assert.throws(() => ease([[0, -0.01]]));
});

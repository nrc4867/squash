import { test } from "node:test";
import assert from "node:assert/strict";
import { MOUTH_SHAPES, mouth, sayShape, toothSlot } from "../src/index.ts";

test("mouth rests closed, fills its roles and flags only its own marks over", () => {
  const group = mouth({
    lip: ["lip"],
    lips: "lip",
    inner: "mouth",
    tongue: "tongue",
    teeth: "teeth",
    teethMarks: ["fang"],
    over: ["fang", "stray"],
    teethInk: true,
  });
  assert.equal(group.id, "mouth");
  assert.deepEqual(group.controls, { open: 0 });
  const role = (name: string) => group.roles.find((r) => r.role === name);
  assert.deepEqual(role("teeth")?.over, ["fang"]);
  assert.equal(role("teeth")?.ink, true);
  assert.equal(role("tongue")?.over, undefined);
  assert.equal(role("lipLine")?.fill, "lip");
  assert.deepEqual(role("lipLine")?.marks, ["lip"]);
  assert.equal(group.style, undefined);
});

test("a mouth names its style and overrides a shape, and sayShape writes the build option", () => {
  const group = mouth({ style: "long-muzzle", shapes: { o: { round: 1 } } });
  assert.equal(group.style, "long-muzzle");
  assert.deepEqual(group.shapes, { o: { round: 1 } });
  assert.equal(sayShape("o"), "set=head:mouth.shape:o");
  assert.equal(MOUTH_SHAPES.length, 9);
});

test("a mouth starts its teeth from a preset and keeps only the slot changes it makes", () => {
  const group = mouth({ teeth: "teeth", dentition: { preset: "cat", upper: { slots: [toothSlot(3, { over: true, length: 0.26 })] } }, tongueLevel: "none" });
  assert.deepEqual(group.teeth, { preset: "cat", upper: { slots: [{ row: 0, slot: 3, over: true, length: 0.26 }] } });
  assert.equal(group.tongueLevel, "none");
  assert.equal(mouth({ teeth: "teeth" }).teeth, undefined, "the fill slot names no dentition");
});

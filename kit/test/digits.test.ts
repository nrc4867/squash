import { test } from "node:test";
import assert from "node:assert/strict";
import { digitCurl, digitPatch, socket } from "squash-kit";

test("digitCurl poses every joint of the numbered digits and the thumb by name", () => {
  const poses = digitCurl("hand-l", { count: 3, curl: [-8, -12], each: (i) => -i, thumb: [6] });
  assert.deepEqual(
    poses.map((p) => `${p.joint} ${p.degrees}`),
    ["fingers:0.1 -8", "fingers:0.2 -12", "fingers:1.1 -9", "fingers:1.2 -13", "fingers:2.1 -10", "fingers:2.2 -14", "fingers:thumb.1 6"],
  );
  assert.ok(poses.every((p) => p.part === "hand-l"));
});

test("a socket names the digit it sits on by its patch", () => {
  assert.equal(digitPatch(2), "fingers:2");
  assert.equal(digitPatch(0, "toes"), "toes:0");
  assert.equal(socket(1, 0.8, 0.25, { patch: digitPatch(2) }).patch, "fingers:2");
});

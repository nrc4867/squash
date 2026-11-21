import { test } from "node:test";
import assert from "node:assert/strict";
import { closedLids, eyeshadow, faceEye, lidsAt, linerWing } from "../src/index.ts";

test("closedLids arches a smile up and sags a frown, and keeps the lids' own fields", () => {
  const smile = closedLids("smile");
  const frown = closedLids("frown", 6, { slot: "lid" });
  assert.ok((smile.closedBend ?? 0) > 0);
  assert.ok((frown.closedBend ?? 0) < 0);
  assert.equal(frown.closedTilt, 6);
  assert.equal(frown.slot, "lid");
  assert.equal(closedLids().closedBend, 0);
});

test("eyeshadow paints the lid and fades it, and a liner wing starts at the back corner", () => {
  const lids = eyeshadow("shadow", { fadeTo: "lid", fade: 1.2 });
  assert.equal(lids.slot, "shadow");
  assert.equal(lids.fadeTo, "lid");
  assert.equal(lids.fade, 1.2);
  assert.equal(eyeshadow("shadow").fadeTo, undefined);
  const wing = linerWing({ up: 0.5 });
  assert.equal(wing.along, -1);
  assert.equal(wing.up, 0.5);
});

test("lidsAt stands an eye's lids", () => {
  const eye = lidsAt(faceEye("eyes", { on: "head", face: [0.05, 0.1] }), { upper: 0.5 });
  assert.equal(eye.upperClose, 0.5);
  assert.equal(eye.lowerRise, 0);
});

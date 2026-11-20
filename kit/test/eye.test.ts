import { test } from "node:test";
import assert from "node:assert/strict";
import { animeEye } from "../src/index.ts";

test("animeEye turns every part of the detail on and keeps the look's own fields", () => {
  const look = animeEye({ width: 0.05, iris: "iris" });
  assert.equal(look.width, 0.05);
  assert.equal(look.iris, "iris");
  assert.equal(look.lashes, 0);
  assert.ok((look.lidMass?.weight ?? 0) > 0);
  assert.ok((look.lashClumps?.count ?? 0) > 0);
  assert.ok((look.irisShade?.upper ?? 0) > 0);
  assert.ok((look.lidShadow?.depth ?? 0) > 0);
});

test("animeEye's detail sets each part, 0 turning it off, and the look's own groups win", () => {
  const look = animeEye(
    { lashes: 2, irisShade: { upperSlot: "deep" }, lidShadow: { slot: "shade" } },
    { clumps: 0, lower: 0, lidShadow: 0.3 },
  );
  assert.equal(look.lashes, 2);
  assert.equal(look.lashClumps?.count, 0);
  assert.equal(look.lidMass?.lower, 0);
  assert.equal(look.irisShade?.upperSlot, "deep");
  assert.equal(look.lidShadow?.depth, 0.3);
  assert.equal(look.lidShadow?.slot, "shade");
});

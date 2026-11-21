import { test } from "node:test";
import assert from "node:assert/strict";
import { beanie, glove, hoofBoot, peakedCap } from "squash-kit";

test("a beanie paints a band and the knit inside it, the ears coming through it", () => {
  const hat = beanie({ color: "knit", bandColor: "rib", brow: 0.7, earHoles: ["ear-l"] });
  assert.equal(hat.slot, "head");
  const [band, knit] = hat.areas ?? [];
  assert.equal(band?.id, "band");
  assert.equal(band?.color, "rib");
  assert.ok((band?.fold ?? 0) > 0);
  assert.equal(knit?.paint?.length, 1);
  const opened = beanie({ color: "knit", brow: 0.7, earHoles: ["ear-l"], earHole: { reach: 0.01 } }).areas?.at(-1)?.paint?.at(-1);
  assert.deepEqual(opened, { on: "ear-l", shape: "opening", reach: 0.01, unpaint: true });
  assert.ok(knit?.paint?.every((stroke) => stroke.shape !== "lasso" || stroke.mirrored));
  assert.deepEqual(hat.holes, [{ part: "ear-l", cuff: false }]);
  assert.throws(() => beanie({ color: "knit", brow: 0.05 }));
});

test("a beanie with no ear holes and no band is one area of knit", () => {
  const hat = beanie({ color: "knit", brow: 0.6, band: 0 });
  assert.equal(hat.areas?.length, 1);
  assert.equal(hat.holes, undefined);
});

test("a peaked cap owns a peak on the head's socket at its brow", () => {
  const { garment, peak } = peakedCap({ color: "cap", brow: 0.6, peakSocket: 20, peak: 0.16, peakWidth: 0.26 });
  assert.equal(garment.id, "cap");
  assert.deepEqual(garment.areas?.map((area) => area.id), ["band", "knit"]);
  assert.equal(peak.garment, "cap");
  assert.equal(peak.socket, "socket.20");
  assert.equal(peak.on, "head");
});

test("a glove covers the whole hand with fingers shaped, a mitten tucks them", () => {
  const gloves = glove({ color: "wool", cuffColor: "rib", cuff: 0.14, fold: 0.004 });
  assert.deepEqual(gloves.tucks, [{ part: "hand-l", kind: "fingers", tucked: false }]);
  assert.deepEqual(gloves.areas?.[0]?.paint?.[0], { on: "hand-l", shape: "band", u: [0, 1] });
  assert.equal(gloves.areas?.[1]?.fold, 0.004);
  const mittens = glove({ color: "wool", digits: "mitten", cuff: 0 });
  assert.equal(mittens.id, "mitten");
  assert.deepEqual(mittens.tucks, [{ part: "hand-l", kind: "fingers", tucked: true }]);
  assert.equal(mittens.areas?.length, 1);
});

test("a hoof boot covers the hoof whole and a shaft up the leg with a band at its top", () => {
  const boot = hoofBoot({ color: "leather", topColor: "trim", shaft: 0.2 });
  assert.equal(boot.slot, "feet");
  const [body, top] = boot.areas ?? [];
  assert.deepEqual(body?.paint?.map((stroke) => stroke.on), ["hoof-l", "leg-l"]);
  assert.equal(top?.color, "trim");
  assert.equal(hoofBoot({ color: "leather", shaft: 0 }).areas?.length, 1);
});

test("a mitten stands further off the hand than a glove", () => {
  const gloves = glove({ color: "wool" });
  const mittens = glove({ color: "wool", digits: "mitten" });
  assert.ok((mittens.fit ?? 0) > (gloves.fit ?? 0));
});

test("a beanie covers the back of the skull from its back, lower than its sides", () => {
  const knit = beanie({ color: "knit", brow: 0.6, sides: 0.3, band: 0 }).areas?.[0]?.paint?.[0];
  assert.ok(knit?.shape === "lasso");
  const points = knit.points as [number, number][];
  assert.deepEqual(points[0], [0, 0]);
  assert.ok(points[1]![1] > 0.3);
});

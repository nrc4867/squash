import { test } from "node:test";
import assert from "node:assert/strict";
import { crewNeck, crewSweater, cuffedJeans, foldedBand, opening, openingBand, ribLines, turtleneck, vNeck } from "squash-kit";

test("an opening unpaints round where a part comes out, its cut and depth kept", () => {
  assert.deepEqual(opening("ear-l"), { on: "ear-l", shape: "opening", unpaint: true });
  assert.deepEqual(crewNeck("neck", { depth: 0.03 }), { on: "neck", shape: "opening", reach: 0.01, depth: 0.03, unpaint: true });
  assert.deepEqual(vNeck(), { on: "neck", shape: "opening", cut: "v", reach: 0.01, depth: 0.1, unpaint: true });
});

test("a band round an opening paints the wider opening and unpaints the opening", () => {
  const [outer, inner] = openingBand("neck", { cut: "v", reach: 0.01, depth: 0.08 }, 0.02);
  assert.deepEqual(outer, { on: "neck", shape: "opening", cut: "v", reach: 0.03, depth: 0.12 });
  assert.deepEqual(inner, { on: "neck", shape: "opening", cut: "v", reach: 0.01, depth: 0.08, unpaint: true });
  assert.throws(() => openingBand("neck", {}, 0));
});

test("a folded band is an area standing out over its rows, in the back face's colour", () => {
  assert.deepEqual(foldedBand("cuff", "leg-l", 0.8, 0.87, { fold: 0.007, color: "denim-light" }), {
    id: "cuff", name: "Fold", fold: 0.007, color: "denim-light", paint: [{ on: "leg-l", shape: "band", u: [0.8, 0.87] }],
  });
  assert.throws(() => foldedBand("cuff", "leg-l", 0.9, 0.8));
  assert.throws(() => foldedBand("cuff", "leg-l", 0.8, 0.9, { fold: 0.05 }));
});

test("a crew sweater has a body and a rib area with hem, cuff and collar band", () => {
  const sweater = crewSweater({ color: "knit", rib: "rib", hem: 0.45 });
  assert.equal(sweater.slot, "top");
  const [body, rib] = sweater.areas ?? [];
  assert.deepEqual(body.paint?.map((s) => s.on), ["torso", "arm-l", "neck"]);
  assert.equal(rib.color, "rib");
  assert.equal(rib.gather, 0.004);
  assert.deepEqual(rib.paint?.map((s) => s.shape), ["band", "band", "opening", "opening"]);
  const vneck = crewSweater({ color: "knit", hem: 0.45, neckline: { cut: "v", depth: 0.09 } });
  assert.equal(vneck.areas?.[1].paint?.[2].cut, "v");
});

test("cuffed jeans fold their hem back in the cuff's colour", () => {
  const jeans = cuffedJeans({ color: "denim", cuffColor: "denim-light", waist: 0.45, hem: 0.95, cuff: 0.08 });
  const cuff = jeans.areas?.[1];
  assert.equal(cuff?.fold, 0.006);
  assert.equal(cuff?.color, "denim-light");
  assert.deepEqual(cuff?.paint?.[0].u, [0.95 - 0.08, 0.95]);
});

test("a turtleneck folds its top back over the collar", () => {
  const [collar, fold] = turtleneck({ top: 0.7, color: "rib" });
  assert.deepEqual(collar.paint?.[0].u, [0, 0.7]);
  assert.equal(fold.fold, 0.006);
  assert.deepEqual(fold.paint?.[0].u, [0.7 - 0.25, 0.7]);
});

test("rib lines run along a band, evenly round", () => {
  const lines = ribLines("rib", "torso", 0.4, 0.45, 4, "rib");
  assert.equal(lines.length, 4);
  assert.deepEqual(lines[1].points, [[0.4, 0.375], [0.45, 0.375]]);
  assert.equal(lines[0].on, "torso");
});

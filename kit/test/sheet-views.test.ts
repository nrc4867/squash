// Splitting synthetic sheets into views: scales, grids, clutter, detached parts, shadows and backgrounds.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readSheet, setPixel, type Box, type Pixel } from "squash-kit/sheet";
import { ellipse, figure, PAPER, rect, sheet } from "./sheet/figures.ts";

/** Whether `inner` lies within `outer` give or take `slack` pixels. */
function within(inner: Box, outer: Box, slack = 2): boolean {
  return (
    inner.x >= outer.x - slack &&
    inner.y >= outer.y - slack &&
    inner.x + inner.w <= outer.x + outer.w + slack &&
    inner.y + inner.h <= outer.y + outer.h + slack
  );
}

test("figures at different scales in a row are separate views, left to right, each its own height", () => {
  const image = sheet(520, 260);
  figure(image, 360, 60, 120);
  figure(image, 90, 20, 200);
  figure(image, 460, 40, 160);
  const { views, other } = readSheet(image, { names: ["front", "small", "side"] });
  assert.deepEqual(views.map((v) => v.name), ["front", "small", "side"]);
  assert.deepEqual(views.map((v) => v.pixelHeight), [200, 120, 160]);
  assert.ok(views[0].box.x < views[1].box.x && views[1].box.x < views[2].box.x);
  assert.equal(other.length, 0);
});

test("a 2 by 2 grid of figures reads row by row", () => {
  const image = sheet(400, 400);
  figure(image, 300, 220, 150);
  figure(image, 100, 20, 150);
  figure(image, 100, 230, 140);
  figure(image, 300, 30, 140);
  const tops = readSheet(image).views.map((v) => [v.box.x < 200 ? "left" : "right", v.box.y < 200 ? "top" : "bottom"].join(" "));
  assert.deepEqual(tops, ["left top", "right top", "left bottom", "right bottom"]);
});

test("a text label beside a figure is other, and the figure's box leaves it out", () => {
  const image = sheet(300, 240);
  figure(image, 90, 20, 200);
  for (let letter = 0; letter < 5; letter++) rect(image, 190 + letter * 12, 110, 198 + letter * 12, 122, [40, 40, 40]);
  const { views, other } = readSheet(image);
  assert.equal(views.length, 1);
  assert.ok(views[0].box.x + views[0].box.w < 190);
  assert.equal(other.length, 1);
  assert.ok(within({ x: 190, y: 110, w: 56, h: 12 }, other[0]));
});

test("a part parted from the figure by a thin gap stays with it", () => {
  const image = sheet(300, 240);
  figure(image, 100, 20, 200);
  ellipse(image, 150, 170, 22, 8, [90, 150, 210]);
  const { views } = readSheet(image);
  assert.equal(views.length, 1);
  assert.ok(views[0].box.x + views[0].box.w >= 171);
});

test("a cast shadow under the feet is split off: the view stands on its feet and reports the shadow", () => {
  const image = sheet(240, 260);
  ellipse(image, 100, 220, 60, 9, [212, 204, 196]);
  figure(image, 100, 20, 200);
  const [view] = readSheet(image).views;
  assert.equal(view.box.y + view.box.h, 220);
  assert.ok(view.shadow);
  assert.ok(view.shadow.w > 100);
});

test("a gradient background is keyed without splitting or swallowing the figures", () => {
  const image = sheet(360, 240);
  for (let y = 0; y < image.height; y++) {
    for (let x = 0; x < image.width; x++) {
      const t = (x + y) / (image.width + image.height);
      const ground: Pixel = [Math.round(250 - 40 * t), Math.round(248 - 30 * t), Math.round(240 - 10 * t)];
      setPixel(image, x, y, ground);
    }
  }
  figure(image, 90, 20, 200);
  figure(image, 260, 30, 180);
  const { views, other } = readSheet(image);
  assert.deepEqual(views.map((v) => v.pixelHeight), [200, 180]);
  assert.equal(other.length, 0);
});

test("a thin ground stroke touching two figures does not join them", () => {
  const image = sheet(400, 260);
  figure(image, 100, 20, 200);
  figure(image, 300, 20, 200);
  rect(image, 60, 219, 340, 220, [40, 40, 40]);
  assert.equal(readSheet(image).views.length, 2);
});

test("given boxes are the views, in their order, and ground= sets the background", () => {
  const image = sheet(400, 240, [20, 20, 20]);
  figure(image, 100, 20, 200, PAPER);
  figure(image, 300, 20, 200, PAPER);
  const { views } = readSheet(image, { ground: "#141414", boxes: [{ x: 200, y: 0, w: 200, h: 240 }, { x: 0, y: 0, w: 200, h: 240 }] });
  assert.equal(views.length, 2);
  assert.ok(views[0].box.x > 200 && views[1].box.x < 200);
});

test("a symmetric figure reads as front, and a lopsided one as side", () => {
  const image = sheet(400, 240);
  figure(image, 100, 20, 200);
  ellipse(image, 310, 60, 32, 32, [90, 150, 210]);
  ellipse(image, 360, 72, 28, 12, [90, 150, 210]);
  rect(image, 250, 90, 300, 220, [90, 150, 210]);
  rect(image, 300, 200, 340, 220, [90, 150, 210]);
  ellipse(image, 92, 52, 6, 6, [20, 20, 20]);
  ellipse(image, 108, 52, 6, 6, [20, 20, 20]);
  const [front, side] = readSheet(image).views;
  assert.equal(front.guess.facing, "front");
  assert.equal(side.guess.facing, "side");
});

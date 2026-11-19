import { test } from "node:test";
import assert from "node:assert/strict";
import {
  band,
  cover,
  furLock,
  leaf,
  mirroredLoop,
  oval,
  rectangle,
  roundCorners,
  roundStations,
  tube,
  tuft,
  type Point,
} from "squash-kit";

test("a tube resting down takes front on Side's +y and outer on Top's -y", () => {
  const outlines = tube({
    rest: "down",
    length: 1,
    at: [0, 0.5],
    profiles: { front: [0.3, 0.2], back: [0.1, 0.1], outer: [0.05, 0.05], inner: [0.07, 0.07] },
  });
  assert.deepEqual(outlines.side.through, [[0, 0.3], [0.5, 0.2], [1, 0], [0.5, -0.1], [0, -0.1]]);
  assert.deepEqual(outlines.top.through, [[0, 0.07], [0.5, 0.07], [1, 0], [0.5, -0.05], [0, -0.05]]);
});

test("a tube resting forward takes up and down in Side, and mirrors a lone Top side", () => {
  const outlines = tube({ rest: "forward", length: 1, at: [0], tip: 0, profiles: { up: [0.2], down: [0.1], left: [0.3] } });
  assert.deepEqual(outlines.side.through, [[0, 0.2], [1, 0], [0, -0.1]]);
  assert.deepEqual(outlines.top.through, [[0, 0.3], [1, 0], [0, -0.3]]);
});

test("a tube missing a Side profile says which", () => {
  assert.throws(() => tube({ rest: "up", length: 1, profiles: { front: [0.1], left: [0.1] } }), /back/);
});

test("a closed tube passes through each end on the axis once", () => {
  const outlines = tube({ rest: "up", length: 1, closed: true, at: [0.2, 0.8], profiles: { front: [0.1, 0.1], back: [0.2, 0.2], left: [0.1, 0.1] } });
  assert.equal(outlines.side.closed, true);
  assert.deepEqual(outlines.side.through, [[0, 0], [0.2, 0.1], [0.8, 0.1], [1, 0], [0.8, -0.2], [0.2, -0.2]]);
});

test("a mirrored loop shares its points on the axis", () => {
  const loop = mirroredLoop([[0, 0], [0.5, 0.2], [1, 0]]);
  assert.deepEqual(loop.points, [[0, 0], [0.5, 0.2], [1, 0], [0.5, -0.2]]);
});

test("a leaf's tip is its one corner", () => {
  const drawn = leaf({ length: 0.3, width: 0.1 });
  assert.deepEqual(drawn.corners, [3]);
  assert.deepEqual(drawn.points[3], [0.3, 0]);
});

test("rounding a rectangle's corners doubles its places and leaves none sharp", () => {
  const rounded = roundCorners(rectangle([0, 0], [1, 1]), 0.1);
  assert.equal(rounded.points.length, 8);
  assert.deepEqual(rounded.corners, []);
});

test("a tuft of three spikes has three tips and two notches, all corners", () => {
  const drawn = tuft({ spikes: 3, length: 0.2, width: 0.05 });
  assert.equal(drawn.points.length, 7);
  assert.deepEqual(drawn.corners, [1, 2, 3, 4, 5]);
});

test("a fur lock's curl moves Side's middle and leaves Top centred", () => {
  const lock = furLock({ length: 1, width: 0.1, curl: 0.3 });
  const tip = (points: Point[]) => points.find(([x]) => x === 1)!;
  assert.equal(tip(lock.side.points)[1], 0.3);
  assert.equal(tip(lock.top.points)[1], 0);
});

test("an oval and round stations", () => {
  assert.equal(oval({ rx: 1, points: 6 }).points.length, 6);
  const stations = roundStations(1, 5);
  assert.ok(Math.abs(stations.at[2] - 0.5) < 1e-9);
  assert.equal(stations.radii(2)[2], 2);
});

test("covers land on chart cell edges, and a band keeps two rows", () => {
  assert.deepEqual(cover("torso", { from: 0.101, to: 0.96 }), { part: "torso", from: 0.09375, to: 0.953125 });
  const belt = band("torso", 0.5, 0.001);
  assert.ok(belt.to! - belt.from! >= 2 / 64 - 1e-9);
});

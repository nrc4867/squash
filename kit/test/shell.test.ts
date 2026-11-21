import { test } from "node:test";
import assert from "node:assert/strict";
import { bootShaft, kneeBand, limbShell, radiusAt, type Limb } from "squash-kit";

const leg: Limb = {
  id: "legs",
  length: 1,
  front: [[0, 0.08], [0.5, 0.06], [1, 0.04]],
  back: [[0, 0.09], [0.5, 0.07], [1, 0.05]],
  side: [[0, 0.085], [0.5, 0.065], [1, 0.045]],
};

test("radiusAt eases between places and holds past the ends", () => {
  assert.ok(Math.abs(radiusAt(leg.front, 0.25) - 0.07) < 1e-9);
  assert.equal(radiusAt(leg.front, -1), 0.08);
  assert.equal(radiusAt(leg.front, 2), 0.04);
});

test("a limb shell stands its ease off the limb and tucks both ends inside it", () => {
  const { part, socket } = limbShell({ id: "shell", limb: leg, socket: 4, garment: "g", color: "c", from: 0.25, to: 0.75, ease: 0.01, pair: true });
  assert.equal(part.on, "legs");
  assert.equal(part.socket, "socket.4");
  assert.equal(part.garment, "g");
  assert.deepEqual(socket, { n: 4, u: 0.25, v: 0, sink: 0 });
  const top = part.outlines?.top?.through ?? [];
  assert.ok(Math.abs(top[0][1] - (radiusAt(leg.side, 0.25) - 0.01)) < 1e-4);
  const middle = top.find(([x]) => Math.abs(x - 0.25) < 1e-6);
  assert.ok(middle && Math.abs(middle[1] - (radiusAt(leg.side, 0.5) + 0.01)) < 1e-4, "the middle stands the whole ease off");
  const edge = top.find(([x]) => Math.abs(x - 0.008) < 1e-6);
  assert.ok(edge && edge[1] - radiusAt(leg.side, 0.258) < 0.007, "the edge hugs the limb");
  const side = part.outlines?.side?.through ?? [];
  assert.equal(side[0][1], -0.01);
  assert.equal(part.bind, undefined);
});

test("a boot shaft is half stiff and follows, and a knee band centres on its place", () => {
  const shaft = bootShaft({ id: "shaft", limb: leg, socket: 5, garment: "g", color: "c", from: 0.5 }).part;
  assert.equal(shaft.stiff, 0.5);
  assert.equal(shaft.bind, "follow");
  const band = kneeBand({ id: "band", limb: leg, socket: 6, garment: "g", color: "c", at: 0.5, width: 0.1, bind: "follow" });
  assert.equal(band.socket.u, 0.45);
  assert.equal(band.part.bind, "follow");
});

test("a limb shell refuses a span that runs backwards and a negative ease", () => {
  assert.throws(() => limbShell({ id: "s", limb: leg, socket: 1, garment: "g", color: "c", from: 0.5, to: 0.4, ease: 0.01 }));
  assert.throws(() => limbShell({ id: "s", limb: leg, socket: 1, garment: "g", color: "c", from: 0.1, to: 0.4, ease: -0.01 }));
});

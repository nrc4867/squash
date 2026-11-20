import { test } from "node:test";
import assert from "node:assert/strict";
import { crown, fall, fringe, hairClump, hairTuft, hairVolume } from "squash-kit";

test("hairVolume sizes the volume to the head's depth and seats it at the back of the skull", () => {
  const { part, headSocket } = hairVolume({ on: "head", socket: 11, depth: 0.26, height: 0.3, thickness: 0.02, preset: "bob", hem: "dark", shine: "light" });
  assert.equal(part.template, "hair-volume");
  assert.equal(part.size, 1);
  assert.equal(part.preset, "bob");
  assert.deepEqual(part.areas, { hem: "dark", shine: "light" });
  assert.equal(part.params?.crown, 0.5667);
  assert.deepEqual(headSocket, { n: 11, u: 0.5, v: 0.5, sink: 0, outset: 0.02 });
});

test("clumps and tufts take their length in body units", () => {
  assert.equal(hairClump({ id: "lock", on: "hair", socket: 3, length: 0.1, tip: "dark" }).size, 0.5);
  assert.deepEqual(hairClump({ id: "lock", on: "hair", socket: 3, length: 0.1, tip: "dark" }).areas, { tip: "dark" });
  assert.equal(hairTuft({ id: "tuft", on: "head", socket: "crown", length: 0.06 }).socket, "crown");
});

test("a fringe, a fall and a crown each place their clumps on their own sockets", () => {
  const front = fringe({ id: "fringe", on: "hair", firstSocket: 1, count: 3, lengths: [0.2, 0.15], sweep: -0.4 });
  assert.deepEqual(front.sockets.map((s) => s.n), [1, 2, 3]);
  assert.deepEqual(front.parts.map((p) => p.size), [1, 0.75, 0.75]);
  assert.ok(front.parts.every((p) => p.rest === "down" && p.curve?.[1]?.[2] === -0.4 && p.preset === "fine"));
  const back = fall({ id: "fall", on: "hair", socket: 9, area: { u: [0, 0.7], v: [0.2, 0.8] }, count: 30, length: 0.1 });
  assert.equal(back.parts.length, 1);
  assert.equal(back.sockets[0].spread?.droop, 70);
  const swirl = crown({ id: "swirl", on: "hair", firstSocket: 20, count: 4, length: 0.06 });
  assert.equal(swirl.parts.length, 4);
  assert.ok(swirl.sockets.every((s) => s.v >= 0 && s.v < 1));
});

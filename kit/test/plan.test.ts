import { test } from "node:test";
import assert from "node:assert/strict";
import { bodyPlan, checkHeights, hangingSpan, parseProbe, vBetween, vToward } from "squash-kit";

test("a body plan turns heights into torso u and leg x", () => {
  const plan = bodyPlan({ ground: 0, hip: 0.7, knee: 0.4, torsoBottom: 0.6, shoulder: 1.075, torsoTop: 1.12 }, { hips: 0.15 });
  const torso = plan.upright("torsoBottom", "torsoTop");
  assert.equal(torso.u(plan.h("shoulder")), 0.9135);
  const leg = hangingSpan(plan.h("hip"), 0);
  assert.ok(Math.abs(leg.x(plan.h("knee")) - 0.3) < 1e-9);
  assert.equal(plan.w("hips"), 0.15);
  assert.throws(() => plan.h("chin" as "hip"), /no height "chin"/);
});

test("v faces as frames.md says", () => {
  assert.equal(vToward("up", "left"), 0.75);
  assert.equal(vToward("down", "back"), 0.5);
  assert.equal(vToward("forward", "outer"), 0.25);
  assert.equal(vBetween("up", "forward", "left", 0.5), 0.875);
  assert.throws(() => vToward("up", "up"), /along its axis/);
});

const probeText = `probe: at rest, body space: x forward, y up, z to the character's right (its left is -z)
part torso: tube 1, origin [0, 0, 0], box [-0.15, 0, -0.206] to [0.166, 0.68, 0.206]
  torso frame: u runs up, Side's +y forward; at u 0.5: v 0 forward, v 0.25 right, v 0.5 back, v 0.75 left
  torso joint tip: u 1, hinge x, limits -30 to 30, at [0, 0.68, 0]
  torso socket socket.2: u 0.83, v 0.75, at [0.031, 0.564, -0.17], faces left
part leg-l: tube 1 left side, origin [-0.011, 0.08, -0.124], box [-0.116, -0.86, -0.223] to [0.083, 0.082, -0.029]
  leg-l joint joint.1: u 0.52, hinge x, limits 0 to 140, at [-0.011, -0.408, -0.133]
part head: head-round 2, origin [-0.016, 0.759, 0], box [-0.188, 0.71, -0.135] to [0.101, 1.087, 0.135]
  head face: x -0.199 to 0.199 (+x its left), y -0.072 to 0.484 (up)
uv head:0.5,0.2 at [0.1, 0.9, -0.05]`;

test("a probe's printout reads into parts, joints, sockets and the ground", () => {
  const report = parseProbe(probeText);
  assert.equal(report.ground, -0.86);
  assert.deepEqual(report.parts.torso.sockets["socket.2"].at, [0.031, 0.564, -0.17]);
  assert.deepEqual(report.parts["leg-l"].joints["joint.1"].limits, [0, 140]);
  assert.deepEqual(report.parts.head.face, { x: [-0.199, 0.199], y: [-0.072, 0.484] });
  assert.deepEqual(report.answers, ["uv head:0.5,0.2 at [0.1, 0.9, -0.05]"]);
});

test("planned heights against a probe", () => {
  const checks = checkHeights(parseProbe(probeText), { knee: 0.45, top: 1.6 }, {
    knee: { part: "leg-l", joint: "joint.1" },
    top: { part: "head", box: "top" },
  });
  assert.deepEqual(checks, [
    { name: "knee", planned: 0.45, built: 0.452, off: 0.002 },
    { name: "top", planned: 1.6, built: 1.947, off: 0.347 },
  ]);
});

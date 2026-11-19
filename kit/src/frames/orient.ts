// A direction and an up in body space as the rest, lean, outward and roll that build them, following frames.md's "Roll".

import type { Vec3 } from "../probe/parse.ts";
import type { Rest } from "./rest.ts";

// Directions are in body space: +x forward, +y up, -z the character's left.

/** The fields that point a part: its rest, then lean and outward in degrees, then roll. */
export interface Orientation {
  rest: Rest;
  lean: number;
  outward: number;
  roll: number;
}

/** The most lean or outward the build turns a rest by, as Move's sliders hold them. */
export const MAX_TURN = 85;
/** A direction this many degrees past MAX_TURN, floating point error, is still on it. */
const TURN_SLACK = 1e-6;

const RESTS: ReadonlyArray<readonly [Rest, Vec3]> = [
  ["forward", [1, 0, 0]],
  ["back", [-1, 0, 0]],
  ["up", [0, 1, 0]],
  ["down", [0, -1, 0]],
  ["out", [0, 0, -1]],
];
const LEFT: Vec3 = [0, 0, -1];
const VERTICAL = Math.SQRT1_2;
const DEGREES = 180 / Math.PI;

const dot = (a: Vec3, b: Vec3) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const add = (a: Vec3, b: Vec3): Vec3 => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const times = (a: Vec3, k: number): Vec3 => [a[0] * k, a[1] * k, a[2] * k];
const cross = (a: Vec3, b: Vec3): Vec3 => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const length = (a: Vec3) => Math.sqrt(dot(a, a));

/** `a` with unit length; fails on no length. */
export function unit(a: Vec3): Vec3 {
  const l = length(a);
  if (l < 1e-6) throw new Error(`a direction needs some length, not [${a.join(", ")}]`);
  return times(a, 1 / l);
}

/** `towards` squared to `along`, with unit length. */
function square(towards: Vec3, along: Vec3): Vec3 {
  return unit(add(towards, times(along, -dot(towards, along))));
}

/** The up a part resting along `along` takes: body up, or body forward within 45 degrees of vertical. */
function defaultUp(along: Vec3): Vec3 {
  return square(Math.abs(along[1]) > VERTICAL ? [1, 0, 0] : [0, 1, 0], along);
}

/**
 * The rest, lean, outward and roll that point a part (its left side, for a pair) along `along` with its up (Side's +y, v 0) toward
 * `up`, squared to `along`; with no `up`, roll 0. The rest is the one nearest `along`; it fails where lean or outward would pass
 * `MAX_TURN` (a turn of exactly `MAX_TURN` is built), as for a left part pointing right.
 */
export function orient(along: Vec3, up?: Vec3): Orientation {
  const d = unit(along);
  const [rest, r] = RESTS.reduce((best, next) => (dot(next[1], d) > dot(best[1], d) ? next : best));
  const restUp = defaultUp(r);
  const away = add(LEFT, times(r, -dot(LEFT, r)));
  const outDirection = length(away) < 1e-3 ? cross(restUp, r) : unit(away);
  const ahead = dot(d, r);
  const outward = Math.atan2(dot(d, outDirection), ahead) * DEGREES;
  const lean = Math.atan2(dot(d, restUp), ahead) * DEGREES;
  if (Math.abs(outward) > MAX_TURN + TURN_SLACK || Math.abs(lean) > MAX_TURN + TURN_SLACK) {
    const turn = Math.round(Math.max(Math.abs(outward), Math.abs(lean)));
    const across = turn >= 89 ? ": no rest reaches a direction across the body to the character's right for a left part, so build the part on the other side and mirror" : "";
    throw new Error(`[${along.join(", ")}] lies ${turn} degrees from the nearest rest; the build turns a rest ${MAX_TURN} at most${across}`);
  }
  const turned = unit(add(add(r, times(outDirection, Math.tan(outward / DEGREES))), times(restUp, Math.tan(lean / DEGREES))));
  const builtUp = square(restUp, turned);
  const builtSide = cross(builtUp, turned);
  const roll = up === undefined ? 0 : Math.atan2(dot(up, builtSide), dot(up, builtUp)) * DEGREES;
  return { rest, lean: round(lean), outward: round(outward), roll: round(roll) };
}

/** `count` directions evenly round `axis`, starting toward `first` squared to it: petals round a flower's facing. */
export function around(axis: Vec3, first: Vec3, count: number): Vec3[] {
  const a = unit(axis);
  const x = square(first, a);
  const y = cross(a, x);
  return Array.from({ length: count }, (_, i) => {
    const t = (2 * Math.PI * i) / count;
    return add(times(x, Math.cos(t)), times(y, Math.sin(t)));
  });
}

function round(degrees: number): number {
  return Math.round(degrees * 100) / 100;
}

/** A place or direction in body space: x forward, y up, z toward the body's left. */
export type V3 = [number, number, number];

export const add = (a: V3, b: V3): V3 => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const sub = (a: V3, b: V3): V3 => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const scale = (a: V3, s: number): V3 => [a[0] * s, a[1] * s, a[2] * s];
export const dot = (a: V3, b: V3): number => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
export const cross = (a: V3, b: V3): V3 => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
export const length = (a: V3): number => Math.hypot(a[0], a[1], a[2]);

export function unit(a: V3): V3 {
  const l = length(a);
  return l === 0 ? a : scale(a, 1 / l);
}

/** `p` turned `degrees` about the unit axis `k` (Rodrigues). */
export function turned(p: V3, k: V3, degrees: number): V3 {
  const t = (degrees * Math.PI) / 180;
  const c = Math.cos(t);
  const s = Math.sin(t);
  return add(add(scale(p, c), scale(cross(k, p), s)), scale(k, dot(k, p) * (1 - c)));
}

/** A unit direction across `along`, any one. */
export function across(along: V3): V3 {
  const helper: V3 = Math.abs(along[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  return unit(cross(along, helper));
}

/** A turn in body space as a 3 by 3 matrix, rows first. */
export type M3 = [V3, V3, V3];

export const IDENTITY: M3 = [
  [1, 0, 0],
  [0, 1, 0],
  [0, 0, 1],
];

/** The turn of `degrees` about the unit axis `k`. */
export function turnAbout(k: V3, degrees: number): M3 {
  return [turned([1, 0, 0], k, degrees), turned([0, 1, 0], k, degrees), turned([0, 0, 1], k, degrees)].reduce<M3>(
    (m, column, j) => {
      for (let i = 0; i < 3; i++) m[i][j] = column[i];
      return m;
    },
    [
      [0, 0, 0],
      [0, 0, 0],
      [0, 0, 0],
    ],
  );
}

export function apply(m: M3, v: V3): V3 {
  return [dot(m[0], v), dot(m[1], v), dot(m[2], v)];
}

/** The turn `a` after `b`. */
export function compose(a: M3, b: M3): M3 {
  const out: M3 = [
    [0, 0, 0],
    [0, 0, 0],
    [0, 0, 0],
  ];
  for (let i = 0; i < 3; i++) for (let j = 0; j < 3; j++) out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
  return out;
}

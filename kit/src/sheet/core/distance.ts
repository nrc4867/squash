import { blankMask, type Mask } from "./mask.ts";

const FAR = 1e20;

/** The squared distance transform of one line of samples (Felzenszwalb and Huttenlocher's lower envelope of parabolas). */
function line(f: Float64Array, n: number, d: Float64Array, v: Int32Array, z: Float64Array): void {
  let k = 0;
  v[0] = 0;
  z[0] = -FAR;
  z[1] = FAR;
  for (let q = 1; q < n; q++) {
    let s = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    while (s <= z[k]) {
      k--;
      s = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    }
    k++;
    v[k] = q;
    z[k] = s;
    z[k + 1] = FAR;
  }
  k = 0;
  for (let q = 0; q < n; q++) {
    while (z[k + 1] < q) k++;
    d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
  }
}

/** Each pixel's squared Euclidean distance to the nearest pixel whose value is `to` (0 on those pixels). */
export function squaredDistance(mask: Mask, to: 0 | 1): Float64Array {
  const { width, height } = mask;
  const out = new Float64Array(width * height);
  for (let i = 0; i < out.length; i++) out[i] = mask.data[i] === to ? 0 : FAR;
  const n = Math.max(width, height);
  const f = new Float64Array(n);
  const d = new Float64Array(n);
  const v = new Int32Array(n);
  const z = new Float64Array(n + 1);
  for (let x = 0; x < width; x++) {
    for (let y = 0; y < height; y++) f[y] = out[y * width + x];
    line(f, height, d, v, z);
    for (let y = 0; y < height; y++) out[y * width + x] = d[y];
  }
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) f[x] = out[y * width + x];
    line(f, width, d, v, z);
    for (let x = 0; x < width; x++) out[y * width + x] = d[x];
  }
  return out;
}

/** The mask grown by a disc of radius `r` pixels. */
export function roundDilate(mask: Mask, r: number): Mask {
  if (r <= 0) return mask;
  const dist = squaredDistance(mask, 1);
  const out = blankMask(mask.width, mask.height);
  const limit = r * r;
  for (let i = 0; i < dist.length; i++) out.data[i] = dist[i] <= limit ? 1 : 0;
  return out;
}

/** The mask shrunk by a disc of radius `r` pixels. */
export function roundErode(mask: Mask, r: number): Mask {
  if (r <= 0) return mask;
  const dist = squaredDistance(mask, 0);
  const out = blankMask(mask.width, mask.height);
  const limit = r * r;
  for (let i = 0; i < dist.length; i++) out.data[i] = dist[i] > limit ? 1 : 0;
  return out;
}

/** The mask closed by a disc of radius `r` pixels: every inward corner and gap narrower than the disc filled with its arc. */
export function roundClose(mask: Mask, r: number): Mask {
  return roundErode(roundDilate(mask, r), r);
}

/** The mask opened by a disc of radius `r` pixels: every outward corner and spur narrower than the disc rounded off. */
export function roundOpen(mask: Mask, r: number): Mask {
  return roundDilate(roundErode(mask, r), r);
}

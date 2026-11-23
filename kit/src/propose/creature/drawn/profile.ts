/** A profile's radii kept rising to their widest and falling after it: a radius that turns back in between takes its neighbour's. */
export function unimodal(radii: readonly number[]): number[] {
  const out = [...radii];
  let widest = 0;
  for (let i = 1; i < out.length; i++) if (out[i] > out[widest]) widest = i;
  for (let i = widest - 1; i >= 0; i--) out[i] = Math.min(out[i], out[i + 1]);
  for (let i = widest + 1; i < out.length; i++) out[i] = Math.min(out[i], out[i - 1]);
  return out;
}

/** Radii eased toward their neighbours' mean by `amount` (0 to 1), the ends kept. */
export function eased(radii: readonly number[], amount: number): number[] {
  return radii.map((r, i) => (i === 0 || i === radii.length - 1 ? r : r * (1 - amount) + ((radii[i - 1] + radii[i + 1]) / 2) * amount));
}

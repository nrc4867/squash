/** One key of an ease profile: `[u, ease]`, or `[u, front, back]` with the back standing off apart from the front, in body units. */
export type EaseKey = [number, number] | [number, number, number];

/**
 * An ease profile for a garment's `eases`, by part: keys along u, in order, read as one smooth curve with no steps, flat past the first
 * and last. `ease([[0, 0.006], [0.7, 0.02], [1, 0.03]])` is a sleeve snug at the shoulder and loose at the wrist. Throws on keys out of
 * order or a negative ease, so a slip shows where the profile is written.
 */
export function ease(keys: EaseKey[]): number[][] {
  if (keys.length === 0) throw new Error("ease: a profile needs at least one key [u, ease]");
  keys.forEach((key, i) => {
    if (key.slice(1).some((e) => e < 0)) throw new Error(`ease: key ${i} stands off by a negative ease: [${key.join(", ")}]`);
    if (i > 0 && key[0] <= keys[i - 1][0]) throw new Error(`ease: key ${i} at u ${key[0]} does not follow u ${keys[i - 1][0]}`);
  });
  return keys.map((key) => [...key]);
}

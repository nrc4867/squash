/** Stations for a rounded closed tube: where each sits along it and its radius as a share of the widest. */
export interface RoundStations {
  at: number[];
  shares: number[];
  /** The radii for a widest radius: one profile of a tube. */
  radii(widest: number): number[];
}

/**
 * Stations of an egg `length` long for a closed tube (a head, a blob torso): placed close together near both ends so
 * they round off, widest a share `widest` of the way along (0.5 an ellipse, above 0.5 fuller toward the far end).
 */
export function roundStations(length: number, count: number, widest = 0.5): RoundStations {
  const power = Math.log(widest) / Math.log(0.5);
  const angles = Array.from({ length: count }, (_, i) => (Math.PI * (i + 1)) / (count + 1));
  const at = angles.map((a) => length * ((1 - Math.cos(a)) / 2) ** power);
  const shares = angles.map((a) => Math.sin(a));
  return { at, shares, radii: (w) => shares.map((s) => s * w) };
}

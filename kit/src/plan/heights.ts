/** Heights above the ground in body units, by name, with the distances between them. */
export interface HeightPlan<K extends string> {
  readonly heights: Readonly<Record<K, number>>;
  /** The height of a named place. */
  h(name: K): number;
  /** How far `upper` stands above `lower`. */
  between(lower: K, upper: K): number;
}

/** Declares a height plan: `heightPlan({ ankle: 0.08, knee: 0.46, hip: 0.86 })`. */
export function heightPlan<const H extends Record<string, number>>(heights: H): HeightPlan<keyof H & string> {
  type K = keyof H & string;
  const h = (name: K): number => {
    const value = heights[name];
    if (typeof value !== "number") throw new Error(`the height plan has no height "${name}"`);
    return value;
  };
  return { heights, h, between: (lower: K, upper: K) => h(upper) - h(lower) };
}

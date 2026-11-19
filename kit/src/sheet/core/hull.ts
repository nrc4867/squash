export interface HullPoint {
  x: number;
  y: number;
}

function cross(o: HullPoint, a: HullPoint, b: HullPoint): number {
  return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
}

/** The convex hull of points, counter-clockwise by Andrew's monotone chain. */
export function convexHull(points: readonly HullPoint[]): HullPoint[] {
  const sorted = [...points].sort((a, b) => a.x - b.x || a.y - b.y);
  if (sorted.length < 3) return sorted;
  const build = (list: readonly HullPoint[]) => {
    const chain: HullPoint[] = [];
    for (const p of list) {
      while (chain.length >= 2 && cross(chain[chain.length - 2], chain[chain.length - 1], p) <= 0) chain.pop();
      chain.push(p);
    }
    chain.pop();
    return chain;
  };
  return [...build(sorted), ...build([...sorted].reverse())];
}

/** The leftmost and rightmost column the polygon covers on row `y`, undefined where it does not reach the row. */
export function polygonSpan(polygon: readonly HullPoint[], y: number): { l: number; r: number } | undefined {
  let l = Infinity;
  let r = -Infinity;
  for (let i = 0; i < polygon.length; i++) {
    const a = polygon[i];
    const b = polygon[(i + 1) % polygon.length];
    if ((a.y > y && b.y > y) || (a.y < y && b.y < y)) continue;
    if (a.y === b.y) {
      l = Math.min(l, a.x, b.x);
      r = Math.max(r, a.x, b.x);
      continue;
    }
    const x = a.x + ((b.x - a.x) * (y - a.y)) / (b.y - a.y);
    l = Math.min(l, x);
    r = Math.max(r, x);
  }
  return r < l ? undefined : { l: Math.ceil(l), r: Math.floor(r) };
}

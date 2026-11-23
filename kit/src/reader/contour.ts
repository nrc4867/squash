import type { Mask } from "../sheet/core/mask.ts";

/** A place in pixels, x right and y down; a contour's corners lie on pixel corners until smoothed. */
export type Vertex = [number, number];

/** Rounds of neighbour averaging that take a traced contour off its pixel steps. */
const SMOOTH_ROUNDS = 4;

/**
 * The boundaries of a mask as closed loops of pixel corners, each walked with the mask on its right as the image shows it (y down):
 * outer boundaries clockwise on the screen, holes the other way. Loops are smoothed off their pixel steps.
 */
export function traceLoops(mask: Mask): Vertex[][] {
  const { width, height } = mask;
  const at = (x: number, y: number) => x >= 0 && y >= 0 && x < width && y < height && mask.data[y * width + x] === 1;
  const key = (x: number, y: number) => y * (width + 1) + x;
  const outgoing = new Map<number, Array<[number, number]>>();
  const add = (x0: number, y0: number, x1: number, y1: number) => {
    const k = key(x0, y0);
    const list = outgoing.get(k);
    if (list) list.push([x1, y1]);
    else outgoing.set(k, [[x1, y1]]);
  };
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!at(x, y)) continue;
      if (!at(x, y - 1)) add(x, y, x + 1, y);
      if (!at(x + 1, y)) add(x + 1, y, x + 1, y + 1);
      if (!at(x, y + 1)) add(x + 1, y + 1, x, y + 1);
      if (!at(x - 1, y)) add(x, y + 1, x, y);
    }
  }
  const loops: Vertex[][] = [];
  for (const [startKey] of outgoing) {
    while ((outgoing.get(startKey)?.length ?? 0) > 0) {
      const sx = startKey % (width + 1);
      const sy = (startKey - sx) / (width + 1);
      const loop: Vertex[] = [[sx, sy]];
      let cx = sx;
      let cy = sy;
      let px = sx;
      let py = sy;
      for (;;) {
        const list = outgoing.get(key(cx, cy));
        if (!list || list.length === 0) break;
        let pick = 0;
        if (list.length > 1) {
          // At a pinch, turn right: keeps pieces touching only at a corner apart.
          const dx = cx - px;
          const dy = cy - py;
          pick = Math.max(0, list.findIndex(([nx, ny]) => (nx - cx) * -dy + (ny - cy) * dx < 0));
        }
        const [nx, ny] = list.splice(pick, 1)[0];
        px = cx;
        py = cy;
        cx = nx;
        cy = ny;
        if (cx === sx && cy === sy) break;
        loop.push([cx, cy]);
      }
      if (loop.length >= 8) loops.push(smoothed(loop));
    }
  }
  return loops;
}

/** A closed loop with each place averaged with its neighbours a few rounds. */
function smoothed(loop: Vertex[]): Vertex[] {
  let out = loop;
  for (let round = 0; round < SMOOTH_ROUNDS; round++) {
    const n = out.length;
    out = out.map((p, i) => {
      const a = out[(i - 1 + n) % n];
      const b = out[(i + 1) % n];
      return [(a[0] + 2 * p[0] + b[0]) / 4, (a[1] + 2 * p[1] + b[1]) / 4];
    });
  }
  return out;
}

/** A closed loop's area, positive where it turns counter-clockwise with y up. */
export function signedArea(loop: readonly Vertex[]): number {
  let sum = 0;
  for (let i = 0; i < loop.length; i++) {
    const [x0, y0] = loop[i];
    const [x1, y1] = loop[(i + 1) % loop.length];
    sum += x0 * y1 - x1 * y0;
  }
  return sum / 2;
}

/** An open run's fewest places within `tolerance` of it (Douglas and Peucker), its ends kept. */
export function simplified(run: readonly Vertex[], tolerance: number): Vertex[] {
  if (run.length <= 2) return [...run];
  const keep = new Uint8Array(run.length);
  keep[0] = 1;
  keep[run.length - 1] = 1;
  const stack: Array<[number, number]> = [[0, run.length - 1]];
  while (stack.length > 0) {
    const [a, b] = stack.pop()!;
    let worst = -1;
    let at = -1;
    const [ax, ay] = run[a];
    const [bx, by] = run[b];
    const dx = bx - ax;
    const dy = by - ay;
    const length = Math.hypot(dx, dy) || 1;
    for (let i = a + 1; i < b; i++) {
      const d = Math.abs((run[i][0] - ax) * dy - (run[i][1] - ay) * dx) / length;
      if (d > worst) {
        worst = d;
        at = i;
      }
    }
    if (worst > tolerance) {
      keep[at] = 1;
      stack.push([a, at], [at, b]);
    }
  }
  return run.filter((_, i) => keep[i]);
}

/** The indices of places where a run turns more sharply than `degrees`. */
export function cornersOf(run: readonly Vertex[], degrees: number): number[] {
  const out: number[] = [];
  for (let i = 1; i < run.length - 1; i++) {
    const a = [run[i][0] - run[i - 1][0], run[i][1] - run[i - 1][1]];
    const b = [run[i + 1][0] - run[i][0], run[i + 1][1] - run[i][1]];
    const cos = (a[0] * b[0] + a[1] * b[1]) / ((Math.hypot(a[0], a[1]) || 1) * (Math.hypot(b[0], b[1]) || 1));
    if (Math.acos(Math.max(-1, Math.min(1, cos))) > (degrees * Math.PI) / 180) out.push(i);
  }
  return out;
}

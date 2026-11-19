import { steps } from "../../../geom/point.ts";
import { maskOf } from "../../core/mask.ts";
import type { Piece } from "../../measure/pieces.ts";
import { r4 } from "./shared.ts";

/** The first and last stations sit this share of the arm in from its ends, where its width is still measurable. */
const END_INSET = 0.06;
import type { MeasuredView, PartProfile } from "./types.ts";

/** The arm's widths across its own axis, from where it leaves the body to its tip. */
export function armProfile(front: MeasuredView, piece: Piece, count: number): PartProfile {
  const { width } = front.anatomy.figure.mask;
  const height = front.anatomy.figure.mask.height;
  const pieceMask = maskOf(width, height, piece.pixels);
  let mx = 0;
  let my = 0;
  for (const at of piece.pixels) {
    mx += at % width;
    my += Math.floor(at / width);
  }
  mx /= piece.pixels.length;
  my /= piece.pixels.length;
  let sxx = 0;
  let syy = 0;
  let sxy = 0;
  for (const at of piece.pixels) {
    const dx = (at % width) - mx;
    const dy = Math.floor(at / width) - my;
    sxx += dx * dx;
    syy += dy * dy;
    sxy += dx * dy;
  }
  const angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);
  let ax = Math.cos(angle);
  let ay = Math.sin(angle);
  if ((piece.tip.x - mx) * ax + (piece.tip.y - my) * ay < 0) {
    ax = -ax;
    ay = -ay;
  }
  let start = Infinity;
  let end = -Infinity;
  for (const at of piece.pixels) {
    const t = ((at % width) - mx) * ax + (Math.floor(at / width) - my) * ay;
    start = Math.min(start, t);
    end = Math.max(end, t);
  }
  const inset = END_INSET * (end - start);
  const ts = steps(start + inset, end - inset, count);
  const nx = -ay;
  const ny = ax;
  const reach = (px: number, py: number, dir: number) => {
    let d = 0;
    const has = (k: number) => {
      const x = Math.round(px + nx * k * dir);
      const y = Math.round(py + ny * k * dir);
      return x >= 0 && y >= 0 && x < width && y < height && pieceMask.data[y * width + x] === 1;
    };
    while (has(d + 1) || has(d + 2)) d++;
    return d + 0.5;
  };
  const plus: number[] = [];
  const minus: number[] = [];
  for (const t of ts) {
    const px = mx + ax * t;
    const py = my + ay * t;
    plus.push(reach(px, py, 1));
    minus.push(reach(px, py, -1));
  }
  const scale = front.measurement.bodyPerPixel;
  const plusIsOuter = Math.sign(nx) === piece.side || nx === 0;
  const outer = (plusIsOuter ? plus : minus).map((v) => r4(v * scale));
  const inner = (plusIsOuter ? minus : plus).map((v) => r4(v * scale));
  return {
    rest: "down",
    length: r4((end - start) * scale),
    at: ts.map((t) => r4((t - start) * scale)),
    profiles: { outer, inner },
    confidence: "medium",
    notes: [
      "measured from where the arm leaves the body's outline to its tip; the upper arm above that is inside the torso's outline",
      "widths across the arm in the image plane only",
    ],
  };
}


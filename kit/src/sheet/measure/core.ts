import { blankMask, type Mask } from "../core/mask.ts";
import { centralRun, EDGE_STEP_SHARE, figureHeight, type Figure } from "./figure.ts";
import type { LegTracks } from "./legs.ts";
import type { Run } from "./rows.ts";

/** The torso's run on each row from `from` to `to`, its edges kept within a step of the row above. */
function heldTorso(figure: Figure, from: number, to: number): Map<number, Run> {
  const step = Math.max(1, Math.round(EDGE_STEP_SHARE * figureHeight(figure)));
  const out = new Map<number, Run>();
  let previous = centralRun(figure, from);
  for (let y = from; y < to && previous; y++) {
    const run = centralRun(figure, y);
    if (!run) break;
    const held = y === from ? run : { l: Math.max(run.l, previous.l - step), r: Math.min(run.r, previous.r + step) };
    out.set(y, held);
    previous = held;
  }
  return out;
}

function fill(mask: Mask, y: number, run: Run | undefined): void {
  if (!run) return;
  for (let x = run.l; x <= run.r; x++) mask.data[y * mask.width + x] = 1;
}

/**
 * The body's core: the head, neck and torso on the axis from the crown down, held below the armpits, and each leg below the crotch.
 * What the figure holds outside it is ears, arms, a tail, and anything carried.
 */
export function coreMask(figure: Figure, crown: number, armpit: number | undefined, crotch: number | undefined, legs: LegTracks | undefined): Mask {
  const mask = blankMask(figure.mask.width, figure.mask.height);
  const torsoEnd = crotch ?? figure.ground;
  const held = armpit !== undefined ? heldTorso(figure, armpit, torsoEnd) : new Map<number, Run>();
  for (let y = crown; y < torsoEnd; y++) fill(mask, y, armpit !== undefined && y >= armpit ? held.get(y) : centralRun(figure, y));
  if (legs) {
    for (let y = legs.from; y < figure.ground; y++) {
      fill(mask, y, legs.left[y - legs.from]);
      fill(mask, y, legs.right[y - legs.from]);
    }
  }
  return mask;
}

/** The figure's pixels outside the core. */
export function outsideCore(figure: Figure, core: Mask): Mask {
  const out = blankMask(core.width, core.height);
  for (let y = figure.top; y < figure.ground; y++) {
    for (let x = figure.box.x; x < figure.box.x + figure.box.w; x++) {
      const at = y * core.width + x;
      out.data[at] = figure.mask.data[at] && !core.data[at] ? 1 : 0;
    }
  }
  return out;
}

import { centralRun, figureHeight, runsAt, type Figure } from "./figure.ts";
import type { PixelMark } from "./landmark.ts";
import { runWidth, type Run } from "./rows.ts";

/** Below the widest head row, a width under this share of it means the head has begun to close toward the neck. */
const NARROWING = 0.75;
/** What stands over the axis narrower than this share of the figure's height (a tuft, a bun's top) is not yet the head: narrowing there is not a neck. */
const MIN_HEAD_WIDTH = 0.06;
/** The skull is at least this share of the head's widest across: narrower things on top are ears, buns or tufts. */
const SKULL_WIDTH = 0.5;
/** A circle's outline rises this share of its radius above the row where it is half its width. */
const ROUND_RISE = 1 - Math.sqrt(0.75);
/** Below the neck, a width above this many times the neck's means the shoulders have begun. */
const SHOULDER_RISE = 1.3;
/** The head and neck lie in this share of the figure's height from its crown. */
const HEAD_SEARCH = 0.6;
/** The head's own rows may miss the axis by this share of the figure's height (a tuft or a lock of hair beside it) and still be on it. */
const AXIS_SLACK = 0.012;
/** The chin is the lowest row whose width is above the neck's by this share of the head's widest over the neck. */
const CHIN_SHARE = 0.25;

export interface HeadReading {
  crown: PixelMark;
  /** The topmost point of each ear or tuft standing above the crown, image left first. */
  earTips: Array<{ x: number; y: number }>;
  chin?: PixelMark;
  neck?: PixelMark;
  /** The widest head row and its width in pixels. */
  widestRow: number;
  headWidth: number;
  neckWidth: number;
}

/** The run on the axis at row y, else the nearest run within the slack of it: the head's row, a lock or tuft beside the axis counted. */
export function headRun(figure: Figure, y: number): Run | undefined {
  const on = centralRun(figure, y);
  if (on) return on;
  const slack = Math.max(1, Math.round(AXIS_SLACK * figureHeight(figure)));
  let best: Run | undefined;
  let gap = slack + 1;
  for (const run of runsAt(figure, y)) {
    const d = run.l > figure.axis ? run.l - figure.axis : figure.axis - run.r;
    if (d < gap) {
      gap = d;
      best = run;
    }
  }
  return best;
}

function headWidthAt(figure: Figure, y: number): number {
  const run = headRun(figure, y);
  return run ? runWidth(run) : 0;
}

function earTips(figure: Figure, crown: number): Array<{ x: number; y: number }> {
  const tips: Array<{ x: number; y: number }> = [];
  for (const side of [-1, 1] as const) {
    for (let y = figure.top; y < crown; y++) {
      const run = runsAt(figure, y).find((r) => (side < 0 ? r.r < figure.axis : r.l > figure.axis));
      if (!run) continue;
      tips.push({ x: Math.round((run.l + run.r) / 2), y });
      break;
    }
  }
  return tips;
}

/**
 * The top of the skull: the first row at least half the head's widest, raised by how far a round head's outline rises above that
 * width. An ear, bun or tuft standing over the axis is narrower and is left above it.
 */
function skullTop(figure: Figure, firstRow: number, widestRow: number, headWidth: number): number {
  let y = firstRow;
  while (y < widestRow && headWidthAt(figure, y) < SKULL_WIDTH * headWidth) y++;
  return Math.max(firstRow, Math.round(y - ROUND_RISE * (headWidth / 2)));
}

/** Crown, ear tips, widest head row, chin and neck, read down the axis from the top. */
export function readHead(figure: Figure): HeadReading {
  let crown = figure.top;
  while (crown < figure.ground && !headRun(figure, crown)) crown++;
  const tips = crown > figure.top ? earTips(figure, crown) : [];
  let widestRow = crown;
  let headWidth = 0;
  let neckRow: number | undefined;
  let neckWidth = Infinity;
  const end = crown + HEAD_SEARCH * figureHeight(figure);
  for (let y = crown; y < end; y++) {
    const w = headWidthAt(figure, y);
    if (w === 0) break;
    if (neckRow === undefined) {
      if (w > headWidth) {
        headWidth = w;
        widestRow = y;
      } else if (w < NARROWING * headWidth && headWidth >= MIN_HEAD_WIDTH * figureHeight(figure)) {
        neckRow = y;
        neckWidth = w;
      }
    } else {
      if (w < neckWidth) {
        neckWidth = w;
        neckRow = y;
      } else if (w > SHOULDER_RISE * neckWidth) {
        break;
      }
    }
  }
  const skull = skullTop(figure, crown, widestRow, headWidth);
  if (skull > crown) tips.push({ x: figure.axis, y: crown });
  crown = skull;
  if (neckRow === undefined) return { crown: { y: crown, status: "found" }, earTips: tips, widestRow, headWidth, neckWidth: headWidth };
  let chin = widestRow;
  const chinWidth = neckWidth + CHIN_SHARE * (headWidth - neckWidth);
  for (let y = widestRow; y <= neckRow; y++) if (headWidthAt(figure, y) >= chinWidth) chin = y;
  return {
    crown: { y: crown, status: "found" },
    earTips: tips,
    chin: { y: chin, status: "found" },
    neck: { y: neckRow, status: "found" },
    widestRow,
    headWidth,
    neckWidth,
  };
}

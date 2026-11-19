import { channelDistance } from "../core/color.ts";
import { blankMask, components, dilate, erode, type Component, type Mask } from "../core/mask.ts";
import { pixelAt, type Pixel, type Raster } from "../raster/image.ts";
import type { Anatomy } from "./anatomy.ts";
import { headRun } from "./head.ts";
import { placeReading, sizeReading, type Proportions, type ReadingScale } from "./readings.ts";

/** The face window runs from this share of the head's length below the crown to this share, and this share of the head's width either side of its middle. */
const FACE_ROWS: [number, number] = [0.25, 0.93];
const FACE_HALF_WIDTH = 0.48;
/** Skin is sampled between these shares of the head's length, either side of the middle by this share of its width. */
const SKIN_ROWS: [number, number] = [0.72, 0.92];
const SKIN_HALF_WIDTH = 0.14;
/** A pixel this far from the skin, in the largest channel, is a feature: an eye, a nose, a mouth. */
const FEATURE = 60;
/** Gaps this share of the head's length wide, inside a feature (an inked line round a pale eye), are closed. */
const CLOSE_SHARE = 0.012;
/** Features are the seeds left once lines thinner than twice this share of the head's length are opened away, each grown by a closing radius and this many pixels to take the lid line and lashes it touches. */
const SEED_SHARE = 0.025;
const RING_PIXELS = 2;
/** A feature is at least this share of the face window's area. */
const FEATURE_AREA = 0.002;
/** An eye pair's two eyes differ in area by at most this share, in height by at most this share of the head, and their middles stray from mirror images by at most this share of the head's width. */
const PAIR_AREA = 0.45;
const PAIR_HEIGHT = 0.07;
const PAIR_MIRROR = 0.18;
/** The two eyes' widths differ by at most this share before the reading is unsure. */
const EYES_ALIKE = 0.25;
/** An eye is at least this share of the head's width across, at most this share, and at least this share of the head apart from its pair, middle to middle. */
const EYE_WIDTH: [number, number] = [0.06, 0.45];
const EYE_APART = 0.1;
/** The eye line lies between these shares of the head's length from the crown. */
const EYE_LINE: [number, number] = [0.3, 0.75];
/** The nose and mouth lie on the axis within this share of the head's width of it. */
const ON_AXIS = 0.14;
/** The nose lies this far below the eye line at least and at most, and the mouth this far below it at least, as shares of the head's length. */
const NOSE_BELOW: [number, number] = [0.03, 0.3];
const MOUTH_BELOW = 0.15;
const MOUTH_BELOW_NOSE = 0.04;

interface Feature {
  box: { x: number; y: number; w: number; h: number };
  area: number;
  cx: number;
  cy: number;
}

function toFeature(c: Component, ox: number, oy: number): Feature {
  return { box: { x: c.box.x + ox, y: c.box.y + oy, w: c.box.w, h: c.box.h }, area: c.area, cx: c.box.x + ox + c.box.w / 2, cy: c.box.y + oy + c.box.h / 2 };
}

export function medianColor(image: Raster, mask: Mask, x0: number, x1: number, y0: number, y1: number): Pixel | undefined {
  const values: number[][] = [[], [], []];
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      if (!mask.data[y * mask.width + x]) continue;
      const p = pixelAt(image, x, y);
      for (let c = 0; c < 3; c++) values[c].push(p[c]);
    }
  }
  if (values[0].length === 0) return undefined;
  return values.map((v) => v.sort((a, b) => a - b)[v.length >> 1]) as Pixel;
}

/** The box of the `mask` pixels within `grow` of `box`, in sheet pixels, `box` itself being in the mask's own. */
function boxAround(mask: Mask, box: { x: number; y: number; w: number; h: number }, grow: number, ox: number, oy: number): Feature["box"] {
  let x0 = Infinity;
  let y0 = Infinity;
  let x1 = -1;
  let y1 = -1;
  for (let y = Math.max(0, box.y - grow); y < Math.min(mask.height, box.y + box.h + grow); y++) {
    for (let x = Math.max(0, box.x - grow); x < Math.min(mask.width, box.x + box.w + grow); x++) {
      if (!mask.data[y * mask.width + x]) continue;
      x0 = Math.min(x0, x);
      x1 = Math.max(x1, x);
      y0 = Math.min(y0, y);
      y1 = Math.max(y1, y);
    }
  }
  return { x: ox + x0, y: oy + y0, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}

function findEyes(found: Feature[], x0: number, hw: number, crown: number, length: number): { pair?: [Feature, Feature]; reason: string } {
  const candidates = found.filter((f) => f.cy >= crown + EYE_LINE[0] * length && f.cy <= crown + EYE_LINE[1] * length && f.box.w >= EYE_WIDTH[0] * hw && f.box.w <= EYE_WIDTH[1] * hw);
  let best: { pair: [Feature, Feature]; score: number } | undefined;
  for (const a of candidates) {
    for (const b of candidates) {
      if (a.cx >= b.cx) continue;
      if (b.cx - a.cx < EYE_APART * hw) continue;
      if (Math.abs(a.cy - b.cy) > PAIR_HEIGHT * length) continue;
      if (Math.abs(a.area - b.area) > PAIR_AREA * Math.max(a.area, b.area)) continue;
      if (Math.abs(a.cx - x0 + (b.cx - x0)) > PAIR_MIRROR * hw) continue;
      const score = a.area + b.area;
      if (!best || score > best.score) best = { pair: [a, b], score };
    }
  }
  if (best) return { pair: best.pair, reason: "" };
  if (candidates.length === 0) return { reason: "no feature stands out from the skin where eyes should be (hair over them, eyes drawn in the skin's colour, or too faint)" };
  return { reason: `${candidates.length} feature(s) lie where eyes should be but none pair up as level, alike and mirrored across the head (a turned head, a fringe joined to an eye, one eye shut)` };
}

/**
 * The face's key places read from a front view: the eye line, eye width and spacing, nose, mouth and the chin's length below the mouth.
 * Features are what stands out from the skin colour (sampled round the chin) inside a window on the face; the eyes are the pair of
 * features about level and mirrored across the head's middle. A feature joined to the hair or the window's edge is not taken as an
 * eye, so a fringe over the eyes leaves them unread, and the reason says so. A three-quarter view reads them unsure, a side or back view
 * not at all.
 */
export function readFace(image: Raster, anatomy: Anatomy, scale: ReadingScale): Proportions {
  const out: Proportions = { readings: {}, missing: {} };
  const names = ["eyeLine", "eyeWidth", "eyeSpacing", "nose", "mouth", "chinLength"];
  const facing = anatomy.view.guess.facing;
  const fail = (reason: string): Proportions => {
    for (const n of names) out.missing[n] = reason;
    return out;
  };
  if (facing === "back") return fail("a back view shows no face");
  if (facing === "side") return fail("a side view shows one eye and no spacing: read the face from a front view");
  const { figure, marks } = anatomy;
  const crown = marks.crown.y;
  const chin = marks.chin.y;
  const length = chin - crown;
  const widest = headRun(figure, anatomy.head.widestRow);
  if (!widest || length < 24) return fail("the head is too small or not read");
  const x0 = Math.round((widest.l + widest.r) / 2);
  const hw = widest.r - widest.l + 1;
  const mask = figure.mask;
  const skin = medianColor(
    image,
    mask,
    Math.round(x0 - SKIN_HALF_WIDTH * hw),
    Math.round(x0 + SKIN_HALF_WIDTH * hw),
    Math.round(crown + SKIN_ROWS[0] * length),
    Math.round(crown + SKIN_ROWS[1] * length),
  );
  if (!skin) return fail("no skin is read round the chin");
  const wx0 = Math.max(0, Math.round(x0 - FACE_HALF_WIDTH * hw));
  const wx1 = Math.min(mask.width, Math.round(x0 + FACE_HALF_WIDTH * hw));
  const wy0 = Math.round(crown + FACE_ROWS[0] * length);
  const wy1 = Math.round(crown + FACE_ROWS[1] * length);
  const w = wx1 - wx0;
  const h = wy1 - wy0;
  const feature = blankMask(w, h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (!mask.data[(wy0 + y) * mask.width + wx0 + x]) continue;
      if (channelDistance(pixelAt(image, wx0 + x, wy0 + y), skin) > FEATURE) feature.data[y * w + x] = 1;
    }
  }
  const close = Math.max(1, Math.round(CLOSE_SHARE * length));
  const closed = erode(dilate(feature, close), close);
  const seedRadius = Math.max(2, Math.round(SEED_SHARE * length));
  const opened = dilate(erode(closed, seedRadius), seedRadius);
  const inside = (p: Component) => p.box.x > 0 && p.box.y > 0 && p.box.x + p.box.w < w && p.box.y + p.box.h < h;
  const seeds = components(opened)
    .pieces.filter((p) => p.area >= FEATURE_AREA * w * h && inside(p))
    .map((p) => {
      const grow = close + RING_PIXELS;
      return { seed: toFeature(p, wx0, wy0), grown: boxAround(closed, p.box, grow, wx0, wy0) };
    });
  const eyes = findEyes(seeds.map((s) => ({ ...s.seed, box: s.grown })), x0, hw, crown, length);
  const rest = blankMask(w, h);
  rest.data.set(closed.data);
  if (eyes.pair) {
    for (const e of eyes.pair) {
      const g = Math.round(0.2 * e.box.w);
      for (let y = Math.max(0, e.box.y - wy0 - g); y < Math.min(h, e.box.y - wy0 + e.box.h + g); y++) for (let x = Math.max(0, e.box.x - wx0 - g); x < Math.min(w, e.box.x - wx0 + e.box.w + g); x++) rest.data[y * w + x] = 0;
    }
  }
  const axisFrom = Math.max(0, Math.round(x0 - ON_AXIS * hw) - wx0);
  const axisTo = Math.min(w, Math.round(x0 + ON_AXIS * hw) - wx0);
  const belowEyes = eyes.pair ? Math.max(0, Math.round((eyes.pair[0].cy + eyes.pair[1].cy) / 2 + NOSE_BELOW[0] * length) - wy0) : h;
  const strip = blankMask(w, h);
  for (let y = belowEyes; y < h; y++) for (let x = axisFrom; x < axisTo; x++) strip.data[y * w + x] = rest.data[y * w + x];
  const found = components(strip)
    .pieces.filter((p) => p.area >= 0.25 * FEATURE_AREA * w * h)
    .map((p) => toFeature(p, wx0, wy0));
  const reasonTurn = facing === "three-quarter" ? "three-quarter view: the head is turned, so places across it are foreshortened" : undefined;
  const status = (base: "found" | "unsure") => (reasonTurn ? ("unsure" as const) : base);
  const withTurn = (reason?: string) => [reason, reasonTurn].filter(Boolean).join("; ") || undefined;
  const across = (f: Feature) => ({ from: { x: f.box.x, y: Math.round(f.cy) }, to: { x: f.box.x + f.box.w, y: Math.round(f.cy) } });
  if (eyes.pair) {
    const [a, b] = eyes.pair;
    const level = (a.cy + b.cy) / 2;
    const widthPx = (a.box.w + b.box.w) / 2;
    out.readings.eyeLine = placeReading("eyeLine", level, scale, status("found"), [{ from: { x: wx0, y: Math.round(level) }, to: { x: wx1, y: Math.round(level) } }], withTurn());
    const unequal = Math.abs(a.box.w - b.box.w) > EYES_ALIKE * Math.max(a.box.w, b.box.w);
    const eyeReason = withTurn(unequal ? `the two eyes read ${Math.round(a.box.w)} and ${Math.round(b.box.w)} pixels across: one may have taken a nose, brow or lash with it; the mean is shown` : "the feature's width as it stands out from the skin, lid line and lashes included");
    out.readings.eyeWidth = sizeReading("eyeWidth", widthPx, scale, unequal ? "unsure" : status("found"), [across(a), across(b)], eyeReason, "both");
    out.readings.eyeSpacing = sizeReading("eyeSpacing", Math.abs(b.cx - a.cx), scale, status("found"), [{ from: { x: Math.round(a.cx), y: Math.round(level) }, to: { x: Math.round(b.cx), y: Math.round(level) } }], withTurn("middle to middle"));
  } else {
    for (const n of ["eyeLine", "eyeWidth", "eyeSpacing"]) out.missing[n] = eyes.reason;
  }
  const eyeY = eyes.pair ? (eyes.pair[0].cy + eyes.pair[1].cy) / 2 : undefined;
  const onAxis = found;
  const nose = (eyeY === undefined ? [] as Feature[] : onAxis.filter((f) => f.cy > eyeY + NOSE_BELOW[0] * length && f.cy < eyeY + NOSE_BELOW[1] * length)).sort((a, b) => a.cy - b.cy)[0];
  if (nose) {
    out.readings.nose = placeReading("nose", nose.cy, scale, "unsure", [across(nose)], withTurn("the first mark on the axis below the eyes: a small nose or a nostril dot can be missed, and a mouth taken for it"));
  } else {
    out.missing.nose = eyeY === undefined ? "the eyes are not read, so the nose is not looked for" : "no mark stands out from the skin on the axis below the eyes (a nose drawn as a faint shade or a dot too small to read)";
  }
  const mouthFrom = eyeY === undefined ? Infinity : Math.max(eyeY + MOUTH_BELOW * length, nose ? nose.cy + MOUTH_BELOW_NOSE * length : 0);
  const mouth = onAxis.filter((f) => f !== nose && f.cy > mouthFrom).sort((a, b) => b.area - a.area)[0];
  if (mouth) {
    out.readings.mouth = placeReading("mouth", mouth.cy, scale, "unsure", [across(mouth)], withTurn("the largest mark on the axis below the nose"));
    out.readings.chinLength = sizeReading("chinLength", chin - mouth.cy, scale, "unsure", [{ from: { x: x0, y: Math.round(mouth.cy) }, to: { x: x0, y: chin } }], withTurn("from the mouth to the chin's row"));
  } else {
    out.missing.mouth = eyeY === undefined ? "the eyes are not read, so the mouth is not looked for" : "no mark stands out from the skin on the axis below the nose";
    out.missing.chinLength = out.missing.mouth;
  }
  return out;
}

/** The skin's colour: the median of the face's lower middle, round the chin and below the mouth; undefined where the head is not read. */
export function skinColor(image: Raster, anatomy: Anatomy): Pixel | undefined {
  const widest = headRun(anatomy.figure, anatomy.head.widestRow);
  if (!widest) return undefined;
  const length = anatomy.marks.chin.y - anatomy.marks.crown.y;
  const x0 = Math.round((widest.l + widest.r) / 2);
  const hw = widest.r - widest.l + 1;
  return medianColor(
    image,
    anatomy.figure.mask,
    Math.round(x0 - SKIN_HALF_WIDTH * hw),
    Math.round(x0 + SKIN_HALF_WIDTH * hw),
    Math.round(anatomy.marks.crown.y + SKIN_ROWS[0] * length),
    Math.round(anatomy.marks.crown.y + SKIN_ROWS[1] * length),
  );
}

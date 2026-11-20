import { channelDistance } from "../core/color.ts";
import { blankMask, components } from "../core/mask.ts";
import { median } from "../core/solve.ts";
import { pixelAt, type Pixel, type Raster } from "../raster/image.ts";
import type { Anatomy } from "./anatomy.ts";
import { skinColor } from "./face/skin.ts";
import type { PixelMark } from "./landmark.ts";
import { farthestFrom, joinPoint, type Piece } from "./pieces.ts";
import { HAND_WIDTH_OF_LENGTH } from "./sizeChecks.ts";
import { joinReasons, sizeReading, type Proportions, type Reading, type ReadingLine, type ReadingScale } from "./readings.ts";

/** The wrist is the narrowest row of the arm in this stretch of its length, from the joint, and at least this many heads short of its tip. */
const WRIST_RANGE: [number, number] = [0.4, 1];
const MIN_HAND_HEADS = 0.12;
/** A hand reads as a hand when it is this much wider than the wrist, a length this many heads, and an arm no wider than this many heads. */
const HAND_WIDENING = 1.15;
const HAND_HEADS: [number, number] = [0.15, 0.9];
/** The two hands' sizes differ by at most this share before the reading is unsure. */
const SIDES_AGREE = 0.25;
/** A bin of an arm's length is this many pixels. */
const BIN = 2;

/** One hand as read off its arm piece, in the sheet's pixels. */
export interface OneHand {
  side: "image-left" | "image-right";
  length: number;
  width: number;
  status: "found" | "unsure";
  reason?: string;
  lines: { length: ReadingLine; width: ReadingLine };
  /** Where the hand meets the arm, and its far end. */
  wrist: { x: number; y: number };
  tip: { x: number; y: number };
  /** The arm piece the hand is read from. */
  piece: Piece;
  /** The hand's own pixels, where its skin tells it from the sleeve. */
  pixels?: Int32Array;
}

/** A hand wider than `HAND_WIDTH_OF_LENGTH` of its length is unsure, with why. */
function shapeChecked(hand: OneHand): OneHand {
  if (hand.width <= HAND_WIDTH_OF_LENGTH * hand.length) return hand;
  const why = `the hand is ${(hand.width / hand.length).toFixed(2)} of its length wide, more than ${HAND_WIDTH_OF_LENGTH}: it may be read with the other hand or the body, or seen end-on`;
  return { ...hand, status: "unsure", reason: joinReasons(hand.reason, why) };
}

/** One arm piece's hand: the wrist is its narrowest row beyond 0.4 of its length from the body, the hand runs from there to its tip. */
function handOf(piece: Piece, anatomy: Anatomy, image?: Raster, skin?: Pixel): OneHand | { skipped: string } {
  const bySkin = image && skin ? skinHandOf(piece, anatomy, image, skin) : undefined;
  if (bySkin) return bySkin;
  const width = anatomy.figure.mask.width;
  const join = joinPoint(piece.pixels, width, anatomy.core);
  const tip = farthestFrom(piece.pixels, width, join);
  const reach = Math.hypot(tip.x - join.x, tip.y - join.y);
  if (reach < 4 * BIN) return { skipped: "the arm is too short to read" };
  const ax = (tip.x - join.x) / reach;
  const ay = (tip.y - join.y) / reach;
  const bins = Math.ceil(reach / BIN) + 1;
  const low = new Array<number>(bins).fill(Infinity);
  const high = new Array<number>(bins).fill(-Infinity);
  for (const at of piece.pixels) {
    const x = (at % width) - join.x;
    const y = Math.floor(at / width) - join.y;
    const t = x * ax + y * ay;
    if (t < 0 || t > reach) continue;
    const s = -x * ay + y * ax;
    const b = Math.floor(t / BIN);
    low[b] = Math.min(low[b], s);
    high[b] = Math.max(high[b], s);
  }
  const across = (b: number) => (Number.isFinite(low[b]) ? high[b] - low[b] + 1 : Infinity);
  const wristFrom = Math.floor((WRIST_RANGE[0] * reach) / BIN);
  const wristTo = Math.floor((reach - MIN_HAND_HEADS * anatomy.headLength) / BIN);
  if (wristTo <= wristFrom) return { skipped: "the arm is too short to find a wrist" };
  let wrist = wristFrom;
  for (let b = wristFrom; b <= wristTo; b++) if (across(b) < across(wrist)) wrist = b;
  let widest = wrist;
  for (let b = wrist; b < bins; b++) if (Number.isFinite(across(b)) && across(b) > (Number.isFinite(across(widest)) ? across(widest) : 0)) widest = b;
  const wristAt = wrist * BIN;
  const length = reach - wristAt;
  const handWidth = across(widest);
  const side = piece.side < 0 ? ("image-left" as const) : ("image-right" as const);
  const point = (t: number, s: number) => ({ x: Math.round(join.x + t * ax - s * ay), y: Math.round(join.y + t * ay + s * ax) });
  const lines = {
    length: { from: point(wristAt, (low[wrist] + high[wrist]) / 2), to: tip },
    width: { from: point(widest * BIN, low[widest]), to: point(widest * BIN, high[widest]) },
  };
  const reasons: string[] = [];
  const widening = handWidth / Math.max(1, across(wrist));
  if (widening < HAND_WIDENING) reasons.push(`no wrist narrowing: the hand is ${widening.toFixed(2)} times the wrist's width (a sleeve, glove or mitten covers the wrist, or the arm is bent)`);
  const heads = length / anatomy.headLength;
  if (heads < HAND_HEADS[0] || heads > HAND_HEADS[1]) reasons.push(`the hand is ${heads.toFixed(2)} heads long, expected ${HAND_HEADS[0]} to ${HAND_HEADS[1]}`);
  return shapeChecked({ side, length, width: handWidth, status: reasons.length ? "unsure" : "found", ...(reasons.length ? { reason: reasons.join("; ") } : {}), lines, wrist: lines.length.from, tip, piece });
}

/** Pixels this near the skin's colour, in the largest channel, are skin. */
const SKIN_REACH = 45;
/** An arm piece more skin than this share, and longer than this many heads, is a bare arm: skin does not tell the hand from it. */
const BARE_ARM = 0.8;
const BARE_ARM_HEADS = 1.2;
/** A piece apart from the body that is at least this share skin and at most this many heads across is a hand, whatever it was sorted as. */
const HAND_SKIN = 0.5;
const HAND_ACROSS_HEADS = 0.9;
/** The skin part at an arm's end is at least this many heads square to be a hand. */
const HAND_MIN_AREA = 0.01;

/**
 * A hand told from its sleeve by the skin's colour: the skin-coloured part of the arm piece that holds its far end, its length and width
 * along and across its own principal axis. Undefined for a bare arm, or where no skin reaches the tip.
 */
function skinHandOf(piece: Piece, anatomy: Anatomy, image: Raster, skin: Pixel): OneHand | undefined {
  const width = anatomy.figure.mask.width;
  const mask = blankMask(width, anatomy.figure.mask.height);
  let skinPixels = 0;
  for (const at of piece.pixels) {
    if (channelDistance(pixelAt(image, at % width, Math.floor(at / width)), skin) <= SKIN_REACH) {
      mask.data[at] = 1;
      skinPixels++;
    }
  }
  const join = joinPoint(piece.pixels, width, anatomy.core);
  const tip = farthestFrom(piece.pixels, width, join);
  if (skinPixels > BARE_ARM * piece.pixels.length && Math.hypot(tip.x - join.x, tip.y - join.y) > BARE_ARM_HEADS * anatomy.headLength) return undefined;
  const pieces = components(mask).pieces.filter((p) => p.area >= HAND_MIN_AREA * anatomy.headLength * anatomy.headLength);
  const found = pieces.find((p) => p.box.x - 2 <= tip.x && tip.x <= p.box.x + p.box.w + 2 && p.box.y - 2 <= tip.y && tip.y <= p.box.y + p.box.h + 2);
  if (!found) return undefined;
  const own = ownSide(found.pixels, width, anatomy.figure.axis, piece.side);
  const handPixels = own.length > 0 ? own : found.pixels;
  const xs = Array.from(handPixels, (at) => at % width);
  const ys = Array.from(handPixels, (at) => Math.floor(at / width));
  const mx = xs.reduce((a, b) => a + b, 0) / xs.length;
  const my = ys.reduce((a, b) => a + b, 0) / ys.length;
  let sxx = 0;
  let syy = 0;
  let sxy = 0;
  for (let i = 0; i < xs.length; i++) {
    sxx += (xs[i] - mx) ** 2;
    syy += (ys[i] - my) ** 2;
    sxy += (xs[i] - mx) * (ys[i] - my);
  }
  const angle = 0.5 * Math.atan2(2 * sxy, sxx - syy);
  const ax = Math.cos(angle);
  const ay = Math.sin(angle);
  const extents = (dx: number, dy: number) => {
    const t = xs.map((x, i) => (x - mx) * dx + (ys[i] - my) * dy);
    return { low: Math.min(...t), high: Math.max(...t) };
  };
  const along = extents(ax, ay);
  const across = extents(-ay, ax);
  const length = along.high - along.low + 1;
  const point = (t: number, s: number) => ({ x: Math.round(mx + t * ax - s * ay), y: Math.round(my + t * ay + s * ax) });
  const heads = length / anatomy.headLength;
  const reason = heads < HAND_HEADS[0] || heads > HAND_HEADS[1] ? `the skin-coloured part is ${heads.toFixed(2)} heads long, expected ${HAND_HEADS[0]} to ${HAND_HEADS[1]}` : undefined;
  const ends = [point(along.low, 0), point(along.high, 0)];
  const nearJoin = (p: { x: number; y: number }) => Math.hypot(p.x - join.x, p.y - join.y);
  const [wrist, end] = nearJoin(ends[0]) <= nearJoin(ends[1]) ? ends : [ends[1], ends[0]];
  const touching = own.length > 0 && own.length < found.pixels.length ? "the hand's skin runs across the figure's middle (the hands touch, or the hand lies on the body): cut at the axis" : undefined;
  const reasons = joinReasons(reason, touching);
  return shapeChecked({
    side: piece.side < 0 ? "image-left" : "image-right",
    length,
    width: across.high - across.low + 1,
    status: reasons ? "unsure" : "found",
    ...(reasons ? { reason: reasons } : {}),
    lines: { length: { from: wrist, to: end }, width: { from: point(0, across.low), to: point(0, across.high) } },
    wrist,
    tip: end,
    piece,
    pixels: handPixels,
  });
}

/** The pixels on one side of the axis: a hand's own, where its skin runs on into the other hand's or across the body. */
function ownSide(pixels: Int32Array, width: number, axis: number, side: -1 | 1): Int32Array {
  return pixels.filter((at) => (side < 0 ? at % width < axis : at % width > axis));
}

/** The given wrist and hand tip, when both are placed with columns: a hand's length by the author's own marks. */
function givenHand(marks: Record<string, PixelMark>): { length: number; line: ReadingLine } | undefined {
  const wrist = marks.wrist;
  const tip = marks.handTip;
  if (wrist?.status !== "given" || tip?.status !== "given" || wrist.x === undefined || tip.x === undefined) return undefined;
  return { length: Math.hypot(tip.x - wrist.x, tip.y - wrist.y), line: { from: { x: wrist.x, y: wrist.y }, to: { x: tip.x, y: tip.y } } };
}

/** The pieces that read as arms or loose hands, and each one's hand or why it was skipped. */
export function handsOf(anatomy: Anatomy, image?: Raster): { arms: Piece[]; hands: Array<OneHand | { skipped: string }> } {
  const skin = image ? skinColor(image, anatomy) : undefined;
  const skinShare = (p: Piece) => {
    if (!image || !skin) return 0;
    let n = 0;
    for (const at of p.pixels) if (channelDistance(pixelAt(image, at % image.width, Math.floor(at / image.width)), skin) <= SKIN_REACH) n++;
    return n / p.pixels.length;
  };
  const handLike = (p: Piece) => p.kind !== "ear" && p.box.y > anatomy.marks.chin.y && p.box.y + p.box.h < (anatomy.marks.knee ?? anatomy.marks.crotch ?? anatomy.marks.ground).y && Math.max(p.box.w, p.box.h) <= HAND_ACROSS_HEADS * anatomy.headLength && skinShare(p) >= HAND_SKIN;
  const hip = (anatomy.marks.hip ?? anatomy.marks.crotch ?? anatomy.marks.ground).y;
  const shoulder = (anatomy.marks.shoulder ?? anatomy.marks.neck ?? anatomy.marks.chin).y;
  const armLike = (p: Piece) => p.kind === "other" && p.box.y >= shoulder && p.box.y <= hip && p.box.y + p.box.h > hip;
  const arms = anatomy.pieces.filter((p) => p.kind === "arm" || armLike(p) || handLike(p));
  return { arms, hands: arms.map((p) => handOf(p, anatomy, image, skin)) };
}

/** Both hands' length and width: each as the two sides' mean, unsure where the sides differ or the wrist is not told from the hand. */
export function readHands(anatomy: Anatomy, scale: ReadingScale, image?: Raster): Proportions {
  const out: Proportions = { readings: {}, missing: {} };
  const { arms, hands } = handsOf(anatomy, image);
  const read = hands.filter((h): h is OneHand => "length" in h);
  const given = givenHand(anatomy.marks);
  if (read.length === 0 && !given) {
    const why = hands.flatMap((h) => ("skipped" in h ? [h.skipped] : []));
    const reason = arms.length === 0 ? "no arm stands apart from the body in this view (arms against the body, or a pose that hides them): give landmarks=wrist:x,y;handTip:x,y" : why.join("; ");
    out.missing.handLength = reason;
    out.missing.handWidth = reason;
    return out;
  }
  const pair = (name: string, pick: (h: OneHand) => number, line: (h: OneHand) => ReadingLine): Reading | undefined => {
    if (read.length === 0) return undefined;
    const values = read.map(pick);
    const mean = median(values);
    let status: "found" | "unsure" = read.every((h) => h.status === "found") ? "found" : "unsure";
    const reasons = read.map((h) => (h.reason ? `${h.side}: ${h.reason}` : undefined));
    if (read.length === 2 && Math.abs(values[0] - values[1]) > SIDES_AGREE * Math.max(...values)) {
      status = "unsure";
      reasons.push(`the two hands differ by ${Math.round((100 * Math.abs(values[0] - values[1])) / Math.max(...values))}% (one is turned, bent or hidden): the mean is shown`);
    }
    return sizeReading(name, mean, scale, status, read.map(line), joinReasons(...reasons), read.length === 2 ? "both" : read[0].side);
  };
  const length = pair("handLength", (h) => h.length, (h) => h.lines.length);
  const width = pair("handWidth", (h) => h.width, (h) => h.lines.width);
  if (given) out.readings.handLength = sizeReading("handLength", given.length, scale, "given", [given.line]);
  else if (length) out.readings.handLength = length;
  if (width) out.readings.handWidth = width;
  else out.missing.handWidth = "no hand stands apart from the body in this view: the width is not read from the given wrist and tip";
  return out;
}

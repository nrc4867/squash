import type { Box } from "../core/box.ts";
import { pixelToHex } from "../core/color.ts";
import type { Raster } from "../raster/image.ts";
import type { Anatomy } from "./anatomy.ts";
import { readCentreMarks, type FaceMark } from "./face/centreLine.ts";
import { bestPair, eyeCandidates, FRONT_PAIR, TURNED_PAIR, type EyeCandidate } from "./face/eyes.ts";
import { readFaceBase, type FaceBase } from "./face/region.ts";
import type { FaceEye, FaceFinding } from "./face/types.ts";
import { placeReading, sizeReading, type Proportions, type ReadingLine, type ReadingScale } from "./readings.ts";

export { skinColor } from "./face/skin.ts";

/** The names the face reads, in the order the tools list them. */
export const FACE_NAMES = ["eyeLine", "eyeWidth", "eyeHeight", "eyeSpacing", "nose", "mouth", "chinLength"] as const;

/** The two eyes' widths, or heights, differ by more than this share of the larger before the reading is unsure. */
const EYES_ALIKE = 0.25;
/** The two eyes' middles differ in height by more than this share of the head's length before the eye line is unsure (a tilted head). */
const LEVEL = 0.05;
const TURNED = "three-quarter view: the head is turned, so places across it are foreshortened and the far eye is narrower";

function noFace(reason: string): Proportions {
  return { readings: {}, missing: Object.fromEntries(FACE_NAMES.map((n) => [n, reason])) };
}

function joined(...parts: Array<string | undefined | false>): string | undefined {
  const kept = parts.filter((p): p is string => !!p);
  return kept.length ? kept.join("; ") : undefined;
}

/** The face's middle line in window pixels: the middle of the skin area's rows from the eyes down, or the head's. */
function faceAxis(base: FaceBase): number {
  const rows = base.rows.filter((r) => r.y - base.win.y >= 0.4 * base.length && r.y - base.win.y <= 0.9 * base.length);
  if (rows.length === 0) return base.centre;
  return rows.reduce((s, r) => s + (r.l + r.r) / 2, 0) / rows.length - base.win.x;
}

function lineAcross(e: FaceEye): ReadingLine {
  const y = Math.round(e.box.y + e.box.h / 2);
  return { from: { x: e.box.x, y }, to: { x: e.box.x + e.box.w, y } };
}

function lineDown(e: FaceEye): ReadingLine {
  const x = Math.round(e.box.x + e.box.w / 2);
  return { from: { x, y: e.box.y }, to: { x, y: e.box.y + e.box.h } };
}

/**
 * The face's key places read from a front or three-quarter view: the eye line, each eye's width and height and the spacing, the nose,
 * the mouth and the chin's length below it. The face is first found as the skin area inside the head below the hair; eyes are the
 * outlines of lid line, lash and white together inside it, a pair about level and mirrored across the face; the nose and mouth are
 * small marks on the middle line below them, looked for inside a muzzle against the muzzle's own colour. What cannot be read is listed
 * with why; a side or back view reads none.
 */
export function readFace(image: Raster, anatomy: Anatomy, scale: ReadingScale): Proportions {
  const facing = anatomy.view.guess.facing;
  if (facing === "back") return noFace("a back view shows no face");
  if (facing === "side") return noFace("a side view shows one eye and no spacing: read the face from a front view");
  const found = readFaceBase(image, anatomy);
  if ("reason" in found) return noFace(found.reason);
  const { base } = found;
  const { win, length, headWidth } = base;
  const turned = facing === "three-quarter";
  const out: Proportions = { readings: {}, missing: {} };
  const sx = (x: number) => win.x + x;
  const sy = (y: number) => win.y + y;
  const sheetBox = (box: Box): Box => ({ x: sx(box.x), y: sy(box.y), w: box.w, h: box.h });
  const axis0 = turned ? faceAxis(base) : base.centre;
  const search = eyeCandidates(base);
  const pair = bestPair(search.candidates, axis0, length, headWidth, turned ? TURNED_PAIR : FRONT_PAIR);
  const lone = pair ? undefined : [...search.candidates].sort((a, b) => b.area - a.area)[0];
  const chosen: EyeCandidate[] = pair ? [...pair] : lone ? [lone] : [];
  const left = Math.min(...base.rows.map((r) => r.l));
  const right = Math.max(...base.rows.map((r) => r.r));
  const regionBox: Box = { x: left, y: base.rows[0].y, w: right - left + 1, h: base.rows[base.rows.length - 1].y - base.rows[0].y + 1 };
  const eyes: FaceEye[] = chosen.map((c) => ({ side: c.cx < axis0 ? "image-left" : "image-right", box: sheetBox(c.box), width: c.box.w, height: c.box.h, coveredTop: c.coveredTop }));
  const axis = pair ? (pair[0].cx + pair[1].cx) / 2 : axis0;
  const finding: FaceFinding = {
    region: { box: regionBox, rows: base.rows },
    axis: sx(axis),
    skin: pixelToHex(base.skinColor),
    ...(base.hairColor ? { hair: pixelToHex(base.hairColor) } : {}),
    eyes,
  };
  out.face = finding;
  const turnReason = turned ? TURNED : undefined;
  if (chosen.length > 0) {
    const level = chosen.reduce((s, c) => s + c.cy, 0) / chosen.length;
    const row = Math.round(sy(level));
    const tilt = chosen.length === 2 ? Math.abs(chosen[0].cy - chosen[1].cy) / length : 0;
    const single = chosen.length === 1 ? `only one eye is found${turned ? " (the far eye is hidden by the turn)" : ": the other may be under the fringe, drawn in the skin's colour, or joined to a brow or lash"}` : undefined;
    const lineWhy = joined(single, tilt > LEVEL && `the eyes sit ${Math.round(tilt * 100)}% of the head apart in height (a tilted head): the mean is shown`, turnReason);
    out.readings.eyeLine = placeReading("eyeLine", row, scale, lineWhy ? "unsure" : "found", [{ from: { x: regionBox.x, y: row }, to: { x: regionBox.x + regionBox.w, y: row } }], lineWhy);
    const sized = (name: "eyeWidth" | "eyeHeight", of: (e: FaceEye) => number, lines: (e: FaceEye) => ReadingLine, extra?: string) => {
      const values = eyes.map(of);
      const wide = Math.max(...values);
      const narrow = Math.min(...values);
      const unequal = values.length === 2 && narrow < (1 - EYES_ALIKE) * wide;
      const px = values.length === 2 && (unequal || turned) ? wide : values.reduce((s, v) => s + v, 0) / values.length;
      const why = joined(
        single,
        unequal && `the two eyes read ${values.map(Math.round).join(" and ")} pixels ${name === "eyeWidth" ? "across" : "high"}, ${Math.round((1 - narrow / wide) * 100)}% apart: the larger is shown, the smaller may be cut by a fringe, a turn or a lid drawn shut`,
        extra,
        turnReason,
      );
      out.readings[name] = sizeReading(name, px, scale, why ? "unsure" : "found", eyes.map(lines), why ?? "the eye's outline, lid line and lashes with its white", "both");
    };
    sized("eyeWidth", (e) => e.width, lineAcross);
    const covered = eyes.filter((e) => e.coveredTop);
    const free = eyes.filter((e) => !e.coveredTop);
    if (covered.length > 0 && free.length > 0) {
      const tall = free.reduce((s, e) => s + e.height, 0) / free.length;
      out.readings.eyeHeight = sizeReading("eyeHeight", tall, scale, "unsure", free.map(lineDown), `hair lies over the top of ${covered.length === 2 ? "both eyes" : `the ${covered[0].side} eye`}: the height is read from the other eye`, "both");
    } else {
      sized("eyeHeight", (e) => e.height, lineDown, covered.length > 0 ? "hair lies over the top of the eyes, so their height is cut short" : undefined);
    }
    if (chosen.length === 2) {
      const apart = Math.abs(chosen[1].cx - chosen[0].cx);
      const spacing: ReadingLine = { from: { x: sx(Math.round(chosen[0].cx)), y: row }, to: { x: sx(Math.round(chosen[1].cx)), y: row } };
      out.readings.eyeSpacing = sizeReading("eyeSpacing", apart, scale, turnReason ? "unsure" : "found", [spacing], joined("middle to middle", turnReason));
    } else {
      out.missing.eyeSpacing = "only one eye is found, so there is no spacing";
    }
  } else {
    const why =
      search.candidates.length === 0
        ? `no eye outline stands out inside the skin area below the hair (${base.hairColor ? "hair may lie over the eyes, " : ""}eyes drawn in the skin's colour, or too faint)`
        : "no feature inside the skin area is shaped and placed like an eye";
    for (const n of ["eyeLine", "eyeWidth", "eyeHeight", "eyeSpacing"]) out.missing[n] = why;
  }
  const marks = readCentreMarks(base, axis, chosen.map((c) => c.box), turned);
  if (marks.muzzle) finding.muzzle = sheetBox(marks.muzzle);
  const paired = chosen.length === 2 && !turned;
  const markStatus = marks.crowded || !paired ? "unsure" : "found";
  const markWhy = (what?: string) => joined(marks.crowded && "more than two marks lie below the eyes: the lowest is taken for the mouth and the highest above it for the nose", !paired && (turned ? TURNED : "the eyes are not a pair, so the middle line is a guess"), what);
  const across = (m: FaceMark): ReadingLine => ({ from: { x: sx(m.box.x), y: Math.round(sy(m.cy)) }, to: { x: sx(m.box.x + m.box.w), y: Math.round(sy(m.cy)) } });
  if (marks.nose) {
    finding.nose = sheetBox(marks.nose.box);
    out.readings.nose = placeReading("nose", sy(marks.nose.cy), scale, markStatus, [across(marks.nose)], markWhy(marks.muzzle ? "the mark in the muzzle nearest its top" : undefined));
  } else {
    out.missing.nose = marks.noseWhy ?? "the nose is not read";
  }
  if (marks.mouth) {
    finding.mouth = sheetBox(marks.mouth.box);
    out.readings.mouth = placeReading("mouth", sy(marks.mouth.cy), scale, markStatus, [across(marks.mouth)], markWhy());
    const chin = win.y + win.h - 1;
    const x = sx(Math.round(axis));
    out.readings.chinLength = sizeReading("chinLength", chin - sy(marks.mouth.cy), scale, markStatus, [{ from: { x, y: Math.round(sy(marks.mouth.cy)) }, to: { x, y: chin } }], joined("from the mouth to the chin's row", markWhy()));
  } else {
    out.missing.mouth = marks.mouthWhy ?? "the mouth is not read";
    out.missing.chinLength = out.missing.mouth;
  }
  return out;
}

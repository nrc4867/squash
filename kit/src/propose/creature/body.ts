import { add, apply, compose, cross, IDENTITY, scale, sub, turnAbout, unit, type M3, type V3 } from "../../geom/vec3.ts";
import { DIRECTION, partFrame, profileFor } from "./frames.ts";
import { bendJoint, type ParamValues } from "./params.ts";
import type { CreaturePlan, PlanEnd, PlanPart } from "./plan.ts";
import { scaleAlong, scalesOf } from "./widths.ts";

/** Sections taken along each part, and round each section. */
const SECTIONS = 14;
export const ROUND = 14;

/** A part's width scale never drops under this, however its three places curve. */
const LEAST_SCALE = 0.2;

/** Rings that round a closed end over, each a further share of a quarter turn. */
const CAP_RINGS = 3;

/** A cross-section of a part: its middle, and its radius toward any direction across it. */
export interface Section {
  center: V3;
  /** The part's directions round it here, named as its profiles are. */
  round: Array<{ name: string; dir: V3 }>;
  radius: (name: string) => number;
  /** Share along the part. */
  t: number;
}

/** A part as built: its chain of joints (root, each joint, tip), its sections and the turn the sheet's pose gives it. */
export interface BuiltPart {
  part: PlanPart;
  joints: V3[];
  sections: Section[];
  turn: M3;
  length: number;
}

/** An end as built: an ellipsoid. */
export interface BuiltEnd {
  end: PlanEnd;
  center: V3;
  axes: [V3, V3, V3];
  radii: V3;
}

/** A body in head units, standing on y 0: its parts, ends, and landmarks by id. */
export interface BuiltBody {
  parts: Map<string, BuiltPart>;
  ends: BuiltEnd[];
  landmarks: Map<string, V3>;
  /** Where the root part's origin stands above the ground. */
  rootHeight: number;
}

/** A radius curve's value at a share along: straight between the stations. */
export function atStations(stations: readonly number[], values: readonly number[], t: number): number {
  if (t <= stations[0]) return values[0];
  for (let i = 1; i < stations.length; i++) {
    if (t <= stations[i]) {
      const w = (t - stations[i - 1]) / (stations[i] - stations[i - 1] || 1);
      return values[i - 1] + (values[i] - values[i - 1]) * w;
    }
  }
  return values[values.length - 1];
}

/** The radius of a part's profile at share `t`, its default times its width scale. */
export function profileRadius(part: PlanPart, values: ParamValues, name: string, t: number): number {
  return radiusReader(part, values)(name, t);
}

/** A part's radius by profile name and share along it, its width scales read once. */
export function radiusReader(part: PlanPart, values: ParamValues): (name: string, t: number) => number {
  const scales = new Map<string, number[]>();
  for (const profile of Object.keys(part.radii)) scales.set(profile, scalesOf(values, part.id, profile));
  return (name, t) => {
    const profile = profileFor(part, name);
    const base = part.radii[profile];
    const s = scales.get(profile);
    if (!base || !s) return 0;
    return atStations(part.stations, base, t) * Math.max(LEAST_SCALE, scaleAlong(s, t));
  };
}

/** A section's radius toward a direction across it: the quarter ellipse between the two profiles either side. */
export function radiusToward(section: Section, dir: V3): number {
  const [a, b, c, d] = section.round;
  const x = dir[0] * a.dir[0] + dir[1] * a.dir[1] + dir[2] * a.dir[2];
  const y = dir[0] * b.dir[0] + dir[1] * b.dir[1] + dir[2] * b.dir[2];
  const rx = section.radius(x >= 0 ? a.name : c.name);
  const ry = section.radius(y >= 0 ? b.name : d.name);
  const n = Math.hypot(x, y) || 1;
  return 1 / Math.sqrt((x / n / rx) ** 2 + (y / n / ry) ** 2);
}

/** The point at share `u` along a chain of joints placed at shares `at`. */
function chainAt(joints: readonly V3[], at: readonly number[], u: number): V3 {
  for (let i = 1; i < joints.length; i++) {
    if (u <= at[i] || i === joints.length - 1) {
      const w = (u - at[i - 1]) / (at[i] - at[i - 1] || 1);
      return add(joints[i - 1], scale(sub(joints[i], joints[i - 1]), w));
    }
  }
  return joints[joints.length - 1];
}

function jointShares(part: PlanPart): number[] {
  return [0, ...(part.joints ?? []).map((j) => j.at).sort((a, b) => a - b), 1];
}

/**
 * The plan's body under `values`: lengths and widths scaled, and, when `posed`, turned as the sheet shows it (the root pitched, each
 * part swung, a limb spread, its middle joint bent); at rest otherwise. Grounded on its lowest point.
 */
export function buildBody(plan: CreaturePlan, values: ParamValues, posed: boolean): BuiltBody {
  const parts = new Map<string, BuiltPart>();
  for (const part of plan.parts) {
    const frame = partFrame(part);
    const length = part.length * values.get(`${part.id}.length`, 1);
    let turn: M3 = IDENTITY;
    let root: V3 = [0, 0, 0];
    const parent = part.on ? parts.get(part.on) : undefined;
    if (parent) {
      const u = part.at ?? 1;
      const axis = chainAt(parent.joints, jointShares(parent.part), u);
      const offset = part.offset ?? [0, 0, 0];
      const parentRadius = radiusReader(parent.part, values);
      const spread = (parentRadius("left", u) + parentRadius("outer", u)) /
        ((parent.part.radii.left ? atStations(parent.part.stations, parent.part.radii.left, u) : 0) + (parent.part.radii.outer ? atStations(parent.part.stations, parent.part.radii.outer, u) : 0) || 1);
      root = add(axis, apply(parent.turn, scale(offset, spread || 1)));
      turn = parent.turn;
    }
    if (posed) {
      if (!parent) turn = turnAbout(DIRECTION.left, values.get("root.pitch"));
      else {
        turn = compose(turnAbout(DIRECTION.left, values.get(`${part.id}.swing`)), turn);
        if (part.kind === "limb") turn = compose(turnAbout(DIRECTION.forward, -values.get(`${part.id}.spread`)), turn);
      }
    }
    const along = apply(turn, frame.along);
    const shares = jointShares(part);
    const bendAt = bendJoint(part);
    const bend = posed && bendAt !== undefined ? turnAbout(DIRECTION.left, values.get(`${part.id}.bend`)) : IDENTITY;
    const joints: V3[] = shares.map((s) => add(root, scale(along, s * length)));
    if (bendAt !== undefined && bend !== IDENTITY) {
      const k = shares.indexOf(bendAt);
      for (let i = k + 1; i < joints.length; i++) joints[i] = add(joints[k], apply(bend, sub(joints[i], joints[k])));
    }
    const round = frame.round.map((r) => ({ name: r.name, dir: apply(turn, r.dir) }));
    const radius = radiusReader(part, values);
    const sections: Section[] = [];
    for (let s = 0; s <= SECTIONS; s++) {
      const t = s / SECTIONS;
      const center = chainAt(joints, shares, t);
      const after = bendAt !== undefined && t > bendAt ? round.map((r) => ({ name: r.name, dir: apply(bend, r.dir) })) : round;
      const cache = new Map<string, number>();
      sections.push({ center, round: after, radius: (name) => cache.get(name) ?? cache.set(name, radius(name, t)).get(name)!, t });
    }
    if (part.closed) {
      sections.unshift(...caps(sections[0], scale(unit(sub(joints[0], joints[1])), 1)).reverse());
      sections.push(...caps(sections[sections.length - 1], unit(sub(joints[joints.length - 1], joints[joints.length - 2]))));
    }
    parts.set(part.id, { part, joints, sections, turn: bendAt !== undefined ? compose(bend, turn) : turn, length });
  }
  const ends = plan.ends.map((end) => buildEnd(end, parts, values));
  const body: BuiltBody = { parts, ends, landmarks: new Map(), rootHeight: 0 };
  const ground = lowest(body);
  shift(body, [0, -ground, 0]);
  body.rootHeight = -ground;
  for (const mark of plan.landmarks) {
    const built = parts.get(mark.part);
    if (built) body.landmarks.set(mark.id, chainAt(built.joints, jointShares(built.part), mark.at));
    const end = body.ends.find((e) => e.end.id === mark.part);
    if (end) body.landmarks.set(mark.id, add(end.center, scale(end.axes[1], (mark.at * 2 - 1) * end.radii[1])));
  }
  return body;
}

/** Rings past a closed end, each smaller, rounding it over. */
function caps(end: Section, outward: V3): Section[] {
  const out: Section[] = [];
  for (let k = 1; k <= CAP_RINGS; k++) {
    const angle = (k / (CAP_RINGS + 0.5)) * (Math.PI / 2);
    const reach = Math.min(...end.round.map((r) => end.radius(r.name)));
    const shrink = Math.cos(angle);
    out.push({ center: add(end.center, scale(outward, reach * Math.sin(angle))), round: end.round, radius: (name) => end.radius(name) * shrink, t: end.t });
  }
  return out;
}

function buildEnd(end: PlanEnd, parts: Map<string, BuiltPart>, values: ParamValues): BuiltEnd {
  const part = parts.get(end.on)!;
  const shares = jointShares(part.part);
  const at = end.at === undefined ? part.joints[part.joints.length - 1] : chainAt(part.joints, shares, end.at);
  let axes: [V3, V3, V3] = [apply(part.turn, DIRECTION.forward), apply(part.turn, DIRECTION.up), apply(part.turn, DIRECTION.left)];
  if (end.follow) {
    const up = unit(sub(part.joints[part.joints.length - 2], part.joints[part.joints.length - 1]));
    const left = unit(cross(axes[0], up));
    axes = [unit(cross(up, left)), up, left];
  }
  const forward = end.center[0] + values.get(`${end.id}.shift.0`);
  const up = end.center[1] + values.get(`${end.id}.shift.1`);
  const center = add(at, add(add(scale(axes[0], forward), scale(axes[1], up)), scale(axes[2], end.center[2])));
  const radii: V3 = [0, 1, 2].map((i) => end.radii[i] * values.get(`${end.id}.size.${i}`, 1)) as V3;
  return { end, center, axes, radii };
}

/** A ring of a body's surface: the part or end it belongs to, its outline points, and whether it is the right side of a mirrored pair. */
export interface BodyRing {
  owner: string;
  points: V3[];
  mirrored?: boolean;
}

/** Every place a body's surface passes through, as rings: its parts' sections and its ends' slices, each with its outline points. */
export function bodyRings(body: BuiltBody): BodyRing[][] {
  const out: BodyRing[][] = [];
  const mirror = (rings: BodyRing[]) => rings.map((r) => ({ owner: r.owner, points: r.points.map(([x, y, z]): V3 => [x, y, -z]), mirrored: true }));
  for (const built of body.parts.values()) {
    const rings = built.sections.map((s) => ({ owner: built.part.id, points: ringPoints(s) }));
    out.push(rings);
    if (built.part.kind === "limb") out.push(mirror(rings));
  }
  for (const end of body.ends) {
    const rings = endRings(end).map((points) => ({ owner: end.end.id, points }));
    out.push(rings);
    if (endIsPaired(body, end.end)) out.push(mirror(rings));
  }
  return out;
}

/** Whether an end is a mirrored pair: a pair of its own, or on a limb. */
export function endIsPaired(body: BuiltBody, end: PlanEnd): boolean {
  return end.pair === true || body.parts.get(end.on)?.part.kind === "limb";
}

export function ringPoints(section: Section): V3[] {
  const [a, b] = section.round;
  const points: V3[] = [];
  for (let k = 0; k < ROUND; k++) {
    const phi = (2 * Math.PI * k) / ROUND;
    const dir = add(scale(a.dir, Math.cos(phi)), scale(b.dir, Math.sin(phi)));
    points.push(add(section.center, scale(dir, radiusToward(section, dir))));
  }
  return points;
}

/** An end's ellipsoid as rings stacked along its up axis. */
export function endRings(end: BuiltEnd): V3[][] {
  const rings: V3[][] = [];
  const slices = 7;
  for (let s = 0; s <= slices; s++) {
    const theta = -Math.PI / 2 + (Math.PI * s) / slices;
    const lift = Math.sin(theta);
    const wide = Math.cos(theta);
    const ring: V3[] = [];
    for (let k = 0; k < ROUND; k++) {
      const phi = (2 * Math.PI * k) / ROUND;
      ring.push(
        add(
          end.center,
          add(add(scale(end.axes[0], end.radii[0] * wide * Math.cos(phi)), scale(end.axes[1], end.radii[1] * lift)), scale(end.axes[2], end.radii[2] * wide * Math.sin(phi))),
        ),
      );
    }
    rings.push(ring);
  }
  return rings;
}

function lowest(body: BuiltBody): number {
  let low = Infinity;
  for (const rings of bodyRings(body)) for (const ring of rings) for (const p of ring.points) low = Math.min(low, p[1]);
  return low;
}

function shift(body: BuiltBody, by: V3): void {
  for (const built of body.parts.values()) {
    built.joints = built.joints.map((j) => add(j, by));
    built.sections = built.sections.map((s) => ({ ...s, center: add(s.center, by) }));
  }
  for (const end of body.ends) end.center = add(end.center, by);
}

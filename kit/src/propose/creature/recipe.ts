import { figure, type FigureOutline } from "../../figure/index.ts";
import { roundTo } from "../../geom/point.ts";
import { vToward, type Direction, type Rest } from "../../frames/rest.ts";
import { figurePart, tubePart } from "../../parts/generic.ts";
import { pair } from "../../parts/pairs.ts";
import { joint, socket } from "../../parts/rig.ts";
import { recipe } from "../../recipe.ts";
import type { BonePose, Recipe, RecipePart, RecipeSocket } from "../../schema/recipe.ts";
import { endIsPaired, radiusReader, type BuiltBody } from "./body.ts";
import { eased, unimodal } from "./drawn/profile.ts";
import { ParamValues } from "./params.ts";
import { add, dot, scale, sub, unit, type V3 } from "../../geom/vec3.ts";
import { tube } from "../../shapes/tube.ts";
import { outline as shapeOutline } from "../../shapes/shape.ts";
import { leaf } from "../../shapes/teardrop.ts";
import type { DrawnHead } from "./drawn/head.ts";
import type { OffPart } from "./drawn/offParts.ts";
import { partFrame } from "./frames.ts";
import type { CreaturePlan, PlanPart } from "./plan.ts";

/** A template part's size stays within what templates take. */
const LEAST_SIZE = 0.4;
const MOST_SIZE = 2.5;

/** A part read off a stand-off layer (an ear): its half widths as shares of its length, where it is widest, and its lean out, kept within these. */
const LEAST_OFF_WIDTH = 0.08;
const MOST_OFF_WIDTH = 0.6;
const LEAST_WIDEST = 0.15;
const MOST_WIDEST = 0.6;
const OFF_LEAN = 80;

/** The colour slot every proposed part takes. */
const SKIN = "skin";
const SKIN_COLOR = "#d9b38c";
const INK = "#1c1617";

/** The id a part takes in the recipe: a pair's entry is named for both sides. */
export function entryId(id: string, paired: boolean): string {
  if (!paired) return id;
  return id.endsWith("foot") ? `${id.slice(0, -4)}feet` : `${id}s`;
}

/**
 * The recipe of a proposed body: its parts as figure parts on a blank plan, fitted to `outline` drawn at rest, each end as its template
 * sized to the body, sockets where the plan puts each part and end.
 */
export function proposalRecipe(
  plan: CreaturePlan,
  body: BuiltBody,
  unit: number,
  outline: FigureOutline,
  id: string,
  fitted: { drawn?: ReadonlyMap<string, DrawnHead>; values?: ParamValues; off?: readonly OffPart[] } = {},
): Recipe {
  const drawn = fitted.drawn ?? new Map<string, DrawnHead>();
  const values = fitted.values ?? new ParamValues([]);
  const sockets = new Map<string, RecipeSocket[]>();
  const socketOn = (partId: string, s: Omit<RecipeSocket, "n">): string => {
    const list = sockets.get(partId) ?? [];
    const n = list.length + 1;
    list.push({ ...s, n });
    sockets.set(partId, list);
    return `socket.${n}`;
  };
  const restOf = new Map(plan.parts.map((p) => [p.id, p.rest]));
  const attachments: Array<{ part: PlanPart; socket?: string }> = plan.parts.map((part) => ({ part }));
  for (const a of attachments) {
    const { part } = a;
    if (!part.on) continue;
    const parentRest = restOf.get(part.on)! as Rest;
    const [, toward] = part.facing ?? [parentRest, "up"];
    const placed = rootPlace(body, part, unit);
    const v = placed ? placed.v : vToward(parentRest, toward as Direction);
    a.socket = socketOn(part.on, {
      ...socket(0, part.at ?? 1, v),
      ...(placed && placed.reach > 0 ? { reach: roundTo(placed.reach) } : {}),
      ...(part.sink !== undefined ? { sink: part.sink } : {}),
      ...(part.kind === "limb" ? { mirrored: true } : {}),
    });
  }
  const endSockets = new Map<string, string>();
  for (const end of plan.ends) {
    if (!end.recipe || end.recipe.within || end.recipe.socket) continue;
    const head = drawn.get(end.id);
    if (head && end.recipe.draw === "forward") {
      const built = body.ends.find((e) => e.end.id === end.id)!;
      const placed = pointPlace(body, end.on, add(built.center, scale(built.axes[0], head.root)), unit);
      endSockets.set(end.id, socketOn(end.on, { ...socket(0, placed.u, placed.v, { sink: 0 }), ...(placed.reach > 0 ? { reach: roundTo(placed.reach) } : {}) }));
      continue;
    }
    const seat = end.recipe.seat;
    if (seat) {
      endSockets.set(end.id, socketOn(end.on, { ...socket(0, seat.u, seat.v, seat.sink === undefined ? {} : { sink: seat.sink }), ...(end.pair ? { mirrored: true } : {}) }));
      continue;
    }
    const rest = restOf.get(end.on)! as Rest;
    const toward = end.pair ? "left" : end.at === 0 ? "up" : alongEnd(rest);
    endSockets.set(end.id, socketOn(end.on, { ...socket(0, end.at ?? seatShare(body, end.id), vToward(rest, toward as Direction), { sink: 0 }), ...(end.pair ? { mirrored: true } : {}) }));
  }
  const parts: RecipePart[] = [];
  for (const { part, socket: on } of attachments) {
    const built = body.parts.get(part.id)!;
    const paired = part.kind === "limb";
    const make = part.figure === false ? (base: Parameters<typeof figurePart>[0]) => ownTube(base, part, values, built.length, unit) : (base: Parameters<typeof figurePart>[0]) =>
      figurePart(base, { rest: part.rest, length: roundTo(built.length * unit), ...(part.closed ? { closed: true } : {}) });
    parts.push(
      make(
        {
          id: entryId(part.id, paired),
          role: part.role,
          color: SKIN,
          inkJoin: false,
          ...(paired ? pair(part.id) : {}),
          ...(part.on ? { on: entryId(part.on, false), socket: on } : {}),
          ...(part.outward ? { outward: part.outward } : {}),
          ...(part.lean ? { lean: part.lean } : {}),
          sockets: (sockets.get(part.id) ?? []).map((s) => ({ ...s, u: roundTo(s.u), v: roundTo(s.v) })),
          joints: (part.joints ?? []).map((j) => joint(Number(j.name.split(".")[1]), j.at, { low: j.low, high: j.high })),
        },
      ),
    );
  }
  for (const end of plan.ends) {
    if (!end.recipe) continue;
    const built = body.ends.find((e) => e.end.id === end.id)!;
    const paired = endIsPaired(body, end);
    const hostPaired = body.parts.get(end.on)?.part.kind === "limb";
    const within = end.recipe.within;
    const on = within ? entryId(within, false) : entryId(end.on, hostPaired);
    const head = drawn.get(end.id);
    const size = head ? 1 : Math.min(MOST_SIZE, Math.max(LEAST_SIZE, (2 * built.radii[end.recipe.spans] * unit) / end.recipe.templateLength));
    parts.push({
      id: entryId(end.id, paired),
      template: end.recipe.template,
      on,
      socket: end.recipe.socket ?? endSockets.get(end.id)!,
      role: end.recipe.role,
      color: SKIN,
      inkJoin: false,
      size: roundTo(size, 3),
      ...(head && end.recipe.draw ? { outlines: headOutlines(body, end.id, head, end.recipe.draw, unit) } : {}),
      ...(paired ? pair(end.id) : {}),
      ...(end.recipe.fields ?? {}),
    } as RecipePart);
  }
  for (const off of fitted.off ?? []) {
    const spec = plan.drawnOff?.[off.kind];
    if (!spec) continue;
    parts.push({
      id: entryId(spec.id, true),
      template: spec.template,
      on: spec.on,
      socket: spec.socket,
      role: spec.role,
      color: SKIN,
      ...pair(spec.id),
      size: 1,
      outward: roundTo(Math.max(-OFF_LEAN, Math.min(OFF_LEAN, off.outward - spec.tilt)), 1),
      ...(spec.fields ?? {}),
      outlines: {
        top: shapeOutline(
          leaf({
            length: roundTo(off.length * unit),
            width: roundTo(Math.max(LEAST_OFF_WIDTH * off.length, Math.min(MOST_OFF_WIDTH * off.length, off.width)) * unit),
            base: roundTo(Math.max(LEAST_OFF_WIDTH * off.length, Math.min(off.width, off.base)) * unit),
            widest: roundTo(Math.max(LEAST_WIDEST, Math.min(MOST_WIDEST, off.widest))),
          }),
        ),
      },
      ...(spec.params ? { params: spec.params } : {}),
    } as RecipePart);
  }
  return recipe({
    id,
    name: `Proposed ${plan.name.toLowerCase()} body`,
    plan: "blank",
    "x-features": `bare body proposed from a sheet on the ${plan.id} plan, at rest (${plan.rest})`,
    palette: [
      { slot: SKIN, color: SKIN_COLOR },
      { slot: "ink", color: INK },
    ],
    parts,
    figure: figure(outline, { rootHeight: roundTo(body.rootHeight * unit), pose: [] }),
    ...(plan.recipe.displayPose ? { displayPose: plan.recipe.displayPose as BonePose[] } : {}),
    ...(plan.recipe.poses ? { poses: plan.recipe.poses as Record<string, BonePose[]> } : {}),
  });
}

/** A part the figure leaves out, as a tube of its fitted radii at its plan's stations, each profile rising to its widest and falling after. */
function ownTube(base: Parameters<typeof figurePart>[0], part: PlanPart, values: ParamValues, length: number, bodyUnit: number): RecipePart {
  const radius = radiusReader(part, values);
  const profiles = Object.fromEntries(
    Object.keys(part.radii).map((name) => [name, eased(unimodal(part.stations.map((t) => radius(name, t))), 0.35).map((r) => roundTo(r * bodyUnit))]),
  );
  return tubePart(base, {
    rest: part.rest,
    length: roundTo(length * bodyUnit),
    at: part.stations.map((t) => roundTo(t * length * bodyUnit)),
    profiles,
    ...(part.closed ? { closed: true } : {}),
  });
}

/** A root this near its parent's axis, in body units, sits on it. */
const ON_AXIS = 1e-4;

/**
 * Where a part's root sits off its parent's axis at rest, as its socket's reach (body units) and v round the parent: the proposal
 * places every root itself, so the figure fit takes them as given.
 */
function rootPlace(body: BuiltBody, part: PlanPart, bodyUnit: number): { reach: number; v: number } | undefined {
  const placed = pointPlace(body, part.on!, body.parts.get(part.id)!.joints[0], bodyUnit, part.at ?? 1);
  return placed.reach < ON_AXIS ? undefined : placed;
}

/**
 * Where a body place lies on a part at rest: its share along the part's axis (or at `u`), and its reach (body units) and v round the
 * part off the axis there.
 */
function pointPlace(body: BuiltBody, partId: string, point: V3, bodyUnit: number, u?: number): { u: number; reach: number; v: number } {
  const parent = body.parts.get(partId)!;
  const ends = parent.joints;
  const span = sub(ends[ends.length - 1], ends[0]);
  const along = unit(span);
  const share = u ?? Math.max(0, Math.min(1, dot(sub(point, ends[0]), along) / (Math.hypot(...span) || 1)));
  const axis = add(ends[0], scale(span, share));
  const off = sub(point, axis);
  const across = sub(off, scale(along, dot(off, along)));
  const reach = Math.hypot(...across) * bodyUnit;
  const frame = partFrame(parent.part);
  const v = Math.atan2(dot(across, frame.round[1].dir), dot(across, frame.round[0].dir)) / (2 * Math.PI);
  return { u: share, reach, v: v - Math.floor(v) };
}

/**
 * A drawn head's Side and Top: a closed tube along its axis from its root, in body units. Upright, its axis runs from the chin on the
 * neck's axis, so the profiles toward the face and the back are moved by how far the head's middle stands forward of that axis.
 */
function headOutlines(body: BuiltBody, endId: string, head: DrawnHead, draw: "upright" | "forward", bodyUnit: number) {
  const built = body.ends.find((e) => e.end.id === endId)!;
  const s = (values: readonly number[], by = 0) => values.map((v) => roundTo(Math.max(0.02, v + by) * bodyUnit));
  let shift = 0;
  if (draw === "upright") {
    const neck = body.parts.get(built.end.on)!;
    const seat = pointPlace(body, built.end.on, built.center, bodyUnit);
    const axisAt = add(neck.joints[0], scale(sub(neck.joints[neck.joints.length - 1], neck.joints[0]), seat.u));
    shift = dot(sub(built.center, axisAt), built.axes[0]);
  }
  const profiles =
    draw === "upright"
      ? { front: s(head.profiles.front, shift), back: s(head.profiles.back, -shift), left: s(head.profiles.left) }
      : { up: s(head.profiles.up), down: s(head.profiles.down), left: s(head.profiles.left) };
  return tube({ rest: draw === "upright" ? "up" : "forward", length: roundTo(head.length * bodyUnit), closed: true, at: head.at.map((a) => roundTo(a * bodyUnit)), profiles });
}

/** Shares along its part an end on a part's tip may sit back to. */
const LEAST_SEAT = 0.3;

/**
 * Where along its part an end on the part's tip sits: where its near edge meets the part's axis, so a head reaching down past the end of
 * its neck sits on the neck at its chin.
 */
function seatShare(body: BuiltBody, endId: string): number {
  const end = body.ends.find((e) => e.end.id === endId)!;
  const part = body.parts.get(end.end.on)!;
  const tip = part.joints[part.joints.length - 1];
  const along = unit(sub(tip, part.joints[part.joints.length - 2]));
  const reach = Math.max(...end.axes.map((axis, i) => Math.abs(dot(axis, along)) * end.radii[i]));
  const near = dot(sub(end.center, tip), along) - reach;
  return Math.max(LEAST_SEAT, Math.min(1, 1 + near / part.length));
}

/** Which way an end leaves the tip of a part resting `rest`: the side its socket faces. */
function alongEnd(rest: Rest): string {
  return rest === "down" ? "back" : rest === "up" ? "forward" : "up";
}


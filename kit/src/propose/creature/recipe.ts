import { figure, type FigureOutline } from "../../figure/index.ts";
import { roundTo } from "../../geom/point.ts";
import { vToward, type Direction, type Rest } from "../../frames/rest.ts";
import { figurePart } from "../../parts/generic.ts";
import { pair } from "../../parts/pairs.ts";
import { joint, socket } from "../../parts/rig.ts";
import { recipe } from "../../recipe.ts";
import type { BonePose, Recipe, RecipePart, RecipeSocket } from "../../schema/recipe.ts";
import { endIsPaired, type BuiltBody } from "./body.ts";
import { add, dot, scale, sub, unit } from "../../geom/vec3.ts";
import { partFrame } from "./frames.ts";
import type { CreaturePlan, PlanPart } from "./plan.ts";

/** A template part's size stays within what templates take. */
const LEAST_SIZE = 0.4;
const MOST_SIZE = 2.5;

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
export function proposalRecipe(plan: CreaturePlan, body: BuiltBody, unit: number, outline: FigureOutline, id: string): Recipe {
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
      ...(part.kind === "limb" ? { mirrored: true } : {}),
    });
  }
  const endSockets = new Map<string, string>();
  for (const end of plan.ends) {
    if (!end.recipe || end.recipe.within || end.recipe.socket) continue;
    const rest = restOf.get(end.on)! as Rest;
    const toward = end.pair ? "left" : end.at === 0 ? "up" : alongEnd(rest);
    endSockets.set(end.id, socketOn(end.on, { ...socket(0, end.at ?? seatShare(body, end.id), vToward(rest, toward as Direction), { sink: 0 }), ...(end.pair ? { mirrored: true } : {}) }));
  }
  const parts: RecipePart[] = [];
  for (const { part, socket: on } of attachments) {
    const built = body.parts.get(part.id)!;
    const paired = part.kind === "limb";
    parts.push(
      figurePart(
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
        { rest: part.rest, length: roundTo(built.length * unit), ...(part.closed ? { closed: true } : {}) },
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
    const size = Math.min(MOST_SIZE, Math.max(LEAST_SIZE, (2 * built.radii[end.recipe.spans] * unit) / end.recipe.templateLength));
    parts.push({
      id: entryId(end.id, paired),
      template: end.recipe.template,
      on,
      socket: end.recipe.socket ?? endSockets.get(end.id)!,
      role: end.recipe.role,
      color: SKIN,
      inkJoin: false,
      size: roundTo(size, 3),
      ...(paired ? pair(end.id) : {}),
      ...(end.recipe.fields ?? {}),
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

/** A root this near its parent's axis, in body units, sits on it. */
const ON_AXIS = 1e-4;

/**
 * Where a part's root sits off its parent's axis at rest, as its socket's reach (body units) and v round the parent: the proposal
 * places every root itself, so the figure fit takes them as given.
 */
function rootPlace(body: BuiltBody, part: PlanPart, bodyUnit: number): { reach: number; v: number } | undefined {
  const built = body.parts.get(part.id)!;
  const parent = body.parts.get(part.on!)!;
  const u = part.at ?? 1;
  const ends = parent.joints;
  const axis = add(ends[0], scale(sub(ends[ends.length - 1], ends[0]), u));
  const along = unit(sub(ends[ends.length - 1], ends[0]));
  const off = sub(built.joints[0], axis);
  const across = sub(off, scale(along, dot(off, along)));
  const reach = Math.hypot(...across) * bodyUnit;
  if (reach < ON_AXIS) return undefined;
  const frame = partFrame(parent.part);
  const v = Math.atan2(dot(across, frame.round[1].dir), dot(across, frame.round[0].dir)) / (2 * Math.PI);
  return { reach, v: v - Math.floor(v) };
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


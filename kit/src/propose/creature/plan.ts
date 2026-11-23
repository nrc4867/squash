import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type { V3 } from "../../geom/vec3.ts";
import type { RecipePart } from "../../schema/recipe.ts";

/** A part's role as a recipe names it. */
export type Role = NonNullable<RecipePart["role"]>;

export const PLAN_FORMAT = "squash-creature-plan/1";

/** The ways a part rests, as a recipe names them. */
export type Rest = "up" | "down" | "forward" | "back" | "out";

/** A body part the figure fits: a tube along a straight chain of joints. */
export interface PlanPart {
  id: string;
  /** A trunk runs on the body's middle; a limb is a mirrored pair, described as its left side. */
  kind: "trunk" | "limb";
  role: Role;
  /** The part it sits on, and where along that part's axis (0 at its root, 1 at its tip). */
  on?: string;
  at?: number;
  /** Where its root sits off its parent's axis at rest, head units [forward, up, left]: scaled by the parent's width there. */
  offset?: V3;
  /** The socket's facing for the recipe: `vToward(rest, toward)` of the parent. */
  facing?: [Rest, string];
  rest: Rest;
  outward?: number;
  lean?: number;
  /** How round its join to its parent is drawn: the radius, head units, its outline curves through where it leaves the parent. */
  join?: number;
  /** Whether its root lies within its parent's side (a thigh's top under the hips): the fit keeps the parent wide enough to cover it. */
  covered?: boolean;
  /** Whether the figure outline draws it; a part left out (a brush of a tail) is a tube of its own fitted radii. */
  figure?: boolean;
  /** How deep its root sinks into its parent, a share of its own depth (a shoulder 0.3 to 0.4, so the arm's top rounds into it). */
  sink?: number;
  /** Its length at rest in head units. */
  length: number;
  closed?: boolean;
  /** Its joints by name and share along it, with their limits for the recipe. */
  joints?: Array<{ name: string; at: number; low: number; high: number }>;
  /** Shares along the part the radii are given at. */
  stations: number[];
  /** Default radii in head units at each station, by profile name (left, front, back, up, down, outer, inner). */
  radii: Record<string, number[]>;
}

/** A part the figure leaves out (a head, hands, feet, paws, wings, a tail of feathers): an ellipsoid in the fit, a template in the recipe. */
export interface PlanEnd {
  id: string;
  /** The part it sits on, at its tip, or at `at` along it. */
  on: string;
  at?: number;
  /** Its middle off that place, head units [forward, up, left] at rest. */
  center: V3;
  /** Its half extents in head units [forward, up, left]. */
  radii: V3;
  /** Whether its extents follow its part's direction (a hand along its arm) rather than the body's axes (a head, a foot). */
  follow?: boolean;
  /** Whether it is a mirrored pair of its own on a trunk (wings); an end on a limb is paired with it. */
  pair?: boolean;
  /** Whether it hides the body under it (folded wings, a tail of feathers): readings it covers are inferred. */
  hides?: boolean;
  /** How the recipe builds it; an end with none only shapes the fit (a snout a template head carries). */
  recipe?: {
    template: string;
    role: Role;
    /** The template's own length at size 1 in body units, and which of the end's extents (0 forward, 1 up, 2 left) it spans. */
    templateLength: number;
    spans: 0 | 1 | 2;
    /** The template socket it sits on; a hand-made socket on its part when left out. */
    socket?: string;
    /** How the head is drawn from the sheet (`drawn/head.ts`): up from the chin, or forward from the back of the skull. Undrawn when left out. */
    draw?: "upright" | "forward";
    /** Where its hand-made socket sits on its part, when not where the end meets it: u along, v round, and how deep it sinks (1 when left out). */
    seat?: { u: number; v: number; sink?: number };
    /** Another end it sits on in the recipe, as a beak on its head, on that end's `socket`. */
    within?: string;
    fields?: Record<string, unknown>;
  };
}

/**
 * A creature body plan: which parts exist and how they rest (A-pose, a square stance, wings folded), the named landmarks, its default
 * proportions in head units (a head is the head's height), and how loose each hiding layer stands by default.
 */
export interface CreaturePlan {
  format: typeof PLAN_FORMAT;
  id: string;
  name: string;
  /** What the rest pose is, for people reading the plan. */
  rest: string;
  /** The end that is the head: its height is the unit. */
  head: string;
  /** How far the root part's origin stands above the ground at rest, in head units. */
  rootHeight: number;
  parts: PlanPart[];
  ends: PlanEnd[];
  /** Named landmarks: a place on a part at a share along it, as `knee` on the leg at 0.5. */
  landmarks: Array<{ id: string; part: string; at: number }>;
  /** Default looseness by hiding layer kind, in head units of stand-off. */
  layers: Record<string, number>;
  /**
   * Layer kinds that stand off the body rather than hide it (ears, horns, wings): what their boxes hold is fitted as no body, and the
   * proposal reads what a box holds past the head as that part, when the plan names it in `drawnOff`.
   */
  standOff?: string[];
  /** Parts read from a stand-off layer's box by kind: their template, the end they sit on and its socket, and their recipe fields. */
  drawnOff?: Record<
    string,
    {
      id: string;
      template: string;
      /** The template's own length at size 1 and its own lean out from straight up, degrees. */
      templateLength: number;
      tilt: number;
      on: string;
      socket: string;
      role: Role;
      params?: Record<string, number>;
      fields?: Record<string, unknown>;
    }
  >;
  /** The recipe's plan, palette colour and pose fields for the proposed body. */
  recipe: { displayPose?: unknown[]; poses?: Record<string, unknown[]> };
}

const PLAN_FOLDER = join(dirname(fileURLToPath(import.meta.url)), "plans");

/** The plans shipped with the kit, by name. */
export function shippedPlans(): string[] {
  return ["biped", "quadruped", "bird"];
}

/** A plan by shipped name or by path to a plan file. */
export function loadPlan(nameOrPath: string): CreaturePlan {
  const shipped = join(PLAN_FOLDER, `${nameOrPath}.plan.json`);
  const path = existsSync(shipped) ? shipped : nameOrPath;
  const plan = JSON.parse(readFileSync(path, "utf8")) as CreaturePlan;
  if (plan.format !== PLAN_FORMAT) throw new Error(`${path}: not a creature plan (format ${PLAN_FORMAT})`);
  checkPlan(plan, path);
  return plan;
}

/** Fails on a plan whose parts do not hang together. */
export function checkPlan(plan: CreaturePlan, where: string): void {
  const ids = new Set<string>();
  for (const part of plan.parts) {
    if (part.on && !ids.has(part.on)) throw new Error(`${where}: ${part.id} sits on ${part.on}, which no part before it is`);
    for (const [name, radii] of Object.entries(part.radii)) {
      if (radii.length !== part.stations.length) throw new Error(`${where}: ${part.id}.radii.${name} gives ${radii.length} radii for ${part.stations.length} stations`);
    }
    ids.add(part.id);
  }
  if (plan.parts.filter((p) => !p.on).length !== 1) throw new Error(`${where}: one part, the root, sits on none`);
  for (const end of plan.ends) if (!ids.has(end.on)) throw new Error(`${where}: end ${end.id} sits on ${end.on}, which is no part`);
  if (!plan.ends.some((e) => e.id === plan.head)) throw new Error(`${where}: the head ${plan.head} is no end`);
}

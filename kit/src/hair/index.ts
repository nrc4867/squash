// Hair: a volume over the skull and clumps over it, tufts for fur, and the layouts hair is built from (a fringe, a ragged fall, a
// cowlick, a crown swirl). See docs/hld/parts.md, Hair, and the skill's head-and-face.md.

import type { RecipePart, RecipeSocket, RecipeSpread } from "../schema/recipe.ts";
import { orient } from "../frames/orient.ts";
import { socket } from "../parts/rig.ts";
import { roundTo } from "../geom/point.ts";

/** The templates' own lengths at size 1, in body units: `templates.md`'s "outline x runs 0 to". */
export const HAIR_LENGTHS = { volume: 0.3, clump: 0.2, tuft: 0.12 } as const;

/** The hair volume's presets (`templates.md`, hair-volume). */
export type VolumePreset = "short" | "bob" | "long" | "ponytail";
/** The hair clump's presets (`templates.md`, hair-clump). */
export type ClumpPreset = "lock" | "spike" | "soft" | "fluff" | "wavy" | "strand" | "fine";
/** The hair tuft's presets (`templates.md`, hair-tuft). */
export type TuftPreset = "crown" | "cheek" | "point";

type Placed = Omit<RecipePart, "id" | "template" | "on" | "socket" | "size" | "preset">;

export interface HairVolumeSpec extends Placed {
  id?: string;
  /** The head it sits on. */
  on: string;
  /** The number of the hand-made socket it takes on the head; `hairVolume` returns that socket. */
  socket: number;
  /** The head's depth from the back of the skull to the forehead, in body units: the volume's length is this and twice `thickness`. */
  depth: number;
  /** The head's height from chin to crown, in body units; with it the volume's crown sits `thickness` over the skull's top, whatever its preset says. */
  height?: number;
  /** How far the hair stands off the skull, in body units. */
  thickness?: number;
  /** Where up the head the socket sits, its u: 0.5 the middle of a `head-round`. */
  at?: number;
  preset?: VolumePreset;
  /**
   * Palette slots for its areas: the hem, a narrow band along every lock's end (left out, the hair's colour runs to the tips), its parting
   * and its band of shine, which the clumps over it show too.
   */
  hem?: string;
  parting?: string;
  shine?: string;
}

/**
 * A `hair-volume` part on a head and the head's socket it takes, at the back of the skull, `thickness` off it: the volume runs from
 * there to the forehead, `depth` and twice `thickness` long. Its `areas` fill from `hem`, `parting` and `shine`.
 */
export function hairVolume(spec: HairVolumeSpec): { part: RecipePart; headSocket: RecipeSocket } {
  const { id = "hair", on, socket: n, depth, height, thickness = 0.02, at = 0.5, preset = "short", hem, parting, shine, areas, params, ...rest } = spec;
  const length = depth + 2 * thickness;
  const crown: Record<string, number> = height === undefined ? {} : { crown: roundTo((height * (1 - at) + thickness) / length) };
  const filled: Record<string, string> = { ...(areas ?? {}) };
  if (hem) filled.hem = hem;
  if (parting) filled.parting = parting;
  if (shine) filled.shine = shine;
  return {
    headSocket: socket(n, at, 0.5, { sink: 0, outset: thickness }),
    part: {
      id,
      template: "hair-volume",
      on,
      socket: `socket.${n}`,
      role: "hair",
      preset,
      size: roundTo(length / HAIR_LENGTHS.volume),
      ...(Object.keys(filled).length > 0 ? { areas: filled } : {}),
      params: { ...crown, ...(params ?? {}) },
      ...rest,
    },
  };
}

export interface HairClumpSpec extends Placed {
  id: string;
  on: string;
  /** The socket on that part: a template socket by name, or a hand-made socket's number. */
  socket: string | number;
  /** Its length along its curve, in body units. */
  length: number;
  preset?: ClumpPreset;
  /** The palette slot its tip area shows. */
  tip?: string;
}

/** A `hair-clump` part `length` long: a curved, tapering lock blended into what it roots on; its `tip` fills the tip area. */
export function hairClump(spec: HairClumpSpec): RecipePart {
  const { id, on, socket: s, length, preset = "lock", tip, areas, ...rest } = spec;
  return {
    id,
    template: "hair-clump",
    on,
    socket: typeof s === "number" ? `socket.${s}` : s,
    role: "hair",
    preset,
    size: roundTo(length / HAIR_LENGTHS.clump),
    ...(tip || areas ? { areas: { ...(areas ?? {}), ...(tip ? { tip } : {}) } } : {}),
    ...rest,
  };
}

export interface HairTuftSpec extends Placed {
  id: string;
  on: string;
  socket: string | number;
  /** Its length along its curl, in body units. */
  length: number;
  preset?: TuftPreset;
  tip?: string;
}

/** A `hair-tuft` part `length` long: a soft tuft in the field, blended into what it grows from: a crown tuft, cheek fluff, an ear tip. */
export function hairTuft(spec: HairTuftSpec): RecipePart {
  const { id, on, socket: s, length, preset = "crown", tip, areas, ...rest } = spec;
  return {
    id,
    template: "hair-tuft",
    on,
    socket: typeof s === "number" ? `socket.${s}` : s,
    role: "hair",
    preset,
    size: roundTo(length / HAIR_LENGTHS.tuft),
    ...(tip || areas ? { areas: { ...(areas ?? {}), ...(tip ? { tip } : {}) } } : {}),
    ...rest,
  };
}

/** A set of clumps and the sockets they take on the part they lie on. */
export interface ClumpSet {
  sockets: RecipeSocket[];
  parts: RecipePart[];
}

/** What every clump of a set shares: its id prefix, the part they lie on, the first socket number, slots and the clump fields. */
export interface SetSpec extends Omit<HairClumpSpec, "id" | "socket" | "length"> {
  id: string;
  /** The first hand-made socket number the set takes; it takes one a clump. */
  firstSocket: number;
}

export interface FringeSpec extends SetSpec {
  /** How many clumps across the front. */
  count: number;
  /** Their lengths, from the first (toward v `from`) to the last, in body units. */
  lengths: number[];
  /** Where round the volume's top they run, v from `from` to `to` (0 the middle, 0.1 toward its v 0.25 side), and how far forward, u. */
  from?: number;
  to?: number;
  u?: number;
  /** How far each sweeps sideways at its tip, a share of its length: positive toward the volume's v 0.25 side. */
  sweep?: number;
  /** How far each bows out over the brow at its middle, a share of its length. */
  bow?: number;
}

/**
 * A fringe: `count` clumps over the front of a volume from v `from` to `to`, hanging down over the brow, bowed out over it and swept
 * sideways together by `sweep`, the longest swept furthest: a side-swept fringe with a long lock across it. Its clumps take the `fine`
 * preset unless `preset` says otherwise: narrow, tapering to points that part at their tips while their roots blend into the volume.
 */
export function fringe(spec: FringeSpec): ClumpSet {
  const { id, firstSocket, count, lengths, from = -0.08, to = 0.08, u = 0.92, sweep = 0, bow = 0.12, preset = "fine", ...clump } = spec;
  const sockets: RecipeSocket[] = [];
  const parts: RecipePart[] = [];
  for (let i = 0; i < count; i++) {
    const share = count === 1 ? 0.5 : i / (count - 1);
    const v = from + (to - from) * share;
    const length = lengths[Math.min(i, lengths.length - 1)];
    sockets.push(socket(firstSocket + i, u, (v + 1) % 1, { sink: 0.5 }));
    parts.push(
      hairClump({
        ...clump,
        preset,
        id: `${id}-${i + 1}`,
        socket: firstSocket + i,
        length,
        rest: "down",
        lean: 25,
        curve: [
          [0.45, roundTo(bow), roundTo(sweep * 0.3)],
          [0.9, roundTo(bow * 0.8), roundTo(sweep)],
        ],
      }),
    );
  }
  return { sockets, parts };
}

export interface FallSpec extends Omit<HairClumpSpec, "id" | "socket"> {
  id: string;
  /** The spread socket's number. */
  socket: number;
  /** The area the clumps root over on the volume: u along it (0 the back, 1 the front) and v round it (0 the top). */
  area: { u: [number, number]; v: [number, number] };
  count: number;
  seed?: number;
  /** Degrees the clumps tilt from the normal toward down along the head: 60 to 80 lies them down. */
  droop?: number;
  /** How far their sizes vary, either way, as a share: a ragged hem. */
  jitter?: number;
  spacing?: number;
}

/**
 * A fall of clumps: one clump shown as copies over an area of a part, lying down over it and varying in size and turn. Copies are each
 * their own mesh, so a fall suits fur and manes; hair meant to merge into one mass takes clumps one by one.
 */
export function fall(spec: FallSpec): ClumpSet {
  const { id, socket: n, area, count, seed = 1, droop = 70, jitter = 0.25, spacing, ...clump } = spec;
  const spread: RecipeSpread = { count, seed, sizeJitter: jitter, leanJitter: 8, turnJitter: 12, droop, ...(spacing ? { spacing } : {}) };
  return {
    sockets: [{ n, u: roundTo(area.u[0]), to: roundTo(area.u[1]), v: roundTo(area.v[0]), toV: roundTo(area.v[1]), spread, sink: 0.5 }],
    parts: [hairClump({ ...clump, id, socket: n })],
  };
}

export interface CrownSpec extends SetSpec {
  /** How many clumps. */
  count: number;
  /** Their length, in body units. */
  length: number;
  /** Where the crown is on the volume, [u, v], and how far round it the clumps root, in u. */
  at?: [number, number];
  reach?: number;
  /** How far round the crown each clump turns as it leaves it, in degrees: 0 points straight out, 90 runs round it. */
  turn?: number;
  /** Degrees they rise off the head: high for a cowlick, low for a swirl lying flat. */
  rise?: number;
}

/**
 * Clumps rooted round the crown, each leaving it turned `turn` degrees round it and rising `rise` off the head: a crown swirl lying
 * flat, or with few short clumps and a high rise, a cowlick standing up.
 */
export function crown(spec: CrownSpec): ClumpSet {
  const { id, firstSocket, count, length, at = [0.42, 0], reach = 0.06, turn = 50, rise = 20, ...clump } = spec;
  const sockets: RecipeSocket[] = [];
  const parts: RecipePart[] = [];
  for (let i = 0; i < count; i++) {
    const angle = (2 * Math.PI * i) / count;
    const u = at[0] + reach * Math.cos(angle);
    const v = at[1] - (reach / 2) * Math.sin(angle);
    const radial: [number, number, number] = [Math.cos(angle), 0, Math.sin(angle)];
    const round: [number, number, number] = [-Math.sin(angle), 0, Math.cos(angle)];
    const t = (turn * Math.PI) / 180;
    const r = (rise * Math.PI) / 180;
    const flat: [number, number, number] = [radial[0] * Math.cos(t) + round[0] * Math.sin(t), 0, radial[2] * Math.cos(t) + round[2] * Math.sin(t)];
    const along: [number, number, number] = [flat[0] * Math.cos(r), Math.sin(r), flat[2] * Math.cos(r)];
    sockets.push(socket(firstSocket + i, u, (v + 1) % 1, { sink: 0.4 }));
    parts.push(hairClump({ ...clump, id: `${id}-${i + 1}`, socket: firstSocket + i, length, ...orient(along, [0, 1, 0]) }));
  }
  return { sockets, parts };
}
export * from "./locks.ts";

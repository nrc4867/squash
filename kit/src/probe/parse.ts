// Reads what `tools/recipe.sh probe` prints into numbers: each part's box, joints and sockets in body space
// (x forward, y up, z to the character's right).

export type Vec3 = [number, number, number];

export interface ProbeJoint {
  u: number;
  hinge: string;
  limits: [number, number];
  at: Vec3;
}

export interface ProbeSocket {
  u: number;
  v: number;
  at: Vec3;
  faces: string;
}

export interface ProbePart {
  id: string;
  template: string;
  origin: Vec3;
  box: { min: Vec3; max: Vec3 };
  joints: Record<string, ProbeJoint>;
  sockets: Record<string, ProbeSocket>;
  /** A head's face reach: x and y ranges of its `face` places. */
  face?: { x: [number, number]; y: [number, number] };
}

export interface ProbeReport {
  parts: Record<string, ProbePart>;
  /** The lowest point of every part read, which is where the ground is when the probe read them all. */
  ground: number;
  /** Lines that are not part, joint, socket or face lines: the answers to uv=, point= and face= questions. */
  answers: string[];
}

const NUMBER = String.raw`-?\d+(?:\.\d+)?(?:[eE]-?\d+)?`;
const VEC = String.raw`\[(${NUMBER}), (${NUMBER}), (${NUMBER})\]`;
const partLine = new RegExp(String.raw`^part (\S+): (.+?), origin ${VEC}, box ${VEC} to ${VEC}`);
const jointLine = new RegExp(
  String.raw`^\s+(\S+) joint (\S+): u (${NUMBER}), hinge (\S+), limits (${NUMBER}) to (${NUMBER}), at ${VEC}`,
);
const socketLine = new RegExp(String.raw`^\s+(\S+) socket (\S+): u (${NUMBER}), v (${NUMBER}), at ${VEC}, faces (.+)$`);
const faceLine = new RegExp(String.raw`^\s+(\S+) face: x (${NUMBER}) to (${NUMBER}) .*?, y (${NUMBER}) to (${NUMBER})`);
const frameLine = /^\s+\S+ frame: /;

function vec(values: string[]): Vec3 {
  return [Number(values[0]), Number(values[1]), Number(values[2])];
}

export function parseProbe(text: string): ProbeReport {
  const parts: Record<string, ProbePart> = {};
  const answers: string[] = [];
  for (const line of text.split(/\r?\n/)) {
    let m: RegExpMatchArray | null;
    if ((m = line.match(partLine))) {
      parts[m[1]] = {
        id: m[1],
        template: m[2],
        origin: vec(m.slice(3, 6)),
        box: { min: vec(m.slice(6, 9)), max: vec(m.slice(9, 12)) },
        joints: {},
        sockets: {},
      };
    } else if ((m = line.match(jointLine)) && parts[m[1]]) {
      parts[m[1]].joints[m[2]] = {
        u: Number(m[3]),
        hinge: m[4],
        limits: [Number(m[5]), Number(m[6])],
        at: vec(m.slice(7, 10)),
      };
    } else if ((m = line.match(socketLine)) && parts[m[1]]) {
      parts[m[1]].sockets[m[2]] = { u: Number(m[3]), v: Number(m[4]), at: vec(m.slice(5, 8)), faces: m[8] };
    } else if ((m = line.match(faceLine)) && parts[m[1]]) {
      parts[m[1]].face = { x: [Number(m[2]), Number(m[3])], y: [Number(m[4]), Number(m[5])] };
    } else if (!frameLine.test(line) && !line.startsWith("probe:") && line.trim().length > 0) {
      answers.push(line);
    }
  }
  const lows = Object.values(parts).map((part) => part.box.min[1]);
  return { parts, ground: lows.length > 0 ? Math.min(...lows) : 0, answers };
}

/** A body-space y as a height above the report's ground. */
export function heightAbove(report: ProbeReport, y: number): number {
  return y - report.ground;
}

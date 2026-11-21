import type { GroupRole, MarkGroup, MouthShapeValues } from "../schema/recipe.ts";

/** A spoken shape's name, as a mouth style and `set=head:mouth.shape:<name>` use it. */
export type MouthShapeName = "rest" | "mbp" | "ai" | "e" | "o" | "u" | "fv" | "l" | "etc";

/** The nine spoken shapes in the order the Face stage's Shapes strip and `faces=shapes` show them. */
export const MOUTH_SHAPES: readonly MouthShapeName[] = ["rest", "mbp", "ai", "e", "o", "u", "fv", "l", "etc"];

export interface MouthSpec {
  /** The lip line's marks; none for a beak, whose jaw opens on its seam. */
  lip?: string[];
  /** The lip line's fill slot, which shows the lips as bands that follow each shape. */
  lips?: string;
  /** The inside's fill slot. */
  inner?: string;
  /** The tongue's fill slot; with no tongue mark it shows a plain tongue as it lifts. */
  tongue?: string;
  /** Drawn tongue marks. */
  tongueMarks?: string[];
  /** The teeth's fill slot. */
  teeth?: string;
  /** Teeth marks. */
  teethMarks?: string[];
  /** Teeth and tongue marks drawn over the lips, open or shut, the lip ink breaking under them: fangs, a blep. */
  over?: string[];
  /** Whether the teeth are inked. */
  teethInk?: boolean;
  /** The mouth style by preset name; none takes the head template's. */
  style?: string;
  /** Spoken shapes this mouth says its own way; each value set replaces the style's. */
  shapes?: Partial<Record<MouthShapeName, MouthShapeValues>>;
  /** The group's id and name, `mouth` unless told. */
  id?: string;
}

/** A mouth group: lip line, inside, teeth and tongue roles with their fills and marks, resting closed. */
export function mouth(spec: MouthSpec = {}): MarkGroup {
  const over = (marks: string[] | undefined) => {
    const flagged = (marks ?? []).filter((m) => spec.over?.includes(m));
    return flagged.length > 0 ? { over: flagged } : {};
  };
  const roles: GroupRole[] = [
    { role: "inner", ink: false, ...(spec.inner ? { fill: spec.inner } : {}) },
    {
      role: "tongue",
      ink: false,
      ...(spec.tongue ? { fill: spec.tongue } : {}),
      ...(spec.tongueMarks ? { marks: spec.tongueMarks } : {}),
      ...over(spec.tongueMarks),
    },
    {
      role: "teeth",
      ink: spec.teethInk ?? false,
      ...(spec.teeth ? { fill: spec.teeth } : {}),
      ...(spec.teethMarks ? { marks: spec.teethMarks } : {}),
      ...over(spec.teethMarks),
    },
    { role: "lipLine", ...(spec.lip ? { marks: spec.lip } : {}), ...(spec.lips ? { fill: spec.lips } : {}) },
    { role: "corners" },
  ];
  const id = spec.id ?? "mouth";
  const group: MarkGroup = { id, name: id, roles, controls: { open: 0 } };
  if (spec.style) group.style = spec.style;
  if (spec.shapes) group.shapes = spec.shapes;
  return group;
}

/** A recipe build option that shows a spoken shape without saving it, as `set=` takes it. */
export function sayShape(shape: MouthShapeName, part = "head", group = "mouth"): string {
  return `set=${part}:${group}.shape:${shape}`;
}

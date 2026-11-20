import type { Measurement } from "../../src/sheet/measure/measurement.ts";
import type { AcrossReading } from "../../src/sheet/measure/across.ts";
import type { Reading } from "../../src/sheet/measure/readings.ts";
import type { Trace } from "../../src/sheet/read/trace.ts";

/** A measurement as a few lines: the view, its head count and each landmark's height, status and row. */
export function summary(m: Measurement): string {
  const lines = [
    `  view ${m.view.index} ${m.view.name} (${m.view.guess.facing}?): ${m.heads} heads, head ${m.head} of ${m.height}, axis x ${m.axis}, ground y ${m.ground}`,
  ];
  const entries = Object.entries(m.landmarks).sort((a, b) => a[1].y - b[1].y);
  for (const [name, l] of entries) {
    lines.push(`  ${name.padEnd(11)}${l.height.toFixed(4).padStart(8)}  ${l.heads.toFixed(2).padStart(5)}H  ${l.status.padEnd(9)} y ${l.y}${l.x !== undefined ? ` x ${l.x}` : ""}`);
    if (l.reason) lines.push(`              ${l.reason}`);
  }
  lines.push(...readingLines(Object.values(m.proportions), Object.entries(m.unread)));
  for (const w of m.warnings) lines.push(`  warning: ${w}`);
  for (const c of m.checks) lines.push(`  check ${c.landmarks.join(" + ")}: ${c.message}`);
  lines.push(`  widths ${Object.entries(m.widths).map(([k, v]) => `${k} ${v}`).join(", ")}`);
  for (const p of m.pieces) lines.push(`  ${p.kind} ${p.side}: tip x ${p.tip.x} y ${p.tip.y} (height ${p.tip.height})`);
  return lines.join("\n");
}

/** Each reading as a line, with its reason under it when it is not found, then each name that could not be read with why. */
export function readingLines(readings: readonly Reading[], unread: ReadonlyArray<[string, string]>): string[] {
  const lines: string[] = [];
  for (const r of readings) {
    const share = r.kind === "size" ? `${r.ofHead.toFixed(2)} head, ${r.ofHeight.toFixed(3)} height` : `${r.ofHead.toFixed(2)} down the head`;
    lines.push(`  ${r.name.padEnd(11)}${r.value.toFixed(4).padStart(8)}  ${share}  ${r.status}${r.side ? ` (${r.side})` : ""}`);
    if (r.reason) lines.push(`              ${r.reason}`);
  }
  for (const [name, why] of unread) lines.push(`  ${name.padEnd(11)} not read: ${why}`);
  return lines;
}

/** Each size read across views as a line, its views' own shares of the head beside it and its reason under it. */
export function acrossLines(readings: readonly AcrossReading[]): string[] {
  const lines: string[] = [];
  for (const r of readings) {
    const views = r.views.map((v) => `${v.view} ${v.ofHead.toFixed(2)}${v.status === "found" ? "" : ` ${v.status}`}`).join(", ");
    lines.push(`  ${r.name.padEnd(11)}${r.value.toFixed(4).padStart(8)}  ${r.ofHead.toFixed(2)} head  ${r.status}  (${views})`);
    if (r.reason) lines.push(`              ${r.reason}`);
  }
  return lines;
}

/** A trace as lines: its points and tolerance, and for a foot or hand in its own frame the size, the notes and the line to paste. */
export function traceLines(trace: Trace): string[] {
  const lines = [`  ${trace.part}: ${trace.points.length} points, tolerance ${trace.tolerance}, ${trace.closed ? "closed" : "open"}`];
  const outline = trace.outline;
  if (!outline) {
    lines.push(`  points (x from the axis, y up from the ground): ${trace.points.map(([x, y]) => `[${x}, ${y}]`).join(", ")}`);
    return lines;
  }
  if (trace.socket) lines.push(`  socket at x ${trace.socket[0].toFixed(1)} y ${trace.socket[1].toFixed(1)} (pixels)`);
  lines.push(`  ${outline.pane} size: ${Object.entries(outline.size).map(([k, v]) => `${k} ${v}`).join(", ")} (body units)`);
  for (const note of outline.notes) lines.push(`  ${note}`);
  lines.push(`  ${outline.line}`);
  return lines;
}

import type { Measurement } from "../../src/sheet/measure/measurement.ts";

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
  for (const c of m.checks) lines.push(`  check ${c.landmarks.join(" + ")}: ${c.message}`);
  lines.push(`  widths ${Object.entries(m.widths).map(([k, v]) => `${k} ${v}`).join(", ")}`);
  for (const p of m.pieces) lines.push(`  ${p.kind} ${p.side}: tip x ${p.tip.x} y ${p.tip.y} (height ${p.tip.height})`);
  return lines.join("\n");
}

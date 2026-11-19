import { roundTo } from "../geom/point.ts";

/** A colour as a recipe takes it: "#rrggbb", or [red, green, blue] from 0 to 1. */
export type Color = string | [number, number, number];
export type Rgb = [number, number, number];

const HEX = /^#([0-9a-fA-F]{6})$/;

export function toRgb(color: Color): Rgb {
  if (Array.isArray(color)) return [...color];
  const m = color.match(HEX);
  if (!m) throw new Error(`"${color}" is not a #rrggbb colour`);
  const n = parseInt(m[1], 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

export function toHex(color: Color): string {
  const [r, g, b] = toRgb(color).map((c) => Math.round(Math.min(1, Math.max(0, c)) * 255));
  return "#" + [r, g, b].map((c) => c.toString(16).padStart(2, "0")).join("");
}

/** A share `t` of the way from `a` to `b`. */
export function mix(a: Color, b: Color, t: number): Rgb {
  const x = toRgb(a);
  const y = toRgb(b);
  return [0, 1, 2].map((i) => roundTo(x[i] + (y[i] - x[i]) * t, 3)) as Rgb;
}

/** Lighter toward white for positive `amount` (0 to 1), darker toward black for negative. */
export function shade(color: Color, amount: number): Rgb {
  return amount >= 0 ? mix(color, [1, 1, 1], amount) : mix(color, [0, 0, 0], -amount);
}

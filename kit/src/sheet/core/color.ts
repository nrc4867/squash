import { toHex, toRgb } from "../../palette/color.ts";
import type { Pixel } from "../raster/image.ts";

const CHANNEL_MAX = 255;

export function pixelToHex(pixel: Pixel): string {
  return toHex([pixel[0] / CHANNEL_MAX, pixel[1] / CHANNEL_MAX, pixel[2] / CHANNEL_MAX]);
}

export function hexToPixel(hex: string): Pixel {
  return toRgb(hex).map((c) => Math.round(c * CHANNEL_MAX)) as Pixel;
}

/** The largest difference of any channel, 0 to 255. */
export function channelDistance(a: Pixel, b: Pixel): number {
  return Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2]));
}

/** Luma, 0 to 1. */
export function luminance(p: Pixel): number {
  return (0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]) / CHANNEL_MAX;
}

export type Lab = [number, number, number];

function linear(c: number): number {
  const s = c / CHANNEL_MAX;
  return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
}

const LAB_EPSILON = 216 / 24389;
const LAB_KAPPA = 24389 / 27;

function labF(t: number): number {
  return t > LAB_EPSILON ? Math.cbrt(t) : (LAB_KAPPA * t + 16) / 116;
}

/** CIE L*a*b* under D65, where a distance of about 2 is the least difference people see. */
export function toLab(p: Pixel): Lab {
  const r = linear(p[0]);
  const g = linear(p[1]);
  const b = linear(p[2]);
  const fx = labF((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047);
  const fy = labF(0.2126 * r + 0.7152 * g + 0.0722 * b);
  const fz = labF((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883);
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)];
}

export function labDistance(a: Lab, b: Lab): number {
  return Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
}

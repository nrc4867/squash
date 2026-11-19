import type { Reading } from "../measure/readings.ts";
import { drawLine } from "../raster/draw.ts";
import type { Zoom, ZoomRegion } from "./zoom.ts";
import { STATUS_COLORS } from "./style.ts";

/** The readings a zoom region is about. */
const REGION_READINGS: Record<ZoomRegion, readonly string[]> = {
  head: ["eyeLine", "eyeWidth", "eyeSpacing", "nose", "mouth", "chinLength"],
  hands: ["handLength", "handWidth"],
  feet: ["footLength", "footWidth"],
};

/** The readings of `region` out of a view's proportions, and the reasons the others could not be read, in the order the region lists them. */
export function regionReadings(region: ZoomRegion, proportions: Record<string, Reading>, unread: Record<string, string>): { readings: Reading[]; unread: Array<[string, string]> } {
  const names = REGION_READINGS[region];
  return {
    readings: names.flatMap((n) => (proportions[n] ? [proportions[n]] : [])),
    unread: names.flatMap((n) => (unread[n] !== undefined ? [[n, unread[n]] as [string, string]] : [])),
  };
}

/** Draws each reading's lines over a zoom of the sheet, in its status colour, the zoom's scale applied. */
export function drawReadings(zoom: Zoom, readings: readonly Reading[]): void {
  for (const reading of readings) {
    const color = STATUS_COLORS[reading.status];
    for (const line of reading.lines) {
      const at = (p: { x: number; y: number }) => [(p.x - zoom.box.x) * zoom.factor, (p.y - zoom.box.y) * zoom.factor] as const;
      const [x0, y0] = at(line.from);
      const [x1, y1] = at(line.to);
      drawLine(zoom.image, x0, y0, x1, y1, color);
      drawLine(zoom.image, x0 + 1, y0 + 1, x1 + 1, y1 + 1, color);
    }
  }
}

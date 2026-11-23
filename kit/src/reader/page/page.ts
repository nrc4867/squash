import { encodePng } from "../../sheet/raster/io.ts";
import type { Raster } from "../../sheet/raster/image.ts";
import type { HeldMark } from "../readings.ts";
import { SOURCE_COLORS } from "../overlay.ts";
import type { Evidence, HidingLayer, ViewName } from "../types.ts";
import { PAGE_SCRIPT } from "./script.ts";

const STYLE = `
body { margin: 0; font: 13px system-ui, sans-serif; background: #f4f2ee; color: #222; }
header { display: flex; gap: 8px; align-items: center; padding: 8px 12px; background: #fff; border-bottom: 1px solid #ddd; }
button { font: inherit; padding: 3px 10px; }
body.busy header { background: #fff6d8; }
body.saved #save { outline: 2px solid #1ea050; }
main { display: flex; gap: 16px; padding: 12px; align-items: flex-start; flex-wrap: wrap; }
figure { margin: 0; }
svg.view { background: #fff; border: 1px solid #ddd; height: 80vh; touch-action: none; user-select: none; }
svg.view image { opacity: 0.45; }
.measure { stroke-width: 2.5; }
.measure.default { stroke-dasharray: 6 4; }
.mark { stroke: #fff; stroke-width: 1.5; cursor: move; }
.layer { fill: rgba(200, 40, 170, 0.08); stroke: rgb(200, 40, 170); stroke-dasharray: 6 4; }
.corner { fill: rgb(200, 40, 170); cursor: nwse-resize; }
table { border-collapse: collapse; background: #fff; }
td { padding: 3px 6px; border-bottom: 1px solid #eee; }
td input { width: 6em; }
`;

/** One view as the page shows it: its name and its image. */
export interface PageView {
  name: ViewName;
  image: Raster;
}

/**
 * The edit page: each view's sheet under its evidence (measurements in their source's colour, plan defaults dashed, landmarks as dots
 * to drag), the hiding layers as boxes with corners to drag and a looseness to type, and Save: to the server when `served`, else a
 * download of the held marks and layers.
 */
export function editPage(views: readonly PageView[], evidence: Evidence, held: readonly HeldMark[], layers: readonly HidingLayer[], looseness: Record<string, number>, served: boolean): string {
  const data = JSON.stringify({
    views: views.map((v) => ({ name: v.name, width: v.image.width, height: v.image.height })),
    evidence,
    held,
    layers,
    looseness,
    served,
    colors: SOURCE_COLORS,
  }).replaceAll("</", "<\\/");
  const figures = views
    .map((v) => {
      const png = encodePng(v.image).toString("base64");
      return `<figure><figcaption>${v.name} <button class="add-layer" data-view="${v.name}">+ layer</button></figcaption><svg class="view" id="view-${v.name}" viewBox="0 0 ${v.image.width} ${v.image.height}"><image href="data:image/png;base64,${png}" x="0" y="0" width="${v.image.width}" height="${v.image.height}"/><g class="edit"></g></svg></figure>`;
    })
    .join("");
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>Sheet readings</title><style>${STYLE}</style></head>
<body>
<header><button id="save">Save</button><button id="release">Release marks</button></header>
<main>${figures}<table><tbody id="layers"></tbody></table></main>
<script type="application/json" id="data">${data}</script>
<script>${PAGE_SCRIPT}</script>
</body></html>
`;
}

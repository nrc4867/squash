/**
 * The edit page's script, plain browser JavaScript with no dependencies. It reads the page's `data` element: each view's image and
 * size, the evidence, the held marks, the hiding layers, the source colours and whether a server answers. Dragging a mark holds it and
 * asks the server for a new proposal; a layer's box is dragged by its corners and its looseness typed; Save writes the readings.
 */
export const PAGE_SCRIPT = String.raw`
"use strict";
const D = JSON.parse(document.getElementById("data").textContent);
const NS = "http://www.w3.org/2000/svg";
let evidence = D.evidence;
let held = D.held.slice();
let layers = D.layers.slice();
let drag = null;

function el(name, attrs) {
  const e = document.createElementNS(NS, name);
  for (const k in attrs) e.setAttribute(k, attrs[k]);
  return e;
}
function color(source) { const c = D.colors[source]; return "rgb(" + c.join(",") + ")"; }
function svgPoint(svg, event) {
  const p = svg.createSVGPoint();
  p.x = event.clientX; p.y = event.clientY;
  const q = p.matrixTransform(svg.getScreenCTM().inverse());
  return [Math.round(q.x * 10) / 10, Math.round(q.y * 10) / 10];
}

function render() {
  for (const view of D.views) {
    const svg = document.getElementById("view-" + view.name);
    const layer = svg.querySelector(".edit");
    layer.replaceChildren();
    layers.forEach(function (l, i) {
      if (l.view !== view.name) return;
      const [x, y, w, h] = l.box;
      layer.appendChild(el("rect", { class: "layer", x: x, y: y, width: w, height: h }));
      [[x, y, "tl"], [x + w, y + h, "br"]].forEach(function ([cx, cy, corner]) {
        const handle = el("rect", { class: "corner", x: cx - 5, y: cy - 5, width: 10, height: 10 });
        handle.addEventListener("pointerdown", function (e) { drag = { kind: "layer", index: i, corner: corner, svg: svg }; svg.setPointerCapture(e.pointerId); });
        layer.appendChild(handle);
      });
    });
    for (const m of evidence.measures) {
      if (m.view !== view.name) continue;
      const line = el("line", { class: "measure " + m.source, x1: m.from[0], y1: m.from[1], x2: m.to[0], y2: m.to[1], stroke: color(m.source) });
      line.appendChild(el("title", {})).textContent = m.id + " " + m.value.toFixed(3);
      layer.appendChild(line);
    }
    for (const mark of evidence.marks) {
      if (mark.view !== view.name) continue;
      const h = held.find(function (k) { return k.id === mark.id && k.view === view.name; });
      const at = h ? h.at : mark.at;
      const c = el("circle", { class: "mark", cx: at[0], cy: at[1], r: 6, fill: color(h ? "held" : mark.source) });
      c.appendChild(el("title", {})).textContent = mark.id;
      c.addEventListener("pointerdown", function (e) { drag = { kind: "mark", id: mark.id, svg: svg, view: view.name }; svg.setPointerCapture(e.pointerId); });
      layer.appendChild(c);
    }
  }
  const rows = document.getElementById("layers");
  rows.replaceChildren();
  layers.forEach(function (l, i) {
    const tr = document.createElement("tr");
    tr.innerHTML = "<td>" + l.view + "</td><td><input class='kind' value='" + l.kind + "'></td><td><input class='loose' type='number' step='0.01' value='" + l.looseness + "'></td><td><button class='drop'>×</button></td>";
    tr.querySelector(".kind").addEventListener("change", function (e) { l.kind = e.target.value; });
    tr.querySelector(".loose").addEventListener("change", function (e) { l.looseness = Number(e.target.value); propose(); });
    tr.querySelector(".drop").addEventListener("click", function () { layers.splice(i, 1); render(); propose(); });
    rows.appendChild(tr);
  });
}

document.addEventListener("pointermove", function (e) {
  if (!drag) return;
  const p = svgPoint(drag.svg, e);
  if (drag.kind === "mark") {
    held = held.filter(function (k) { return !(k.id === drag.id && k.view === drag.view); });
    held.push({ id: drag.id, view: drag.view, at: p });
  } else {
    const l = layers[drag.index];
    const [x, y, w, h] = l.box;
    l.box = drag.corner === "tl" ? [p[0], p[1], x + w - p[0], y + h - p[1]] : [x, y, p[0] - x, p[1] - y];
  }
  render();
});
document.addEventListener("pointerup", function () {
  if (!drag) return;
  drag = null;
  propose();
});

function busy(on) { document.body.classList.toggle("busy", on); }
async function post(path, body) {
  const response = await fetch(path, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
  if (!response.ok) throw new Error((await response.json()).error);
  return response.json();
}
async function propose() {
  if (!D.served) return;
  busy(true);
  try { evidence = (await post("/propose", { held: held, layers: layers })).evidence; render(); }
  finally { busy(false); }
}
document.getElementById("save").addEventListener("click", async function () {
  if (!D.served) {
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([JSON.stringify({ held: held, layers: layers }, null, 2)], { type: "application/json" }));
    a.download = "held.json";
    a.click();
    return;
  }
  busy(true);
  try { await post("/save", { held: held, layers: layers }); document.body.classList.add("saved"); }
  finally { busy(false); }
});
document.querySelectorAll(".add-layer").forEach(function (button) {
  button.addEventListener("click", function () {
    const view = D.views.find(function (v) { return v.name === button.dataset.view; });
    layers.push({ id: "layer-" + (layers.length + 1), kind: "clothing", looseness: D.looseness.clothing || 0.06, view: view.name, box: [view.width * 0.25, view.height * 0.3, view.width * 0.5, view.height * 0.4] });
    render();
    propose();
  });
});
document.getElementById("release").addEventListener("click", function () { held = []; render(); propose(); });
render();
`;

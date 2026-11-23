import { join } from "node:path";
import { proposers } from "../../src/propose/registry.ts";
import type { Proposal } from "../../src/propose/proposer.ts";
import { loadPlan } from "../../src/propose/creature/plan.ts";
import { evidenceImage, shapeImage } from "../../src/reader/overlay.ts";
import { editPage } from "../../src/reader/page/page.ts";
import { EDIT_PORT, serveEditPage } from "../../src/reader/page/server.ts";
import { readReadings, writeReadings, type HeldMark } from "../../src/reader/readings.ts";
import { readSheetViews, type ReadSheet } from "../../src/reader/sheet.ts";
import type { HidingLayer } from "../../src/reader/types.ts";
import type { SheetOptions } from "./options.ts";
import { SheetOutput } from "./output.ts";

/** Writes a proposal's files, each view's evidence image and the edit page. */
function write(out: SheetOutput, sheet: ReadSheet, proposal: Proposal, looseness: Record<string, number>, served: boolean): void {
  for (const file of proposal.files) out.json(file.name, file.value);
  const layers = sheet.readings.layers ?? [];
  for (const [name, view] of sheet.views) out.png(`evidence-${name}.png`, evidenceImage(name, view.image, proposal.evidence, layers));
  for (const [name, view] of sheet.views) {
    const shape = proposal.shapes[name];
    if (shape) out.png(`shape-${name}.png`, shapeImage(view.mask, shape));
  }
  out.text("edit.html", editPage(pageViews(sheet), proposal.evidence, sheet.readings.held ?? [], layers, looseness, served));
  for (const line of proposal.lines) console.log(line);
}

function pageViews(sheet: ReadSheet) {
  return [...sheet.views].map(([name, view]) => ({ name, image: view.image }));
}

/** The default looseness of each hiding layer kind the readings' plan names, for a creature. */
function loosenessOf(sheet: ReadSheet): Record<string, number> {
  return sheet.readings.proposer === "creature" ? loadPlan(sheet.readings.plan).layers : {};
}

/**
 * `sheet propose <readings.json>`: reads the views the readings name, proposes with the proposer they name, and writes the proposal,
 * each view's evidence image and the edit page into out= (default `<readings>-proposal`); with edit=true serves the page on port=
 * (47842), where dragged marks propose again and Save keeps them and the layers in the readings file.
 */
export function proposeCommand(path: string, options: SheetOptions): void {
  let sheet = readSheetViews(path);
  const proposer = proposers[sheet.readings.proposer];
  if (!proposer) throw new Error(`${path}: no proposer "${sheet.readings.proposer}"; proposers: ${Object.keys(proposers).join(", ")}`);
  const out = new SheetOutput(options.text("out") ? options.outDir(path) : path.replace(/\.json$/, "") + "-proposal");
  const looseness = loosenessOf(sheet);
  let proposal = proposer.propose(sheet);
  write(out, sheet, proposal, looseness, false);
  if (!options.flag("edit")) return;
  const edited = (held: HeldMark[], layers: HidingLayer[]): ReadSheet => ({ ...sheet, readings: { ...sheet.readings, held, layers } });
  serveEditPage({
    port: options.number("port") ?? EDIT_PORT,
    page: () => editPage(pageViews(sheet), proposal.evidence, sheet.readings.held ?? [], sheet.readings.layers ?? [], looseness, true),
    propose: (held, layers) => {
      proposal = proposer.propose(edited(held, layers));
      return proposal.evidence;
    },
    save: (held, layers) => {
      writeReadings(path, { ...readReadings(path), held, layers });
      sheet = readSheetViews(path);
      proposal = proposer.propose(sheet);
      write(out, sheet, proposal, looseness, true);
      return join(out.dir);
    },
  });
}

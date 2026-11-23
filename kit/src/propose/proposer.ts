import type { ReadSheet } from "../reader/sheet.ts";
import type { Evidence, ViewName, ViewPlace } from "../reader/types.ts";
import type { Mask } from "../sheet/core/mask.ts";

/** A file a proposal writes: its name and its JSON value. */
export interface ProposalFile {
  name: string;
  value: unknown;
}

/** What a proposer gives back: the evidence to show on the views, where each view lies, and the files it proposes. */
export interface Proposal {
  evidence: Evidence;
  places: Partial<Record<ViewName, ViewPlace>>;
  files: ProposalFile[];
  /** What it proposes as each view would show it, in the view's pixels: laid over the view, it shows how near the proposal came. */
  shapes: Partial<Record<ViewName, Mask>>;
  /** Lines to print about it. */
  lines: string[];
}

/**
 * A proposer: reads a sheet's views and proposes what they show. The sheet reader is shared; a proposer knows its subject (a
 * creature's body, later an object).
 */
export interface Proposer {
  id: string;
  propose(sheet: ReadSheet): Proposal;
}

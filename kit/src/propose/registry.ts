import { creatureProposer } from "./creature/index.ts";
import type { Proposer } from "./proposer.ts";

/** The proposers a readings file may name. */
export const proposers: Readonly<Record<string, Proposer>> = { creature: creatureProposer };

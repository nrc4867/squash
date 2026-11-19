// Which way a part's outlines and v run for each rest direction, as docs/reference/frames.md states them.

/** The direction a part rests in. */
export type Rest = "forward" | "back" | "up" | "down" | "out";
/** A direction in the character's own terms. */
export type Direction = "forward" | "back" | "up" | "down" | "left" | "right";
/** On a pair, described as its left side: outer is its left, inner its right. */
export type PairDirection = "outer" | "inner";

export interface RestFrame {
  /** Where Side's +y faces. */
  side: Direction;
  /** Where Top's +y faces. */
  top: Direction;
  /** Where v 0, 0.25, 0.5 and 0.75 face, at u 0.5. */
  v: readonly [Direction, Direction, Direction, Direction];
}

export const restFrames: Readonly<Record<Rest, RestFrame>> = {
  forward: { side: "up", top: "right", v: ["up", "left", "down", "right"] },
  back: { side: "up", top: "left", v: ["up", "right", "down", "left"] },
  up: { side: "forward", top: "left", v: ["forward", "right", "back", "left"] },
  down: { side: "forward", top: "right", v: ["forward", "left", "back", "right"] },
  out: { side: "up", top: "forward", v: ["up", "back", "down", "forward"] },
};

const opposites: Readonly<Record<Direction, Direction>> = {
  forward: "back",
  back: "forward",
  up: "down",
  down: "up",
  left: "right",
  right: "left",
};

export function opposite(direction: Direction): Direction {
  return opposites[direction];
}

/** A pair's outer or inner side as a direction of its left side. */
export function pairDirection(direction: Direction | PairDirection): Direction {
  if (direction === "outer") return "left";
  if (direction === "inner") return "right";
  return direction;
}

/** The v that faces `direction` on a part resting `rest`. */
export function vToward(rest: Rest, direction: Direction | PairDirection): number {
  const index = restFrames[rest].v.indexOf(pairDirection(direction));
  if (index < 0) throw new Error(`a part resting ${rest} has no v facing ${direction}: that is along its axis`);
  return index * 0.25;
}

/** The v a share `t` of the way round from facing `from` to facing `to`, the short way. */
export function vBetween(rest: Rest, from: Direction | PairDirection, to: Direction | PairDirection, t: number): number {
  const a = vToward(rest, from);
  let b = vToward(rest, to);
  if (b - a > 0.5) b -= 1;
  if (a - b > 0.5) b += 1;
  const v = a + (b - a) * t;
  return ((v % 1) + 1) % 1;
}

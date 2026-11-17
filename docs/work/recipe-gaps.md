# Recipe gaps

Where an author writing a recipe still has to read code, or guess and render, to learn what is possible. Each line: what was read or tried, the fact needed, and what tool or reference would have told it. A working note: entries close as the reference below is built.

## What a generated reference needs

The recipe schema describes the recipe's own fields. These are open-ended and need a reference generated from the code that reads them:

- **Templates.** Per template, its newest version and every version, its role, its axis and rest direction, which way Side's y points, what Side, Top and Front set, its length (where an outline ends), its sockets with their u and v and what each takes, its joints by name with hinge axis, limits and which way positive degrees turn, its areas and their slots, and every parameter with its range, default and a one-line meaning. From `templates/*.part.json` and `PartBuilder`.
- **Plans.** Each plan's sockets, root rest, starting palette and base slot.
- **Dotted part parameters.** `move.*` (`move.lean`, `move.outward`, `move.rest` and the order `RestDirection` counts in), `socket.<n>.*` including rows (`count`, `to`, `size`, `falloff`), `joint.<n>.*`, `landmark.*`, `generic.*` (`rings`, `segments`, `section` and the profiles its index counts), `join.ink`. From `HandSockets`, `HandJoints`, `HandLandmarks`, `GenericShape`, `Placement`.
- **The part frame.** Where v 0, 0.25, 0.5 and 0.75 face for a part resting up, forward and out; which way Side's y runs; how a socket's sink moves a child. A drawing per rest direction, or a headless command that prints the u and v at a body point.
- **Bones.** Each template's joint names a pose can use, and for a tube's `base`, a hand-made joint and an elbow which way `degrees`, `turn`, `lean` and `twist` move it. A command that prints a built character's bones with their limits would do.
- **Mark groups.** The mouth's five roles, their draw order, and what `controls` do.
- **Garments.** How `fit`, `partFits` and a cover's `ease` combine, what a redraw lays over (outlines and parameters by key, regions and marks by id), and how covers on a pair mirror.

## Log

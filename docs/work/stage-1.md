# Stage 1: draw a part and see it built

A working note for the sessions that build stage 1. Folded into the HLD or deleted when the stage lands.

## Goal

A person opens Squash, picks the canine quadruped, picks a leg, draws its Side and Top outlines in the Sheet layout, watches the leg build from the slices in all four panes, and reshapes it by oversketching a section or dragging a handle. Home, New character and Pick a part type exist as thin screens around that.

That is boards Home, 1, 2, 3, 4, 5 and the Sheet layouts A (both), plus spikes 1 and 2 behind them. The library, versions, the cast menu, Focus and Full first layouts, and every board from 6 on are out of scope.

## Exit criteria

Stage 1 is done when all of these hold, in this order of importance:

1. A drawn Side outline alone produces a leg mesh with the leg template's fixed topology through the real slice build. Top adds width. No placeholder mesh anywhere.
2. Oversketching a section of an outline, or dragging a handle, refits only the touched span of slices. Verified by a golden test that compares vertex positions outside the span before and after.
3. The six starter templates (torso, leg, paw, head, ear, tail) load, pass schema, lint and the golden build with default parameters and no outlines. The paw has pads underneath; the head reads as skull and muzzle.
4. The Sheet layout runs four viewports in one `NativeSurface` at display rate, with the views-disagree highlight updating at stroke rate.
5. Every screen matches its board under the Build to the boards rule, with the drift noted in `docs/work/boards.md` where the board itself is wrong.
6. `./gradlew check` passes in under two minutes without the smoke tier. `docs/work/boards.md` shows boards Home, 1, 2, 3, 4, 5, A and A-disagree as verifiable with their verify steps.

## How the sessions run

One orchestrating session per sitting. It plans, lands the shared skeleton itself, then fans work out to subagents by module, each in a worktree under `.worktrees/`, and merges by rebase, squash and fast-forward. It runs `check` before every landing and updates `docs/work/boards.md` and the artifact's board notes when a state changes.

**Session 1 is serial.** No subagents until this lands on `main`:

- Module layout from `docs/hld/architecture.md`: `annotations`, `geom`, `graph`, `document`, `character`, `render`, `ui-design`, `ui-screens`, `app`, `schema-gen`, each a Gradle subproject with its dependencies declared and nothing else. Empty modules are fine; the dependency direction is the deliverable.
- `annotations`: `@Range` and `@Doc`.
- `character`: the serializable model from `docs/hld/data-model.md` (template, body plan, part instance, blocks as a sealed class on `kind`, the `$param` value class), the loader with strict parsing and `x-` keys, and the semantic lint.
- `schema-gen`: the descriptor walk, the `generateSchemas` task, the committed schemas under `schema/`, and the staleness test.
- `templates/`: the six starter files and `plans/canine-quadruped.plan.json`, passing schema and lint.
- `document` and `graph`: the document type with a character holding part instances, the first commands (add part, set outline, set parameter), undo through `stretch-core`, and a graph with one node (part instance to slice row) that is `Settled`.
- `geom`: curves (cubic Bezier, sampling, crossings with a line), the slice row builder for `tube`, `cap` and `joint`, and slices to mesh with fixed topology and mesh invariants. Unit and golden tests.
- `ui-design`: the icon folder and generate task wired like Stretch's, with the first Squash-only icons the boards need (mirror, part template bones, body curve, push), and the strings file.

**Sessions 2 and on fan out.** Independent branches, one per line, merged by the orchestrator:

| Branch | Module | Delivers | Board |
| --- | --- | --- | --- |
| `sheet-layout` | `ui-screens`, `render` | One `NativeSurface`, four viewports with scissor and a camera each, Side, Top and Front locked to the selected part's axes, Full orbiting, guides projected between panes, the Layout segmented control | A |
| `draw-outline` | `ui-screens`, `document` | The Draw outline tool: pen and mouse strokes in a locked pane become an `OutlineCurve` through a command, with cleanup (smooth, simplify, close or cut at the socket plane); the disagree highlight | 3, A-disagree |
| `slice-build` | `geom`, `character`, `graph` | Views sizing slices: Side to height, Top to width, Front to cap; missing views from roundness; `toeRow`, `pad`, `skull` blocks; the derived mesh shown in every pane with the old result kept while a run is cancelled | 4 |
| `oversketch` | `ui-screens`, `geom` | Edit curves tool: handles and points on the stored curve, oversketch a span, re-sample only that span; golden test for the untouched span | 5 |
| `shell-screens` | `ui-screens`, `app` | Home with recent projects (no library), New character with the one body plan, Pick a part type with the starter set and sockets, the Parts drawer, the Part drawer with the template's parameters, status bar text | Home, 1, 2 |

Each branch owns its modules for the duration. A branch that needs a change in another branch's module asks the orchestrator, which lands it on `main` first.

## Verify steps, written as the branches land

Each board's row in `docs/work/boards.md` gets a verify entry of this shape:

```
Run: ./gradlew :app:run
Do: New character, Canine quadruped, Front legs, Draw outline, draw a closed shape in Side
See: the leg builds in Top, Front and Full; the status bar reads "1 drawn, 2 from template"
Test: ./gradlew :geom:test --tests "*SliceBuild*"
```

Screenshots for the orchestrator come from Stretch's devtools endpoint (`-Pdevtools=47811`), never from the desktop.

## Stretch

Expected gaps, to be landed in the Stretch repo on their own branches before the Squash branch that needs them: an in-bar menu for Windows and Linux (the boards put File, Edit, View, Help inside the top bar), several viewports in one `NativeSurface`, and whatever the Parts tree needs beyond `stretch-ui`'s tree. A Squash session may do this work; it commits it in Stretch, not Squash.

## Out of scope for stage 1

Library and versions, Save to library, the cast card menu, Focus and Full first layouts, mirroring beyond the template's `pair` flag, rig and weights, color, lines, marks, posing, scenes, render to file, imported images, objects and sets, Linux, the project file format spike (the document lives in memory and in a scratch JSON save until that spike runs).

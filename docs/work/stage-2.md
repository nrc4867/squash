# Stage 2: an assembled character, and the boards as drawn

A working note for the sessions that build stage 2. Folded into the HLD or deleted when the stage lands.

## Goal

Drawing happens in place on the body. The whole character is assembled from its sockets, pairs share their data, every screen of stage 1 matches its board, and what a stroke will do on release is visible before release.

## Decisions

- **Panes stay upright and look along the part's own planes.** A locked pane keeps the body's up as up as the view's plane lets it be, so a leg hangs down in Side with the character ghosted around it at the board's 40%, and a tilted part's camera tilts with it. Panes line up by what they share: Top above Side shares the column, Side beside Front shares the row, for a part square to the body; see HLD: data-model, How a pane looks at a part. Outlines stay in part axes, so for a part whose rest direction is vertical the top-left pane holds the part's Front view and the bottom-right holds its Top view. A pane's label names the direction it looks from, so the top-left always reads Top.
- **Rings stand on the rest axis.** A slice lies in its plane a = constant, so a drawn Side outline is the mesh's silhouette: the crossings at a are the ring's ends. The axis still follows the midline through the slice centres.
- **A pair is one set of data.** Outlines and params live on the pair; a part's overrides replace the values they name. A paired part's own outlines and params are empty unless overridden. With Mirror on, a command writes the pair; with it off, the selected side's overrides.
- **Release does what the preview shows.** While a stroke grows, the pane draws what release would produce: the connectors to the start or the socket plane, and the start point lit when the stroke is close enough to close on it.
- **Squash draws its own icons.** Every Squash function has a Squash icon from the boards; only the frame's own controls use Stretch's.
- **Half outline mirrors across the part's axis, or down the middle of Front.** One edge keeps to its side of the centre line and of a socket plane, its far end joins its mirror image, square across the line if it stops short, and Close end decides only the other end. The released outline is exactly symmetric: the first half's fit and its mirror image. Over a drawn outline a stroke redraws its span and that span's mirror image in one step; an image too far from the curve shows dotted and changes nothing.
- **Assists work ahead of the preview.** Stabilizer, guides and forced points change the samples or the options going in, so preview and release share them. The Stabilizer is a string the line trails on, moved only by the pointer's positions. Guides stretch the stroke's coordinate across them, so nothing breaks and no point moves past the reach. A modifier pressed during a stroke marks where the pointer is, taking up the string, and holding it marks no more.
- **The shade on a stroke is `OutlineProfile` itself.** Height and width views read the two outermost crossings, section views the whole loop, as the build does.
- **Exit criterion 2 reads as the HLD says.** Moving an outline's far end, a span holding its lowest or highest x, or a Front section outline may move every ring, for now.

## To do

Each row is a branch in `.worktrees/`, landed by the orchestrator by rebase, squash and fast-forward, with `check` before each landing. Rows in one round run at once. The differences table in `docs/work/boards.md` is the list of open bugs; a branch removes each row it fixes.

| Round | Branch | Delivers |
| --- | --- | --- |
| 1 | `pair-data` | Shared pair data and per-side overrides in `document`, resolved before the slice build; every reader and command moved to it |
| 1 | `silhouette-and-graph` | Rings on the rest axis; cancellation checks between blocks and in the mesh node; the template in the mesh node's inputs; lint for the open end, sockets on the surface, patch-name collisions and the views map; golden fixtures with vertices; toe and pad grids in the template; the ear's fold joint and the head's nose pad |
| 1 | `stroke-preview` | The live completion preview, Outline drawer with Smooth, Snap line and Close end, and tests that the preview equals the release |
| 1 | `squash-icons` | Squash's own icon for every Squash function on a board, replacing the reused Stretch glyphs |
| 1 | `tiers-and-packaging` | The smoke tier behind `-Pslow`; templates loaded from the app's resources |
| 2 | `assembly` | A graph node placing every part on its socket; Full shows the whole character with the selection ringed; locked panes upright with the ghost and the Ghost and Template hints; normals and silhouettes derived in the graph; rest-pose art on Home, New character and Pick a part type with socket dots |
| 3 | `chrome-boards` | Edit menu, undo, redo and command buttons, Erase, the Mirror toggle, the status bar's conflict count and pen readout, Refit progress, pixel readouts |
| 3 | `single-layout` | The Single layout of board 3: orbit snapping to the part's views, Snap view, thumbnails with the missing view highlighted, the Layout control enabling it |

Round 4 runs after `assembly`, with board 3 redrawn first, since the boards are the specification. These tools make outlines and nothing else: each ends in `SetOutline` commands as one undo step, and the document stores only the curves.

| Round | Branch | Delivers |
| --- | --- | --- |
| 4 | `pen-boards` | Board 3's strip and Outline drawer redrawn with the tools below, and a new board for Spine and width; only these changes in the artifact |
| 4 | `pen-assists` | Half outline, mirrored across the part's centre line. Guide snapping for a stroke's ends and widest points. The part the build will read, shaded on the stroke, so an overhang shows as unread. Stabilizer, live while drawing. A modifier forcing a corner or a smooth point |
| 4 | `pen-shapes` | Hold to snap: pausing at a stroke's end makes a line, ellipse or capsule to adjust before release, timed by a pointer wait with a timeout. Start from the template: the dashed hint becomes an outline to push and pull |
| 4 | `spine-and-width` | A centre stroke, then a drag or pen pressure for thickness along it, producing the Side and Top outlines in one step |

Focus, Full first and Shot stay planned: they have their own boards.

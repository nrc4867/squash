# Stage 3: the look on the surface

A working note for the sessions that build stage 3. Folded into the HLD or deleted when the stage lands.

## Goal

The assembled character gets its look. Color, lines, fur and faces are drawn on the surface from any camera and stored as anchors in template coordinates, so they survive an oversketch and a template update. The character renders flat or two-tone with shadows that fall as clean shapes. Eyes and mouths are mark groups with controls, and the pupils move.

That is boards 9, 10 and 11, layout C (Full first, since a surface stroke can land from any camera), and spikes 7, 9 and 6 behind them, with the part of spike 3 that inks the overlap where parts meet. Board 11 and layout A for face marks are redrawn first, since they predate the eye and mouth design in `docs/hld/look.md`.

## Before round 1

Nicholas decides, and the orchestrator does not start until both are written down in `docs/work/boards.md`:

- **Locality.** Whether moving an outline's far end, or a Front section outline, may rebuild every ring. The HLD currently says it may.
- **Move.** A leg leaned out leaves its paw above the ground. This is a bug, not drift, since planted paws in posing depend on feet meeting the ground. It is fixed in round 0.

The orchestrator then:

- Folds `docs/work/stage-1.md` and `docs/work/stage-2.md` into the HLD where they still say something the HLD does not, and deletes them.
- Deletes the empty folders under `.worktrees/`.
- Looks at Home by eye at 1440 by 900 for a clipped right edge and a missing status bar, and fixes it if real.
- Redraws board 11 and layout A for face marks in Squash flow of work to the design in `docs/hld/look.md`, Eyes and gaze and Mouth: roles, per-eye controls, the gaze target as a Build-side test control, expression sets that carry a face pose, sweat and shock lines moved out to the shot. Only those boards change in the artifact. The Shot board for eyes stays future work.

## Exit criteria

1. **Marks are anchors.** A stroke drawn on the surface from any camera, in Full first or a locked pane, is stored as anchors (part, patch, u, v, offset) through a command, never as positions. After an oversketch of the part, and after Update from library with a template of the same version, the stroke sits on the same place on the skin. A golden test holds the anchors fixed and checks the rendered positions follow the mesh.
2. **Lines hold from any angle.** Silhouette lines thick, interior lines thin, a line where one part overlaps another, inked by default at sockets. The width tool stores weight on the surface, and the heavier stretch stays heavier as the camera orbits. Tapered strokes and silhouettes do not swim: a smoke test orbits the character through twelve frames and asserts stroke positions move with the surface and nothing flickers.
3. **Fur follows the grain.** Fur strokes show where their area nears the silhouette, spaced on screen by the drawer's spacing, with the length and show-near controls of board 10. Each part has a default grain along its axis, outward on a ruff, and a comb tool overrides it. Tufts drawn into an outline remain outline work and need nothing here.
4. **Color without textures.** Blocking out comes first: a character palette of named slots with defaults from the body plan, a base slot per part set by Fill on the part, the Parts tree swatch showing it, editing a slot recolouring every part and region that names it; no colour constant in code decides what a new character looks like. Then paint by brush and fill, a region border drawn as a curve on the surface for a crisp edge, a palette with the template's color slots (pad, nose, inner ear) and per-border ink toggles. A stroke or border crossing a socket continues onto the parent. Paired parts mirror paint, regions and marks by default. No image textures: color lives on the fixed grid and region borders in anchor space, and borders render crisp, not along triangle edges.
5. **Two-tone falls as shapes.** Per object flat or two-tone, within limits a scene will later set. Two-tone shades from each slice's proxy normal, so a part's shadow is one clean shape, and far paired parts darken by side, switchable. Spike 6 passes on the fennec: crisp edges, clean shadows, flat and two-tone.
6. **Eyes and mouths act.** An eye is a mark group with the seven roles, draw order and clipping, and per-eye open, expression blend, dilation and gaze offset. The highlight is camera-locked. A mouth is a mark group whose open control drives the jaw joint and a lip morph together. Angle versions switch with hysteresis. Spike 9 passes: with a test gaze target moved around in 3D and open run 1 to 0 and back, from side, three-quarter and front at two head pitches, gaze reads at every angle, pupils stay inside the lid region, both pupils show at three-quarter, blinks are smooth, nothing pops except at a version switch. Spike 7 passes on the same head: the three angle versions of one eye switch without swimming.
7. **Expressions carry poses.** An expression set holds marks and a face pose. Ears back and ears forward are expressions on the fennec, and switching sets moves the ear joints.
8. **Boards match.** Boards 9, 10, 11 and layout C match under the Build to the boards rule. The differences table holds only rows where the board is wrong or the work belongs to another board.
9. **Tiers hold.** `./gradlew check` under two minutes without the smoke tier; the smoke tier covers the orbit test and a render of each new screen.

## How the sessions run

One orchestrating session per sitting. Round 0 is serial and lands on `main` before anything fans out. Later rounds are branches in `.worktrees/`, one per row, landed by rebase, squash and fast-forward with `check` before each landing. The differences table in `docs/work/boards.md` is the list of open bugs; a branch removes each row it fixes. A session that needs a Stretch change lands it in the Stretch repo first and pushes it.

### Round 0, serial

- **Split `ui-screens`.** It is 17,000 lines in one module, so one agent owns it per round. Split by screen into `screens-shell` (home, new character, pick, frame, chrome), `screens-sheet` (panes, locked cameras, layouts), `screens-draw` (draw outline, edit curves, oversketch, spine and width, smooth, move) and a new `screens-surface` for this stage. Each depends on `ui-design`, `render`, `character` and Stretch, never on each other; shared pieces go down into `ui-design` or `character`. `app` wires them. No behaviour changes in this step; the test suites move with their screens.
- **Document types for surface work**, from `docs/hld/data-model.md`: `Mark` (a polyline of anchors with a width profile, ink flag, camera-locked flag, per-angle versions keyed by camera bin), `MarkGroup` with roles and controls, `Region` (a border curve in anchor space, a palette slot, an ink flag), `Paint` (brush strokes in anchor space), `Grain` combs, `ExpressionSet` with marks per role and a face pose, `Palette`. Commands for each, serializable, with inverses. `PartInstance.grain` already exists.
- **Graph nodes** that turn anchors into positions on the current mesh, and a `Settled` path for them, so every later branch reads positions from the graph and never computes them in composition.
- **Render seams**: a stroke ribbon pass (tapered, screen-space width from the line rules and the width tool), a region and paint pass that renders crisp borders in anchor space, and the two-tone shading with proxy normals and far-side darkening. Each behind a small interface in `render` so the branches below fill them in without touching each other's files.
- **`geom`**: curve morphing between two curve versions with equal control-point counts, and clipping one closed curve by another, both needed by the mark groups.
- **The Move fix** from Before round 1.

### Round 1 opens with the look probe

Before any tool, one branch, `look-probe`, renders the saved fennec with base colors from the palette, the line rules, one fur patch on the chest, and one eye mark group with its three angle versions, flat and two-tone, through commands in a smoke test, from side, three-quarter and front. The images go under `docs/work/look-probe/` for Nicholas to judge. This is the plan's style test brought forward to the first moment it can be seen: if the look is not right, the stage stops here and the art approach is revisited before the tools are built around it. What it shows wanting is written into the HLD as the fix, not worked around in the probe.

### Round 1, parallel

| Branch | Modules | Delivers | Board |
| --- | --- | --- | --- |
| `surface-strokes` | `screens-surface`, `render`, `geom` | Drawing a stroke on the surface from any camera into anchors; the Surface stroke tool; line rules (silhouette, interior, overlap inked at sockets, taper); the Line width tool and its drawer rows; the Lines and Marks drawers' list rows; the orbit smoke test | 10 |
| `regions-paint` | `screens-surface`, `render`, `document` | The palette with slot editing and the body plan's defaults; a base slot per part, set by Fill on the part, shown in the Parts tree swatch; Brush, Fill, Region border and Pick color tools; the Color drawer with palette, template color slots, per-border ink toggles; strokes and borders crossing sockets; mirrored pairs mirroring surface work; the Flat and Two-tone previews | 9 |
| `two-tone` | `render`, `character` | Proxy normals per slice carried to the mesh; two-tone shading with one clean shadow per part; far-side darkening by pair side; the per-object shading choice | 9 |
| `fur-grain` | `screens-surface`, `character`, `render` | The grain field with defaults per template and the comb tool; the Fur stroke tool with show-near, spacing and length; fur strokes near the silhouette from any angle | 10 |
| `full-first` | `screens-sheet` | Layout C: the large free pane that takes surface strokes, three locked sheets beside it, the Layout control enabling it | C |

### Round 2, parallel, after round 1 lands

| Branch | Modules | Delivers | Board |
| --- | --- | --- | --- |
| `mark-groups` | `screens-surface`, `document`, `geom`, `render` | Eye and mouth as mark groups with roles, draw order and clipping; per-eye open, expression blend, dilation; the mouth's open driving jaw and lip morph; camera-locked highlights; angle versions with hysteresis; the Draw mark and Edit curves tools on the head; the Face marks drawer | 11 |
| `expressions` | `screens-surface`, `document` | Expression sets with a face pose; the Expressions and angle-versions strips; the Head angle control; ears back and forward on the fennec | 11 |
| `gaze` | `character`, `screens-surface` | The gaze solve for surface and bulging eyes: target, convergence, camera bias, size compensation, lid clipping; a Build-side test target control; the spike 9 harness as a smoke test | 11 |

### Round 3

| Branch | Delivers |
| --- | --- |
| `look-boards` | Every remaining row of the differences table for boards 9, 10, 11 and C; the status bar counts the boards show; the rail and drawer states |
| `style-test-prep` | A saved fennec with color, lines, fur and a face set, built only from drawn outlines and surface work, as the fixture the style test in `docs/hld/pipeline.md` will pose |

## Verify steps

Each board's row in `docs/work/boards.md` gets a verify entry of this shape when its branch lands:

```
Run: ./gradlew :app:run
Do: open the fennec, Full first, Fur stroke, drag along the chest from the free pane, then orbit
See: strokes show only near the silhouette and move with the chest; the Marks drawer lists Chest with its count
Test: ./gradlew :screens-surface:test --tests "*FurStroke*"
```

Screenshots for the orchestrator come from Stretch's devtools endpoint, never from the desktop.

## Decisions the orchestrator makes and writes into the HLD

- How color lives on the surface without an image texture: per-vertex color on the fixed grid with region borders rendered crisp in anchor space, or a per-patch color grid in u and v at a fixed resolution. Either is allowed by the HLD; the constraint is crisp borders that do not follow triangle edges, and anchors as the only stored form.
- Camera bins for angle versions and the hysteresis width, as template data on the head or as a character setting.
- Where the proxy normal frame is computed: it is already a field on `Slice` and is currently the frame rotation; two-tone needs the ellipse normal blended along the axis.

## Stretch

Expected gaps, landed in the Stretch repo first: the free pane taking strokes while three locked panes stay live, a tile strip for expressions and angle versions if `stretch-ui` lacks one, and the color picker and swatch row wired to a palette model. Check `stretch-ui` first; it already has a color picker, swatches, a curve editor and a shading ramp.

## Out of scope

Rig and weights (board 7), mirror and join beyond inked overlaps (board 6), garments, posing and the Shot workspace, the Shot board for eyes, scenes, render to file, the library and versions, imported images, objects and sets, export.

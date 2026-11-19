# Exact garment coverage

Coverage is read from a garment's painted cells as a smooth field per covered part: the cells blurred by 0.8 cells along and round the part's chart, the half level being the hem, read as a distance in body units. Areas meet along the zero of one area's field less the most of the later areas'. A part grows over its painted rows, two rows more each way, rounded out to steps of 32 rows. Every garment's cloth is cells: a box is painted as cells where it arrives, and a part no cell paints is not covered.

## Built: the engine (round 1 of 4)

In `character`'s `surface` package, described in `docs/hld/surface.md`, Garments on the surface:

- `ClothField`: one part's cloth as a field over its chart, its areas blurred together for the cloth and each on its own for seams, equal for the same cells in the same areas; `ClothField.union` for garments worn together.
- `clothOf` (`GarmentCloth.kt`): a garment's areas read per covered part, on a pair's leader, kept to the part's branch. `GarmentCover.cloth` holds it and `WornCover.clothOf` gives it to `Coverage`, `GarmentField`, `ClothSeams` and `coverUnion`.
- `CoverageMemo`: a read unchanged on the same mesh needs no work, and its smoothed values and the level cut of what shows are taken as they were (`SlotMemo` per kind); `HiddenSkin` uses them. `WornCover.shapeKey` holds a painted part's stepped range, not its cells, so a stroke within the same steps keeps the grown surface.
- `cellsOfRuns` and `runsOfCells` (`shape/ChartRuns.kt`): the cells' run-length form, shared with `SurfaceMask`.
- Tests: `ClothFieldTest` (level hem at the half level and read as a distance, a slanted hem a straight line through the steps, a patch and one across v = 0, two areas meeting with no gap and each place in one area, a one-cell line dropping out and a two-cell line staying, equality, union), `GarmentClothTest` (a twin through its leader, a cuff's band reading its box, cells outside the branch let go, an area field per area on every part), `rig`'s `PaintedCoverageTest` (an end a part comes out through cuts the cloth painted over it; painting less of a part keeps the grown surface; both match a fresh build).

The example garments carry boxes and no cells, so their renders are those of the box path.

**Verdict: go.** On every shape tried on the fennec and the woman, the cloth follows the paint to within 0.34 cells (p95), against the box's straight bands. Per stroke, coverage costs about the same as the box, and the dress is ready sooner because the grown garment surface holds across strokes while the box regrows it whenever its range moves. Whole builds show no measurable added cost.

## Built: growth and cost (round 2 of 4)

- `ClothReach` (`ClothReach.kt`): where a part's painted cells lie, first and last row and the fewest columns round that hold them, kept on `ClothField` (`reach`). Its `from` and `to` are the painted rows `GROWN_MARGIN_ROWS` (2) further each way, rounded out to `GROWN_STEP_ROWS` (16), reaching an end where they come within a step of it.
- `garmentField` clips a painted part's run to that range through `RunClips`, as a box branch is clipped to its range, so the grown surface covers only the painted rows' steps.
- `WornCover.shapeKey` holds a painted part's branch at the stepped range, the whole way round. Painting more or less inside the same steps keeps the grown surface and only cuts it again; crossing a step grows it again.
- The garment's cell (`bandCell`) reads a painted part's band from its painted cells' span along and round (`ClothReach.along`, `around`), not the branch's box, so a narrow painted strap meshes finer and the cell is a function of the cells alone. A cell change still regrows, at most once per halving of the band.
- Tests: `ClothReachTest` (the range is the painted rows with margin rounded to steps, a small change keeps it, paint near an end grows to the end, columns across v = 0, union); `PaintedCoverageTest` paints the leg whole, then rows 0–44 (a new range, a new surface), then rows 0–41 (the same step, the same surface, a new cut), each matching a fresh build through two undos.

Round 2's example garments carried boxes and no cells, so their renders were those of the box path.

## Built: one path for every garment (round 3 of 4)

- The chart's size, the cells' run-length form and `ChartBox` moved to the document (`GarmentChart.kt`), since saved areas are written in them. `boxCells` is the cells whose middles lie in a branch's box, cut to the part's ends, at least two rows and two columns (`LEAST_BOX_CELLS`).
- `Garment.withBoxesPainted` paints the box of each branch whose part no area paints into the first area (`area-1`), leaving the branch as given. `AddGarment`, `CoverPart` and `SetWardrobe` apply it, so fixtures, recipes (whose covers build through `AddGarment`) and scripts give cells. Format 19: a save of format 18 or before is painted on load and drops the edges and fill a garment saved before areas held; the legacy readers (`legacyMask`, `branchMask`, `GarmentEdge`) are gone.
- Coverage reads cells alone. A branch's range is read only past an end the cells reach (from below 0, to past 1): the hem on a shell past the rump, a skirt's drop. What reads a cloth's rows instead of the box: open ends (`reachesLow`, `reachesHigh`), the socket test for holes and cuffs (`alongOf`), shells, `hangsFree` and `hangsOver`, the inner layers a garment stands off, the cover union, the garment's clip and band cell, fur under a garment (`coversAnchor`). A cuff's band is cells (`cuffCloth`).
- `ClothField.inside` reads a true distance: near the hem 0.8 cells times the probit of the field, along its slope; further off the distance to the painted cells' edge, blended between 0.35 and 0.45 of the field's range off its half level. The level over the slope it read before grows steeply a cell or two from the hem, so the skin under a hood hid right up to its face opening and the hood's inside showed there as a red rim and a second ink line.
- `shapeCommands` keeps a branch running past an end while the cells reach that end, and keeps a change to a part the cloth says nothing of before or after (a hood's to the hair), dropping one the cloth said something of before.
- Still read from the box, the cells' bounds by construction: a part-round covering's hider over separate parts (`GarmentHiding`), the hair push, Tuck rows and the Dress inspector's reading; holes, cuffs and hanging are read from the cells (round 4).

Examples: a box's edge moves by up to half a cell; a band under two cells is widened to two (the twin's dress belt, 1.55 rows, becomes 2); a sausage to 0.98 reaches its row beside the end and so reaches the neck end. The vampire cape's legs, which hang inside it, sit half a cell outside its cells' `ChartBox`; round 4 reads them from the cells, so painting it keeps them hanging.

Fidelity after the round (`mode=render`, p95 / max in cells) is unchanged from round 2 on every shape (fennec 0.08–0.26, woman 0.13–0.34). Vertices the field covers that the cut leaves out, away from openings, rose a little with the distance read: fennec thigh-stripe 0 to 11, diagonal-stripe 3 to 12, woman thin-line 6 to 20 of about 800 to 1,200.

Timings, same runs as round 2 (`mode=time runs=5`, dress ready p50 / p95 per drag frame, then settled):

| Example | Dress ready, round 2 | Dress ready, round 3 | Settled, round 2 | Settled, round 3 | Kept frame p50, round 2 / 3 | Re-mesh frame p50, round 2 / 3 |
| --- | --- | --- | --- | --- | --- | --- |
| fennec | 43 / 90 | 36 / 80 | 96 / 145 | 102 / 154 | 41 / 35 | 98 / 87 |
| woman | 52 / 319 | 51 / 259 | 353 / 524 | 316 / 461 | 51 / 50 | 337 / 302 |
| fennec-dressed | 41 / 121 | 52 / 98 | 123 / 201 | 115 / 190 | 40 / 51 | 133 / 215 |
| woman-dressed | 48 / 203 | 49 / 233 | 291 / 424 | 284 / 386 | 47 / 49 | 257 / 233 |

The dressed fennec's kept frames cost about 11 ms more: its own garments now read cells, grown over their rows' steps rather than clipped to their boxes. Whole builds, p50: fennec 1.36 to 1.23 s with painted garments and 1.45 to 1.36 s bare, woman 1.26 to 1.16 s and 1.13 to 1.06 s; no added cost.

## Built: the Dress painting UI on the engine (round 4 of 4)

- Holes, cuffs and hanging are read over the region a garment's cells paint (`clothBranch`: along the rows the cells take, round from the edge of their first column to their last, `ClothReach.fromV`, `toV`), in `WornCover` and in `Dressing`'s hiding of separate pieces under cuffs; Dress writes a part's `Through` where its socket lies in that region (`ClothReach.holds`). The branch's box, which Dress writes as the cells' `ChartBox` half a cell wider, only records bounds. The vampire cape repainted through the paint commands keeps the cat's legs hanging inside it (`ClothDecisionsFlowTest`); deciding on its `ChartBox` would cuff them. A part counts as covered whole for its separate pieces where every cell of it is painted (`ClothField.paintsAll`).
- The twin's flats paint a column more each side round the heel up to behind the ankle, so the counter stands as high as the sides' hem and runs level across the back of the heel with no notch.
- Edges show as the curve the cloth is cut on: found along the cells' sides, then moved onto `areaLevel` (the cloth field less its hem level; where areas meet, the area's field less the most of the others') by `onLevel`. Each hem is fitted with as few joined cubics as stay within half a cell (`HemHandles`, `fitFewCubics`), at least four round a closed hem: a slanted sock round the fennec's leg takes 5 handles against about 40 points. Dragging a handle bends the hem between its neighbours and repaints only the cells either side of where the curve moved (`repainted`); Smooth repaints the same way. `SetBranchRange` and `SetBranchAround` record bounds only; no tool repaints through them.
- Painted cells an opening will cut, round the root of each part the cloth leaves out on a socket inside the region its parent's cells paint, show as warn-coloured dots (`cutCells`).
- A brush or line keeps off the parts above the one it starts on until a dab is centred on one with no cell of the start part within the brush's size (`BrushReach`). A sleeve brushed from the shoulder lays no torso cells round the socket; a stroke carried onto the chest takes the torso in.
- Steps of 32 rows instead of 16: a stroke down a torso crosses one step, not two.

Timings, `mode=time runs=5`, the same strokes, on this round's code with each step size (ms; dress ready p50 / p95, re-meshed frames of 115, re-mesh and kept frame dress ready p50, settled p50 / p95):

| Example | Steps | Dress ready | Re-meshed | Re-mesh frame | Kept frame | Settled |
| --- | --- | --- | --- | --- | --- | --- |
| fennec | 16 | 38 / 84 | 9 | 92 | 37 | 86 / 146 |
| fennec | 24 | 45 / 83 | 9 | 119 | 44 | 129 / 231 |
| fennec | 32 | 43 / 69 | 5 | 120 | 43 | 124 / 167 |
| woman | 16 | 49 / 230 | 10 | 250 | 48 | 308 / 475 |
| woman | 24 | 53 / 274 | 10 | 312 | 52 | 342 / 471 |
| woman | 32 | 53 / 178 | 7 | 260 | 52 | 317 / 415 |
| fennec-dressed | 16 | 49 / 100 | 9 | 140 | 48 | 109 / 201 |
| fennec-dressed | 24 | 50 / 137 | 9 | 161 | 49 | 121 / 270 |
| fennec-dressed | 32 | 50 / 94 | 5 | 179 | 49 | 113 / 185 |
| woman-dressed | 16 | 45 / 200 | 10 | 243 | 44 | 276 / 379 |
| woman-dressed | 24 | 49 / 231 | 10 | 231 | 48 | 297 / 443 |
| woman-dressed | 32 | 48 / 85 | 7 | 188 | 48 | 280 / 372 |

Runs on this machine vary by about 5 ms on kept frames from run to run (the 24-row run is slower everywhere). With 32 rows a third to a half fewer frames regrow, and dress-ready p95 falls on all four (woman 230 to 178 ms, woman-dressed 200 to 85 ms); a regrow costs about the same, and kept frames a few ms more, the grown surface covering more rows.

## How the cloth is measured

The harness (`./gradlew :app:spike -Pspike="mode=render subjects=fennec,woman"`, in `app`'s tests under `look/spike`; `mode=time` and `mode=build` time strokes and whole builds) checks each vertex of the grown garment that lies at least 95% on one part's first run against the chart cell under it. It reports two numbers:

- **field against paint:** whether the field's half level agrees with the painted cell. A disagreement is a stray, measured in cells to the nearest cell that agrees. This is the fidelity of exact coverage.
- **coverage against field:** vertices the field covers but the garment cuts away. These are openings: holes where an uncovered part comes out, and open ends such as the neck, the tail and the front legs. They are cut the same way under the box, and they are correct.

The older single measure counted openings as misses, as many cells deep as the paint is wide. That made slanted-hem look as if it failed (p50 23.6 cells). It also gave a chart painted whole, two-areas, no cell to measure against (Float.MAX).

## Results

Field against paint, in chart cells (p95 / max), with the vertices cut at openings:

| Subject | Shape | Field stray p95 / max | Cut at openings | Box, for comparison |
| --- | --- | --- | --- | --- |
| fennec | slanted-hem | 0.11 / 0.11 | 100 (front-leg holes, rows 46–55) | — |
| fennec | thigh-stripe | 0.19 / 0.22 | 0 | — |
| fennec | chest-patch | 0.10 / 0.14 | 10 (neck end) | — |
| fennec | forearm-sleeve | 0.08 / 0.11 | 1 | over 62, p95 1.71, max 2.33 |
| fennec | wavy-boot | 0.26 / 0.26 | 5 | — |
| fennec | diagonal-stripe (tilted ring round the front leg) | 0.23 / 0.27 | 3 | — |
| fennec | two-areas | 0 / 0 | 279 (tail end rows 0–18, leg holes 46–55) | — |
| woman | slanted-hem | 0.17 / 0.17 | 214 (neck rows 60–63, arm holes 44–53) | over 309, p50 5.8, p95 38 |
| woman | thigh-stripe | 0.15 / 0.17 | 2 | — |
| woman | chest-patch | 0.17 / 0.28 | 2 | — |
| woman | forearm-sleeve | 0.13 / 0.19 | 1 | — |
| woman | mid-arm-sleeve | 0.13 / 0.19 | 1 | over 82, p95 2.17, max 2.95 |
| woman | wavy-boot | 0.30 / 0.33 | 9 | — |
| woman | diagonal-stripe (tilted ring round the arm) | 0.14 / 0.27 | 3 | — |
| woman | thin-line (one to two cells wide, along the arm) | 0.34 / 0.37 | 6 | — |
| woman | two-areas | 0 / 0 | 210 (neck, arm holes) | — |

Box numbers are the same shapes read through their branch boxes. The box makes the woman's slanted hem a straight band, and grows a rectangle of torso round the sleeve's root.

The ragged edge where the fennec's forearm sleeve meets the chest is in the reference overlay, not the cloth. The round brush at the leg's root lays a few torso cells round the socket (torso rows 44–58, columns 10–16). Torso cells are about three times the size of leg cells, so the drawn outline of those cells steps. The cloth's edge is a smooth curve over them (`fennec-forearm-sleeve-close.png`); the box puts a rectangle of torso there instead (`fennec-forearm-sleeve-close-box.png`).

Limits found:

- A line one cell wide drops out: blurred by 0.8 cells, its middle reads 0.47, under the half level.
- A line two cells wide survives in the field but renders broken, finer than the garment's mesh (thin-line).
- Paint over an opening is not cloth: the hole and the end planes cut it, as under the box.

## Timings

Measured with `mode=time` and `mode=build` on a quiet machine (one build at a time), `runs=5`: 5 strokes of 23 drag frames each, 115 frames per example. Whole part is the round 1 engine, which grows a painted part whole; stepped is the range above with 16-row steps, and 8-row steps are shown for the woman to show the trade.

**Per drag frame** of a brush stroke down the torso, into a garment holding one dab, in ms (p50 / p95):

| Example | Dress ready, whole part | Dress ready, stepped | Settled, whole part | Settled, stepped |
| --- | --- | --- | --- | --- |
| fennec | 42 / 81 | 43 / 90 | 96 / 147 | 96 / 145 |
| woman | 82 / 131 | 52 / 319 | 367 / 528 | 353 / 524 |
| fennec-dressed | 39 / 82 | 41 / 121 | 123 / 216 | 123 / 201 |
| woman-dressed | 72 / 117 | 48 / 203 | 302 / 537 | 291 / 424 |

**Re-meshes**, frames where the garment grew again, of 115, and dress ready on those frames and on the others, p50 in ms:

| Example | Re-meshed, whole part | Re-meshed, stepped | Re-mesh frame, whole part | Re-mesh frame, stepped | Kept frame, whole part | Kept frame, stepped |
| --- | --- | --- | --- | --- | --- | --- |
| fennec | 5 | 9 | 146 | 98 | 41 | 41 |
| woman | 6 | 10 | 507 | 337 | 81 | 51 |
| fennec-dressed | 5 | 9 | 190 | 133 | 39 | 40 |
| woman-dressed | 6 | 10 | 425 | 257 | 72 | 47 |

With 8-row steps the woman re-meshed 19 times at 290 ms a re-mesh, dress ready p50 48 and p95 366 ms. Summed over the drag, dress ready on the woman takes 11.9 s grown whole, 10.0 s in 8-row steps and 8.9 s in 16-row steps.

- **Cost of a re-mesh** on the woman falls from 507 to 337 ms (34%), and on the dressed woman from 425 to 257 ms, since only the painted rows' steps of the torso grow.
- **Kept frames** are cheaper too on the woman, 81 to 51 ms, since the cut reads a smaller surface.
- **More re-meshes:** a stroke down most of the torso crosses about two steps, so 9–10 frames of 115 regrow against 5–6 grown whole. Those frames sit above the 95th percentile's rank, so dress-ready p95 rises (woman 131 to 319 ms) while p50 and p90 fall (woman p90 116 to 76 ms). The box path re-meshed about every frame.
- **Release** after a drag takes 1–2 ms. **Whole stroke sent cold**, dress ready p50: fennec 61 to 65 ms, woman 151 to 283 ms, fennec-dressed 74 to 150 ms, woman-dressed 99 to 163 ms: a cold stroke always regrows now, and a whole part's surface held from the dab.
- **Live feel:** settled frames are unchanged or faster (woman-dressed p95 537 to 424 ms); the settled time is spent mostly downstream (lines, posed surface, skin).

**Whole build**, opening the character with three painted garments (slanted-hem, forearm-sleeve, wavy-boot), p50: fennec 1.35 s whole part against 1.36 s stepped, woman 1.47 s against 1.26 s. Bare, 1.39 s and 1.45 s on the fennec, 1.14 s and 1.13 s on the woman. No added cost.

## The rounds left

1. **Engine.** Built; see above.
2. **Growth and cost.** Built; see above. Left open:
   - The regrow frames set the drag's p95. The range must be a function of the cells alone for an edited build to equal a fresh one, so the stroke's history cannot widen it; round 4 takes 32-row steps.
3. **One path for every garment.** Built; see above.
4. **Dress painting UI.** Built; see above.

Risks:

- **Coarse charts on big parts.** The chart is 64 by 32 cells whatever the part's size, so a torso's cell is about three leg cells. Detail finer than one cell is lost there. A finer chart for big parts is the fix if this matters.
- **Regrow hitches.** A long stroke on the woman's torso regrows the garment about once, about 260 ms, where the rest of its frames are ready in about 50 ms.
- **Structural reads of the box.** Still read from the branch's box, the cells' bounds: a part-round covering's hider over separate parts (`GarmentHiding`), the hair push, Tuck rows and the Dress inspector's reading.
- **Handles at a hem's steep stretch.** A hem whose cells climb several rows at one column (a slanted sock where its slant wraps round) gathers handles there; a long cubic between far handles bends wide when one end is dragged.
- **Cut dots on the far side.** The warn dots, as the edges, draw on the side of the body away from the view too.

## Renders

`spike-render-2` beside the job's tmp folder. For each subject and shape, the painted cells, the cloth, and the cloth with the cells' edge drawn over it, plus a posed view. `-box` renders are the same shape under the branch box. `fennec-forearm-sleeve-close*.png` are framed on the leg and torso.

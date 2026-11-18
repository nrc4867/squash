# Test audit: duplicated heavy tests

This note audits the tests that run over 2 s in a full run (261 tests, about 1650 s of about 1940 s). The heaviest classes by total time were read, test body by test body, in `app`, `screens-surface`, `screens-draw`, `screens-sheet`, `screens-shell`, `character` and `rig`. A test is a duplicate candidate when (a) another test checks the same fact through the same path, (b) several tests in one class each rebuild the same session to assert neighbouring facts, or (c) a UI-flow test in `app` re-checks what a lower-module test covers through the same pipeline. Nothing was deleted or edited; this is a list for the owner to decide on.

Recommendations used below:

- merge: fold the tests into one (or two) so the shared build or window mount happens once. A merged test keeps every assertion of its parts and undoes its own steps between parts where the parts conflict.
- drop: only where a named test checks the same thing through the same path.
- keep: looks similar but checks something distinct, said in the Why column.

Savings are estimates. A build or mount costs roughly the time of the cheapest test in its class (a saved fennec session is about 3.5 s, a dressed fennec in the app window about 5 to 6 s, a plain app workspace about 2.5 s). The first test of a class also pays class loading and warm-up, which no merge removes, so a few figures are lower than the listed times suggest. Classes whose tests build the session in a field initialiser or a per-test helper (`SurfaceToolsTest`, `FaceWorkTest`, `PaintSectionsTest`, `MovePrecisionTest`, the posed-fennec classes in `rig`) can share one session per class (`@TestInstance(PER_CLASS)` or a lazily created session) when their tests are read-only or undo what they send; that is the "merge" in those rows.

## app

| Test(s) | Builds | Duplicates | Recommendation | Why | ~s saved |
| --- | --- | --- | --- | --- | --- |
| `PaintPointerFlowTest` palette and region group: picker drag, ink toggle, region swatch, region row, double-click name, delete slot, long palette, add slot (8 tests, 44 s) | 8 fennec sessions with the drawer mounted in the Sheet layout | Each opens the same drawer over the same fennec and touches a different control; none shares state with another | merge into two tests (fill-tool drawer; brush-tool drawer with the region rows), each part undoing its step before the next | Every part asserts on the drawer and the palette in the document; nothing needs a fresh character | 22 |
| `PaintPointerFlowTest.aBrushStrokeLandsFromFullFirstsFreePane` | 1 fennec, Full-first | `PaintSelectFlowTest.selectPicksWithoutPaintingAndLettingTheChoiceGoFreesTheBrush` ends by dragging the brush on the torso in the free pane with nothing chosen and undoing once | drop; move its two extra assertions (anchor `u` under the pointer, a cut on the torso) into `aDragFromALegUpOntoTheTorsoLandsOnBothAndMirrors` | Same pane, same tool, same part, same one-undo check | 6 |
| `PaintPointerFlowTest.eraseTakesAwayTheBrushStrokeUnderAPressInOneStep`, `aDragFromALegUpOntoTheTorsoLandsOnBothAndMirrors` | 2 fennec sessions, Full-first | Same layout and part | merge (one Full-first mount) | Erase seeds its own stroke; a leg-to-torso drag does not conflict | 8 |
| `PaintPointerFlowTest.underThePointerFillsTheAreaThereAndWholePartTheBase` (16 s) | 1 | `PaintSectionsTest.underThePointerFillsWhatShowsThereAndWholePartTheBase` (graph level) | keep | The graph test checks the section kinds returned; this one checks the written `areaFills` and `baseSlot`, the Whole part toggle and undo through the real pointer | 0 |
| `PaintSelectFlowTest` `aPartChosenWithSelect...` and `selectPicksWithoutPainting...` (6 s, 10.6 s) | 2 builds of the same Color stage | Both press Select, choose a part, arm the brush and drag, then undo | merge into one | The two run in sequence on one stage | 6 |
| `PaintSelectFlowTest.paintingNearAnotherPartNeitherChoosesItNorSpillsOntoIt` | 1 | none | keep | Needs the torso already chosen; the others start with nothing chosen | 0 |
| `GarmentEdgeFlowTest` all three (87 s: 49.1, 27.9, 10.4) | 3 bare fennec windows, each followed by New garment | `newGarmentIsWorn...` asserts state right after New garment, which the other two reach as set-up | merge `newGarmentIsWorn...` into the edge-across-a-leg test (assert, undo, redo the creation, go on); run the tail test on the same window after the leg test has removed its edges | Same window, same fennec, same first step; the tail sock edge is independent of the boot edges once all edges are removed | 18 |
| `LibraryFlowTest` new-character group: F1 tail, F2 paw, leg from a tile menu, F3 leg removal, paw tips row, Ctrl+L and Ctrl+F, arrows and Enter, dragged tile (8 tests, 28 s) | 8 new-character windows | All begin from `created()` and exercise the Library over sockets; F1 repeats the socket-row path in `ShellFlowTest.aSocketRowTargetsItInLibraryAndAddingSelectsThePart` | merge into three tests (Library keys and drag; add and replace; remove) | Each starts on the same new character; the sockets used differ, so the parts can follow one another | 12 |
| `LibraryFlowTest` dressed group: F4 new in slot, F5 outfit, F6 press wears, double click, Shift+F10 (5 tests, 57 s) | 5 dressed fennec windows | F6 and the double click both click the boots tile on the same slot; F4 and F5 both rename through the same field | merge into two tests: (F6, double click, Shift+F10) and (F4, F5) | Same boots tile and winter outfit; each part restores its pick or undoes before the next | 15 |
| `DressFlowTest` all five (63 s) | 5 dressed fennec windows | The strip, outfit and layer tests all start at Dress on the same character | merge into two: (strip, inspector, status, layers list, pick leaves row) and (boots toes, grip drag) | Read-mostly assertions on one window; the two that edit finish by undoing | 15 |
| `FurPointerFlowTest` torso group: fur drag in Full-first, press off the character, edit-curves drag (3); tail group: floating stroke, comb (2) | 5 fennec windows | Same layout and selected part within each group | merge into two tests (torso, tail) | Each part undoes its stroke | 14 |
| `FurPointerFlowTest.aFurStrokeDragInTheSheetsFullLands` | 1 | the Full-first fur drag | keep | The only test that runs the Sheet layout's own Full pane | 0 |
| `ShellFlowTest.aSocketRowTargetsItInLibraryAndAddingSelectsThePart` | 1 | `LibraryFlowTest.f1ATailGoesOnItsSocketInOneStep` (press a socket row, Library cursor, Add, part selected) | drop | F1 asserts the same path and more (one step, Recent) | 2.5 |
| `ShellFlowTest` `tListsTheOpenSockets...`, `undoInTheTopBar...`, `aSocketOffersOnlyTheTypes...`, `aSliderOverValuesSkipsWhatTheyLeaveOut` | 4 new-character windows | Same new character and the same tail and leg placement | merge into two tests | Independent key, undo and slider checks | 5 |
| `ShellFlowTest` other nine (first launch, recent files, menus, status bar, rails, mirror) | 9 | none | keep | Each checks a distinct screen or file flow; under 3 s each apart from the mirror test | 0 |
| `FaceFlowTest` painted eye, mouth shape, locked version, head angle (4) | 4 fennec windows on the Face stage | All pick the head's mouth or eye through the same helper | merge into one test | One window serves all four; each part resets its pick | 10 |
| `FaceFlowTest.anExpressionsTileShowsItOnTheCharacterAndMovesTheEars` | 1 | `FaceWorkTest.anExpressionsPoseTurnsTheEars` moves the same ears at graph level | keep | This one checks the tile, the neutral reset and New and Delete through the UI; the ear-position assertion alone is the overlap | 0 |
| `LinesFlowTest` inspector list, stroke colour, width drag (3) | 3 fennec windows | Same mount on the Lines drawer | merge into one; keep the strip-and-keys test apart | The three work on one drawer and each undoes | 10 |
| `KeysFlowTest` Esc and press, F fits one pane (2) | 2 | Same fennec window | merge | Independent keys on one window | 5 |
| `StartFromTemplateTest` all three (25.6 s) | 3 workspace mounts | The two state-only tests (`inSingleItWaitsForASnappedView`, `thePressWithAStrokeUnderWayDoesNothing`) mount the full workspace only to read `templateStartView()` | merge into the undo-step test | One workspace; the state-only checks run after the row click and reset their flags | 8 |
| `ReferencesFlowTest` image traces and deleting an image (2); `ReferencesFlowTest.focusSwapsThePaneUnderThePointerIntoTheLargePlace` with `FocusPaneButtonTest` (4) | 7 windows | The trace and delete tests both place an image on the board and trace it; `focusSwaps...` and the focus-button tests all set the Focus layout on a fennec | merge the two image tests; move `focusSwaps...` into `FocusPaneButtonTest` and merge that class's four | Same board set-up; same layout | 14 |
| `SingleLayoutFlowTest` all five | 5 windows at 1900x900 | Snap and drawing tests share the Single layout over the same fennec | merge into two (layout control and persistence; snapping and drawing) | Each part leaves state the next can reset | 8 |
| `WardrobeTilesTest` all five | 5 | The name-field tests share one dressed mount | merge into two (naming; thumbnails) | Each naming test opens a name field on the same window | 6 |
| `OversketchToolTest` (3 of 6), `CurveToolsFlowTest` (4), `SpineFlowTest` (4), `OutlineDrawerTest` (4) | 15 workspace mounts | Within each class every test mounts the same workspace to read the same drawer | merge per class | Drawer read-outs and key arming do not conflict; undo between parts | 20 |
| `FullFirstFlowTest` (2), `PaneLabelsTest` (2) | 4 | Same mount per class; `FullFirstFlowTest.drawOutlineWritesNothingInFullAndAnOutlineInASheetOfFullFirst` states the rule `FullFirstUiTest.anOutlineToolIsRefusedInTheFreePaneWithTheChipLitAndWorksInTheSheets` states with a stub tool | merge within each class; keep both across the pair | The lower-module test uses a stub tool in a bare pane; this one checks the real outline tool and the layout picker | 7 |
| `BodyCurveFlowTest`, `PoseFlowTest`, `RigFlowTest`, `AddSocketFlowTest` | 1 each | `BodyCurvePoseTest` in `rig` checks the same planted-paws invariant at solver level | keep | These drive the gesture (strokes in two panes, a paw drag, a seam stroke, an Add socket press) and check the written command and steps, which the solver tests cannot | 0 |

## screens-surface

| Test(s) | Builds | Duplicates | Recommendation | Why | ~s saved |
| --- | --- | --- | --- | --- | --- |
| `SurfaceToolsTest` 14 tests (56 s) | 14 sessions (`session()` rebuilds the fennec from commands each time) | All build the same character; most only read, the rest undo | merge: one shared session per class | Every test either reads or restores history; the camera and look tests need no session at all | 35 |
| `FaceWorkTest` 5 heavy | 5 fennec sessions from a field initialiser | Same character; ears, mouth, role drawing | merge: one shared session | Each part ends with undo or a reset to neutral | 15 |
| `FloatingMarksTest` 6 heavy | 6 sessions (lazy per instance) | Same head root, undo between marks | merge: one shared session | Each test undoes the mark it sends | 12 |
| `PaintSectionsTest` 7 heavy | 7 sessions | Same fennec at rest | merge: one shared session; the test that sends a command undoes it | The picks and fills are read-only at graph level | 15 |
| `RigInspectorTest` tool options, joints row, paw toes (3) | 3 | Same fennec and inspector | merge into one; keep the reach step and the seams buttons apart | The last two edit the rig and read the last command | 8 |
| `PoseInspectorTest.aWomanUprightByMoveNamesHerFeetAndCurlsAPickedHand` | 1 | `anUprightCharactersFeetAreItsPlantedRowsAndItsHandsAreNot` and `aPickedHandCurlsItsFingersInOneStepAndTheTwinsWithMirror` cover the same feet-pin and curl rules on the same kind of upright character | drop | Same rule, same path, different instance of the same body plan | 4 |
| `PoseInspectorTest` `dragsOptionsAreAppStateAndWriteNothing`, `aPickedLimbShowsItsSectionThenItsKneesTurn`, `eachPawHasAPinRow` | 3 | Same fennec inspector | merge | One mount; each part reads the last command | 4 |
| `PoseInspectorTest` bird feet, picked finger joint | 2 | none | keep | A different template family and the turn section of a finger bone | 0 |
| `RigJointsTest` two cases on the upright character (at rest, walking) | 2 sessions | Same character | merge into one (rest, then walking) | One session, two poses | 4 |
| `RigJointsTest` quadruped case, `BodyCurveSectionTest` | 2 | none | keep | A different template family; the body curve section is the only inspector check | 0 |

## screens-draw

| Test(s) | Builds | Duplicates | Recommendation | Why | ~s saved |
| --- | --- | --- | --- | --- | --- |
| `MovePrecisionTest` all six (64 s) | 6 workspaces built in field initialisers | Same fennec, same dragged socket; each ends on its own undo or a fresh drag | merge: one shared state per class, each test undoing its drag | Axis holds, snap, Ctrl speed, add joint and arrows are separate drags on one workspace | 25 |
| `FewPointStrokeTest` leg/ear/tail fit, fit toward smooth, curve joins, same stroke small and large (4) | 4 benches | The tail-fit and size tests run the same loop (stroke, count points, undo); the joins test draws the leg stroke the first test draws | merge into one bench | Each already undoes after every stroke | 15 |
| `FewPointStrokeTest` Ctrl snaps, release preview, overshooting loop | 3 | none | keep | Modifiers, the live preview overlay and a torso loop are distinct | 0 |
| `TiltedStrokeInPlaceTest` stroke-alone tests (ear side, ear top, tail side, tail top) and `theRightEarBuildsUnderAStrokeInEveryView` | 5 | `theLeftEarBuilds...` and `theTailBuilds...` draw side, top and front in turn and assert each silhouette under the mesh; the right ear is the left ear mirrored through the same path | drop; keep the bent-stroke, oversketch and edit tests (the right ear still appears in the edit and oversketch cases) | Same bench, strokes and assertion; only the order of asserting differs | 10 |

## screens-sheet and screens-shell

| Test(s) | Builds | Duplicates | Recommendation | Why | ~s saved |
| --- | --- | --- | --- | --- | --- |
| `OutfitSwitchDrawsTest` | 1 dressed upright character, three outfit switches | none | keep | The only test that checks draws between outfit switches take patches and mesh from one build; the three switches are the swap and its reverse | 0 |
| `FullFirstUiTest` three | 3 | `FullFirstFlowTest` rule above | keep | Stub-tool harness in a bare pane; checks the ray pick, orbit versus stroke and the refusal chip | 0 |
| `AnatomySectionTest` save template, saved template offered by Library (2) | 2 | Both save a template through the same dialog | merge | The second continues from the file the first writes | 4 |

## character

| Test(s) | Builds | Duplicates | Recommendation | Why | ~s saved |
| --- | --- | --- | --- | --- | --- |
| `LeaningLegsTest.drawnLegsOfOneLengthStayLevelWhateverTheirEndsDo` (27.6 s) | 15 level checks on the dog | The four non-limit commands repeat `aLeanEitherWayStaysLevel`, `aLeanAndAnOutwardTogetherStayLevel` and the outward case, with drawn outlines | keep, but run the four ordinary commands on one drawn shape and the limit command on all three | The limit residual is what depends on the shape; the ordinary cases are covered undrawn | 10 |
| `LeaningLegsTest` `aLeanedPairStaysLevelWithBothSidesAtFullLength`, `aLeanEitherWayStaysLevel` | 2 | Same leg lean with one more assertion | merge into one shared-session test | Both use the shared dog | 4 |
| `LeaningLegsTest` others (6) | 6 | none | keep | Distinct limits, mirror, ratio and half-built cases; most already share one dog | 0 |
| `LeaningLegsRebuildTest` all five | 5 dogs | Leaning back and undo both assert "every vertex returns to square"; the other three each build the same dog for a different node check | merge into two tests (vertices return exactly; what rebuilds and what is reused) | One dog per test, each part applies and removes its command | 9 |
| `EveryVersionSurfaceTest` | 1 | `ShippedTemplatesTest.everyTemplateBuildsWatertightExceptItsOpenEnd` | keep | Checks a one-surface closed, manifold mesh at the coarse cell, not the patch invariants | 0 |
| `ShippedTemplatesTest.randomParametersInsideTheirRangesBuild` (22 s) | 25 builds per template | `everyTemplateBuildsWatertightExceptItsOpenEnd` covers the default parameters | keep, with `RANDOM_BUILDS` lowered from 25 to about 8 | A fixed seed keeps it repeatable; further samples of the same ranges add little | 14 |
| `TemplateOutlineTest` (3), `DrawnPartTest.everyTemplateBuildsWatertightFromDrawnViews` | 4 | Both build watertight parts from drawn views over every template | keep | One starts from the template's own silhouette and checks tolerance; the other uses fixed hand outlines and checks the invariants | 0 |
| `BodyChangeBuildTest` (3), `APoseTest` (2) | 5 sessions | Same character within each class | merge per class | Each applies a change and reverts | 7 |

## rig

| Test(s) | Builds | Duplicates | Recommendation | Why | ~s saved |
| --- | --- | --- | --- | --- | --- |
| `PosedSurfaceTest` 5 | 5 posed fennec sessions | Same pose set-up; legs, tail, inked join, stroke, rest | merge: one shared posed session | Each reads one part's lines | 12 |
| `LooseEndRigTest` 3 | 3 scarfed fennec sessions | Same | merge: one shared session | One reads at rest, one turns the root and undoes | 10 |
| `PoseStepWorkTest` 2, `SessionPoseTest` 2 | 4 | Same fennec; the two step tests differ only in the pose passed to one helper | merge per class | The helper can run both poses in one session | 7 |
| `SpikeEightTest` 2, `DigitRigTest` 2 | 4 | Same rig per class | merge per class | Different curves or poses on one rig | 6 |
| `RolesNotTemplatesTest` 3 | 3 | none | keep | Three characters with every template renamed; each character is its own case | 0 |
| `BodyCurvePoseTest`, `WomanPoseTest`, `DragPosedTest`, `SurfaceEditCellsTest`, `LibraryUpdateRigTest`, `ClawRigTest`, `HairRigTest` | 7 | none | keep | Each covers a distinct rig behaviour on its own character | 0 |

## Totals

Tests named in the tables: about 170 recommended merge, 8 drop and 56 keep (each test of a merged group counted once). A full adoption of the merges and drops would save an estimated 430 s of the 1940 s total, about 22 %. A conservative reading, counting only merges with identical set-up (the shared sessions in `screens-surface`, `MovePrecisionTest`, the `rig` classes and the window mounts in `app`) and halving the rest, is about 300 s.

| Module | ~s saved |
| --- | --- |
| app | 205 |
| screens-surface | 97 |
| screens-draw | 50 |
| character | 44 |
| rig | 35 |
| screens-shell and screens-sheet | 4 |

The largest single items:

1. `SurfaceToolsTest`, one shared session for 14 tests: 35 s.
2. `MovePrecisionTest`, one shared workspace for six tests: 25 s.
3. `PaintPointerFlowTest` palette and region group into two tests: 22 s.
4. `GarmentEdgeFlowTest`, three windows into two: 18 s.
5. `DressFlowTest` into two, `LibraryFlowTest` dressed group into two, `PaintSectionsTest` shared session and `FaceWorkTest` shared session: 15 s each; `ShippedTemplatesTest` random builds from 25 to 8: 14 s.

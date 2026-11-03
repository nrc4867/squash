# Boards

The state of every screen against its board in [Squash flow of work](https://claude.ai/artifact/KfBGmYcUEuoRJuhvGky3CT). States: planned, building, verifiable, verified. A session sets building and verifiable and fills in Verify; only Nicholas sets verified. The board's note in the artifact carries the same state.

Verify is what a person runs to see the board working: a run configuration or Gradle task, then the actions to take, then what must be true.

## Foundation

Not a screen, so it has no board in the artifact.

| Work | State | Verify |
| --- | --- | --- |
| Stage 1 skeleton | verifiable | Run: `./gradlew check`. See: passes, including the schema staleness test and `verifyIcons`. Test: `./gradlew :character:desktopTest --tests "*ShippedTemplates*"`; the six starter templates and the canine plan load, lint and build watertight except the open end. `./gradlew :app:run` opens the empty frame |

## Home

| Board | State | Verify |
| --- | --- | --- |
| Home | verifiable | Run: `./gradlew :app:run`. Do: save a character with File, Save as, close the app and start it again. See: Home lists the file under Recent files, its card shows "Build · 0 of 8 parts · today HH:MM", and Continue opens it; Clear recent files shows "No projects" with New character. Test: `./gradlew :ui-screens:test --tests "*ShellFlowTest*" --tests "*FilesTest*"` |
| Home: library | planned | |
| Home: cast card menu | planned | |

## Build a part

| Board | State | Verify |
| --- | --- | --- |
| 1 New character | verifiable | Run: `./gradlew :app:run` with no `recent.txt` in the app data folder. Do: first launch opens here; type a name; Canine quadruped is selected; Create character. See: "8 parts" and chips Torso, Head, Leg 4, Tail; then the workspace with "<name> · draft" and a dot, Torso selected, status "0 drawn · 3 from template". Test: `./gradlew :ui-screens:test --tests "*CharacterFactoryTest*" --tests "*ShellFlowTest.firstLaunch*"` |
| 2 Pick a part type | verifiable | Run: `./gradlew :app:run`. Do: in Parts click Front leg; click Draw leg, or another tile first, which moves the socket. See: the bottom panel shows the six tiles, the HUD reads "Socket · front leg", the drawer shows You draw, Tool knows and Areas, status "0 of 8 parts". After Draw leg, Front leg is selected with Paw under it; one undo removes both sides. Test: `./gradlew :ui-screens:test --tests "*SocketsTest*" --tests "*AppStateTest*" --tests "*ShellFlowTest.aSocketRow*"` |
| 3 Draw outlines in place | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs, Draw outline armed, draw a closed shape across the right edge of Side, press Ctrl Z. See: the stroke follows the pointer with a tip dot and Side shows Drawing; on release a curve open on the socket plane replaces it and the chip reads Drawn; undo removes it in one step; Full takes no stroke. Test: `./gradlew :geom:desktopTest --tests "*StrokeCleanup*"` and `./gradlew :ui-screens:test --tests "*DrawOutlineTool*" --tests "*StrokeOutline*"` |
| 4 Fit the part template | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs, draw a closed shape in Side, then one in Top. See: the leg fills in Side and follows the drawing in Full; Top adds width; the status bar reads "1 drawn · 2 from template", then "2 drawn · 1 from template". Test: `./gradlew :ui-screens:test --tests "*DrawnPartPipeline*"` and `./gradlew :character:desktopTest --tests "*DrawnPart*" --tests "*PawAndHead*" --tests "*ShippedTemplates*" --tests "*SessionMesh*"` |
| 5 Oversketch, drag handles | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs. Draw a closed shape across the right edge of Side, then one in Top. With D armed, stroke over part of Side's curve; then a short stroke far from it. Press E, drag a handle in Side, then drag the curve's far tip. Press Ctrl Z after each. See: Side's chip reads a px value. While stroking, the old span is dashed and the highlight follows the stroke. On release only that span changes in all panes, with the status "1 section touched" and the toast "Section N refit · other sections unchanged". The far stroke does nothing. The handle drag shows live and is one undo step, and the chip returns to Drawn. Dragging the tip moves every ring and the toast says "whole part rebuilt". Test: `./gradlew :character:desktopTest --tests "*OversketchLocality*"`; `./gradlew :geom:desktopTest --tests "*Oversketch*"`; `./gradlew :document:desktopTest --tests "*OutlineEdit*" --tests "*OutlineGesture*"`; `./gradlew :ui-screens:test --tests "*EditCurvesTool*" --tests "*OversketchTool*" --tests "*CurveReadouts*" --tests "*CurveToolsFlow*"` |

## Several cameras

| Board | State | Verify |
| --- | --- | --- |
| A Sheet: three cameras and full view | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs. Middle-drag and scroll in Full. Press Top. See: Top, Full, Side and Front in one surface, the Layout control above them with only Sheet enabled. Full shows the built part filled and orbits. Side, Top and Front show its dashed silhouette with "From template" chips at one scale. Side's extents appear as dashed guides lining up across panes. The accent ring moves to Top. Test: `./gradlew :ui-screens:test --tests "*sheet*"` and `./gradlew :render:test` |
| A Sheet: views that disagree | verifiable | Run: `./gradlew :app:run`. Do: with a part selected, draw Side, then Top longer than Side, then Front taller than Side. See: past Side's end, Top's outline turns thick warn-edge with a warn line at that end in Top and Side, growing as the stroke does; above Side's height, Front's outline does the same; no message appears. Test: `./gradlew :ui-screens:test --tests "*ViewsDisagree*" --tests "*DisagreeCost*"` |
| B Focus | planned | |
| C Full first | planned | |
| A Sheet for face marks | planned | |
| A Sheet for posing | planned | |

## Build the character

| Board | State | Verify |
| --- | --- | --- |
| 6 Mirror and join | planned | |
| 7 Rig and weights | planned | |
| 8 Garments and slots | planned | |

## The look

| Board | State | Verify |
| --- | --- | --- |
| 9 Color and regions | planned | |
| 10 Lines and fur | planned | |
| 11 Face marks | planned | |

## Make a shot

| Board | State | Verify |
| --- | --- | --- |
| 12 Body curve | planned | |
| 13 Limbs and stretch | planned | |
| 14 Settle, touch up | planned | |
| 15 Timing | planned | |
| 16 Scene | planned | |
| 17 Render | planned | |
| Shot: eyes and gaze (board not yet drawn; needed before the posing screens) | planned | |

## Imported images

| Board | State | Verify |
| --- | --- | --- |
| References: inspiration board | planned | |
| Trace template: one image per view | planned | |
| Trace template: place an image | planned | |

## Objects and sets

| Board | State | Verify |
| --- | --- | --- |
| Object: rigid, from pieces | planned | |
| Object: organic, from clumps and marks | planned | |
| Set: a room from its plan | planned | |
| Set: dress the room | planned | |
| Library: objects | planned | |

## Differences from the boards

Where stage 1 is built differently from a board, and why. Omissions of stage 2 work are listed only when a board draws them on a stage 1 screen.

| Board | Difference |
| --- | --- |
| All | Top bar menus hold only what has an action. Rails hold only the drawers that exist. Tree rows are Stretch's 32 px, not 36; the left drawer is Stretch's minimum 260, not 248. Part types shares the rail's foot group with Help, because Stretch ties the bottom panel to that group |
| Home | No rest-pose art, since nothing renders one there; the card shows the parts icon. Search, Settings and the Cast, Shots and Objects rows are left out. The drop zone's second line is JSON |
| 1 | No rest-pose preview. The count and chips come from the plan: 8 parts, Torso, Head, Leg 4, Tail. The dialog is Stretch's, with its close button |
| 2 | No rest-pose art or socket dots, so sockets are picked in the tree. Tool Select and Add part are left out. Row names come from the plan. Tile art is the 16-grid icons |
| A | Other parts are not shown: Full holds only the selected part, because parts are not yet placed on sockets. Readouts in pixels and Refit progress are not built. Erase and Mirror are not in the strip. Undrawn views show a dashed silhouette, as the board labels them |
| 3 | Built on the Sheet panes, the default layout, not the Single layout the board shows. The Outline drawer, Snap view, thumbnails, Erase and Mirror are not built; smoothing is fixed at the board's default. A drawn pane also shows its stored curve. Pen pressure is not read, so the status bar has no pen readout |
| 4 | The Refit and Working readouts are not built; the old mesh stays shown while a run is pending, and the status bar reads Building |
| A disagree | Pixel readouts and the status bar's conflict count are not built. Front past Top's width is also highlighted, by analogy, though no board draws it |
| 5 | Only the touched section changes for spans inside a Side or Top outline. Moving the far end, or any span holding the outline's lowest or highest x, moves every ring; a Front section outline moves every ring but the closed ends. The toast says so, and overstates a Front cap, which changes only the last sixth of the axis. A stroke crossing the socket plane twice redraws the whole outline, since Erase is not built. Erase and the View control are not in the strip. The toast sits at Stretch's bottom centre. Outline rows are readouts. The drawer keeps Parameters below the board's sections |
| A disagree, 4 | Refit is a label with no progress bar, because the graph reports only settled or not |

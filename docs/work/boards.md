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
| Home | verifiable | Run: `./gradlew :app:run`. Do: save a character with File, Save as, close the app and start it again. Type in Search; open Settings and change the theme. See: File, Edit, View and Help on the bar with what cannot act faint, Commands and Settings at its end, Saved on the left of the status bar and the file's name on the right. Home lists the file under Recent files, Search narrows the list, its card shows the character's rest pose above "Build · 0 of 8 parts · today HH:MM", with what is placed filled and the rest dashed, and Continue opens it; Cast shows the character and New character, with Objects and Sets faint; Clear recent files shows "No projects" with New character. Test: `./gradlew :ui-screens:test --tests "*ShellFlowTest*" --tests "*FilesTest*"` |
| Home: library | planned | |
| Home: cast card menu | planned | |

## Build a part

| Board | State | Verify |
| --- | --- | --- |
| 1 New character | verifiable | Run: `./gradlew :app:run` with no `recent.txt` in the app data folder. Do: first launch opens here; type a name; Canine quadruped is selected; Create character. See: the plan's rest pose in dashes, standing on the floor, above "8 parts" and chips Torso, Head, Leg 4, Tail; then the workspace with "<name> · draft" and a dot, Torso selected, status "0 drawn · 3 from template". Test: `./gradlew :ui-screens:test --tests "*CharacterFactoryTest*" --tests "*ShellFlowTest.firstLaunch*"`; `./gradlew :ui-screens:test -Pslow --tests "*smoke*"` renders the art |
| 2 Pick a part type | verifiable | Run: `./gradlew :app:run`. Do: in Parts click Front leg; click the tail's dot on the rest pose, then the front leg's; click Draw leg, or another tile first, which moves the socket. Then again with Add part (A) armed, press a dot. See: Select and Add part at the top left; Add part places the chosen type on the pressed dot; the rest pose with the torso filled, the rest dashed, a dot on each open socket and the picked one larger and accent; a dot picks like its row. The bottom panel shows the six tiles, the HUD reads "Socket · front leg L", the drawer shows You draw, Tool knows and Areas, status "0 of 8 parts". After Draw leg, Front leg is selected with Paw under it; one undo removes both sides. Test: `./gradlew :ui-screens:test --tests "*SocketsTest*" --tests "*AppStateTest*" --tests "*ShellFlowTest.aSocketRow*" --tests "*ShellFlowTest.aDotOnTheRestPose*"` |
| 3 Draw outlines in place | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs, Draw outline armed, open the Outline drawer on the right rail, draw a shape across the right edge of Side and hold before letting go, let go, press Ctrl Z. Then draw an arc short of the edge; a loop short of it; turn Close end off and draw an arc; turn it back on and draw a closed shape in Front, slowly returning to its start. See: the stroke follows the pointer with a tip dot and Side shows Drawing, with a hollow marker at its start, and the status bar counts "1 drawing"; with a pen it reads "Pen · 0.62" at the pen's pressure. Dashed runs show what release adds, from the tip to the socket plane in Side and Top, or back to the start in Front; what release discards shows thin and dotted. In Front the marker turns accent and larger once the tip is within closing reach. With Close end off no dashed run shows. The released curve is what was shown; the chip reads Drawn; undo removes it in one step; Full takes no stroke. The drawer reads Smooth 45%, Snap line and Close end on, Pressure not used, Width shape only, Template on and Ghost 40%. The leg hangs upright in Side with the rest of the dog around it at the ghost's strength, the stroke draws across the socket plane at the torso, and Ghost and Template change the panes at once. Then pick Single in the Layout control and select Front leg. See: one viewport with the tools and Snap view floating top-left, the HUD "Front leg · L · side" top-right and three thumbnails bottom-left: Side lit in accent, Top and Front dashed in warn with the template's dashed outline. Draw a closed shape: it follows the pointer, shows in Side's thumbnail as it grows and ends as Side's outline, one undo step. Middle-drag about 40 px: the view lets go, the HUD reads "free orbit", no segment of Snap view is selected, the part fills over the ghosted dog in perspective and a left drag draws nothing. Middle-drag back: within 10 degrees of a view it snaps at once to that view, with no animation, and drawing works again. The wheel zooms and Shift middle-drag pans at the same scale snapped or free. Click the Top thumbnail: the camera snaps to Top; a stroke makes Top's outline and its thumbnail turns plain once the view moves away. Ghost and Template change the view at once. Pick Sheet: the panes return with the same document. Test: `./gradlew :ui-screens:test --tests "*screens.single*"`; `./gradlew :ui-screens:test -Pslow --tests "*theSingleLayout*"` leaves `single-snapped.png` and `single-free.png` under `ui-screens/build/smoke-images`; `./gradlew :ui-screens:test --tests "*StrokeInPlace*"` and `./gradlew :geom:desktopTest --tests "*StrokeCleanup*" --tests "*StrokeEnds*" --tests "*StrokePreview*"` and `./gradlew :ui-screens:test --tests "*DrawOutlineTool*" --tests "*StrokeOutline*" --tests "*StrokeGuide*" --tests "*OutlineDrawer*"` |
| 4 Fit the part template | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs, draw a closed shape in Side, then one in Top. See: the leg fills in Side under the stroke and follows the drawing on the dog in Full, its paw moving with its end; Top adds width; the status bar reads "Working · part mesh" and "1 of 3 views drawn" while a run is under way, with Refit's bar filling in Full by the graph's own count, or Surface's at the bottom right in Single, then "1 drawn · 2 from template", then "2 drawn · 1 from template". Press the armed tool to disarm it and open the Part drawer: "Leg template" with Fit, Shape and Built from. Test: `./gradlew :ui-screens:test --tests "*DrawnPartPipeline*"` and `./gradlew :character:desktopTest --tests "*DrawnPart*" --tests "*PawAndHead*" --tests "*ShippedTemplates*" --tests "*SessionMesh*" --tests "*Placement*"` |
| 5 Oversketch, drag handles | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs. Draw a closed shape across the right edge of Side, then one in Top. With D armed, stroke over part of Side's curve; then a short stroke far from it. Press E, drag a handle in Side, then drag the curve's far tip. Press Ctrl Z after each. See: Side's chip reads a px value. While stroking, the old span is dashed and the highlight follows the stroke; a marker at the stroke's start turns accent once the tip can join the curve, with dashed runs to where its ends join, and a stroke starting too far from the curve shows thin and dotted. On release only that span changes in all panes, with the status "1 section touched" and the toast "Section N refit · other sections unchanged". The far stroke does nothing. The handle drag shows live and is one undo step, and the chip returns to Drawn. Dragging the tip moves every ring and the toast says "whole part rebuilt"; a stroke in a leg's Front says "closed end rebuilt". Press X and stroke over one section: it goes, as a straight run; a stroke over most of the outline clears it; each is one undo step. Press M off and select Right: an edit changes that side alone, the tree shows Left and Right with "differs", and with M back on Side's chip warns "This side" and a stroke lands on the pair's outline, which shows dashed. Test: `./gradlew :character:desktopTest --tests "*OversketchLocality*"`; `./gradlew :geom:desktopTest --tests "*Oversketch*"`; `./gradlew :document:desktopTest --tests "*OutlineEdit*" --tests "*OutlineGesture*"`; `./gradlew :ui-screens:test --tests "*EditCurvesTool*" --tests "*OversketchTool*" --tests "*EraseTool*" --tests "*PartTreeSides*" --tests "*CurveReadouts*" --tests "*CurveToolsFlow*"` |

## Several cameras

| Board | State | Verify |
| --- | --- | --- |
| A Sheet: three cameras and full view | verifiable | Run: `./gradlew :app:run`. Do: New character, Canine quadruped, Front legs. Middle-drag and scroll in Full. Press Top. Then select Torso and Neck. See: the Layout control above one surface with Single and Sheet enabled, and Erase and Mirror in the strip. Full holds the whole dog standing on the ground, head forward, the selected part inside its ring, what is not placed yet translucent, and orbits around the dog. A locked pane keeps the body up and forward left, and shows the rest of the dog behind the part at the ghost's strength, with the ground under it. The leg's panes read Front, Side and Top with Front above Side and Top beside it; Torso's and Neck's read Top, Side and Front. An undrawn view shows its dashed silhouette and a "From template" chip at one scale. Side's extents appear as dashed guides lining up across panes; with Tail or an ear selected the panes tilt to look along the part's planes, the guides lie square to its axis, and a stroke in any pane builds under the pointer. The accent ring moves to Top. Test: `./gradlew :ui-screens:test --tests "*sheet*"`, `./gradlew :render:test` and `./gradlew :ui-screens:test -Pslow --tests "*smoke*"` |
| A Sheet: views that disagree | verifiable | Run: `./gradlew :app:run`. Do: with a part selected, draw Side, then Top longer than Side, then Front taller than Side. See: past Side's end, Top's outline turns thick warn-edge with a warn line at that end in Top and Side, growing as the stroke does; above Side's height, Front's outline does the same; no message appears; drawn views read a px value and the view past the others has no chip; the status bar counts "1 conflict". Test: `./gradlew :ui-screens:test --tests "*ViewsDisagree*" --tests "*DisagreeCost*"` |
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

Where a screen is built differently from its board, and why. A row stays only while the board is wrong or its work belongs to another branch.

| Board | Difference |
| --- | --- |
| All | Tree rows are Stretch's 32 px, not 36; the left drawer is Stretch's minimum 260, not 248. The boards draw the old sizes; Stretch's are the components. |
| Home | Shots wait for Shot. Library and From library wait for Home: library. Objects and Sets are faint: a file holds one character and no objects. The stats line counts what a file holds, so it reads 1 character, and the Cast row holds that character and New character. The board draws a project of several, which the document does not have. Search filters Recent files. |
| 1 | The board is wrong on these: the count and chips come from the plan, 8 parts, Torso, Head, Leg 4, Tail; and the dialog is Stretch's, with its close button, which is the one way to leave it by pointer. |
| 2 | The board is wrong on this: row names and counts come from the plan, not the richer plan the board draws. |
| A | Focus and Full first have their own boards. Undrawn views show a dashed silhouette, as the board labels them. |
| 3 | The board is wrong on these: the strip with the Layout control stays above the viewport in Single, so the layout can be changed back; a snap is immediate, since moving to it over time would need a timer; a thumbnail for a drawn view off screen also snaps there, and the view on screen is lit in accent whether or not it is drawn; the HUD names a part as the Parts tree does. The drawer shows Outline curves once the part has any outline, so Stroke's settings hold from the part's first stroke for later views; `pen-boards` decides that. |
| 4 | Toes reads as a parameter's control, not a segmented one. The board is wrong to leave Erase and Mirror out of the strip: tools do not vanish while a run is under way. The Sheet shows the fit's bar in Full as board A does; Single shows it as this board does. |
| A disagree | The board is wrong to leave Mirror out of the strip, as it is on 5: what an edit will write to must stay in view once a part is drawn. Front past Top's width is also highlighted, by analogy; no board draws it. |
| 5 | Only the touched section changes for spans inside a Side or Top outline. Moving the far end, or any span holding the outline's lowest or highest x, moves every ring; a Front section outline moves every ring but the closed ends; the toast says which. That reading of exit criterion 2 is pending Nicholas's call. A stroke crossing the socket plane twice redraws the whole outline, kept until Nicholas decides whether Erase replaces it. The Sheet has no one viewport corner, so the toast sits at the bottom left of Side; in Single the thumbnails hold that corner, so it sits at the foot. The board is wrong to leave the Hint section out: Template and Ghost would have no way back once a part is drawn. |

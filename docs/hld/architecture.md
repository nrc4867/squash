# Architecture

## What Squash is

A desktop tool for making new cartoon characters and props in one style: bold outlines, flat color, simple shading. You draw a part once, the tool builds a 3D body from it, and you pose it as many times as you like. It is for people who can draw a little but cannot model, rig or weight-paint.

It is a character maker that knows anatomy, a posing and animation studio for those characters, and later a kit for props and scenes. It is not a general modeler, a realistic renderer, or a converter for existing models.

| Question | Decision |
| --- | --- |
| Output | Stills rendered in the app first, animation next. glTF export later; a feature may be in-app-only if it does not rule out export, so files keep source data such as curves and marks |
| First version | Creatures, posing and rendered stills, with reference and trace images. Garments and slots, timing and animation, objects and sets and export come after |
| Input | Pen tablet and mouse, both first-class. Pressure and tilt with a pen, sensible defaults without |
| Platforms | Windows first, then Linux. Android and iPad later, so core code stays portable |
| Language | Kotlin on the JVM. Not web, not Rust |
| Rendering | A 3D app on WebGPU through Stretch's `stretch-gpu` |
| Code | Written fresh. lego-viewer's rigging methods are reimplemented with lego-viewer as the reference; no code is copied |
| First body plan | Quadruped. Any animal shape must be possible later: wings, tails, extra limbs, no limbs |
| Shading | Chosen per object within limits the scene sets. Flat characters in a shaded world is the house look |
| Look and feel | Stretch's Ink and paper theme, light and dark |
| Tests | Catch logic bugs first. Agent sessions must not spend an hour on integration tests |
| Agents | Work in git worktrees under `.worktrees/` |

## Platform

Kotlin on the JVM, Compose Multiplatform for Desktop as the UI, and a small renderer of our own on WebGPU through `stretch-gpu`. Platform spikes confirmed this on Windows: pen pressure through Windows Ink works on a pen display; WebGPU renders in a native child window inside the Compose window at a steady 60 fps with no frame copies; Compose popups show over the 3D view in window-layer mode; orbiting 400 instances stays smooth. Window-layer mode crashes with Compose's default Direct3D renderer, so Compose runs on OpenGL, which `NativeSurfaces.enable()` sets before anything else in `main`.

Why it fits:

- Compose is declarative: the UI is a tree of functions over state, and redraws itself when state changes.
- Coroutines give async with cancellation. Structured concurrency cancels a stale recompute when its parent job is cancelled.
- The style needs a small renderer, not a general engine. `stretch-gpu` supplies the WebGPU API, frame loop, multisampled targets, orbit camera, overlay pass for gizmos, picking and render-to-image. Squash's `render` module adds skinned meshes, flat and two-tone shading, outlines and stroke ribbons.
- Tooling: Gradle with the Kotlin DSL, IntelliJ IDEA, JUnit 5, Compose UI tests. JDK 25.

Performance: drawing happens on the GPU. CPU work (slice building, skinning, stroke fitting) runs on the JIT at roughly the speed of .NET, provided hot loops use primitive arrays and direct buffers rather than boxed objects. The real costs are integration, not speed.

### GPU backends and portability

WebGPU instead of our own backends: `stretch-gpu` wraps wgpu-native, which picks Vulkan, Metal, D3D12 or OpenGL underneath; shaders are WGSL, written once. The module is desktop only and its published build works only on Windows; Android and iPad need their own binding later.

The renderer goes through a thin seam of our own (mesh, material, pass) so the choice can change without touching the rest of the code. Everything except the app shell, the renderer backend and native input is Kotlin Multiplatform `commonMain` code: no LWJGL, JNA, AWT or JVM-only libraries. Pen input is a platform module behind one interface.

### Desktop viewport

Copying GPU frames into Compose is too slow (about 43 fps and 60 ms of delay at 2882x1660). A `NativeSurface` inside Stretch's `Viewport` is a native child window rendered by WebGPU on its own thread at display rate. Compose draws the panels around it, popups over it open as OS-level windows, and gizmos and stroke previews over the view are drawn by our renderer through the overlay pass. Mouse, pen and touch pass through to the Compose UI.

The Sheet and Full first layouts are one `NativeSurface` drawing four viewports in one frame, with scissor and a camera per pane, and differ only in the rectangles. Four surfaces would mean four swapchains and four sets of popups. Every screen but Home sits in one Build frame, and the screens that draw in 3D share one surface: it moves with the screen, so its GPU device and uploaded meshes stay.

## Architecture

The app is a full command system over one document. Data flows one way: a view sends a command, the command changes the document, derived data recomputes, views redraw from the new state. Nothing refreshes anything else by hand.

Squash is built on Stretch, which supplies the theme, components, app frame, command registry, undo, keymap, palette, settings and input. Its 3D viewports run on WebGPU through `stretch-gpu`. This document covers only what is Squash's own.

### Commands

Every change anyone can make is a command: from a button, a shortcut, a drag, a test, an agent or a script. Squash defines its document type and its commands in `document`. The registry, undo with merged gestures, keymap, palette and session log come from `stretch-core`. Commands are serializable, so the agent connection can send them, and the agent tool list is generated from the same registry.

### State

| Kind | Holds | Undo | Saved |
| --- | --- | --- | --- |
| Document | Characters, parts, outlines, surface marks, poses, scenes, shots | Yes | In the project file |
| Session | Selection, active tool, camera, open panels | No | Restored on reopen |
| Derived | Slices, meshes, weights, lines, thumbnails, renders | No | Cache only |

The document is immutable data. Views subscribe to slices of state and recompose when those slices change.

A project holds a cast and scenes. A scene owns the set, backdrop, light and shading limits. Each shot in a scene owns the camera, poses and timing. See [data-model.md](data-model.md).

### Derived data

Drawing to slices to part mesh to rig and weights to lines and marks to render and thumbnails, with surface work resolved from anchors to positions on the way to lines and marks, once on the body at rest and again on the posed body for what shows the pose. Each step is a node in `graph` that declares its inputs. When an input changes, the node recomputes in a coroutine and any older run of it is cancelled. Results are cached by their inputs, so undo is instant.

The rig is one node, `SessionRig.rig`, on the character session's graph, gathering the assembly and what the document gives the rig (`rigInput`: each part's joint reaches and each seam's strokes), so a stroke or a reach reruns the rig and no mesh, and the posed character a node after it, `SessionPose.posed`, which gathers the rig and the shown pose and gives each part's posed mesh with its normals. The shown pose is the character's own, or while a view holds one for a moment (`SessionPose.held`, the Rig stage's wiggle) that one, which never reaches the document. `AppState.posing` holds it for the open session. `SessionPose` hands the session its posed node (`CharacterSession.showPose`), and the session's `posedBody`, `posedSurfaceWork`, `posedLines` and `posedFur` follow it, each the rest node's own result while the pose leaves every part at rest and otherwise found again only for the parts the pose moved. Full draws the posed body when the shown pose moves a bone, and in the Pose stage every pane does, and in the Rig stage every pane and Single; Single shows the rest pose otherwise. A pane that shows the pose draws as one at rest does, each part's mesh taken from the posed lines' parts so the two agree, its color layers laid again on it (`PatchCut.laidOn` over `PartSurface.showing`, kept per cut in `SheetPart.posedCut`), with its lines, inked borders, marks and face from `rememberSurfaceLines(state, posed = true)` and its fur from the posed fur views; `bodyThrough` gives a tool the body its pane shows to pick on. See [data-model.md](data-model.md), Surface work on the mesh and The rig. Poses are made by `PoseSolver` in `rig`, plain functions from a pose and a handle's target to a pose: it reads the rig's shape once (`poseChains`: the root, each weight-bearing leg with its paw, each tail on the root's part, the look chain to the head) and solves from the pose a drag started on at every step, so a drag is one transaction of `SetPose` previews; see [data-model.md](data-model.md), Poses are solved.

The graph implements Stretch's `Settled`: settled means nothing dirty and nothing running. Tests, agents and render-to-file wait on it, never on a timer.

### Storage

Storage is an adapter behind the document, not the model itself. SQL where queries earn it: the part and pose libraries, search, the derived-data cache. The project file format is chosen in its own small spike: a single SQLite file or a folder of plain files, for both projects and standalone character files.

A character can be saved and loaded as its own file in a personal library, separately from scenes. A project holds its own copy of each character and remembers which library version it came from, so an edit in one project never reaches another by itself. Changes travel through three actions: Save to library, Update from library, Save as new character. Update from library can visibly change existing shots, because poses and per-shot touch-ups attach to the character's bones and surface.

### Modules

Each module is a Gradle subproject. A module may use any layer below it, never one above or beside it. Everything outside the window and GPU layer is tested without a window.

| Layer | Module | Holds |
| --- | --- | --- |
| App | `app` | Entry point; `SquashApp`, which wires the screens together and registers modes, drawers and commands with the Stretch frame |
| Interfaces | `screens-shell` | Home, New character, the Build frame and its chrome, Add part's popover |
| Interfaces | `screens-sheet` | The panes, the layouts and Single |
| Interfaces | `screens-draw` | Draw outline, Edit curves, Oversketch, Erase, Smooth, Spine and width, Move, the tool strip and their inspector sections |
| Interfaces | `screens-surface` | Surface work: strokes, regions, paint, fur, marks and expressions. No sources yet |
| Interfaces | `agent` | Later: MCP tools generated from the command registry |
| Interfaces core | `screens-core` | What every screen shares: `AppState`, the `PaneTool` seam, locked cameras, the panes' look, labels and scene surface, the curve and spine edit state |
| Presentation | `ui-design` | Squash-only components, generated icons, strings |
| Presentation | `render` | On `stretch-gpu`: skinned meshes, flat and two-tone shading, outlines, stroke ribbons |
| Domain | `anim` | Later: clips and timing |
| Domain | `rig` | The rig derived from the assembled character: bones, weights, seams and linear blend skinning of positions and normals, and the session's rig and posed nodes |
| Domain | `character` | Part templates and body plans, the catalog over what is loaded, slice building, the slice row and surface work graph nodes, the open character session |
| Core | `document` | The document types (character, part instances, outlines, anchors, grain, pose) and commands, on `stretch-core` |
| Core | `graph` | The derived-data graph and `Settled` |
| Core | `geom` | Vectors, curves, slices to meshes, mesh invariants |
| Core | `annotations` | `@Range` and `@Doc` |
| Tooling | `schema-gen` | The `generateSchemas` task |

Stretch's modules sit below all of these. Within Domain, `rig` sits above `character` and uses it.

The `screens-*` modules never depend on each other. What they share is in `screens-core`, or in `character` where it is plain domain. `screens-core` exists because `AppState`, `PaneTool` and the locked cameras use both `ui-design` and `render`, which sit beside each other. The shell takes the workspace and the tools' drawer sections as a `BuildContent`, and `app` supplies it.

### Stages

Build has six stages: Shape, Color, Lines, Face, Rig and Pose; Dress shows faint in the stage switcher until its board is built. `AppState.stage` holds the one on screen and follows the armed tool, since every tool belongs to one stage (`BuildTool.stage`). Each stage remembers the tool it was left holding, and `AppState.showStage` arms it again. The stage alone decides three things: the tools on the strip, the key mode the tools' actions are scoped to, and what the inspector holds. The inspector is one drawer on the right, titled with the stage, whose content `app` assembles from the screens' modules with the armed tool's sections first. Parts, References, History, Help and the inspector are drawers that only show and hide: none arms a tool, and no tool opens or closes one.

Keys. `ShellActions.registerActions` registers every action once. A tool's action is scoped to its stage's key mode, so a letter means one tool per stage: D, E and X draw, edit and erase in each stage's own material, and G is Fill in Color and Gaze in Face. The rest mean the same everywhere in Build: 1 to 6 switch stage, Tab shows or hides the inspector, T is Add part, M is Mirror, F fits the pane under the pointer (`AppState.hoveredView`, or the free camera of Full and Single through `PaneNavigation.fitFree`), Space swaps the pane under the pointer in Focus, and Esc puts the tool down. With no tool armed, a press on the character selects the part under it (`SelectPartTool`). No default chord is shared by two actions that can answer at once, which a test holds against `Keymap.conflicts`. Tooltips read their key caps from the keymap through `LocalActionKeys`, and the strip draws none. Changes made in Help's keymap editor are kept in `keymap.txt` in the app's data folder by `KeymapStore`.

Add part. Parts are added in the workspace. `AppState.addingSocket` is the open socket Add part's popover is open on, and `addingType` the type it would add; `offerSocket` opens it, from a dot of the Add part tool (`AddPartTool`) or of Move, a + row in Parts or a row of Add part's inspector section, and `addPart` sends one `AddParts` for the chosen type, both sides of a pair in one undo step. The popover lists only the types the socket takes, or every loaded type for a socket that names none. Each added part takes the role `PartRoles.placed` gives it there (see [data-model.md](data-model.md), Roles), through the same `AddParts`.

Color. The strip holds Fill, Brush, Region, Pick color and Erase. Fill colors what is under the pointer, down to an area the template names there, or with Whole part the part's base (`FillReach`, `sectionAt`). The inspector's part section lists the selected part's base, blocks, areas, regions and brush strokes, each with a swatch opening the palette for that item. Picking a region's row sets `PaintEditState.selectedRegion`, which the Region tool edits; a region drawn leaves none picked. Erase (`PaintEraseTool`) takes away the brush strokes and regions showing on top where it presses or drags (`erasableAt`), as one `Together`.

Lines. The strip holds Line width, Stroke, Fur, Comb, Edit curves and Erase. Stroke draws on the surface or, with `LineEditState.floating`, from it into the air. The inspector holds the armed tool's options, then one On the character list of weights, strokes and fur; a picked weight (`LineEditState.picked`) or stroke (`pickedMark`) lights in accent through `SurfaceLines`. Edit curves (`LineEditTool`) drags a stroke's points over the surface. A stroke's Color swatch, in the Stroke tool's options and under a picked stroke, opens the reduced palette (`SlotChoice` with an editor): the slots, New color, and the picker over the chosen slot, which edits it in place as the Color stage does, so a line's color is set without leaving Lines.

Face. The stage works on the head, the part whose role is head (`Character.faceHead`), whatever is selected, and while it is up its band picks the head's first eye, or its mouth, so the face tools work at once. The inspector holds Eyes or Mouth (`FaceEditState.feature`); Add eyes makes two starter eyes, each a lid, a filled white and a filled pupil, the right mirroring the left. Both, Left and Right choose whether an eye's work writes both eyes (`bothEyes`). The picked feature's roles show as layers, the last drawn on top, each saying whether it is empty, being drawn, clipping, filled or drawn, a filled role with its swatch, which opens the same reduced palette as a stroke's Color in Lines. Every face tool draws the gaze handle, at the test target once the Gaze tool has moved it or ahead of the head, and names in each pane the version a stroke there writes (`PaneTool.paneNote`). An Angle versions tile picks the version the tools write; one with nothing of its own drawn shows its stand-in inside a dashed edge, and Lock holds the picked version in the view from any camera (`lockedVersion`). The picked version, its lock and the test target go when the band does, and the view reads the target and the lock only on the Face stage.

Rig. The strip holds Paint weight, Erase weight and Smooth weight (D, X, S), then Mirror, which the shared rule enables on a character with a pair. While the stage is up every pane shows the whole character as the rig sees it (`RigShading`, from `rememberRigShading`, in place of its color, lines and fur): each part in a flat color from a fixed palette of muted colors (`RIG_PALETTE`), given in the order the character first holds each part or pair, a pair's right side darker; each part's mesh carries color layers, one per other part whose bones share its vertices, each vertex's opacity that part's share of the weight so far (`seamLayers`), so a seam's band mixes the two colors by weight; and the bone under the pointer, or picked in Bones, as three more layers, yellow, orange and red, coming in as its weight grows (`heatLayers`). These are the renderer's own fading color layers over the part's mesh, with no change to the renderer. A dot sits at every joint where the shown pose puts it. A press on a dot picks the bone and wiggles it: `RigEditState.wiggle` asks, and `RigWiggle` plays a bend of that bone about its hinge, dying away over under a second, one animation frame at a time (`withFrameNanos`), as the pose held in `RigEditState.held`, which `SessionPose` shows and nothing writes; leaving the stage ends it. A drag elsewhere lays one seam stroke on the seam `seamFor` picks where it starts, the nearest of the seams the part under it is a side of, keeping to the picked bone's seams; Paint weight paints for the picked bone where it is one of that seam's, else for the side the drag starts on. The drag is one `History.begin()` transaction of `SetSeamStroke` previews, its radius the brush's Size in pixels at the press. The inspector holds the armed tool's options (Paint weight's bone, Size, Strength, This side or Both, which is Mirror), then Bones, each rigged part with its joints and a Reach stepper each (`SetParameter` of `rig.reach.<joint>`, one undo step per step), then Seams, one row per seam with its stroke count and a link on a paired one; Tidy, To twin and Reset act on the picked seam, or the picked bone's own, each one command. The status bar reads the stage and its tool, and counts bones, parts and painted seams.

Pose. The strip holds Drag, Turn, Body curve, Pin and Look (D, E, C, P, L) (`POSE_STRIP`), then Mirror, which the shared rule enables on a character with a pair. While the stage is up every pane shows the whole character in its pose (`SheetInput.posedPanes`). The handles are found by the parts' roles, never their templates (`poseChains`): a limb is a leg or an arm with three joints and the foot or hand hanging from its end, a tail is a part of role tail, the look-at turns the head with any necks above it, or with the bone it hangs from, and the body curve's chain runs from the root part's tail through the root part to its spine, neck or head (`bodyChain`), in the order the rig gives. Each tool is a `PoseTool`, a `PaneTool` that draws the pose's handles through the overlay path of every pane (`poseHandles`, from `PoseSolver.handles`): a ring at the middle of the root part, a dot at each limb's end, a curl arc on each tail, the look-at dot ahead of the head with a dashed lead, a pin glyph on the ground under each planted paw, a pole target out from each bent knee, and a double arrow beside each stretched span; the handle under the pointer, held, or the picked limb's end shows in the accent. Drag (`DragTool`) takes any handle: the ring moves the whole body (`moveBody`), a paw's dot reaches with its leg and lifts a planted paw (`reach`), a pole swings the knee (`swing`), the arc curls the tail (`curl`), and the look-at dot turns the head (`look`); the handle moves in the plane through it facing the viewer (`SurfaceView.dragged`). Firm and Stretch (`PoseEditState.firm`, `stretch`, app state), or Alt and Shift held, stop the body where planted paws reach and let a limb stretch. Turn (`TurnTool`) picks the bone that moves the skin under a click, read from the weights of the posed vertex nearest the viewer there (`boneUnder`), Shift adding it to a chain, and draws three rings on it, Tip, Lean and Turn, a drag on a ring sweeping the angle round the bone's joint, shared equally along a chain and, with Mirror on, given to each twin. Pin (`PinTool`) plants or frees the paw under a click with one `SetPlanted`; Look (`LookTool`) drags the look-at point from wherever it is pressed. Body curve (`BodyCurveTool`) draws on a pane that looks squarely at Side or Top, within ten degrees (`bodyCurveView`): a locked one, or Full turned square; Full turned any other way takes nothing and its pane chip says Side or Top only. The pointer is read on that view's plane through the middle of the root part (`strokePoint`) and fitted on release as an outline is fitted from a stroke, its ends left where drawn (`fitBodyStroke`), into that view's `BodyStroke`, in place of the one before and keeping the other view's (`withStroke`). While it is armed each view's stroke is drawn on the panes that look at it, in the selection ring's colour over a halo, with a handle at each end and halfway along; a drag on an end moves that end, a drag on the middle bows the curve (`BodyStroke.moved`). Every drag of the tool, a stroke or a handle, is one transaction of `SetPose` previews, each the curve solved from the pose the drag started on with the planted paws held (`PoseSolver.bendTo`: `solveBodyCurve` then `holdPaws` with each planted paw's place before the bend); the pane holding a stroke shows its Length on a chip. Its handles hide the others' but the pins. The inspector holds Drag's options while Drag is armed, then the picked limb (`PoseEditState.limb`: Planted, Rides with body, the knee's Bend, the Length of a limb whose end joint offers a stretch with Keep volume), then the picked bone's Turn section (Tip, Turn and Lean sliders, the Limits chip and Free, Limited or Locked), first while Turn is armed, then a Planted row per paw, then Reset bone and Reset pose. With Body curve armed it holds the curve's section instead (`BodyCurveSection`: Length from 50% to 150% with a squash or stretch chip away from 100%, Keep volume for every joint the length stretches, and Clear curve, which removes the curve and rests the chain, `PoseSolver.clearCurve`), the Planted rows, and Pose, the chain's bend per part read only (`chainBends`: Spine and Tail the sum of their joints' tips, Neck the joint at the head's base, Head how far the head points from rest seen from the side). A slider's drag is one transaction (`SliderGesture`). The status bar reads Pose and its tool, and the lifted paw with the count of planted paws; with Body curve armed over a curve, the curve's Length and the count.

Shape. Mirror's strip button and its M key are enabled by one rule, `AppState.mirrorApplies`: off Face, with a paired part selected or Stroke or Fur armed. The Parts tree and the inspector name a part by its type, led by the place its socket names before the type, or its parent's socket does, as front-leg names Front leg and a paw on it Front paw (`partName`, `partNameOn`). The inspector's selected part section opens with the part's role as a chip (`RoleRow`, `ChoiceChip`) reading the role's name, Front leg and Front paw for an arm and a hand on a four-legged character; its chevron opens the closed list as a Stretch menu, and choosing one sends `SetRole`, which confirms it on both sides of a pair. A role that is a guess draws its chip's edge dashed, and the Parts tree shows it as a dashed chip after the part's name, Tail?, until it is chosen. Start from template sits on the Outlines row of a view with no outline (`OutlineRowsSection`, `startFromTemplate(view)`). A trace the References drawer shows unlocked shows its handles in the panes (`ReferenceEditState.handles`, `TraceHandles`), over whatever tool is armed: the box moves it, the eight handles scale it and the handle above its top edge turns it, each one undo step.

### Principles

The shared engineering principles are in the Stretch design document under Conventions and in this repo's `CLAUDE.md`. Squash adds one: upstream drives downstream. What the user marks early (a part type, a joint role, a slot) feeds every later step through the derived-data graph.

## Testing and tooling

Logic bugs come first, so almost every test is a plain JVM unit test that runs in seconds without a window or a GPU.

| Tier | Covers | Tools | When |
| --- | --- | --- | --- |
| Unit | Commands, undo, graph nodes, slice building, rig math | JUnit 5, `kotlinx-coroutines-test` with virtual time | Constantly, only the module being changed |
| Command scenarios | A sequence of commands against a document, then assertions on state | `stretch-test`'s `scenario`, no UI | Constantly |
| Golden | Generated meshes, rigs and schemas against saved fixtures | JUnit 5 | When a generator changes |
| UI | A screen's structure and wiring, found by semantics tags; flows across screens are in `app`'s tests | `runComposeUiTest`, waits for idle on its own | Per UI module, seconds |
| Smoke | `SquashApp` over the packaged templates and a real graph: each screen captured to an offscreen image, and the workspace's frame rendered by the real scene through `stretch-gpu`'s `GpuSnapshot`. A native surface needs a window, so the swapchain path and the window around `SquashApp` are not covered | Needs a GPU; JUnit tag `slow`, in `app`'s tests, run by `check -Pslow` | Before a merge only |

- Waits are conditions with a timeout, never delays.
- If a lower tier can catch it, it does not go in a higher tier.
- Tests tagged slow are skipped while working.

Tooling: one Gradle build with every module, opened in IntelliJ IDEA before any feature work. Build and configuration caches on. Run configurations for the app and the smoke tests committed. Agents get an in-app MCP connection generated from the command registry, plus a screenshot command, once `agent` exists; until then, Stretch's devtools screenshot endpoint.

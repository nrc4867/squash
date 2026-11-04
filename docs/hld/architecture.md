# Architecture

## What Squash is

A desktop tool for making new cartoon characters and props in one style: bold outlines, flat color, simple shading. You draw a part once, the tool builds a 3D body from it, and you pose it as many times as you like. It is for people who can draw a little but cannot model, rig or weight-paint.

It is a character maker that knows anatomy, a posing and animation studio for those characters, and later a kit for props and scenes. It is not a general modeler, a realistic renderer, or a converter for existing models.

| Question | Decision |
| --- | --- |
| Output | Stills rendered in the app first, animation next. glTF export later; a feature may be in-app-only if it does not rule out export, so files keep source data such as curves and marks |
| First version | Creatures, posing and rendered stills. Garments and slots, timing and animation, objects and sets, imported images and export come after |
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

The Sheet layout is one `NativeSurface` drawing four viewports in one frame, with scissor and a camera per pane. Four surfaces would mean four swapchains and four sets of popups. Every screen but Home sits in one Build frame, and the screens that draw in 3D share one surface: it moves with the screen, so its GPU device and uploaded meshes stay.

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

Drawing to slices to part mesh to rig and weights to lines and marks to render and thumbnails, with surface work resolved from anchors to positions on the way to lines and marks. Each step is a node in `graph` that declares its inputs. When an input changes, the node recomputes in a coroutine and any older run of it is cancelled. Results are cached by their inputs, so undo is instant.

The graph implements Stretch's `Settled`: settled means nothing dirty and nothing running. Tests, agents and render-to-file wait on it, never on a timer.

### Storage

Storage is an adapter behind the document, not the model itself. SQL where queries earn it: the part and pose libraries, search, the derived-data cache. The project file format is chosen in its own small spike: a single SQLite file or a folder of plain files, for both projects and standalone character files.

A character can be saved and loaded as its own file in a personal library, separately from scenes. A project holds its own copy of each character and remembers which library version it came from, so an edit in one project never reaches another by itself. Changes travel through three actions: Save to library, Update from library, Save as new character. Update from library can visibly change existing shots, because poses and per-shot touch-ups attach to the character's bones and surface.

### Modules

Each module is a Gradle subproject. A module may use any layer below it, never one above or beside it. Everything outside the window and GPU layer is tested without a window.

| Layer | Module | Holds |
| --- | --- | --- |
| App | `app` | Entry point; `SquashApp`, which wires the screens together and registers modes, drawers and commands with the Stretch frame |
| Interfaces | `screens-shell` | Home, New character, Pick a part type, the Build frame and its chrome |
| Interfaces | `screens-sheet` | The panes, the layouts and Single |
| Interfaces | `screens-draw` | Draw outline, Edit curves, Oversketch, Erase, Smooth, Spine and width, Move, the tool strip and their drawers |
| Interfaces | `screens-surface` | Surface work: strokes, regions, paint, fur, marks and expressions. No sources yet |
| Interfaces | `agent` | Later: MCP tools generated from the command registry |
| Interfaces core | `screens-core` | What every screen shares: `AppState`, the `PaneTool` seam, locked cameras, the panes' look, labels and scene surface, the curve and spine edit state |
| Presentation | `ui-design` | Squash-only components, generated icons, strings |
| Presentation | `render` | On `stretch-gpu`: skinned meshes, flat and two-tone shading, outlines, stroke ribbons |
| Domain | `character` | Part templates and body plans, the catalog over what is loaded, slice building, the slice row and surface work graph nodes, the open character session |
| Domain | `rig`, `anim` | Later: weights, IK, stretch; clips and timing |
| Core | `document` | The document types (character, part instances, outlines, anchors, grain) and commands, on `stretch-core` |
| Core | `graph` | The derived-data graph and `Settled` |
| Core | `geom` | Vectors, curves, slices to meshes, mesh invariants |
| Core | `annotations` | `@Range` and `@Doc` |
| Tooling | `schema-gen` | The `generateSchemas` task |

Stretch's modules sit below all of these.

The `screens-*` modules never depend on each other. What they share is in `screens-core`, or in `character` where it is plain domain. `screens-core` exists because `AppState`, `PaneTool` and the locked cameras use both `ui-design` and `render`, which sit beside each other. The shell takes the workspace and the tools' drawer sections as a `BuildContent`, and `app` supplies it.

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

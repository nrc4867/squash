# Squash

A desktop tool for making cartoon characters in one style: draw a part once, Squash builds a 3D body from it, you pose it as many times as you like. Kotlin on the JVM, Compose Multiplatform for Desktop, WebGPU through Stretch's `stretch-gpu`. Built on Stretch, the shared app shell, which is included as a composite build from the sibling `stretch` folder.

## Read before working

| What | Where |
| --- | --- |
| The design, kept current. Code may reference these sections | `docs/hld/` |
| Status of every screen against its board | `docs/work/boards.md` |
| The screens, the specification for every UI | [Squash flow of work](https://claude.ai/artifact/KfBGmYcUEuoRJuhvGky3CT) |
| The Build workspace's stages, strip, keys and inspector, which replace their boards in Squash flow of work | [Squash flow of work · restructure](https://claude.ai/artifact/81F1TgKZKUdhUVPzKZs9zJ) |
| Library: the Build workspace's parts and garments drawer, which replaces boards W2 and W14 of the restructure | [Squash parts and outfits rethink · Spec: Library drawer](https://claude.ai/artifact/4sq6ucMkoUW2E1NiHNwxTm) |
| The plan these docs were exported from | [Squash: plan](https://claude.ai/artifact/9sKH64ERAeZktGEtNmQBfP) |
| Stretch: design, components, conventions | [Stretch: design](https://claude.ai/artifact/VtgHzTtWDUYiVa6eKSqpJR), `../stretch/docs/` |

## Build, run, test

Gradle needs JDK 25 as `JAVA_HOME` (`~/.jdks/corretto-25.0.1` on this machine). Stretch is found as the sibling folder `../stretch` or through `stretch.dir` in `~/.gradle/gradle.properties`.

Builds run on up to four workers, the heaviest modules each forking several test JVMs (`gradle.properties`, `maxParallelForks`). Agents run Gradle through `tools/gradle-locked.sh <tasks>`, never `./gradlew` directly: it holds a machine-wide lock (`~/.squash-gradle.lock`) so heavy builds from the main checkout and every worktree run one at a time, in the order they asked. `tools/recipe.sh` runs Gradle only to compile the engine when a source changed since its last launcher (`:app:recipeLauncher`, which takes a second lock, so it runs beside one heavy build, never behind it, unless the heavy build is in its own checkout); every character build then runs in its own JVM outside Gradle and both locks. While working, run the fast tier of the modules touched (`tools/gradle-locked.sh :screens-core:test`, or `:character:desktopTest` in a multiplatform module), and `checkFast` once before pushing. The full tier runs in CI, and `main` takes nothing that hasn't passed it (`.github/workflows/ci.yml`) on every pull request and every push to `main`, split into the fast tier with the source and generated-file checks, the integration tier (three parallel shards by module, the last taking every module the others don't, behind one "Integration tier" job that needs them all), and the kit's typecheck and tests; run `check` here only when CI can't, or to reproduce a CI failure.

```
./gradlew checkFast                  # the fast tier of every module, with the source and generated-file checks
./gradlew check                      # the full tier: the fast and integration tiers, every tier except smoke
./gradlew :rig:desktopTest           # one module's fast tier (:app:test in a JVM module)
./gradlew :rig:integrationTest       # one module's integration tier
./gradlew check -Pslow               # also the smoke tier (needs a GPU) and the benchmarks
./gradlew :app:run                   # the app
./gradlew :app:run -Pdevtools=47811  # the app, with window screenshots at curl 127.0.0.1:47811/capture
./gradlew :app:createDistributable   # the packaged app, under app/build/compose/binaries/main/app/Squash
./gradlew generateSchemas            # JSON Schemas from the Kotlin model, into schema/, and the recipe reference, into docs/reference/
./gradlew :ui-design:generateIcons   # Squash icons from design/icons/*.svg
./gradlew :character:desktopTest -PupdateGoldens  # rewrite golden fixtures; a missing one fails without this
./gradlew :rig:desktopTest -PupdateGoldens        # rewrite the rig's golden fixtures
./gradlew :screens-core:test -PupdateGoldens  # rewrite examples/cat.json, examples/cat-dressed.json, examples/dragon.json, examples/fennec.json, examples/fennec-dressed.json, examples/fennec-winged.json, examples/koi.json, examples/lizard.json, examples/toucan.json, examples/woman.json, examples/woman-dressed.json, examples/woman-twin.json and examples/woman-twin-dressed.json, the example characters, from their commands
./gradlew :app:test --tests '*.smoke.LookProbe*' -Pslow -PlookProbe  # rewrite the images under docs/work/look-probe
tools/recipe.sh recipes/fennec.recipe.json   # build a recipe headless: the character file, renders, measurements under build/recipes/<id> (needs a GPU)
tools/recipe.sh probe recipes/fennec.recipe.json part=head uv=head:0.5,0.2 point=0.9,0.5,-0.1 face=head:0.1,0.1  # frame facts of a built recipe at rest, no renders
tools/recipe.sh export examples/fennec.json out=<file>  # write a character file as a recipe
tools/recipe.sh kit <anywhere>/cat.ts   # a TypeScript character file on the kit (kit/README.md): typechecked, run, written as cat.recipe.json beside it, built
tools/recipe.sh watch recipes/fennec.recipe.json   # the same build, then again on each save of the recipe, in one warm JVM outside the lock, until Ctrl-C
tools/recipe.sh crop <sheet.png> out=<folder>   # split a sheet of views into one image per view
./gradlew :app:look -Plook="example=woman-dressed outfits=all poses=all close=face,hand,foot"  # render examples to PNGs under app/build/look (needs a GPU)
./gradlew :app:measure -Pmeasure="example=woman-dressed"  # print an example's quality checks as numbers
./gradlew :app:look -Plook="example=fennec-dressed thumbs=true"  # render and time an example's Library outfit and garment thumbnails, under app/build/look/<example>/thumbs
./gradlew :app:look -Plook="example=woman-twin-dressed shots=reference"  # render the reference's 24 shots through its cameras
./gradlew :app:compare -Pcompare="example=woman-twin-dressed reference=<folder>"  # those shots beside the reference renders, overlays and silhouette IoU under app/build/compare
./gradlew :app:drag -Pdrag="example=fennec-dressed outfits=winter drags=move:front-leg-l,pose:front-leg-l:knee switch=true"  # scripted drags, undo and outfit switches timed through the app's graph
```

Stop a running app before rebuilding; a rebuild under a running app breaks it. To see the running app, use Stretch's screenshot endpoint as described in `../stretch/.claude/skills/app-screenshot/SKILL.md`, never a desktop screenshot.

## Modules

Each module is a Gradle subproject under `dev.chieppa.squash.<module>`. A module may use any module in a lower layer, never one above or beside it. Everything outside the window and GPU layer is plain Kotlin, unit tested in seconds without a window.

| Layer | Module | Holds |
| --- | --- | --- |
| App | `app` | Entry point; `SquashApp`, which wires the screens together and registers modes, drawers and commands with the Stretch frame |
| Interfaces | `screens-shell` | Home, New character, the Build frame and its chrome, the Library drawer and its Parts half, the Parts tree |
| Interfaces | `screens-sheet` | The panes, the layouts and Single |
| Interfaces | `screens-draw` | Draw outline, Edit curves, Oversketch, Erase, Smooth, Spine and width, Move, the tool strip and their drawers |
| Interfaces | `screens-surface` | Surface work: strokes, regions, paint, fur, marks and expressions; the Rig stage's view, seam stroke tools and inspector |
| Interfaces | `screens-dress` | The Dress stage: its inspector (layers, the armed tool's options, garment with its fit, areas, edges, holes and hem ink, tuck, loose ends, color), Library's Garments half (outfits, garments by slot and their menus), the Paint tool that paints a garment's areas on the body in a brush or a shape, Edit curves, Erase and Smooth on their edges, Hole, and the press tools that pick a garment and hang a loose end |
| Interfaces | `agent` | Later: MCP tools generated from the command registry |
| Interfaces core | `screens-core` | What every screen shares: `AppState`, the `PaneTool` seam, locked cameras, the panes' look, labels and scene surface, the curve and spine edit state |
| Presentation | `ui-design` | Squash-only components, generated icons, strings |
| Presentation | `render` | On `stretch-gpu`: skinned meshes, flat and two-tone shading, outlines, stroke ribbons. Needs a GPU to test |
| Presentation | `field-gpu` | On `stretch-gpu`: `GpuFieldReads`, the one surface's field reads in a compute shader (see `docs/hld/architecture.md`, Field reads). Its GPU tests are slow |
| Domain | `anim` | Later: clips and timing |
| Domain | `rig` | The rig derived from the assembled character: bones from template joints in the socket tree, weights from part ownership with joint and seam bands and the `SeamPaint` seam, taken onto the surface's vertices by each part's share, linear blend skinning by a `Pose`, the session's rig and posed nodes |
| Domain | `recipe` | Character recipes (`recipes/*.recipe.json`, schema `schema/recipe.schema.json`): the model and reader, the builder that sends a recipe's document commands with errors at the recipe's JSON path, the export of a character as a recipe, and the probe that answers frame questions about a built one |
| Domain | `character` | Part templates and body plans (the serializable model, loader, lint), the catalog over what is loaded (lookups, open sockets, new characters), slice building, the field and surface of the character, the slice row and surface work graph nodes, the open character session, a garment's painted cloth and the commands that write it (`dress`), shared by Dress and recipes |
| Core | `document` | The document types (character, part instances, outlines, anchors, surface work, palette, expressions, pose) and every command that changes them, on `stretch-core`'s undo and registry |
| Core | `graph` | The derived-data graph: nodes, cancellation, caching, `Settled` |
| Core | `geom` | Vectors, curves and their morphing and clipping, slices to solids, the joined field and its mesher, slices to meshes for separate parts, mesh invariants. No dependencies but the standard library |
| Core | `annotations` | `@Range` and `@Doc` only. Depended on by `document`, `character`, `recipe` and `schema-gen`, nothing else |
| Tooling | `schema-gen` | JVM only: the `generateSchemas` task, walking kotlinx.serialization descriptors, and the recipe reference under `docs/reference/` |

Dependency direction is enforced by Gradle, not by convention: a module declares only the modules below it. Within Domain, `rig` and `recipe` sit above `character` and use it, and `anim` will sit above `rig`. The `screens-*` modules reach `ui-design`, `render`, `character` and Stretch through `screens-core` and never depend on each other. The shell takes the workspace and the tools' drawer sections as a `BuildContent` that `app` supplies.

A test lives with the module it tests. Tests that need two `screens-*` modules at once, and the smoke tier, are in `app`'s test source set. Helpers tests share are in the `testFixtures` of `screens-core` and `screens-draw`.

## Rules

These are Stretch's conventions plus Squash's own. They apply from the first commit.

**Build to the boards.** The boards in Squash flow of work are the specification for every screen, and for the Build workspace the boards in Squash flow of work · restructure replace them: layout, drawers, tool strips, chips, status bar text, keyboard hints and icons are built as drawn. A difference from the board is a bug unless the board's note says the board is tentative. When a board needs a component Stretch lacks, the order is fixed: look in Stretch first (`stretch-ui` components, the frame's drawers and viewports); if Stretch has it, use it unchanged; if Stretch nearly has it and the need is general, add it to Stretch in its own commit in the Stretch repo; only when the need is Squash's alone does a new component go in `ui-design`, drawn in Stretch's tokens. Icons are the exception: every icon for a Squash function (tools, drawers, part types, parts) is Squash's own, in `ui-design`, drawn to the boards in Stretch's 16-grid style, even where Stretch has something close. The frame's own controls keep Stretch's icons. Never redraw a Stretch component to look closer to a board: change the board or Stretch, and say which in the commit.

**Never fake the pipeline.** A screen is not done because it looks like its board. Board 4 is done when a drawn outline produces the character's surface through the real slice build, field and mesher, and board 5 when oversketching refits it. No placeholder meshes to unblock UI work; the UI waits, or runs against a template built with no outlines.

**One way to change anything.** Every change to the document is a command, from a button, a shortcut, a drag, a test, an agent or a script. Commands are serializable. Nothing reaches into the document directly.

**Views never refresh each other.** A view reads a slice of state and sends commands. When state changes, every view that reads it recomposes. A method that redraws another panel is a bug.

**Derived data is a graph.** Drawing to slices to mesh to rig to lines to render. Each node declares its inputs, recomputes in a coroutine when they change, cancels its own stale run, and caches by inputs. The graph is `Settled` when nothing is dirty and nothing is running. Tests, agents and render-to-file wait on that, never on a timer.

**No timed waits.** Nothing waits on the wall clock: no `Thread.sleep`, `delay`, `waitUntil` or polling loop, in a test or in app code. Work that runs in the background takes its dispatcher from where it is made: the graph from `CharacterSession`, and the screens from `AppState`'s `Workers`. A test injects a test dispatcher (`TestWork` in `screens-core`'s test fixtures, closed by `TestWorkCloser` when the test ends, or `StandardTestDispatcher(testScheduler)` under `runTest`) and runs the queued work itself, then checks Settled: `runCurrent()` under `runTest`, `work.settle(session)` in a plain test, `settle(work)` in a UI test. Anything else is awaited through a hook, such as a state flow's `first`. `checkTestSources`, part of `check`, fails on `Thread.sleep(`, `delay(`, `waitUntil(`, `setAccessible` and `isAccessible = true` in test sources.

**Text lives outside code.** Every user-facing string is a key in `ui-design`'s resources. No literal UI text in Kotlin.

**Clean panels.** Icons, labels, numbers and tooltips. No explanatory text in panels, no messages that explain incomplete state; show it in place by highlighting it.

**Tests catch logic bugs first.** Tiers, lowest that can catch the bug: unit (JUnit 5, `kotlinx-coroutines-test`), command scenarios (`stretch-test`'s `scenario`), golden (sampled fields and coordinates, generated part meshes and schemas against fixtures), UI (`runComposeUiTest` from `androidx.compose.ui.test.v2`, its composition queued on a `StandardTestDispatcher` that `waitForIdle` runs), smoke (each screen of the app composed whole over the packaged templates and a real graph and captured offscreen, the panes rendered with `GpuSnapshot`, tagged slow, before a merge only). Benchmarks that measure wall-clock time are tagged slow too. Tests that build a whole character through the real graph, or take more than about two seconds, are tagged integration (`@Tag("integration")`, on the class when most of its tests are): a module's `test` or `desktopTest` leaves them out, its `integrationTest` runs them, and `check` runs both. Tests in `check` open no window and stay light on the CPU: one small character per case where one will do, the same session reused through undo rather than rebuilt, and anything that needs many full builds or a timer tagged slow. No reflection to widen visibility in tests. A session never spends an hour on integration tests. Run only the module being changed while working.

**Research from papers only.** Methods are implemented from the papers' own descriptions. Research code is not opened, read or copied without asking Nicholas first, and only permissively licensed code (MIT, Apache, BSD) is ever a candidate. A method gets a patent check before it is built; see `docs/hld/pipeline.md`.

**Original concepts only.** Code, comments, tests, fixtures, sample files, docs, commit messages, branch and worktree names use only generic or invented concepts: a fennec, a dog, an invented character. Never a real character, franchise or brand name.

**Comments describe the code as it stands.** Never the work that produced it: no reference to a request, a session, a change or an earlier behaviour. A comment is written only when the code cannot say it. Public APIs get a one-line KDoc where the name is not enough. The same rule holds for docs: state current behaviour only.

**Docs.** High-level design in `docs/hld/`, one file per area, kept current, the only docs code may reference. The recipe reference in `docs/reference/` is generated by `generateSchemas` and never edited by hand. Working notes, plans and handovers in `docs/work/`, deleted or folded into the HLD when the work merges. The readme holds a description with images of the app, a feature table, the local development guide and the license, nothing else: no links to Claude artifacts and no project state or progress. No Markdown scattered through source folders. No dates, session names or progress logs in docs.

**Status protocol.** `docs/work/boards.md` holds the state of every board: planned, building, verifiable, verified. A session moves a board to building when it starts it and to verifiable when its verify steps pass, and writes those steps into the table. Only Nicholas sets verified. The same four states appear on the boards in the Squash flow of work artifact; a session that changes a state here changes the board's note there too, and changes nothing else in that artifact.

## Git

- Work in a git worktree under `.worktrees/`, one branch per task, named for the work: `git worktree add .worktrees/slice-build -b slice-build`. The folder is ignored by git, so worktrees stay inside the project and out of the index. Remove a worktree when its branch has landed. Never commit to `main` from a worktree.
- GitHub's `main` is the source of truth. A ruleset there takes changes only through a pull request whose three CI jobs pass on a branch up to date with `main`, merged as a squash; direct pushes, force pushes and merge commits are refused.
- Every branch lands as one commit: rebase onto the latest `origin/main`, push the branch, open a pull request against `main`. Once CI is green, `gh pr merge --squash` lands it and deletes the branch; local `main` then follows with `git pull --ff-only`. Local git is set to `pull.rebase = true` and `merge.ff = only`. Agents push their own branch and open its pull request; merging is left to Nicholas or the session that started them.
- The repo is public: everything pushed is published. Private material (anything under `private-models/` or another folder outside this repo) never appears in a commit, a branch name, a pull request title or body, or a CI log.
- Commit messages describe the change, not the session. End with `Co-Authored-By: Claude <model> <noreply@anthropic.com>`.
- A change to Stretch is made in the Stretch repo on its own branch and landed there first; Squash then builds against it through the composite build. Never patch Stretch classes from inside Squash.
- Generated files (`schema/`, icons) are committed; `check` fails when they are stale.

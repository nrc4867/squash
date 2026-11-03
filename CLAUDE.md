# Squash

A desktop tool for making cartoon characters in one style: draw a part once, Squash builds a 3D body from it, you pose it as many times as you like. Kotlin on the JVM, Compose Multiplatform for Desktop, WebGPU through Stretch's `stretch-gpu`. Built on Stretch, the shared app shell, which is included as a composite build from the sibling `stretch` folder.

## Read before working

| What | Where |
| --- | --- |
| The design, kept current. Code may reference these sections | `docs/hld/` |
| Status of every screen against its board | `docs/work/boards.md` |
| The screens, the specification for every UI | [Squash flow of work](https://claude.ai/artifact/KfBGmYcUEuoRJuhvGky3CT) |
| The plan these docs were exported from | [Squash: plan](https://claude.ai/artifact/9sKH64ERAeZktGEtNmQBfP) |
| Stretch: design, components, conventions | [Stretch: design](https://claude.ai/artifact/VtgHzTtWDUYiVa6eKSqpJR), `../stretch/docs/` |

## Build, run, test

Gradle needs JDK 25 as `JAVA_HOME` (`~/.jdks/corretto-25.0.1` on this machine). Stretch is found as the sibling folder `../stretch` or through `stretch.dir` in `~/.gradle/gradle.properties`.

```
./gradlew check                      # every test tier except smoke
./gradlew check -Pslow               # also the smoke tier (needs a GPU)
./gradlew :app:run                   # the app
./gradlew :app:run -Pdevtools=47811  # the app, with window screenshots at curl 127.0.0.1:47811/capture
./gradlew :app:createDistributable   # the packaged app, under app/build/compose/binaries/main/app/Squash
./gradlew generateSchemas            # JSON Schemas from the Kotlin model, into schema/
./gradlew :ui-design:generateIcons   # Squash icons from design/icons/*.svg
./gradlew :character:desktopTest -PupdateGoldens  # rewrite golden fixtures; a missing one fails without this
```

Stop a running app before rebuilding; a rebuild under a running app breaks it. To see the running app, use Stretch's screenshot endpoint as described in `../stretch/.claude/skills/app-screenshot/SKILL.md`, never a desktop screenshot.

## Modules

Each module is a Gradle subproject under `dev.chieppa.squash.<module>`. A module may use any module in a lower layer, never one above or beside it. Everything outside the window and GPU layer is plain Kotlin, unit tested in seconds without a window.

| Layer | Module | Holds |
| --- | --- | --- |
| App | `app` | Entry point; registers modes, drawers and commands with the Stretch frame |
| Interfaces | `ui-screens` | Squash drawers, panels and layouts over state. Needs a window to test |
| Interfaces | `agent` | Later: MCP tools generated from the command registry |
| Presentation | `ui-design` | Squash-only components, generated icons, strings |
| Presentation | `render` | On `stretch-gpu`: skinned meshes, flat and two-tone shading, outlines, stroke ribbons. Needs a GPU to test |
| Domain | `character` | Part templates and body plans (the serializable model, loader, lint), slice building, the slice row graph node, the open character session |
| Domain | `rig`, `anim` | Later: weights, IK, stretch; clips and timing |
| Core | `document` | The document types (character, part instances, outlines, anchors, grain) and every command that changes them, on `stretch-core`'s undo and registry |
| Core | `graph` | The derived-data graph: nodes, cancellation, caching, `Settled` |
| Core | `geom` | Vectors, curves, slices to meshes, mesh invariants. No dependencies but the standard library |
| Core | `annotations` | `@Range` and `@Doc` only. Depended on by `character` and `schema-gen`, nothing else |
| Tooling | `schema-gen` | JVM only: the `generateSchemas` task, walking kotlinx.serialization descriptors |

Dependency direction is enforced by Gradle, not by convention: a module declares only the modules below it.

## Rules

These are Stretch's conventions plus Squash's own. They apply from the first commit.

**Build to the boards.** The boards in Squash flow of work are the specification for every screen: layout, drawers, tool strips, chips, status bar text, keyboard hints and icons are built as drawn. A difference from the board is a bug unless the board's note says the board is tentative. When a board needs a component Stretch lacks, the order is fixed: look in Stretch first (`stretch-ui` components, the frame's drawers and viewports); if Stretch has it, use it unchanged; if Stretch nearly has it and the need is general, add it to Stretch in its own commit in the Stretch repo; only when the need is Squash's alone does a new component go in `ui-design`, drawn in Stretch's tokens. Icons are the exception: every icon for a Squash function (tools, drawers, part types, parts) is Squash's own, in `ui-design`, drawn to the boards in Stretch's 16-grid style, even where Stretch has something close. The frame's own controls keep Stretch's icons. Never redraw a Stretch component to look closer to a board: change the board or Stretch, and say which in the commit.

**Never fake the pipeline.** A screen is not done because it looks like its board. Board 4 is done when a drawn outline produces a mesh with the template's fixed topology through the real slice build, and board 5 when oversketching refits it. No placeholder meshes to unblock UI work; the UI waits, or runs against a template built with no outlines.

**One way to change anything.** Every change to the document is a command, from a button, a shortcut, a drag, a test, an agent or a script. Commands are serializable. Nothing reaches into the document directly.

**Views never refresh each other.** A view reads a slice of state and sends commands. When state changes, every view that reads it recomposes. A method that redraws another panel is a bug.

**Derived data is a graph.** Drawing to slices to mesh to rig to lines to render. Each node declares its inputs, recomputes in a coroutine when they change, cancels its own stale run, and caches by inputs. The graph is `Settled` when nothing is dirty and nothing is running. Tests, agents and render-to-file wait on that, never on a timer.

**No timed waits.** Anything that waits, waits for a condition with a timeout: `waitUntil { graph.settled }`. A `delay` in a test or in app code is a bug.

**Text lives outside code.** Every user-facing string is a key in `ui-design`'s resources. No literal UI text in Kotlin.

**Clean panels.** Icons, labels, numbers and tooltips. No explanatory text in panels, no messages that explain incomplete state; show it in place by highlighting it.

**Tests catch logic bugs first.** Tiers, lowest that can catch the bug: unit (JUnit 5, `kotlinx-coroutines-test`), command scenarios (`stretch-test`'s `scenario`), golden (generated meshes and schemas against fixtures), UI (`runComposeUiTest`, waits for idle on its own), smoke (each screen of the app composed whole over the packaged templates and a real graph and captured offscreen, the panes rendered with `GpuSnapshot`, tagged slow, before a merge only). A session never spends an hour on integration tests. Run only the module being changed while working.

**Research from papers only.** Methods are implemented from the papers' own descriptions. Research code is not opened, read or copied without asking Nicholas first, and only permissively licensed code (MIT, Apache, BSD) is ever a candidate. A method gets a patent check before it is built; see `docs/hld/pipeline.md`.

**Original concepts only.** Code, comments, tests, fixtures, sample files, docs, commit messages, branch and worktree names use only generic or invented concepts: a fennec, a dog, an invented character. Never a real character, franchise or brand name.

**Comments describe the code as it stands.** Never the work that produced it: no reference to a request, a session, a change or an earlier behaviour. A comment is written only when the code cannot say it. Public APIs get a one-line KDoc where the name is not enough. The same rule holds for docs: state current behaviour only.

**Docs.** High-level design in `docs/hld/`, one file per area, kept current, the only docs code may reference. Working notes, plans and handovers in `docs/work/`, deleted or folded into the HLD when the work merges. The readme says how to build, run and test, nothing else. No Markdown scattered through source folders. No dates, session names or progress logs in docs.

**Status protocol.** `docs/work/boards.md` holds the state of every board: planned, building, verifiable, verified. A session moves a board to building when it starts it and to verifiable when its verify steps pass, and writes those steps into the table. Only Nicholas sets verified. The same four states appear on the boards in the Squash flow of work artifact; a session that changes a state here changes the board's note there too, and changes nothing else in that artifact.

## Git

- Work in a git worktree under `.worktrees/`, one branch per task, named for the work: `git worktree add .worktrees/slice-build -b slice-build`. The folder is ignored by git, so worktrees stay inside the project and out of the index. Remove a worktree when its branch has landed. Never commit to `main` from a worktree.
- Every branch lands on `main` as one commit: rebase onto the latest `main`, squash, fast-forward. Local git is set to `pull.rebase = true` and `merge.ff = only`; a merge commit is refused.
- Commit messages describe the change, not the session. End with `Co-Authored-By: Claude <model> <noreply@anthropic.com>`.
- A change to Stretch is made in the Stretch repo on its own branch and landed there first; Squash then builds against it through the composite build. Never patch Stretch classes from inside Squash.
- Generated files (`schema/`, icons) are committed; `check` fails when they are stale.

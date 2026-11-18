# app

App layer. The entry point and the packaged app. `SquashApp` wires the screens together and registers modes, drawers and commands with the Stretch frame. It supplies the workspace and stage inspector to the shell, and the GPU wardrobe thumbnails to Dress.

Headless entry points run as Gradle tasks: `look` (renders to PNG), `recipe` (behind `tools/recipe.sh`), `measure`, `compare`, `drag` and `tris`. The project's `CLAUDE.md` lists their arguments.

Tests that need two `screens-*` modules at once live here, and so does the smoke tier (tagged slow, needs a GPU).

**Uses:** every `screens-*` module, `field-gpu`, and Stretch's core, theme, UI, frame, document and devtools.

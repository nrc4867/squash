# recipe

Domain layer. Character recipes: a character described as JSON (`recipes/*.recipe.json`, schema `schema/recipe.schema.json`), so it can be made without the UI.

- `model/`: the recipe model and reader.
- `build/`: the builder, which sends a recipe's steps as document commands and reports errors at the recipe's JSON path.
- `export/`: an existing character written out as a recipe.
- `probe/`: answers frame questions (points, UVs, faces) about a built recipe at rest.
- `pen/`: pen-style curve input for outlines.

`tools/recipe.sh` runs it headless through `:app:recipe`.

**Uses:** `character`.
**Used by:** `schema-gen`; the test fixtures of `screens-core` and the tests of `app`.

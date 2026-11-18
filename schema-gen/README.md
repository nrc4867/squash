# schema-gen

Tooling, JVM only. The `generateSchemas` task walks the kotlinx.serialization descriptors of the model, with their `@Range` and `@Doc` annotations, and writes the JSON Schemas into `schema/` and the recipe reference into `docs/reference/`. Both are committed and never edited by hand; `check` fails when they are stale.

**Uses:** `annotations`, `character`, `recipe`, `rig`.

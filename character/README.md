# character

Domain layer. Turns a document into a character's surface.

- `model/`, `load/`, `lint/`: part templates and body plans (the serializable model behind `templates/` and `schema/`), their loader and their lint.
- `catalog/` (desktop): the catalog over what is loaded: lookups, open sockets, roles, template swaps and new characters.
- `block/`, `shape/`, `face/`, `role/`: part building blocks (digits, caps, areas), body charts and surface curves, the starter face, part roles.
- `build/`: slice building and assembly, and the graph nodes for each build stage.
- `surface/`: the character's field and its one surface, with coverage.
- `session/`: `CharacterSession`, the open character and its graph.

The `packTemplates` task bundles `templates/` into the module's resources.

**Uses:** `annotations`, `geom`, `graph`, `document`.
**Used by:** `rig`, `recipe`, `screens-core`, `schema-gen`.
**Tests:** unit tests in `commonTest`; golden fixtures in `desktopTest`, rewritten with `-PupdateGoldens`.

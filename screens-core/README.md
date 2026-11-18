# screens-core

Interfaces core. What every screen shares, and the one way the `screens-*` modules reach `ui-design`, `render`, `character`, `rig` and Stretch.

- `AppState`, `Workers` and the screen model.
- The `PaneTool` seam, locked cameras, and the panes' look, labels and scene surface.
- Shared edit state for curves and spines, and the actions behind adding parts and anatomy, moving joints and sockets, and the Library.

Its test fixtures (`TestWork`, `TestWorkCloser`, helpers over the example characters) are shared with the other `screens-*` modules. Its tests rewrite `examples/*.json` from their commands with `-PupdateGoldens`.

**Uses:** `ui-design`, `render`, `character`, `rig`, `stretch-frame`, `stretch-ui`, `stretch-document`.
**Used by:** every `screens-*` module and `app`.

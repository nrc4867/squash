# graph

Core layer. The derived-data graph: drawing to slices to mesh to rig to render.

Each `Node` declares its inputs, recomputes in a coroutine when they change, cancels its own stale run and caches by inputs. The graph is `Settled` when nothing is dirty and nothing is running; tests, agents and render-to-file wait on that, never on a timer.

**Uses:** `stretch-core`.
**Used by:** `character`, `rig`.

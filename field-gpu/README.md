# field-gpu

Presentation layer. `GpuFieldReads`: the one surface's field reads, done in a compute shader on `stretch-gpu`. See `docs/hld/architecture.md`, Field reads.

**Uses:** `geom`, `stretch-gpu`.
**Used by:** `app`, which installs it.
**Tests:** GPU tests, tagged slow.

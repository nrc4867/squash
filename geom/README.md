# geom

Core layer. The geometry everything else is built on, in plain Kotlin with no dependency beyond the standard library.

- Vectors, polylines, Bézier and spline curves, and curve fitting, morphing, flattening, simplification and clipping.
- Outline reading, oversketch and erase on curves.
- Slices to solids: part axes, bending, shells, membranes, pads and patches.
- The joined field and its mesher (`field/`, `surface/`), and slices to meshes for parts built separately.
- Mesh types and the mesh invariants that tests check against.

**Uses:** the standard library only.
**Used by:** `character`, `rig`, `render`, `field-gpu`, and everything above them.
**Tests:** unit tests in `commonTest`, golden fixtures in `desktopTest`.

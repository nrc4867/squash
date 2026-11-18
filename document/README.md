# document

Core layer. The Squash document and every command that changes it.

- The document types: character, part instances, outlines and anchors, surface work (strokes, regions, paint, marks), palette, eyes and expressions, garments and wardrobe, references, pose.
- The commands (`*Commands.kt`), one per kind of change, serializable and registered on `stretch-core`'s undo and command registry. Nothing outside a command writes to the document.
- The document file format.

**Uses:** `stretch-core`, `kotlinx-serialization-json`.
**Used by:** `character`, `rig`, and everything above them.
**Tests:** command scenarios with `stretch-test`.

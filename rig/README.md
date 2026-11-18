# rig

Domain layer. The rig derived from the assembled character, and posing it.

- Bones from the template joints in the socket tree, and body curves for spines and tails.
- Weights from part ownership, with joint and seam bands and the painted seam, carried onto the surface's vertices by each part's share.
- Linear blend skinning by a `Pose`; pose solving for limbs, chains and digits; the rig of loose garment ends.
- The session's rig and posed nodes in the graph.

**Uses:** `geom`, `graph`, `document`, `character`.
**Used by:** `screens-core`, `schema-gen`.
**Tests:** golden fixtures in `desktopTest`, rewritten with `-PupdateGoldens`.

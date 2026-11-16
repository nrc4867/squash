# One surface per character

A plan for building every character's body as one seamless surface, meshed from the blended field of its parts, with the parts kept as coordinates on that surface. The plan is approved (see Decisions).

## Summary

**What changes.**

- **One mesh per character.** The body is meshed whole from one field: each part's drawn outlines sweep a solid, and each solid joins its socket parent's by a smooth union. There are no per-part grids to cut, so a join has no edge, no seam and no buried point.
- **Parts become coordinates.** Every vertex of that mesh carries, for each part near it, how much it belongs to that part (its share) and where it lies on that part (u along the spine, v around it). Paint, regions, marks, fur, landmarks, sockets and rig weights read those coordinates instead of grid cells. A stored (u, v) means what it means today, so saved anchors do not move.
- **Garments are the body grown.** A garment is the body's field offset by its ease, meshed the same way, with its hems, openings and holes cut along smooth level curves of the coordinates instead of along grid rows.
- **Ink comes from the one surface.** The contour, hems and inked borders are found on one mesh with no rule for joins.
- **Eyes are painted on the head.** They are a parametric eye drawn on the head's surface by its coordinates, so they wrap as the head turns and give the three-quarter eye of the original fennec.
- **Retired:** socket strips, socket fillets, garment fillets, the arm-on-body ring push's ring form, per-part recasting onto the field, buried points, seam points, seated eyeballs, laying garments over joins, and every contour exception for joins.

**What stays.**

- Drawing, every draw tool, the outlines and what they mean. Each part's slices are still built from its drawing; they now sweep the part's solid instead of being the mesh.
- Commands, undo, the document's anchors, the palette, the stages and their tools, posing, the pose solver, the body curve and the display pose.
- The light, the two-tone shading with its proxy normals, the outline weights and the line style. Nothing about the look is changed to imitate anything; the proxy normals are carried onto the one mesh so a part shades as it does now.
- Thin parts (ears, wings, fins, loose ends) and hard parts (claws, horns, beaks, nails, owned parts such as a heel) stay separate meshes sitting on the body.

**Decisions for Nicholas** (each is detailed under Decisions).

1. **Retire fixed topology.** In this design a template gives no mesh a fixed topology; the character's mesh changes its vertices whenever its shape is edited (never when it is posed). Every rule, doc, test and system that assumes it is listed with its replacement under Fixed topology.
2. **Move every saved character onto the one surface** when it opens, rather than keeping the lofted-and-strips build alive for old files.
3. **Thin and hard parts stay separate meshes**, meeting the body with an inked overlap as an ear does today; the toucan's beak and every claw are hard parts.
4. **A coarser surface while a large part is dragged.** A torso edit cannot reach 50 ms at full detail; while a drag is in progress the surface is meshed at twice the cell size, and at full detail when the drag ends.
5. **The default eye.** Start the fennec's painted eye from the original fennec's eye, re-expressed as parameters, with the factor sheet's best row kept as a preset.
6. **Blended joins are inked by default (decided).** A join draws its line along the curve where the two parts' shares are equal, as the reference draws a leg against a body; the join's ink switch turns it off.
7. **The Dress inspector loses Fillet.** Garment joins are always smooth, so the setting has nothing to do.
8. **Golden fixtures change kind.** Per-template vertex-by-vertex mesh fixtures give way to fixtures of sampled field values and part coordinates, plus mesh invariants.

**The proof**, the first build step after approval: the bare fennec and the winter outfit's sweater on one surface, judged on the fennec test's round 5 sheets (full body, the walk, the joins' spot sheets, the dressed winter views and walk). It passes with clean joins, a clean sweater, a head or leg edit in 50 ms or less, and no join special case anywhere in its code path. Every other example keeps building as it does now until its phase.

## Why

The fennec test (branch `fennec-test`) rebuilt the fennec from blended fields: drawn outlines became swept solids joined to their socket parent by a smooth union. That part worked: the neck, shoulders and hips became one surface, eyes sat in the head, and the walk held. But each part kept its own (u, v) grid, cast onto the blended surface by rays and cut where another part owned the surface. That hybrid needed a new patch at the joins in every system that runs over the surface:

- seam points, with fallbacks for when the seam cannot be found, to close holes where two cut grids meet;
- burying cast faces that fold, flatten or crease;
- leaving contour edges out where both ends were cast, to remove ticks and curls inside join bands;
- the eyeball's `seatShown`, so the ball's lid would not lie over the head's paint;
- garments copying the cast body grid (`ShellBuilder.laidOverJoin`), which left the dressed fennec's collar, shoulders and boot cuffs ragged and crumpled.

Five rounds took a one-part edit from 276 ms to about 50 ms and removed fault after fault, but each fix was another join rule, and the next system to run over the surface would need its own. The faults come from cutting grids, not from the field. One surface meshed from the field has no cut grids to patch.

The fennec test is a parts bin and the yardstick, not a base. Taken from it: `SweptSolid`, `JoinedField` with parent-only unions, `PartInstance.blend` with `SetSocketBlend` and the Join blend slider, the field-read speedups (`QuickAngles`, flat arrays, false position), and the eye parameters. Left behind: `RunRecast`, `WalledRays`, `RecastMemo`, `SeamPoint`, `SeatedCap`, buried points, `Patch.facing`, `seatShown`, `handedCuts`, `laidOverJoin` and the contour exceptions.

## The surface

### The field

The character's field is the fennec test's joined field, with two changes.

- **Members are runs.** Each swept run of a part (a head's skull and muzzle, each digit of a paw or hand) is its own member, joined to the run or part it grows from. A part's runs join each other by the part's own blend (the paw's `merge` for its toes), and the part's first run joins its socket parent's by the socket's blend. Siblings never blend: two legs, or a tail and a hind leg, meet by a plain union.
- **Cuts stay inside a part.** A part's own cut, such as a paw's flat sole, is a smooth intersection applied to that part's field before the union, so it flattens nothing else. Claws are hard parts (see Thin and hard parts), so the sole does not cut them.

Each member's field is distance-like: negative inside, near the true distance close to the surface. The field is evaluated only near the surface (see Speed).

### The mesher

**Recommended: a dual mesher on a sparse block grid** (one vertex in each cell the surface crosses, one quad across each grid edge it crosses), placed by projection onto the surface, with feature placement available per cell, and a manifold split for cells that hold two sheets.

What the papers say, in their own terms:

- **Marching cubes** (Lorensen and Cline, 1987) puts vertices on grid edges and triangulates each cell from a table of its corner signs. It is simple and watertight, but the triangles near a cell corner are thin slivers, and vertices pinned to edges cannot follow a sharp edge. Slivers matter here: the contour runs along mesh edges and the colour cuts split triangles, so slivers show as jagged ink and fragile cuts.
- **Surface nets** (Gibson, 1998, constrained elastic surface nets) put one node in each cell the surface crosses, link nodes in neighbouring cells, and relax the net by moving each node toward its neighbours while keeping it inside its own cell. The result is a quad mesh of near-even vertices with mostly valence four, smooth, and without the slivers of a per-cell table.
- **Dual contouring** (Ju, Losasso, Schaefer and Warren, 2002) builds the same dual topology from Hermite data (crossing points and their normals) and places each cell's vertex by minimizing a quadratic error over the tangent planes of its crossings, which reproduces sharp edges and corners. It works on an octree, so cells of different sizes join without cracks. Its known faults are non-manifold vertices where one cell holds two sheets of surface, and vertices placed outside their cell.
- **Manifold dual contouring** (Schaefer, Ju and Warren, 2007) gives a cell one vertex per connected component of its surface instead of one vertex in total, which removes the non-manifold vertices while keeping the dual topology.
- **Dual marching cubes** (Schaefer and Warren, 2004) contours a grid dual to the octree to fit features with fewer cells; more machinery than this needs.
- **Extended marching cubes** (Kobbelt, Botsch, Schwanecke and Seidel, 2001) inserts feature vertices into marching cubes' cells from the normals; dual contouring reaches the same feature placement more simply.
- **Continuation polygonization** (Bloomenthal, 1988) visits only cells next to cells already found on the surface, from a seed. The sparse block grid below gets the same narrow band by testing blocks against the distance-like field.
- **Particle sampling** (Witkin and Heckbert, 1994) spreads repelling particles over the surface for even samples, and **Delaunay refinement** (Boissonnat and Oudot, 2005) gives guaranteed triangle quality. Both are iterative and global, need a separate connection step or global restructuring, and give no cheap way to mesh again only where an edit reached.
- **Isotropic remeshing** (Botsch and Kobbelt, 2004: split long edges, collapse short ones, flip toward valence six, relax tangentially, project back) can follow any of the above for even triangles. It is held in reserve if the proof's sheets show uneven triangles in the ink.

Why the dual mesher fits Squash:

- **Ink.** Even quads with vertices inside their cells give the contour and the colour cuts the kind of mesh they have today, a regular grid of near-square quads, instead of slivers.
- **Incremental.** A vertex belongs to one cell and a quad to one grid edge, so a block of cells can be meshed again on its own and joins its neighbours exactly, keyed by global cell index. The rest of the mesh stays the same objects.
- **Sharp features.** The topology is the same with or without features; only where a vertex is placed changes. A cell marked sharp (a beak's edge, a ridge a template asks for) places its vertex by the quadratic error of dual contouring; every other cell is placed by Gibson's in-cell relaxation and then projected onto the surface along the field's gradient. In practice the hard edges (claws, horns, beaks) are separate hard parts, so the feature placement is for the rare ridge on the body.
- **Thin and close.** Two toes or fingers in one cell would share a vertex; the manifold split gives each its own.
- **Adaptive.** The octree form lets small parts (fingers, toes) take cells half or a quarter of the body's, with no cracks between sizes. The proof uses one cell size; the adaptive form comes in the creatures phase (see Phases).

### Resolution and triangle budget

The spike (see Speed) meshed the field fennec, which is about 2.26 body units nose to tail tip, 1.34 tall and 0.61 wide:

| Cell size | Vertices | Triangles | Triangles with a corner under 10° |
|---|---|---|---|
| 0.0357 | 3,834 | 7,672 | 100 (1.3%) |
| 0.0238 | 8,700 | 17,408 | 84 (0.5%) |
| 0.0178 | 15,533 | 31,034 | 114 (0.4%) |
| 0.0119 | 35,162 | 70,310 | 178 (0.3%) |
| 0.0089 | 62,696 | 125,396 | 193 (0.2%) |

The fennec's lofted parts hold 18,140 vertices today, its eyes another 3,840. **Recommended: 0.012 for the fennec's body** (about 35,000 vertices, 70,000 triangles), with paws and digits at half that once the adaptive form is in. The cell size is set per character from its parts' sizes: the body's cell is a fixed share of its root part's least half-extent, which no outfit swaps, and a member's own cells are at most a quarter of its smallest radius. Neither is a user setting. The renderer already uploads each mesh fresh and sizes its buffers to it, so the triangle count has no fixed limit; the CPU work that runs per vertex (contours, colour cuts, skinning) is what sets the budget, and 70,000 triangles is about twice today's, within what those systems handle for the dressed woman.

### Shading on one mesh

The two-tone look comes from proxy normals: each slice's ellipse normal, carried to the mesh per vertex and smoothed over the part. On one mesh, a vertex's proxy normal is the share-weighted blend of each owning part's proxy normal at that vertex's (u, v), read from that part's slices exactly as today, then smoothed as today (`SmoothedShading`). A vertex deep in one part shades exactly as that part's own grid would; across a join the shadow edge runs over the fillet as one curve. Far-part darkening becomes a per-vertex amount (the shares of the far paired parts) instead of a per-mesh flag, so a far leg darkens and its fillet into the body ramps. The light, the shadow tones and the outline weights do not change.

## Parts as coordinates

### What a vertex carries

For each member near it, a vertex holds:

- **Its share**: how much of the surface there is that member's. Where a member is not in any blend with its neighbours, the member whose own field is least owns the vertex outright. Across a smooth union of a child c with its parent p and blend k, the child's share is the union's own mixing weight, clamp(0.5 + 0.5 (d_p − d_c) / k, 0, 1), so the shares change exactly where the surface changes from one part's shape to the other's, and sum to one. Shares of a part's runs add up to the part's share.
- **Its place on the member**: u along the member's spine and v around it, found from the member's swept solid. The solid already finds, for any point, the two rings whose planes hold it and how far it lies between them, and the direction round the ring; u is the rings' u eased by that share, and v is the ring's section parameter in that direction, from a table the solid keeps beside its radii. Past an end ring u carries on along the end ring's axis, so a point on a fillet below a leg's first ring reads a u a little under 0, and a pole reads the end ring's u.

Only members with a share above a small floor, or within their blend of the vertex, are kept: at most four a vertex, as rig weights are.

### What a stored (u, v) means

A stored anchor is `(part, patch, u, v, offset)`, and keeps that form. The patch is the member: a part's run by its name (`body`, `muzzle`, `pad:<name>`, a digit `toes:i`), as today. u and v are the same template coordinates as today, defined by the same rings swept from the same drawing, so an anchor laid on a lofted fennec's leg lands on the same place on the one surface's leg. What differs is only where the surface is near a join: the lofted grid had the leg's rings running into the body; the one surface has the fillet there, and the leg's coordinates read the fillet as the leg's continuation.

### How an anchor becomes a position

An anchor resolves on the field, not the mesh: start from its point on its member's own swept surface at (u, v), then move along the field's gradient to where the joined field is zero, and off by its offset along the normal there. On a part's own share that is where the anchor would land without the join, moved a little by the blend; in a fillet it lands on the fillet. An anchor whose point lies inside another member's share, past the wall, still resolves on the surface where its move ends, so a stroke that runs onto the fillet keeps going smoothly. This needs no mesh, so the resolved surface work does not wait on meshing, and it reads the same field the pose moves (see The rig).

Picking goes the other way: a ray hits a triangle of the character mesh, the part is the member with the largest share at the hit, and the anchor is that member's (u, v) interpolated across the triangle, with v unwrapped across its seam and a pole read in a small flat chart round it.

### Surviving a redraw

The coordinates are defined by the rings, and the rings by the drawing, exactly as now. A redraw moves the surface and leaves each anchor's (u, v) meaning the same place on the part. Vertices do not survive a redraw where the field changed, and nothing stores a vertex: every vertex's coordinates are computed again from the solids, so what reads coordinates (paint, weights) follows the redraw.

## Surface work on one surface

- **Brush strokes** already measure their radius and fade by distance in space. On one surface a stroke resolves its anchors on the field, and each vertex's opacity is its distance from the resolved curve, cut along each triangle where that distance crosses the radius. No chart is involved, so a stroke crosses a join as it crosses the middle of a part.
- **Shapes within one part** (filled areas the template names, pads, nails, beans, regions drawn on one part): each vertex where that part has a share takes its signed distance from the shape in the part's own (u, v), scaled to body units by the part's length per u and its girth; the shape is cut where that crosses zero, through each triangle's edges. Hard edges stay crisp at any zoom; the edge follows the shape, not the grid. v wraps across its seam; near a pole the test is made in the pole's flat chart.
- **Shapes across parts** (a sock from paw to leg, a face mask from muzzle to skull, the cream from jaw down the throat): the border's anchors resolve onto the field, the curve between them is laid on the surface, and the mesh is cut along that curve, the inside taken as the side the border's order gives. This replaces today's closing past socket rings, and replaces handing paint across joins.
- **A part's base colour** is the part's own section: everything where its share is the largest, cut along the curve where its share equals its neighbour's. Fur meeting cream at a join meets on that curve.
- **Marks** resolve their anchors on the field and lay the curve between them on the surface. Floating strokes keep their frame from the part's coordinates at the root (along u, across v, out of the surface).
- **Fur** roots resolve as anchors. Grain reads the owning part's frame at the root, blended by share across a join. The fur rule's spacing walks the one surface instead of each part's grid.
- **Line weights** act by distance in space, as now.
- **Sockets and landmarks** read the parent's own swept surface at (u, v), as `A socket reads the built surface` does now, so placing parts does not wait on the character's mesh. A child's place is unchanged by the field.
- **The face map** casts its rays at the field instead of the head's grid.

## Ink

- **The contour** is found on the character's mesh, the garments' meshes and the separate parts' meshes, along edges between faces toward and away from the camera, as now. There are no buried corners, no cast points and no end caps on the body, so every exception for them goes. The ribbon's smoothing of the edge path stays.
- **Joins** are inked by default: the line is the curve where the two parts' shares are equal, drawn as a crease ribbon, faded where the surface turns away. The join's ink switch turns it off.
- **Separate parts** meeting the body keep the overlap crease they draw today: the curve on the part where it leaves the body's field.
- **Hems and openings** are the garment mesh's open edges, which lie on smooth level curves (see Garments); their ink is on by the hem switch, as now.
- **Region borders** are their cut curves.

## Eyes

An eye is painted on the head's surface. It is a mark group whose shapes are made from parameters instead of drawn per view, so the roles, controls and gaze of Eyes and gaze still apply.

- **Where it sits.** Its middle is an anchor on the head (the head's eye landmark by default, moved by the Face tools), with the eye's facing the head's surface normal there turned `lookOut` degrees outward.
- **How it is painted.** Each shape is an oval in the plane square to the eye's facing, projected along the facing onto the head and cut on the head's mesh by each vertex's signed distance from it in that plane. Because the paint lies on the curved head, it foreshortens and wraps as the head turns: the near eye in three-quarter is the original fennec's three-quarter eye, and the far eye can never stand past the head's outline, since nothing stands off the surface.
- **Its parameters**: opening width and height, tilt, look outward, iris colour (a palette slot) and iris size, round or slit pupil, highlight size, lids in the head's colour (the head's own base shows outside the opening, so the lid is the head itself), and a lash line along the opening's upper edge, plus a brow line as a mark above it. Face sets size, tilt and place with handles on the panes, and Roundness, the pupil, the colours, how much rounder it reads from the front, the highlight, the gaze and the lines in its panel.
- **Gaze** moves the iris, pupil and highlight within the opening in the eye's plane, clipped by the opening: the eye's `gazeForward` and `gazeUp`, shares of the opening's half width and half height.
- **Drawn eyes stay.** A head can still hold per-view drawn eye marks; a character uses one or the other.

The fennec's factor sheet's best row (dark-brown iris, round pupil, iris filling the opening, a larger highlight, eyes lower and closer, a brow line) becomes a preset; the default starts from the original fennec's eye re-expressed in these parameters (decision 5).

## Thin and hard parts

Some parts are thinner than any useful cell, or need edges the field rounds off:

- **Thin**: ears, membrane wings, fins, a koi's tail and fins, loose ends, hair's locks and clumps (role hair, pushed out of the body in each pose; see Hair shell results).
- **Hard**: claws, nails, horns, a beak, teeth, and garment-owned parts (a heel, buttons, a buckle, shades).
- **Pads** with a thickness (nose leather) stay the small meshes they are, laid on the surface.

They stay separate meshes built by their own blocks as today (`membrane`, `pad`, a lofted run). They attach as they do now: placed at their socket, read on the parent's own swept surface, and sunk into the body by the socket's sink, so their root lies inside the body's field and the body hides it. Their ink is the overlap crease of the curve where they leave the body's field. Their rig is their own bones, with the parent's bone fading in toward the root by the seam rule. A template marks such a part with `"surface": "separate"`; everything else is in the field.

A claw on a toe sits on the toe's tip socket with a small sink; a horn on the head the same. The toucan's beak is a hard part: its edge is a cartoon beak's line, which the overlap crease draws.

## Creatures

- **Paws and feet** (paw 5's `toes`, `merge`, `claws`, `pads`): the palm and each toe are members of the paw, the toes joined to the palm by `merge`, so a mitten is a large merge and separate toes a small one. Claws are hard parts on the toes' tips. Beans and the main pad are areas painted in the paw's own coordinates (cut by the paw's (u, v), one chart over palm and toes, so the pad is whole across the toes' roots). The sole's flat cut applies to the paw's own field.
- **Hands** (hand 5's webs, knuckles, cup, nails): fingers are members joined to the palm by the web's blend; nails are hard parts or painted areas on the fingers' tips.
- **Dragon**: body, neck, tail, legs and head in the field; wings thin; horns and claws hard.
- **Lizard**: body, legs, tail and toes in the field; claws hard.
- **Toucan**: body, head and legs in the field; the beak hard; the feet's toes in the field, claws hard.
- **Koi**: body in the field; fins and tail thin.
- **The woman and the twin**: torso, neck, head, arms, legs, hands (palm and fingers) and feet (heel, ball, toes) in the field, with the adaptive cells for fingers and toes; hair as a `hair-cap` in the field riding the head's bone, and its fall and locks as `hair` parts of their own meshes (see Hair shell results).

Generalizing is the point of the design: a creature needs no join rule, only parts with solids, blends at its sockets, and a separate mark on the parts that are thin or hard.

## Garments

### A garment's surface

A garment covers parts over ranges of their coordinates, as a branch does now. Its surface is the zero level of

g(p) = min( f_covered(p) − ease(p), drawn(p) )

- **f_covered** is the joined field of the parts it covers, with their blends, so a sleeve joins the sweater's body with the same fillet the leg joins the torso with. No garment fillet, no laying over a join.
- **ease(p)** is the branch's ease, read at the vertex's coordinates on the part under it. A drawn Side or Top outline that stands off further than the ease becomes a swept solid of the branch's own (drawn(p)), joined by a plain union, so a skirt hanging free from the waist is the drawn solid below the body.
- **Layers**: an outer garment's covered field is the inner garment's surface, so eases add as they do now.

The garment is meshed by the same mesher, over its covered region only (a thin band round the body), and each garment vertex carries the body's coordinates at the point it grows from (where the gradient leads back to the body).

### Edges as level curves

- **Coverage** is a function on the garment's surface: for each part the garment covers, the signed distance, in body units, inside its u range (and its v range for a branch part of the way round); for each part it does not cover, minus the distance into that part's share. The share-weighted sum of those is one smooth function, and the garment is cut where it is zero. So a hem lies along a smooth curve at the drawn u, a neck opening along a smooth curve round where the head takes over, and a sleeve's cuff where the paw does, none of them following a cell.
- **Holes** where a part comes through (a tail through trousers) are the same: the part through it is not covered, so its share opens the hole.
- **Cuffs** are a narrow band beside a hole or hem whose ease grows by the cuff's size, part of the same field, so the cuff is one surface with the garment.
- **Hidden skin**: a body triangle is hidden where all its corners lie inside the garment's field by more than a sliver and coverage is above the hem's margin there, so skin seen through a neck opening still shows. Posed, the test runs again on the posed meshes, as Hiding does now.
- **Owned parts and loose ends** stay separate parts as they are.

### What a garment keeps

The document's garment keeps its branches (part, u range, v range, ease, outlines, surface work), slot, layer, changes, join and edge ink, colour and loose ends. Its fillet goes (decision 7). A branch's surface work is anchored in its part's coordinates; a branch part of the way round whose stored v is a share of its own span is read as part v by `fromV + v (toV − fromV)` (the migration checks which it is and converts).

## The rig

### Weights

A vertex's weights are its shares times each part's own weights at its coordinates:

w(bone) = Σ over parts i of share_i × W_i(u_i, v_i)(bone)

W_i is today's rule for one part: the joint owning u, joint bands over the part's radius, digit bands over the digit's radius. Across a join the shares give the parent-to-child blend, so the seam band is the union's own mixing, widened to at least today's seam reach where the blend is narrower than the seam radius, so a shoulder with a tight blend still bends smoothly. A vertex keeps at most four bones, renormalized.

- **Painted seams** plug in where they do now: `SeamPaint` is asked with each vertex's address (its part coordinates), body position and guessed share, and its strokes act by distance in space, as now.
- **Tidy** finds pieces on the one mesh by its edges, as `MeshPieces` does on any mesh.
- **Garments** take the weights of the body at the coordinates they grow from. **Separate parts** keep their own.
- **Hair** in the field weighs only its own run and its socket parent's, never further up its line of joins.

### Skinning a mesh whose topology changes

The mesh changes its vertices only when the shape is edited. The rig follows the assembly already, so an edit builds the weights again for the new mesh; a pose only skins. Skinning stays linear blend on the CPU (`Rig.posed`), producing a posed mesh with the same vertices in the same order, and everything that reads the pose (posed surface work, lines, fur, colour cuts laid on the posed mesh, hiding) reads that posed mesh by vertex, as `PartSurface.posed` does now. Weights are rebuilt only for the blocks an edit meshed again; vertices kept from the last mesh keep their weights.

### Bending joints

Linear blend skinning with the bands is what the walk uses now, and what the proof judges. A posed arm or leg on the body (`withArmsOnBody`) becomes a push of the vertices whose arm share is large, grouped by their arm u instead of by ring. If the proof's walk shows the shoulder or hip fillet collapsing at deep bends, the remedy is implicit skinning (Vaillant, Barthe, Guennebaud, Cani, Rohmer, Wyvill, Gourmel and Paulin, 2013): skin by the bones, then move each vertex back onto the zero level of the posed parts' fields composed by their joins, which keeps volume at the joint and gives contact where the arm presses the body, without changing the topology. Each member's solid moves rigidly with its bone(s), so the posed field is the same field read in each bone's space. It is planned for after the creatures phase unless the proof needs it sooner.

## Speed

### The spike

A throwaway timing test (not committed) meshed the field fennec's joined field with a plain surface net on a block grid, on this machine (8 logical cores, JDK 25), median of 5 warm runs, eyeballs left out. Blocks are 8 cells a side; a block is meshed only where the field at its middle is within 1.5 half-diagonals of zero. Vertices take the mean of their cell's edge crossings, then one step along the gradient (central differences, 7 field reads a vertex) onto the surface.

One field read costs about 0.9 µs at a random point in the body's box and about 0.55 µs near the surface, single-threaded.

Whole fennec, from nothing:

| Cell | Field reads | One thread | 8 threads | of which: reads / meshing / projection |
|---|---|---|---|---|
| 0.0357 | 88 k | 82 ms | 32 ms | 13 / 10 / 5 |
| 0.0238 | 196 k | 153 ms | 34 ms | 18 / 5 / 7 |
| 0.0178 | 367 k | 281–302 ms | 63–88 ms | 34–43 / 10–26 / 13–15 |
| 0.0119 | 806 k | 567–681 ms | 139–149 ms | 68–72 / 23–26 / 27–29 |
| 0.0089 | 1.47 M | 1129 ms | 290–322 ms | 127–177 / 47–48 / 75–85 |

Field reads for a one-part edit, 8 threads, only the blocks within the edited member's box grown by its reach and a block (the cost the incremental mesher pays before meshing those blocks):

| Cell | Front leg | Head |
|---|---|---|
| 0.0178 | 16 ms (166 blocks) | 14 ms (214 blocks) |
| 0.0119 | 21 ms (283 blocks) | 21 ms (410 blocks) |
| 0.0089 | 28 ms (419 blocks) | 36 ms (716 blocks) |

Also measured: leaving out of each block the members whose field at the block's middle shows they cannot matter there changed nothing (the reads near the surface are of the members that do matter). The projection left vertices within 0.004 to 0.006 of the surface at the coarser cells after one step, 0.0008 at 0.0089; two steps or false position along the gradient are wanted.

### Reaching 50 ms

At 0.012 a leg or head edit spends about 21 ms reading the field, plus meshing, placing and coordinating the blocks it reaches. The plan to bring it under 50 ms:

- **Blocks are the unit.** The mesh is held per block of 8³ cells: corner values, vertices keyed by global cell index, quads, coordinates, shading normals, weights. An edit marks dirty the blocks within the changed members' old and new boxes grown by their reach; everything else is kept as the same objects, and downstream caches (colour cuts, contours' grids, weights) are kept per block too.
- **Member channels.** Each corner near the surface keeps the own field of each member within reach of it (at most four or so). An edit reads only the changed member's field at the dirty corners and recombines the union from the kept channels, which is arithmetic. This should take the leg edit's reads from 21 ms to well under 10.
- **Projection without reads.** Vertices are placed from the corner values already held (in-cell relaxation, then a step along the gradient interpolated from the corners), reading the field only to finish in cells that need it.
- **Coordinates from the same read.** A member's swept solid finds a point's ring pair and direction when it reads its field; reading u, v there costs nothing extra if both come from the same call.
- **Threads.** Blocks are meshed in parallel on the graph's dispatcher's threads, cancelled with the node.
- **Large parts.** A torso edit reaches most of the body, so it costs about a whole build (about 140 ms at 0.012). While a drag is in progress, a large edit meshes at twice the cell size (about 35 ms in the spike) and at full detail when the drag ends (decision 4).

Estimated leg edit at 0.012 with these: reads 5 to 8 ms, meshing the dirty blocks 5, placing 3, coordinates and shading 5 to 8, weights 3, so about 25 to 30 ms before colour cuts and lines, which keep their own per-block caches. The proof measures this; it is an estimate until then.

## The derived-data graph

| Node | Now | One surface |
|---|---|---|
| Slice row (per part) | Drawing to slices to patches | Unchanged: drawing to slices |
| Part mesh (per part) | Patches to the part's mesh | Only for separate parts |
| Swept solid (per part) | Inside the field join | New node: slices to the part's solid, its runs as members, and the (u, v) tables |
| Assembly | Places parts by sockets on their own surfaces | Unchanged; sockets read each part's own swept surface |
| Fillets, field joins, strips | Three nodes after the assembly | Gone |
| Character field | Inside the field join | New: members, blends and parents from the assembly; cheap, keyed by each member's solid and place |
| Character surface | None | New: the incremental mesher with its blocks and member channels; vertices, quads, coordinates, shares, shading normals |
| Garment surfaces | Dressed node lofting branches | Per worn garment: its field and mesh over the covered blocks, its coverage cut; layered innermost first |
| Surface work | Anchors resolved on each part's grid | Anchors resolved on the character field; does not wait on the mesh |
| Colour | Shapes cut on each patch's grid | Shapes cut on the character mesh per block, by coordinates or by surface curves |
| Lines | Each part's grids, creases | The character mesh, garment meshes and separate parts; share-level creases where inked |
| Fur | Roots on each part's grid | Roots resolved on the field; spacing over the one surface |
| Rig | Weights per part's vertices | Weights per character vertex from shares and coordinates, per block |
| Posed | Skins each part | Skins the character mesh, the garments and the separate parts |

A vertex's identity is its cell, so a block that an edit did not reach keeps every vertex, and every per-block cache with it.

## The data model and saved documents

### What changes

- `PartInstance.blend`: the smooth union's blend at the part's socket, null for the default (a quarter of the solid's least box extent). Both sides of a pair share it. Taken from the fennec test.
- Templates: `surface` is `field` (in the one surface, the default for body parts) or `separate` (thin and hard parts). `seated` does not come back. A field template's ring and segment counts are how finely its drawing is sampled into the solid, not a mesh's topology; changing them changes the solid only by sampling.
- Painted eyes: a new eye item in the head's surface work holding its anchor and parameters, mirrored by flag as a centre part's marks are.
- Garments: `fillet` goes.
- Nothing stores a vertex, a cell or a mesh.

### Migration, format 14 to 15

- **Template pins.** Each body part pinned to a lofted template version moves to the field version made from it, with the same id, the next version number and the same drawings, parameters, joints, sockets, areas and landmarks. The new versions ship beside the old, as every template version does; a part already separate (an ear, a wing) keeps its pin with `separate` added to its template.
- **Anchors.** Unchanged: `(part, patch, u, v, offset)` keeps its meaning. Anchors that name a patch the new version does not build are kept and drop at resolution as dangling anchors do now. A u below 0 or above 1, which a strip's range used, reads on the extended chart past the end ring.
- **Garments.** `fillet` is dropped. Branch anchors on a branch part of the way round are converted to part v if they were stored as a share of the span.
- **Eyes.** Drawn per-view eye marks are kept as they are; a character moves to painted eyes only by choosing it in the Face stage.
- **`PartInstance.blend`** is absent, so null.
- Everything else (palette, outlines, marks, regions, paint, line weights, seam strokes, fur, grain, poses, expressions, wardrobe) passes through unchanged.

The example characters are rewritten from their commands, as now, once each moves in its phase.

## Fixed topology

In this design a template gives a part's mesh no fixed topology, and a character's mesh changes its vertices whenever its shape changes. This is decision 1. What assumes it, and what replaces it:

### Rules

| Where | What it says | Replaced by |
|---|---|---|
| CLAUDE.md, Never fake the pipeline | Board 4 is done when a drawn outline produces a mesh with the template's fixed topology through the real slice build | ...produces the character's surface through the real slice build, field and mesher |
| CLAUDE.md, test tiers | Golden: generated meshes against fixtures | Golden: sampled fields, coordinates and schemas against fixtures; mesh invariants on meshes |
| CLAUDE.md, modules | geom: slices to meshes, mesh invariants; rig: weights from part ownership with joint and seam bands | geom: slices to solids, the mesher, mesh invariants; rig: weights from shares and part coordinates with joint bands |

### Design docs

| Where | What it says | Replaced by |
|---|---|---|
| data-model, intro | The mesh is generated with fixed topology; weights and marks address the slices | The surface is meshed from the parts' solids; weights and marks address part coordinates |
| data-model, Identity and versions | A template version that changes its ring or segment count is a migration | Changing sampling counts is an ordinary new version; anchors are template coordinates, not vertices |
| data-model, Topology block; Fixed topology per template version; Rings can focus; Refits are local | Ring i maps to u, segment j to v; anchors reach vertices by arithmetic; focus packs rings | Ring and segment counts sample the drawing into the solid; focus samples a stretch more finely; anchors reach the surface through the solid and the field |
| data-model, Surface work on the mesh | An anchor lands at `positionAt` on the patch; posed grids keep slices so u, v land on the same rings | An anchor lands on the field from its member's swept surface; posed meshes keep their vertices |
| data-model, The rig | A vertex follows the joint owning its u; seam band by ring radii | Shares times each part's own rule; the seam is the union's mixing, widened to the seam reach |
| data-model, Garments | A branch is the body's slices grown, at the part's segment count; joins are fillets; hiding by u and v | A garment is the covered field grown by ease, cut along coverage curves; joins are the field's |
| data-model, Part template file format | `version` changes when topology changes; `topology` with rings and segments | `version` changes when what the template builds changes; `topology` becomes `sampling` for field parts |
| look, Shading | Proxy normals per slice per vertex | The same normals, share-blended on the one mesh |
| look, Color is vector (Cutting, Where a shape reaches) | Shapes cut along rings, lines of points and diagonals; wrap across v seam; close past socket rings | Shapes cut along each triangle by a per-vertex function (coordinate distance or surface curve) |
| look, Where parts meet; Field joins | Strips, fillets, field joins with casting, burying, seam points | One surface; separate parts by overlap |
| look, The contour | Exceptions for end rings, strips and buried corners | None |
| look, Eyes and gaze | Seated eyeball | Painted eye |
| pipeline, Part templates | Fixed ring and segment counts | Sampling counts |
| parts | Templates by version with rings | Field and separate templates |
| boards (working notes), Shape · Generic part | Rings and Segments rows | Rows stay for separate generic parts only |

### Code

| System | Relies on | Replaced by |
|---|---|---|
| `Patch`, `MeshBuilder`, `PatchMesh`, `RenderMesh.of(PartMesh)` | nu by nv grid per patch, poles, vertex order | Kept for separate parts; the character mesh is its own type, rendered as one `RenderMesh` with per-vertex part data |
| `PatchSurface.positionAt`, `surfaceAt`, `PatchGrid` | Bilinear lookups on the grid | Anchor resolution on the field; triangle lookups on the mesh for picks |
| `ShapeCut`, `GridPieces`, `SoftBrushCut`, `BrushSweep`, `FadeField`, `CutOnMesh`, `CutMerge` | Cuts along rings, columns, diagonals; grid coordinates | Cuts along triangles by per-vertex functions, per block |
| `LineGrid`, `ZeroCurves` | Contours and zero curves on grid cells | The same on triangles |
| `ProxyNormals`, `SmoothedShading` | Per-ring normals, part-wide smoothing | Share-blended normals, smoothing on the character mesh per block |
| `SocketStrips`, `StripPlan`, `StripShape`, `StripJoin`, `BridgeStrip`, `SocketFillets`, `PartBlendNode`, `geom/blend` | Strips and fillets between grids | Gone |
| `SocketCrease` | Crease on the part's grid | Separate parts only, on the field |
| `ShellBuilder`, `ShellInside`, `HemPush`, `GarmentBranches`, `GarmentCover`, `GarmentHiding`, `GarmentJoins`, `GarmentStrips`, `GarmentEdges`, `HangingBranches` | Rings and columns under each branch ring; fillets; hiding by u and v | Garment field and mesh, coverage cuts, hiding by the garment's field; the hanging push by vertices grouped by u |
| `Weights`, `SeamBand`, `StripWeights`, `GarmentWeights`, `PartRings`, `BoneTree` | Vertices addressed as anchors in grid order; ring middles | Weights from shares and coordinates; ring middles from the slices (unchanged) |
| `Skinning`, `PosedSurface`, `PosedBody`, `PosedNodes`, `PosedWork`, `ArmsOnBody` | Same vertices in the same order; ring-wise push | Same vertices in the same order across a pose (still true); push by vertices grouped by u |
| `SurfacePick` | Projects every patch point and tests cells | Ray against the character mesh, coordinates by interpolation |
| `FaceMap`, `EyeDraws`, `EyeFacing`, `LineEditTool` | `positionAt`, `surfaceAt` on the head's grid | The field |
| `PartBend` (jaw) | Turns grid vertices past a u | Turns the head member's solid past the u, so the jaw's opening is meshed |
| `MeshShift`, `JoinedDigits` buried flags | Grid indices | Gone |
| `TemplateLint` ring and segment checks | Counts against joints and sections | Sampling checks for field parts; unchanged for separate parts |
| `MeshInvariants` | Watertight by ring grid | Watertight and manifold by edges on any mesh |

### Tests and fixtures

| Fixture or test | Pins | Replaced by |
|---|---|---|
| `character/src/desktopTest/golden/<template>-<version>.txt` (52) | Every vertex of each template's default build | For field templates: the solid's field at fixed sample points and the (u, v) read there; for separate templates: kept |
| `oversketch-*.txt`, `OversketchLocalityTest` | Vertex-level locality of an oversketch | Locality in the solid: field values far from the stroke unchanged |
| `color-cut.txt` | Cut meshes on the grid | Cut areas and boundary lengths on the one surface |
| `surface-anchors.txt`, `SurfaceAnchorGoldenTest` | Anchors on grid vertices through oversketches | The same anchors' resolved positions on the field through the same oversketches |
| `rig/.../fennec-bones.txt` | Bones from ring middles | Unchanged |
| Tests named for topology (`DrawnPartTest.aSideOutlineAloneBuildsTheFixedTopology`, `SpineBuildTest.theOutlinesBuildTheTemplatesTopology`, `SessionMeshTest.aPartHasAMeshOnceSettledInTheTemplatesTopology`, `MovePlacementTest.aTurnedPartKeepsItsTopologyAndItsUp`) | Grid sizes | The surface is meshed, watertight and on the field; the part's coordinates cover its share |
| Rebuild tests asserting the same vertices (`LeaningLegsRebuildTest`, `BodyChangeBuildTest`) | Vertex-for-vertex restore | Undo restores the same blocks (same objects) and the same field |
| Strip, fillet and field-join tests (`SocketStripsTest`, `SocketFilletsTest`, `GarmentJoinsTest`, `FieldJoinsTest`, `RunRecastTest`) | Systems that retire | Retired with them |
| `examples/*.json` | Anchors and template pins | Rewritten from their commands with the new pins, in each example's phase |

## The UI

**Stays the same:** every Draw tool and what a drawing means; Move, sockets, joints and landmarks; Color's Fill, Brush and Region; Lines; the Pose stage and its handles; the Rig stage's seam strokes and Tidy; Dress's outfits, slots, branches and their ranges, ease and outlines; every command.

**Changes:**

- **Join blend per socket**: the Shape inspector's Join blend slider on a selected part, from the fennec test (`SocketBlendRow`, `SetSocketBlend` on both sides of a pair).
- **Selection** shows the selected part as its share of the surface, outlined by the curve where its share falls to its neighbour's.
- **Face**: the Eyes section holds the painted eye's sliders and swatches, and Add eyes makes painted eyes on a head in the field.
- **Dress**: Fillet goes from the joins and edges section.
- **Generic parts**: Rings and Segments show only for separate generic parts.
- **Rig shading** colours each vertex by its bones' shares, as now, on the one mesh.

No new panels and no explanatory text.

## What retires

Socket strips and their posed form; socket fillets; the grid-preserving implicit blend (`geom/blend`, `PartBlendNode`); garment fillets and garment strips; laying garments over joins; per-part casting onto the field (`RunRecast`, `WalledRays`, `RecastMemo`), seam points, buried points and buried faces, `Patch.facing`; seated eyeballs (`SeatedCap`, `seatShown`), the eye `oval` and `lid` blocks; handing paint across joins (`handedCuts`); the contour's exceptions for end caps, strips, cast and buried points; fixed topology for body parts; vertex-by-vertex mesh goldens for field templates. The lofted build of body parts retires once every example has moved; the lofted build stays for separate parts.

## Phases

| Phase | What | Done when |
|---|---|---|
| 0. Approval | Nicholas answers the decisions | Each decision is answered in this file |
| 1. The proof | The fennec's body and the winter sweater on one surface, behind a per-character switch; the field, the mesher with blocks and member channels, coordinates and shares, shading, colour by coordinates, ink, painted eyes, rig weights and posing, the sweater's field, coverage and hiding | The proof passes (below) |
| 2. Surface work on one surface | Every surface tool and node on the one surface: picks, marks, regions across parts, fur, line weights, the face map, Face's painted eyes, selection, Rig stage | The fennec, the dressed fennec bare and the winged fennec build on one surface, their surface work lands where it did, and the screens' tests pass on them |
| 3. Garments whole | Parts of the way round, hanging free with its push, layers, cuffs, holes, owned parts, loose ends, posed hiding | Every outfit of the dressed fennec and the dressed woman passes its look sheets with no ragged or crumpled edges |
| 4. Creatures | The adaptive cells; paws, hands, claws, mittens; separate and hard parts; dragon, lizard, toucan, koi, the woman and the twin | All examples build on one surface; the twin's compare IoU is reported beside main's; a one-part edit on the woman stays near 50 ms |
| 5. Migration and retiring | Format 15, the template versions, the examples rewritten, retired code and tests removed, the HLD rewritten | No body part builds lofted; `check` and `check -Pslow` pass; nothing in What retires remains |
| 6. Joints | Implicit skinning, if the walk sheets need it | The walk's shoulders and hips keep their volume at the walk's deepest bends on the spot sheets |

## The proof

The first build step after approval, on this branch.

**Proposed: the proof in two steps.** The proof below is as broad as the five rounds of the fennec test, so it is split, each step judged before the next starts:

- **1a, go or no-go on the mesher.** The bare fennec meshed, shaded and inked, with no paint, eyes, rig or garment. It passes with clean joins (one smooth join line each), an outline as smooth as main's at close-up in every view (a contour that steps at cell scale fails), and a head, front-leg and torso edit timed, median of 21 warm runs, head and leg at 50 ms or less. The coarser surface while dragging (decision 4) is shown as a before-and-after of the drag's end, so the change at release can be judged before decision 4 is accepted.
- **1b, everything on top.** Colour by coordinates including a region cut across parts (the cream chest and socks), the painted eyes, rig weights and the walk, and the winter sweater with its hem, neck opening and cuffs as level-curve edges. Anchors near joins are checked to resolve on the right side and to stay still across a redraw that does not move them.

The pass list below is the union of the two steps.

**What is built.** The bare fennec (`examples/fennec.json`'s drawings) on one surface, its eyes painted with the default eye of decision 5, and the winter outfit's sweater over it. Nothing else of the winter outfit, and no other example changes: everything else builds and renders pixel for pixel as main.

**How it is judged.** The same sheets as the fennec test's round 5, made by `look` with `close=face,foot,joins` and the walk: the full body front, three-quarter and side; the walk; the joins' spot sheets (neck, shoulders, hips, ankles, the forelegs' parting); the dressed views of the sweater front, three-quarter and side, its face, and its walk; beside main and beside round 5.

**It passes when:**

- **Clean joins.** At the neck, shoulders, hips, tail root and ankles, at rest and through the walk: the join's line is one smooth stroke along the equal-share curve, and there is no other line, tick, curl, dot, hole, speck, wedge of wrong colour or ragged edge on any spot sheet.
- **A clean sweater.** No flare, scribble, crumple or pale edge at the collar, shoulders, sleeves or cuffs, at rest and through the walk; the hem, neck opening and cuffs are smooth curves.
- **Edits near 50 ms.** A head edit and a front-leg edit (the part made 2% larger or back), median of 21 warm runs, each 50 ms or less from the edit to the settled surface with its coordinates, shading and weights; a torso edit reported, with its drag-time cost at the coarser cell.
- **No join special cases.** In the proof's code path, nothing names a join: no strip, fillet, cast, buried point, seam point, seated ball, `laidOverJoin` or contour exception, and no code reads whether a point is near a join except the share formula itself.
- **Eyes.** The painted eye wraps in three-quarter as the original fennec's does, never crosses the head's outline, and its sliders work.
- **Numbers.** `measure` on the proof beside main and round 5: profile depth, face in shade, terminator blotches, digit gaps, the walk's volume.

Risks the proof is meant to settle: the contour's look on a dual mesh, the paws' toes at the body's cell size, and the shoulder and hip in the walk under linear blend skinning.

## Decisions

1. **Retire fixed topology** (see Fixed topology). Recommended: yes; it is the design.
2. **Move saved characters onto the one surface when they open.** The alternative keeps both builds alive, and with them every strip, fillet and grid rule this plan retires. Recommended: move them; anchors keep their meaning, so the move is template pins and a dropped fillet.
3. **Thin and hard parts stay separate.** Ears, wings, fins, loose ends; claws, nails, horns, beaks, teeth, owned parts. Recommended: yes, with the toucan's beak hard.
4. **Coarser while dragging a large part.** Recommended: yes; it is the real pipeline at twice the cell, and the full surface follows the drag's end. The alternative is a torso edit of about 140 ms.
5. **The default eye.** Recommended: start from the original fennec's eye in parameters, with the factor sheet's best row as a preset.
6. **Blended joins are inked by default.** Decided by Nicholas: the equal-share curve is drawn unless the join's switch is off.
7. **Fillet goes from Dress.** Recommended: yes.
8. **Golden fixtures of fields and coordinates** instead of vertices for field templates. Recommended: yes.

### Approved

Nicholas approved the plan:

- Decisions 1, 2, 3, 4, 5, 7 and 8 as recommended.
- Decision 6 as amended: blended joins are inked by default along the equal-share curve, and the join's switch turns the line off.
- The proof is split into 1a and 1b, as proposed under The proof; 1b starts only after Nicholas has judged 1a.

## 1a results

Built behind `Character.oneSurface` (command `SetOneSurface`), off for every saved character; the example `fennec-one-surface` is the fennec with it on. Every other example renders pixel for pixel as main (131 images of 13 examples, every outfit, face, hand and foot close-ups, compared byte for byte).

**What is built.** Each part's runs become swept solids, joined parent-only by smooth unions, a part's later runs cut inside the part (`geom/field`). The surface is meshed by a dual mesher over a sparse grid of 8³-cell blocks, with the manifold split per cell, vertices started at the mean of their edge crossings, relaxed toward their neighbours inside the cell and projected onto the field, and each block keeping every member's own field at its grid points, so an edit reads only the changed member again, and only in the blocks its old and new boxes reach (`geom/surface`). Each vertex keeps the four members with the largest shares of the surface there, with each one's (u, v) on its run and its own field. Shading takes the surface's normal smoothed over four rings and turns it as each owning part's own build turns its shading normal at the vertex's (u, v), weighed by share and by nearness to that part's surface. Ink: the contour is interpolated across faces from per-vertex facing (Hertzmann and Zorin) on the one mesh, with no exception anywhere; each inked join gets one line along its equal-share curve, eased, faded with facing, and off where the join's ink switch is. Ears and the nose pad stay separate meshes. While a gesture is open the surface meshes at twice the cell; the full cell follows when it ends. The part drawer has a join blend slider for field parts on a one-surface character.

**Timings,** median of 21 warm runs after 5, on this machine's threads, from the placed character to the settled surface with coordinates, shading, contours' edge map and join lines (rig weights do not exist yet in 1a). An edit is the part's `size` made 2% larger, alternating with back. The ranges are two runs of `measure`.

| Build | ms | Target |
|---|---|---|
| Whole surface, 34 808 vertices, 1102 blocks at cell 0.012 | 1070 to 1089 | — |
| Head edit | 188 to 234 | 50: fails |
| Front-leg edit (moves the paw and its toes) | 293 to 354 | 50: fails |
| Torso edit, full cell | 1047 to 1071 | reported |
| Torso edit, coarse cell (while dragging) | 490 | reported |

Where a head edit's time goes: reading the changed members' channels in about 400 blocks, 100 ms; quads 7 ms; numbering the whole mesh and placing the fresh blocks' vertices with their coordinates, 80 to 100 ms; shading, contours and join lines over the whole mesh, 25 ms. The member channels work (a block the edit does not reach is untouched), but the field reads of a swept solid at every grid point of a reached block, and the whole-mesh assembly and shading, are each over the budget on their own. The torso at the coarse cell is still half a second, so decision 4 does not by itself make a torso drag interactive.

**Against the pass list.**

| Criterion | Result |
|---|---|
| One smooth join line at the neck, shoulders, hips, tail root and ankles | The join lines themselves are single smooth strokes. Fails on the rest of the criterion: the two-tone terminator near the joins steps at cell scale (a sawtooth edge where the light and dark tones meet under the jaw, at the shoulder's three-quarter view and round the tail root), and the underside of the forelegs' parting shows a few streaks and a small contour fragment |
| Outline as smooth as main at close-up in every view | Passes by eye on the outline close-ups, with the contour interpolated across faces; relaxation before projection is in from the start; isotropic remeshing near the silhouette was not needed |
| No join special cases | Passes: no strip, fillet, cast, buried point, seam point, seated ball or contour exception in the code path; only the join line reads shares, as decision 6 asks |
| Paws' toes at the body's cell | The toes read as scallops on the paw's outline from the front and as bumps from above, not as separate toes; they melt together underneath |
| Drag sheet (decision 4) | The coarse surface while dragging is close to the full one at full-body size: toes soften and the terminator's edges coarsen, with no change of shape |
| Closed manifold | 0 open edges and 0 edges on more than two triangles |

`measure` beside main and round 5 measures the parts' own meshes, which the one surface does not change, so its numbers are main's; the one surface adds its own lines (mesh size, outline pieces per view, join lines, timings).

**Open problems for 1b or a 1a round 2.** The terminator's cell-scale steps need shading that is smooth below the cell: more rings of normal smoothing, shading normals from the field's gradient interpolated per pixel, or a finer cell near the terminator. Edit times need field reads cut, either sparse reads inside a block (exact values only near the zero set) or cheaper swept-solid evaluation, and assembly and shading kept per block instead of over the whole mesh. The paws want the adaptive cells of phase 4.

## 1a fix results

Still behind `Character.oneSurface`; every other example still renders pixel for pixel as main (the same 131 images, byte for byte).

**Where a whole build's time went, and goes.** `measure` with `profile=true` prints each batch of a build: what its pieces took together, how long the batch took on the machine's 8 threads, and the work between batches on one. The spike's figures are its own phases at cell 0.0119.

| Stage, whole fennec at cell 0.012 | 1a | Fix | Spike |
|---|---|---|---|
| Members from the placed parts | 2 ms | 2 ms | — |
| Field reads into the blocks' member channels, with each block's cells and manifold split | 646 ms on 8 threads (5.1 s of work, 98% busy) | 92 ms (0.68 s, 93%) | 68–72 ms |
| Taking the read blocks, their neighbours, quads | 27 ms on one thread, 2 ms on 8 | 27 ms, 2 ms | 23–26 ms (meshing) |
| Relaxing, projecting, shares and (u, v) | 119 ms (0.92 s, 97%) | 37 ms (0.27 s, 92%) | 27–29 ms (one projection step, no coordinates) |
| Numbering the mesh, gathering, cutting quads | 5 ms on one thread | 3 ms on one, 1 ms on 8 | — |
| Shading and ink | 22 ms on one thread | 10 ms, mostly on 8 | — |
| Whole build, profiled once | 825 ms | 184 ms | 139–149 ms |

What made 1a slow, against the spike:

- **Four times the field reads.** 1a read 4.5 million member fields for the channels and 0.76 million for placing, where the spike read 0.8 million joined fields of about two members each. Every member a block might matter to was read at all 729 of its grid points, and a member's reach counted its children's widest blend (the head's 0.15 for the torso), so most blocks read four to six members everywhere.
- **Each read twice as dear.** A swept-solid read cost 1.2 µs near the surface, against the spike's 0.55 µs a joined read: it looked at every ring pair and read about 20 of them in full, because pairs were tried in order along the run and the first ones set a loose bound.
- **Placing read every member.** Each vertex's line search and coordinates read every member its block held.

What the fix does about each:

- **Sub-blocks.** Each block is cut into 64 sub-blocks of 2³ cells. A member's field is read at a sub-block's middle only where bounds from its block's middle or its octant's middle cannot show it leaves the union unchanged there. Its channel is read exactly only in sub-blocks where it can change the union and the union can change sign. Everywhere else it stands in by a bound: below it where the surface is near, and its middle's value where only the union's sign matters. Both are bounds chosen so that the joined field at every grid point a crossing uses comes out exactly as a full read gives it. Reads for a whole build fell to 0.9 million for the channels and 0.36 million for placing.
- **Cheaper swept-solid reads.** The pair whose planes the point lies least far beyond is read first. Another pair is read only where the point lies less far beyond its planes, and nearer its middles' segment, than the least so far. About 6 pairs are read in full instead of 20, for 0.53–0.66 µs a read.
- **Placing.** A vertex reads only the members that can change the union round its sub-block for its position, and those that can hold a share there for its coordinates. Its line search stops at a thousandth of a cell. A vertex whose cell, corners, start and members are unchanged since the last mesh takes its old placing.
- **Edits.** A block an edit re-reads is kept whole when nothing it holds changed, and keeps its placing when its field and members' reach are unchanged. The mesh's gathering, quad cutting and finishing run in parallel batches.

Threads: every batch keeps the 8 threads 85–98% busy. The work between batches is on one thread: about 10 ms of an edit's time, and 35 ms of a whole build's (27 of them taking 10 662 block reads).

**Timings,** median of 21 warm runs after 5 warm-up runs, exactly as in 1a: the placed character to the settled surface with coordinates, shading, contours and join lines, the part's `size` 2% larger alternating with back, on 8 threads. Runs of `measure` on this machine vary by up to 10 ms between runs, so each figure is the range over two runs.

| Build | 1a | Fix | Target |
|---|---|---|---|
| Whole surface, 34 808 vertices, cell 0.012 | 1070–1089 ms | 206–207 ms | — |
| Head edit | 188–234 ms | 50–54 ms | 50 ms: fails, just |
| Front-leg edit | 293–354 ms | 57–58 ms | 50 ms: fails |
| Torso edit, full cell | 1047–1071 ms | 185–186 ms | reported |
| Torso edit, coarse cell (while dragging) | 490 ms | 71–72 ms | reported |

**Head and front-leg edits do not reliably reach 50 ms.** Before the surface was shaded as quad fans, which doubled the finishing work, runs gave 38–47 ms for the head and 49–55 ms for the leg; the fans cost about 4 ms. What remains in a front-leg edit, which moves the leg, the paw and its four toes:

- About 20 ms of field reads on 8 threads: 0.12 million channel reads, 0.09 million sub-block middles and 0.13 million while placing.
- About 13 ms placing 8 600 vertices.
- About 10 ms of single-threaded bookkeeping.
- About 8 ms of finishing.

Getting under 50 ms with margin would still need:

- A cheaper swept-solid read, for instance a coarse distance grid per run to start each read from the right pair with a tight bound.
- Numbering, gathering and finishing kept per block, so an edit's bookkeeping and finishing scale with the blocks it touches, not the mesh.
- Fewer re-placed vertices where an edit only moves a part rigidly.

**Shading.** The shadow edge stepped at cell scale because each quad was shaded as two triangles split along one diagonal: where the shading normal bends fast, as in a fillet, the line where light meets shade kinked at every diagonal. The surface is now shaded as a fan of four triangles round a middle vertex in each quad, whose values are the mean of its corners'. Each part's shading turn is averaged over the same four rings of neighbours as the normal. The cel bands, colours, light and outline style are unchanged; only where the edge falls moved. Shading is still per vertex: per-pixel normals were not needed for the edge to read smooth at the spot sheets' close-up.

**Ink.** A contour is left out where the surface plainly bends toward the viewer across it, a hollow where a closed surface always hides it, and where it is shorter than four of the mesh's edges.

**Spot cameras.** A spot's camera stands 4 units out, or, where another part of the character lies on the line out past the body round the spot, just short of that part.

**By eye.**

| Criterion | 1a | Fix |
|---|---|---|
| Clean joins: neck | fail, sawtooth shadow edge under the jaw | pass at rest: the shadow edge and the join line are smooth curves; the edge waves gently at cell scale in places |
| Clean joins: shoulders | fail, sawtooth edge in three-quarter view, a light wedge at the silhouette | sawtooth gone; a small light wedge where the leg meets the silhouette from the side remains, a fail by the letter |
| Clean joins: hips | fail, ragged edge | pass at rest |
| Clean joins: tail root | fail, ragged edge, cameras inside the body | pass from the views that see it; from the front the chest hides it, as it hides it at full view |
| Clean joins: ankles | pass | pass |
| Forelegs' parting | fail, streaks and a contour fragment | streaks gone; a few short ticks of join line where the leg joins meet the chest from below remain, a fail by the letter |
| Outline as smooth as main | pass | pass, no change from 1a |

Sheets, 1a beside the fix: full body, each join spot, outline close-ups, paws, the drag before and after its end, and the mesh.

## 1b part A results

Still behind `Character.oneSurface`. Part A is the speed, shading and ink work; paint, eyes, the rig, anchors and the sweater are the rest of 1b.

**Speed: what is built.**

- **A pair grid per run.** Each swept solid keeps a coarse grid over its box in its own space, a twelfth of its longest side a cell, reaching half its size past the box (`PairGrid`). A cell, found the first time a read lands in it, keeps the ring pairs that can give the field anywhere in it: the pair least at its middle first, then the rest by a lower bound over the cell (how far beyond the pair's planes, and how far from its middles' segment less its widest ring). A read tries that pair, then the others until the bound passes the least so far, and falls back to every pair outside the grid or where the cell's bound is beaten. The field is exactly the least over all pairs, as before (`PairGridTest` checks 4 000 points in, near and far from a bent, swelling run). Because the grid is in the run's own space, a part that only moves keeps its run's solid and every cell already found: a front-leg edit moves the paw and its four toes, and their reads go straight to the right pair.
- **Placing reads once.** A vertex's coordinates take the members' fields and places from the line search's last kept read.
- **Bookkeeping.** Block keys are spread over all their bits, so hashed tables keep neighbouring blocks in different buckets. The run's pending blocks are held as a set; a mesher run rebinds kept blocks only when the members moved in the list. The mesh is numbered per block by arrays; joining each block's quads to the numbered vertices is a parallel batch, followed by keeping the whole quads in order.
- **Finishing.** Building the quad fan, linking it, the face normals and the contours' edge map (`TriangleEdges`, three parallel batches beside the others) run in parallel pieces; the join curves are found beside the last batch, from an edge-indexed table.

What did not go in: finishing kept per block, reusing the last surface's shading for blocks no change reached. With the work above in parallel pieces, finishing a leg edit takes about 6 ms on 8 threads, and the per-block form would trade that for a second identity of every vertex across meshes; not worth it at this size.

**Where a front-leg edit's time goes** (one profiled run each, on 8 threads):

| Stage | Before | Part A |
|---|---|---|
| Members from the placed parts | 0.2 ms | 0.2 ms |
| One-thread work between batches, all of it | 6.7 ms | 4.0 ms |
| Field reads into the reached blocks' channels | 18.9 ms (148 ms of work) | 18.1 ms (138 ms) |
| Quads, joining them to the numbered vertices | 0.9 ms | 1.3 ms |
| Relaxing, placing, shares and (u, v) | 12.0 ms (86 ms of work) | 6.7 ms (48 ms) |
| Gathering, cutting quads | 0.5 ms | 0.5 ms |
| Shading, contours' edge map, join lines | 8.3 ms | 6.0 ms |
| All | 48.3 ms | 39.6 ms |

**Timings,** median of 21 warm runs after 5, measured as in 1a (`./gradlew :app:measure -Pmeasure="example=fennec-one-surface"`), three runs each, before and after on the same day. This machine's runs moved by up to 15 ms from run to run while other work shared it, so each figure is the range.

| Build | Before | Part A | Target |
|---|---|---|---|
| Whole surface | 154–210 ms | 127–183 ms | — |
| Head edit | 40–52 ms | 30–40 ms | about 40, at most 50: passes |
| Front-leg edit | 43–60 ms | 37–47 ms | about 40, at most 50: passes the limit, the 40 only in the best run |
| Torso edit, full cell | 132–186 ms | 119–131 ms | reported |
| Torso edit, coarse cell | 64–71 ms | 56–59 ms | reported |

On one thread, which other work on the machine disturbs less, a head edit fell from about 152 ms of work to 121 ms and a front-leg edit from about 188 ms to 156 ms.

What would take the front-leg edit reliably under 40 ms: its reads, now the largest stage at about 18 ms. A read still evaluates about two pairs in full at about 50 ns each, and an edit reads about 170 000 times. Either fewer reads (the dirty margin round a changed member is four block half-diagonals; the channels' exact reads could stop at the sub-blocks the surface actually crosses) or a cheaper pair (precomputing each pair's frame along its slab so a read does one square root instead of three) would close it.

**Shading: what is built.** The waves the shadow edge followed are the swept solids' own: each run's surface bends a little at every ring, and with the turns and the true field gradient both taken out, the edge still waved. So:

- Each vertex reads its owning runs' own shading normals, weighed by share and by nearness to each run's own surface. Where the runs hold the vertex whole it shades exactly as the part's own build does; across a join the surface's own normal takes over by as much as the runs leave unheld.
- The normals and the runs' shading are smoothed on the mesh's own vertices along the quads' sides (no diagonal, so nothing leans one way), three steps everywhere, then sixteen more by the square of how loosely the vertex is held, in groups that are smoothed apart in parallel. Inside a part nothing more moves; across a join the edge becomes a smooth curve.
- Each quad's middle takes the mean of its corners. Light, the two tones, the far-side darkening and the outline are unchanged; per-pixel normals in the renderer were not needed.

**Ink.** A join's line is cut where its surface faces the viewer less than a cosine of 0.15, thinning to nothing there from full weight at 0.35, and a piece shorter than six cells is left out.

**By eye,** fix beside part A. Sheets in the scratchpad's `one-surface-1b/partA/sheets/`: `full-body.png`, `join-neck.png`, `join-shoulder.png`, `join-hip.png`, `join-tail-root.png`, `join-front-ankle.png`, `join-back-ankle.png`, `join-parting.png`, `outline-close-ups.png`, `paws.png`, `drag-pop.png`, `wireframe.png`.

| Criterion | Fix | Part A |
|---|---|---|
| Neck: notches in the shadow edge under the jaw and at the back of the neck | fail | pass at the back of the neck and down the throat; under the jaw the edge is smooth with one soft bump where the head's own shading meets the neck, no notch |
| Shoulders: ragged tone crops | fail | the shoulder's shadow edge is smooth; under the far paw, where the far-side darkening meets the paw's own shadow, small ragged crops remain: fail by the letter |
| Shoulders: light wedge where the leg meets the silhouette from the side | fail | narrower, still there: fail |
| Forelegs from below: short join-line ticks | fail | the two line ends now taper into each other instead of stopping short; one small tick remains where the far leg's join meets its contour: fail by the letter |
| Hips, ankles | pass | pass |
| Tail root | pass | the shadow patch beside the tail root, seen from behind and below, comes to a point where it was rounded: no notch or rag, but a new shape to judge |
| Outline as smooth as main | pass | pass, unchanged |
| Elsewhere: bands, colours, outline | — | the same bands, colours and outline; the head's shadow in the front view reaches a little further under the muzzle, where the head's two runs join |

Every other example renders pixel for pixel as main (131 images of 13 examples, compared byte for byte).

## 1b results

Still behind `Character.oneSurface`. Part A was the speed, shading and ink work; part B is the paint, eyes, rig, anchors and sweater. The examples are `fennec-one-surface` (bare, painted eyes), `fennec-one-surface-round` (the round eye preset) and `fennec-one-surface-dressed` (the dressed fennec's wardrobe, wearing an outfit of the winter sweater alone).

**What is built.**

- **Paint by coordinates.** Each colour shape a part holds (template areas, regions, strokes) is read at every surface vertex its run holds as a signed distance in the run's own (u, v), measured in body units by how long a unit of u and of v is on the run there (`UvShapeDistance`, `RunScales`). It is kept to the run's own section of the surface, the side of each neighbour where its own field is the lower, so a shape ends exactly on the join's line. The surface is cut along each triangle where that value crosses zero (`LevelCut`), and each cut is a colour layer laid on the surface with its far-side darkening. A part whose base slot differs from the root's paints its own section. Paint is a node of its own after the surface; it lays its shapes in parallel.
- **Painted eyes** (`PaintedEye`, `EyeLook`, `SetPaintedEye`, held on the character for the proof). An eye's middle is an anchor on the head resolved on the field; its facing is the surface's normal there turned by `lookOut`. Its fills (white, iris, pupil, highlight) are ovals and an almond in the plane square to that facing, scaled by how far the surface runs per unit of the head's u and v there, projected along the facing onto the head and cut on the surface; its lid line, lash flick and brow are ink lines laid on the surface with the lid's tapered weight. The default look is the original fennec's three-quarter eye in these parameters (opening 0.075 by 0.052 of the head's u and v at u 0.60, v 0.185, the lower lid 0.72 of the upper, the front corner wider, the iris 0.05 across set forward and up, pupil 0.58 of it, highlight 0.3). The round preset (`ROUND_EYE`) is row h of the fennec test's factor sheet: a dark-brown iris filling the opening, a round pupil, a larger highlight, set lower and nearer the middle, with the brow.
- **Face marks.** Every ink mark in no group and every lip line a part on the surface holds is resolved again on the field and drawn as it is on main: the mouth and the muzzle creases.
- **Anchors on the field** (`FieldAnchors`). An anchor starts at its point on its run's own swept surface at (u, v), moves along the field's gradient there to the zero of the joined field by false position, and off by its offset along the normal there.
- **Rig.** Each vertex's weights are each run's share of it times that part's own weights at the vertex's (u, v), read from the part's own rig (`SurfaceSkin`). A vertex follows only the runs in its own line of joins (what it grows from, what grows from it, and its siblings on the same parent), so a paw never follows the paw beside it. A quad joining two parts no join relates, which touch closer than a cell (the hind paws' inner toes), is left out once a pose parts them. The surface, its paint layers, its ink marks and its garments are skinned by linear blend skinning as the parts are (`posedSurface`). The walk is looked at in four moments of a stride.
- **The sweater.** A garment's field is the covered parts' runs, each grown by its branch's ease and joined as the body joins them (`garmentField`), meshed by the same mesher with a mesher kept per garment, so an edit meshes it again only where the edited member reaches, and kept whole while nothing it covers changed. Its coverage is the share-weighted distance into each branch's u range along the run (`coverage`); the garment is cut where coverage is zero, so its hem, neck opening and cuffs are smooth level curves, inked as hems. Body quads well inside the coverage are hidden. The garment takes the body's weights at the coordinates it grows from. Ears stay separate. Not built: the branch's drawn Side outline (the hem's flare), cuffs as their own band, layers.
- **No socket strips on a one-surface character.** The strips' extra patches were drawn over the surface as pieces; that was the light wedge at the shoulder from the side on the part A sheets.

**Migration.** None: saved characters are not migrated, and 1b does not need it. The painted eyes are a new optional list on the character, empty unless set.

**Timings,** median of 21 warm runs after 5, measured on `fennec-one-surface-dressed` (`./gradlew :app:measure -Pmeasure="example=fennec-one-surface-dressed"`), an edit being the part's `size` 2% larger alternating with back, on this machine's 8 threads. The stack is what the graph runs after an edit: the surface, then beside one another its paint and eyes, its weights, and the sweater meshed, finished, cut and weighted.

| Build | Surface alone | Stack: surface, paint, eyes, weights, sweater | Target |
|---|---|---|---|
| Whole build | 101 ms | 356–376 ms | — |
| Head edit | 31–33 ms | 88–144 ms | 50: fails |
| Front-leg edit | 27–28 ms | 147–166 ms | 50: fails |
| Torso edit, full cell | 76–81 ms | 319–332 ms | reported |
| Torso edit, coarse cell | 34–35 ms | 117–130 ms | reported |

On the whole surface the stages beside the surface take: paint and eyes 29–38 ms, weights 7–8 ms, the sweater from nothing 186–220 ms. The parts' own rig, which the surface's weights read, takes 10–11 ms and runs before them. The ranges are three runs of `measure`, before and after keeping a garment whole, and its weights, while nothing it covers changed, laying paint shapes in parallel and resolving anchors by false position.

Where the time goes over the limit, estimated from the stage medians above (no edit was profiled stage by stage), and the next levers:

- **Head edit:** the sweater is kept whole, so what is left over the surface's 31 ms is paint and eyes laid again over the whole surface (29–38 ms on its own) and the skin the sweater hides found again. Lever: keep each paint layer, and the hidden skin, per block, laying again only the blocks the edit meshed again, as the surface keeps its blocks.
- **Front-leg edit:** the sleeve is meshed again only where the leg reaches, but the sweater's finishing (shading, contours, coverage, cut, hems) runs over its whole mesh, then its weights over all its vertices. Lever: finishing and weights per block for garments, the read-less lever named in part A (a tighter dirty margin, exact channel reads only in the sub-blocks the surface crosses), and precomputed pair frames, now for two meshes an edit.

**Against the pass list,** by eye on the sheets in the scratchpad's `one-surface-1b/sheets/`, each main, fennec test round 5 and 1b in rows: `full-body-bare`, `full-body-dressed`, `face-front`, `face-three-quarter`, `face-side`, `face-round-preset`, `join-{neck,shoulder,hip}-{bare,dressed}`, `spot-{neck,shoulder,hip,tail-root,front-ankle,back-ankle,parting}` (1b's six-sided spots, bare and dressed), `sweater-edges`, `paws`, `walk-{0..3}-{bare,dressed}` and `anchors`. Round 5 has one walk pose and no dressed join close-ups or anchors; those rows are marked missing.

| Criterion | Result |
|---|---|
| Paint matches main's, regions across parts | Passes: the cream chest, muzzle and cheeks, the black tail tip, the inner ears, claws, beans and pads read as main's. The cream meets fur exactly on each join's line. Where the chest's own border crosses the shoulder's fillet, from the front, the edge waves at cell scale, as the torso's (u, v) read off its own surface waves: a fail by the letter |
| Eyes: the default from the original eye, read at least as well as main's | Three-quarter: passes, the almond, the forward iris clipped by the upper lid, the highlight, lid line, lash flick and brow read as main's three-quarter eye and wrap with the head. Front: reads, as slanted almonds rather than main's round front drawing. Side: reads, smaller than main's side drawing, since one painted eye is seen edge-on where main swaps in a drawing. The far eye never crosses the outline |
| Round preset | Built from row h's factors (dark-brown iris filling the opening, round pupil, larger highlight, lower and nearer the middle, brow). Front reads rounder and darker than the default eye and the side keeps its shape; the opening still slants, so it is not main's round front drawing |
| Eye sliders | Not built: the eye is a document item set by a command, with no Face stage section yet (phase 2) |
| Face marks as on main | Passes: the mouth lines and muzzle creases sit where main's do |
| Rig and walk: no tearing, pops or shading jumps at the joins | Passes on the four walk moments, bare and dressed, after two fixes: a vertex follows only its own line of joins (paws had followed the paw beside them), and the hind paws' touching inner toes part instead of stretching. The joins bend under linear blend skinning without visible collapse at the walk's angles |
| Anchors near joins sit where expected and survive an edit of the neighbouring part | Passes: rings on each side of every join line stay on their side and on their part when the torso is made 6% larger (`anchors`); `FieldAnchorsTest` checks it |
| Sweater does not crumple; hem, neck opening and cuffs are smooth curves | Passes at rest and through the walk: no crumple, flare or scribble, against round 5's crumpled collar and shoulders. Two marks remain: seen from below, the cream through the neck opening ends in the hidden skin's quad steps, a pale ragged edge at the collar (fail by the letter); the far sleeve's far-side darkening shows as a blocky patch in some walk frames |
| Ears and separate pieces stay separate | Passes |
| No join special cases | Passes: nothing names a join; weights read lineage through the field's parent links, and the contact quads are found by the same relation, not by part names |
| Every other example pixel for pixel as main | Passes: 131 images of 13 examples, 0 differ |
| Edits within 50 ms with everything on | Fails: see the timings |

**Low-priority items from part A.** The light wedge where the foreleg meets the silhouette from the side was the socket strips' patches drawn over the surface; with no strips on a one-surface character it is gone from the shoulder spot. The tick under the forelegs no longer shows from below, but seen from behind and below one join line there breaks in two short pieces. The ragged tone crops under the far paw were not judged again and stay open.

**Open.** The edit times; the chest border's waves on a fillet; the collar's pale edge from below; eye sliders and the Face stage; the branch's drawn Side outline, cuff bands and layers for garments; posed hiding (hiding is found at rest and carried); the toes at the body's cell (phase 4's adaptive cells).

## Speed round results

Still behind `Character.oneSurface`. Everything the stack lays on the one surface (paint, eyes, the sweater, the skin it hides, the weights) renders pixel for pixel as in 1b: 42 images of `fennec-one-surface` and `fennec-one-surface-dressed` (every join spot, face, paws, walk frames, outline, mesh) compared byte for byte, 0 differ. Every other example renders pixel for pixel as main (131 images of 13 examples, 0 differ).

**How it was profiled.** `./gradlew :app:measure -Pmeasure="example=fennec-one-surface-dressed outfits=<outfit> only=stack profile=true edits=head,front-leg-l jfr=<folder>"` times the stack alone; with `profile=true` it also runs each stage of 11 warm edits one after another and prints each stage's median, and with `jfr=` it records 40 more edits under the JDK's flight recorder with its profile settings. The recordings were rolled up by stage (the innermost stage class on each sampled stack), by method, by allocation site, and for GC pauses and parked threads. The stopwatch medians of 21 warm runs remain the pass and fail numbers.

**What the recorder showed before** (winter outfit, head and front-leg edits; samples on all threads):

| Stage | Head | Front leg |
|---|---|---|
| Surface meshing (field reads, placing) | 56% | 54% |
| Eyes | 12% | 9% |
| Paint | 9% | 7% |
| Garment cut (coverage, level cut, kept triangles, edges), on one thread | 5% | 16% |
| Weights, body and garments, on one thread | 9% | 9% |
| Surface finishing | 6% | 4% |

Hot methods, self: `SweptSolid.pairField` 29%, `SweptSolid.read` 11%, `HashMap.getNode` 6–10% and `Integer.valueOf` 3–4% (the level cut's and the chart's boxed maps and lists), `UvShapeDistance.nearest` 3% (eyes read at every head vertex), `RunWeights.add` and `WeightBlend.write` (sorting a boxed list per vertex). The main thread held 31–37% of all samples: eyes, weights, the garment cut and the hidden skin ran there while the pool waited. GC: 18 and 35 collections over the 40 edits, 77 and 196 ms of pauses; the largest allocation sites were the mesher's blocks, `CellTables.components` (a union-find array for every cell, crossed or not) and boxed integers. Parked waits: 967 and 1391 parks, the pool's threads idle while one thread worked.

**What was changed, from that.**

- **Level cuts** index their vertices by array and crossed edges by an open-addressed long map, with growable int and float lists (`geom/collections`), so no cut boxes anything.
- **The chart** is built once a surface with counting arrays and shared by paint and eyes; joining several runs' triangles uses a flag array, not a boxed distinct.
- **Eyes** read only the head vertices within the opening's reach of the eye's middle that face it, and the iris, pupil and highlight only where the opening is not far outside. The values everywhere else are what the full read clamps to, so the paint is the same.
- **Paint** is incremental per vertex: each mesh matches its vertices against the mesher's last mesh (`MeshChange`: same block, same values, every member it holds the same member placed and joined the same way), and a shape takes its value from the last painting at every vertex that matches. A head edit keeps 22–23 thousand of 35 thousand vertices, a front-leg edit 28 thousand of 34 thousand. The ink lines are resolved as one more parallel piece.
- **Weights** run in parallel chunks, and choose each vertex's heaviest four bones without sorting a list.
- **The garment** is grown from the body's mesh rather than its finished surface: the one surface is meshed in one graph node and finished in the next, and the garments node takes the mesh, so a garment is meshed, finished and cut while the body is being finished. Its mesher's dirty blocks follow the body's changed members as before, and a grown member reads its channel from the body's block where the body read the same solid there, less its ease, exactly as its own read would give it (`ChannelSource`). Its coverage, cut, kept triangles' edges and hems are parallel batches of the garment build. The skin it hides is a node of its own over the finished body and the garments, in parallel chunks.
- **The mesher's reads**: a block keeps its members' fields at its middle and its octants' middles, so a kept member is not read there again; a cell's union-find is made only where the surface crosses the cell; a block's quads are held unboxed.

What was not done: hidden skin and garment weights kept per vertex (each is now under 2 ms); per-block finishing of the body or the garment; the read levers of part A (a tighter dirty margin, exact reads only in sub-blocks the surface crosses, precomputed pair frames). The last of these changes the field slightly and so the look.

**Stages after,** each run alone on the pool's 8 threads, medians of 11 edits:

| Stage | Head, sweater | Head, winter | Front leg, sweater | Front leg, winter |
|---|---|---|---|---|
| Surface | 35 ms | 34 ms | 42 ms | 31 ms |
| Paint | 7 ms | 6 ms | 5 ms | 5 ms |
| Eyes | 4 ms | 4 ms | 4 ms | 4 ms |
| Weights | 1.7 ms | 1.6 ms | 1.7 ms | 1.5 ms |
| Garments meshed / finished / cut | kept whole | 16 / 4 / 1 ms (the cap) | 14 / 5 / 3 ms | 32 / 10 / 5 ms |
| Hidden skin | 0.5 ms | 1.1 ms | 0.5 ms | 1.1 ms |
| Garment weights | 0 ms | 0.7 ms | 1.0 ms | 1.7 ms |
| All, one after another | 49 ms | 70 ms | 78 ms | 93 ms |

Before, on the winter outfit, the same stages one after another took 154–175 ms for a head edit (surface 49–59, paint 12, eyes 27, weights 10, garments 51–58, garment weights 3) and 246–252 ms for a front-leg edit (surface 44–52, paint 11–13, eyes 25–26, weights 9–10, garments 126–143, garment weights 9).

**What the recorder shows after:** surface meshing (the body's and the garments') is 71–79% of all samples; finishing 8–11%; paint 3–9%, eyes 1–4%, weights 2–5%, the garment cut too small to show. Hot methods, self: `SweptSolid.pairField` 35%, `SweptSolid.read` 12–16%, `SurfaceBlock.findCells` and `BlockReading.buildQuads` 2–6% each, `TriangleEdges.ownEdges` 4–6%. Of the samples inside `SweptSolid.read` on a front-leg edit, 32% are channel reads, 26% sub-block middles, 37% placing vertices and 5% block middles. GC: 9–21 collections over the 40 edits, 37–133 ms of pauses. The main thread holds 7–10% of samples, with 2–32 parks against 967–1391: the pool does not wait on single-threaded work, though its threads still take uneven shares of the samples (the busiest about four times the least busy on a head edit), the short finishing and garment batches leaving some idle.

**Timings,** median of 21 warm runs after 5, an edit being the part's `size` 2% larger alternating with back, as in 1b. The stack runs as the graph runs it: the surface meshed; the garments grown, meshed and cut beside the surface's finishing; then beside one another paint and eyes, the weights, and the hidden skin with the garments' weights. Three runs of `measure` each on a quiet machine; runs while other work shared the machine were up to 50% slower and are not counted. `winter-sweater` is the sweater alone, the stack this round was set; `winter` is the example's first outfit (cap, sweater, boots and scarf), which the 1b figures were measured on.

| Build | 1b (winter) | Speed, winter | Speed, sweater alone | Target |
|---|---|---|---|---|
| Whole build | 356–376 ms | 277–283 ms | 210–229 ms | — |
| Head edit | 88–144 ms | 59–64 ms | 50–51 ms | 50, about 40: sweater passes at the limit, not the 40; winter fails |
| Front-leg edit | 147–166 ms | 84–87 ms | 70–73 ms | 50: fails |
| Torso edit, full cell | 319–332 ms | 231–232 ms | 168 ms | reported |
| Torso edit, coarse cell | 117–130 ms | 85–86 ms | 71–80 ms | reported |

The code as this round found it, measured the same day on the winter outfit, gave 92–95 ms for a head edit and 181–199 ms for a front-leg edit.

**Against the goal.** Head edits with the sweater on reach 50 ms, at the limit and not the 40 aimed for; front-leg edits fail, by about 20 ms with the sweater and 35 ms with the winter outfit. "Edit equals fresh whole build" still passes, and `OneSurfaceStackTest` checks the same for the whole stack: after a leg edit, the surface, paint and eyes, the sweater, the skin it hides, and the body's and the sweater's weights match the stack built afresh.

**What would close the gap.** Both edits are now bound by field reads: about half of all samples are inside `SweptSolid.read`, on the body and again on each garment the edit reaches. A front-leg edit reads the leg, both paws and their toes again in about 860 blocks (160 ms of work on 8 threads) before the sleeve reads them again for itself. In order of likely gain:

- **Cheaper pair reads**: each pair's frame along its slab precomputed so a read takes one square root instead of three. This is about a third of every read, but changes the field a little, and with it the look.
- **Fewer reads per block**: exact channel reads only in the sub-blocks the surface crosses, and sub-block middles taken from the channel where the channel already holds them (each middle is a grid point).
- **Placing without reads** where an edit only moves a part (the paws and toes of a leg edit): their vertices moved by the part's move rather than placed again.
- **Garments from the body's placing**: a garment vertex near the body read from the body's placing rather than placed again on the grown field.
- **Finishing per block**, body and garment, as 1b part A described; with the garment beside the body's finishing it saves less than it would have.

**By eye.** No change: the sheets in the scratchpad's `one-surface-speed/sheets/` set 1b above this round for the full body bare and dressed, the face in front and three-quarter view, the sweater's neck opening, cuffs and hem, and a walk frame bare and dressed, each labelled with the pixels that differ (none).

**Open from this round.** The secondary items were not reached: the default eye's front view still reads as a slanted almond rather than main's round front eye; the pale ragged collar edge from below (the hidden skin hides whole quads, so the cream through the neck opening ends in quad steps; cutting the body's fill along the coverage level instead of hiding quads would end it on a smooth curve); the blocky dark patch on the far sleeve in some walk frames (the garment's far-side darkening, carried per vertex from the body's shares). In the app the weights node waits for the dress node, which `measure` runs beside it; and an edit rebuilds the parts' rig, which the weights read, so in the app the weights are read again in full.

## Phase 2 results

The fennec, the dressed fennec and the winged fennec are built as one surface: `fennecCommands()` ends with `SetOneSurface(true)` and the painted eyes, and their example files are written again from it (`./gradlew :screens-core:test -PupdateGoldens`). `fennec-one-surface` is the fennec. The feathered wing template is `"surface": "separate"`, so the wings stay pieces on the surface as the ears do. `look ... surface=parts` renders an example as its parts' own meshes, for comparing with main.

**What is built.**

- **Picks on the one surface.** Every surface tool (paint, regions, strokes, fur, marks, Move, Add part, Dress's press tools) picks on the one surface's mesh and lands the hit in the part whose run holds the largest share there, at that run's (u, v) (`SurfaceMeshPick`).
- **Selection as a section.** A selected part is drawn as its own section of the surface, the triangles where its run holds the largest share, cut along its equal-share curve (`SurfaceSection`), with the rest ghosted; its section's contour and border are inked. Its fur stands on its section.
- **Surface work on the field.** Strokes, region points, fur roots and marks are placed on the field as anchors are (`fieldWork`), at rest; in a pose each is carried by the vertex nearest it at rest (`PosedSurface.carry`).
- **Paint across a fillet.** A shape's distance, read in its run's own (u, v), is eased toward its neighbours' where a vertex lies off its run's own surface, on the blend into the next part, before it is kept to the run's section and cut. The chest's border runs smoothly over the shoulder's fillet and ends on the join's line.
- **Line weights.** The surface's contour takes each part's line weight at the run that holds the largest share there, so the back's heavier line runs on the one surface.
- **Face's painted eyes.** Eyes in Face shows the head's painted eye as the Face board draws it: a box on the panes whose corners resize the eye, round handle turns it and inside moves it, the other side mirrored; Roundness (`SetEyeRoundness`, keeping the opening's area), Pupil and Colors; under More, From front (`SetEyeFromFront`), Highlight, Gaze (`SetEyeGaze`) and the Brow, Lashes and Lid outline switches. An eye can hold a front look; a view within 20 degrees of facing the head sees the eye `fromFront` of the way from its look to the front look, and every control and handle changes both looks alike. Painted eyes have no angle versions. A head with no painted eye shows Add eyes.
- **The fennec's front eye.** The fennec's eyes hold a front look from its front drawing: an upright oval as tall as it is wide, the iris filling it, no lashes. The three-quarter look is unchanged.
- **The face map on the surface.** `CharacterSurface.faceMap` casts the head's face map onto the one surface; no phase 2 example draws on it.
- **Rig stage.** The one surface is coloured by its weights, with seam and heat layers laid on it; separate pieces are coloured as their parts.
- **Panes.** Sheet, Single and the stage views draw the one surface whole, ghosted or as the selected section.
- **Tests on the parts-built fennec.** What only a parts-built character has (strips' weights, the drawn eyes' angle versions, a garment's loose end) is tested on the fennec built as parts (`asParts()`), since the fennec is now one surface.

**Against the criteria,** by eye on the sheets in the scratchpad's `one-surface-phase2/sheets/`, each main above phase 2: `full-body-{fennec,dressed-bare,winged}`, `face-*`, `region-{neck,shoulder,hip}-*`, `walk-fennec`, `walk-dressed-bare`, `chest-front-fennec`, `chest-three-quarter-fennec`, `eyes-front-fennec`, `eyes-three-quarter-fennec`, `wings-winged` and `back-line-weight-fennec`. The winged fennec has no walk. App screenshots of the wired panes are in `one-surface-phase2/app/`: `shape-torso`, `face-head`, `rig` and `color-torso`.

| Criterion | Result |
|---|---|
| The fennec, the dressed fennec bare and the winged fennec build on one surface | Passes |
| Their surface work lands where it did on main | Passes, with these differences: the cream chest meets the legs' fur at the inked join lines, where main's leg fur rose over the chest; the front mouth is subtler than main's smile; the side eye is smaller than main's side drawing, one painted eye seen edge-on; the toes and claws in side view are less divided than main's; a small notch in the muzzle's cream border in three-quarter view |
| The screens' tests pass on them | Passes: `./gradlew check` passes. Tests of what only a parts-built character has run on the fennec built as parts |
| Panes wired to the one surface | Passes: Shape with the torso selected shows its section with its fur, the rest ghosted; Face shows the painted eye's controls; Rig colours the surface by weight; Color paints the surface |
| Face eye sliders | Built; no board draws them, so they follow the nearest board rows (Controls' sliders, Layers' swatches). Face stays verifiable, with its verify steps in `boards.md` |
| Default front eye round like main's, three-quarter unchanged | Passes (`eyes-front-fennec`, `eyes-three-quarter-fennec`) |
| Chest border smooth across the shoulder fillet | Passes (`chest-front-fennec`, `chest-three-quarter-fennec`) |
| Per-character switch set in the examples' commands | Passes |
| Golden example files written by the documented command | Passes |
| Every other example pixel for pixel as main | Passes: 113 images of 10 examples, 0 differ |
| Speed does not regress | Fails for a head edit with the sweater alone, by about 6 ms; the front-leg edits and the winter head edit hold within the noise, the winter front leg 2–3 ms slower (below) |

**Timings,** `./gradlew :app:measure -Pmeasure="example=fennec-one-surface-dressed outfits=<outfit> only=stack"`, each run the median of 21 warm edits; six runs each, the phase's start and phase 2 run in turn on the same machine, since other work on it moved single runs by up to 40%. Each cell is the median of the six runs, in ms, with their range.

| Edit | Start, sweater alone | Phase 2, sweater alone | Start, winter | Phase 2, winter |
|---|---|---|---|---|
| Head | 50 (41–61) | 56 (51–62) | 61 (59–69) | 61 (60–72) |
| Front leg | 74 (59–81) | 74 (70–96) | 85 (83–89) | 88 (87–98) |

Where the head edit's time goes: with the sweater kept whole, paint is on the edit's longest path, and it takes about 10 ms against 7.7 ms at the start (`profile=true`): each run's section is laid on the whole surface every edit and each shape's eased values every edit; only the distances are taken from the last painting at the vertices an edit keeps. Eyes take about 3 ms against 4.4 ms, each eye, look and side laid in parallel. Lever: carry the sections and eased values across meshes at the kept vertices, as the distances are.

**Open.** The head edit's paint time; the painted eye's gaze is set on the eye, not by the Gaze target tool or the pose's Look; the posed surface work is carried by the nearest vertex at rest, not by the field in the pose; a region whose shapes span several parts is laid as each part's pieces kept to their sections (no example has one); the dressed fennec's outfits on one surface are phase 3's (the winter outfit builds, its scarf does not show).

## Phase 3 results

The dressed fennec wears every outfit (Winter: cap, scarf, sweater and boots; Hooded: hood and sweater; Bare) whole on the one surface. The dressed person, the dressed woman and the twin still build as their parts, pixel for pixel as main. Phase 3 is therefore not done: its criterion names the person's and the woman's outfits too.

**What is built.**

- **A garment worn over the placed body** (`WornCover`). Before a garment is meshed it is resolved against the placed parts. Each branch stands off the garments under it: an inner garment's branch the whole way round the same part, over any of the same span, adds its ease, so the scarf sits over the sweater. Each part that comes out through a branch, found as the parts-built dressing finds it (a part on a socket inside the branch's range that the garment neither covers nor hides and that does not hang inside it), either gets a cuff branch over its first run, from inside the garment to past where it comes out by the cuff's size, or, with its cuff off, opens a hole where its own field lies inside the garment. A branch drawn standing off its ease, or hanging free past its part's end, reads its shell's solid in place of its grown run, joined as the run is; the shell is built a little past each end, so its hem is cut on its wall.
- **Cuffs over separate parts.** A cuff over a part outside the body's field, such as an ear through the cap or the hood, sweeps that part's own run, grows it by the ease and joins it to the garment's run over its socket parent by a blend at least twice the ease, so the cuff is one surface with the garment.
- **Partial garments.** A branch part of the way round is cut by its v range in the coverage, as in 1b. A garment reads a part's branch only on the part's first run, whose (u, v) a branch is drawn in, so the fennec's muzzle run does not read the cap's range in its own coordinates.
- **Coverage near its zero is smoothed** along the garment's mesh before the cut (`smoothedNearZero`), so a hem crossing a fast change of shares runs across cells. No value moves more than a quarter cell, so a garment narrower than the smoothing, such as a sandal's strap, keeps its sign.
- **Hiding along a curve** (`HiddenSkin`). What a garment hides is cut along the level curve where the skin, or an inner garment, lies 1.5 cells inside an outer garment's coverage, instead of hiding whole quads; skin round a part coming out through a hole stays shown. The body's far-side darkening layers read the cut. This ends the pale ragged collar edge seen from below.
- **Loose ends** are placed on a one-surface character (`Dressing.endsOnly`), hung from their branch fitted over the layers under it, and rigged from the bone moving their part most at the part's vertex nearest the root.
- **Garments mesh only round what they cover** (`RunClips`, `ClippedSolid`): a run a garment covers part of is kept to a box round its covered rings, one ring and six cells past them.
- **Touching quads in a pose.** Where a pose leaves out the touching quads of two unrelated runs (the boots' touching toes), each garment's cut is made again over what is left and its lines read only the triangles kept, so the boots do not stretch between the feet in the walk.
- **Fur under garments.** A fur root that a one-surface garment's branch covers on its part's first run is kept apart as covered, as on a parts-built character.
- **Paint speed.** Each shape's distances and each run's section are read in pieces of a run's vertices across the threads; a vertex kept from the last mesh takes its section, and its eased value where it lies further from any re-meshed vertex than easing reaches (`ChartEasing`, `ChartLay`, `PaintMemo`).

**Not built.** The hanging push (a skirt pushed out of the legs it hangs over, in each pose). Owned parts in an outfit on a one surface: they are placed and drawn as separate parts, as phase 2 left them, but no fennec garment owns one, so none is judged. Hiding found again in each pose beyond what coverage gives: coverage reads coordinates, which a pose does not change, so what it hides follows the pose, but a pose that carries skin out through a garment's wall is not tested again. Moving the dressed person and the dressed woman onto the one surface.

**Timings,** `./gradlew :app:measure -Pmeasure="example=fennec-one-surface-dressed outfits=<outfit> only=stack"`, each run the median of 21 warm edits; four runs each, the phase start (ca502d5) and phase 3 in turn on the same machine. Median of the four runs in ms, with their range.

| Build | Start, sweater | Phase 3, sweater | Start, winter | Phase 3, winter |
|---|---|---|---|---|
| Whole build | 214 (171–222) | 239 (218–245) | 287 (277–291) | 327 (322–332) |
| Head edit | 50 (45–64) | 53 (48–58) | 60 (59–60) | 96 (93–97) |
| Front-leg edit | 74 (68–77) | 80 (76–84) | 87 (85–88) | 97 (94–100) |
| Torso edit, full cell | 166 (161–169) | 182 (168–183) | 230 (230–232) | 275 (270–277) |
| Torso edit, coarse cell | 69 (68–72) | 86 (81–89) | 85 (84–90) | 125 (124–130) |

The winter outfit at the start meshed a cap without ear cuffs and a scarf under the sweater; it now meshes the cap's ear cuffs, which a head edit moves, and those cuffs are most of the head edit's 36 ms more. With the sweater alone the edits regress by a few ms: the sweater's drawn Side outline now gives it its shell, whose solid the garment reads itself rather than from the body's channels (about 3 ms of a front-leg edit), and the hidden skin is cut along a curve over the whole body (about 2.6 ms more than hiding quads). Levers: cut the hidden skin only over the quads its curve crosses, keeping whole the quads either side; read a shell's channels once per edit; mesh the ear cuffs again only where a head edit moves them. Paint on a head edit with the sweater alone, profiled stage by stage, is 8.1–8.6 ms against 10.0–10.7 ms at the start in the same runs.

No woman outfit is timed: the woman is not on the one surface.

**Against the criteria,** by eye on the sheets in the scratchpad's `one-surface-phase3/sheets/`, main above phase 3: `fennec-<outfit>-full-body`, `-face`, `-walk-{0..3}` (main's look has no walk frames of the dressed fennec, so those rows are marked missing), `-edge-collar-front`, `-edge-head-three-quarter`, `-edge-sleeve-hem-side`, `fennec-winter-edge-boots`, `fennec-winter-edge-scarf`, and phase 3's six-sided spots `fennec-<outfit>-spot-{neck,shoulder,hip,front-ankle,parting}-phase3`. App screenshot of the Dress stage on the one surface: `one-surface-phase3/app/dress-winter.png`.

| Outfit or criterion | Result |
|---|---|
| Fennec, Winter | Passes by eye at full body and in the walk: the cap with its ear cuffs, the scarf over the sweater with its loose end, the sweater with its drawn flare, the boots over the sleeves. Fails by the letter on edges: the cap's lower corners, where the ear cuffs meet its side edge, are ragged at cell scale; the boot tops show small serrations; a near boot's sole shows a small hole in one walk frame where its touching quads are left out |
| Fennec, Hooded | Passes at full body, face and walk; seen from below, two small cream specks of chest show through the sweater beside the bib, where the head's blend into the chest stands out past the grown torso (fails by the letter) |
| Fennec, Bare | Passes, as in phase 2 |
| Person, Summer | Not moved: builds as parts, pixel for pixel as main. Fails the phase criterion |
| Woman, every outfit | Not moved: builds as parts, pixel for pixel as main. Fails the phase criterion |
| Twin, dressed | Kept on parts, as asked; pixel for pixel as main, so its compare mean is unchanged |
| Pale ragged collar edge from below | Fixed: the cream through the neck opening ends on a smooth curve (`fennec-hooded-spot-neck-phase3`, bottom right) |
| Blocky dark patch on the far sleeve in walk frames | Not seen in the four walk frames of either outfit |
| Paint speed on the sweater head edit | Improved, 8.1–8.6 ms against 10.0–10.7 ms; not back to 7.7 |
| Edits do not regress | Fails: see the timings |
| No join special cases | Passes: nothing names a join; holes and cuffs come from the socket tree and coverage, touching quads from the field's parent links |
| Every unmoved example pixel for pixel as main | Passes: 113 images of 10 examples, 0 differ |

**Open.** The person and woman outfits on the one surface: their bodies, their faces' drawn eyes (which a one surface does not draw), and the woman's skirts (which need the hanging push); the cap's ragged cuff corners; the bib specks; the edit-time regressions above; hiding tested again on the posed meshes.

**The Face stage's angle versions (a note for the boards, not built).** With painted eyes, Face still shows the Angle versions band and the panes' "Writes Side version" and "Writes Front version" chips, which belong to drawn eyes. A painted eye has its own look, seen from every side, and an optional front look, seen within 20 degrees of facing it. What the boards would change: on a head with painted eyes, the Angle versions band gives way to a choice of Own look and Front look (or a Front look switch in Eyes, its tile shown only while it is on); the "Writes Side version" chip goes, since no side look exists; "Writes Front version" reads as editing the front look; Lock locks the panes to the front look; a dashed tile means a front look not yet made. A head with drawn eyes keeps the band as drawn. That is Nicholas's call.

## Phase 3 finish results

The dressed person and the dressed woman wear every outfit on the one surface, with painted eyes; the twin stays on parts.

**What main holds.** Main before the one surface landed (6fdbc35) and main with it (1a58451) render dragon, koi, lizard, person, person-dressed, toucan, woman, woman-dressed, woman-twin and woman-twin-dressed alike: 113 images, none differ in any channel. The one-surface landing changed no example it left on parts.

**An edit meshed again equals a fresh build.** A block read again whose grid point signs and sheets are as before takes its last read's placed vertices (`placesAs`, `takePlacement`) and is not counted as read anew. Its quads are built only for blocks read anew and their neighbours, so where such a block had no neighbour read anew, it kept the empty quads of its new read and the edited surface lacked its quads: 134 quads of the stack test's fennec at a cell of 0.1 of the root's half-extent, a few at 0.0487, none at 0.05, where every block read again happened to lie beside one read anew. The block now takes its last read's quads with its vertices, which the same signs and sheets make the same. `OneSurfaceEditCellsTest` builds the stack (surface, paint, garments, hidden skin) with kept builders at 0.0487, 0.05, 0.0731 and 0.1 of the root's half-extent against fresh builders, and fails at 0.0487 without the fix. The cell rule stays 0.05 of the root's least half-extent: at that share the fennec meshes at the 0.012 its triangle budget sets, and no outfit moves it.

A further limit, not fixed: a block's channels keep exact values once read and bounds elsewhere, which agree in the union wherever each member's field changes no faster than `LIPSCHITZ`. A synthetic field eight times steeper than a distance gives an edit that differs from a fresh build near the blocks read exactly to close seams; the characters' fields stay inside the bound at every cell tested.

**What is built, by fault.**

- **Holes in the casual woman's temples**: blocks that disagree in sign with a neighbour at a shared grid point are read again exactly until the seams agree (`BlockSeams`). Fixed.
- **Painted eyes.** The fennec's, the person's and the woman's eyes read as main's, as before. Kept.
- **The fennec cap's red corner slivers**: fixed; from the side the cuff round the ear is still ragged, much as main's is. Open.
- **The skirt in the walk.** A hanging branch is belled out of the legs as a cone hinged at its top (`hemFlare`, in `geom`, tested on its own), then pushed clear along the body's normals. Its line normals are turned as the push turns the cloth (`turnedNormals`), so its contour follows the pushed silhouette; its shading keeps the posed normals. The hems of a garment hanging free are drawn as its outline, since where the cloth turns up at the hem its own contour lies past the hem in what the garment does not cover. The flared hem is inked in every walk frame. Fixed.
- **The blazer round the neck.** A garment grown over a run grows over the run's closed end as well, a lid past the end's ring; the woman's torso run ends at y 0.68 and the blazer's lid stood 0.045 above it, nearly to her jaw. Where a part the garment does not cover comes out through that end, coverage past the plane across the run at the end's ring (`RunEnd`) is kept only under a part coming out through the end that overhangs it, clear of that part, and ends a little short of the plane elsewhere. A part overhangs a point when the way out along the run from the point comes at least half the garment's ease nearer the part than the point lies, read every half ease out to eight eases. So the blazer and the blouse end at the base of the neck, which rises straight from the end, and the neck shows, as on main; and the person's jacket and shirt close round the base of his head, which sits over his torso's broad end. `OpenEndCoverageTest` checks both against a ball over the end and one beside it. Since coverage now reads the parts coming out through a garment's ends, an edit to such a part alone cuts the garment again on its kept surface rather than meshing and finishing it anew (`RecutGarmentTest`, a paw edit under a sweater down the whole leg, against the sweater built afresh). Fixed.
- **The office lapels.** The blazer grown over her one surface stands further out over the bust than the lapels' Side drawing allowed, so most of each lapel lay inside it. The drawing stands 0.015 further out (`LAPEL_LIFT`, in the fixture), and the lapels fold over the blazer as on main. Fixed.
- **The person's jacket.** Its flat, boxy top and the shirt's ring round the whole neck came from the open-end plane across his torso's broad end; with the rule above it closes round his head with rounded shoulders, a thin line of shirt at the head as on main from the side. Fixed. Its sleeves still meet the body under the arms, where main's are separate rounded sleeves: the jacket stands off the shirt, 0.06 in all, and his arms hang closer to the torso than that twice over, as they hang on his bare one surface too, so the grown sleeve and body run into one another. Open.
- **The sandal's sole.** A band of the foot's run part of the way round follows the run's rounded underside up round the heel, with the toes, their own runs, reaching past it; that is how such a band reads on the one surface. The person's sandals now own a sole each, a slab drawn as his shoe's footprint with a margin on a socket under the ankle, as the woman's sandals do, and the bare toes rest on it. Fixed in the fixture.
- **The woman's scarf** stands up her neck as a cowl. The scarf covers the neck as well as the torso's top, so the torso's end is not open to it, and its torso branch, grown by its own ease and the coat's and the top's under it, 0.092 in all, rises over the torso's closed end round the neck. Lowering the scarf's reach up the neck changes nothing. Opening an end to a part the garment also covers would open the trousers between the legs and the hood round the fennec's head as well, so it needs a rule for a lid under a covered part, not built. The dark sliver under the coat's collar at the front is the top under it showing between the coat's and the scarf's edges. Open.

**Against the criteria,** by eye on the sheets in the scratchpad's `phase3-finish/sheets4/`, main above phase 3: `<example>-<outfit>` (full body front, 3/4, side), `-face` (front, 3/4, side), `-foot` and `-hand` (close-ups), `-pose-walk`, `-pose-salute` (the woman), `-pose-walk-0` to `-pose-walk-3` (the fennec).

| Outfit | Result |
|---|---|
| Woman, Bare | Passes at rest, in the walk and the salute |
| Woman, Beach | Passes |
| Woman, Casual | Passes |
| Woman, Dress | Passes at rest and in the walk: the flared hem is inked where it is pushed |
| Woman, Goth | Passes |
| Woman, Office | Passes: the neck shows and the lapels fold over the blazer |
| Woman, Winter | Fails by the letter: the scarf stands up the neck where main shows the neck, and a dark sliver shows under the collar's front |
| Person, Summer | Passes at the neck and the feet: the jacket closes round the head with rounded shoulders, the sandals are slabs under the feet with the toes resting on them. Fails by the letter under the arms, where the sleeves run into the body |
| Person, Bare | Passes; eyes near main's |
| Fennec, Winter | Passes; the cap's side cuff is ragged |
| Fennec, Hooded and Bare | Pass; the face reads as main's |
| An edit equals a fresh build | Passes at four cells |
| No join or part named in code | Passes: open ends come from the socket tree, hanging from the branch's range |

**Every unmoved example pixel for pixel as main.** Dragon, koi, lizard, toucan, woman-twin and woman-twin-dressed against `phase3-finish/main-look`: 49 images, none differ in any channel. Of the moved examples, against the round before (`final3` and `final4`), only the person's summer outfit and the woman's office outfit change, and one pixel of the woman's winter outfit at rest; every fennec image and the dress walk are as they were.

**Timings,** `./gradlew :app:measure -Pmeasure="example=<example> outfits=<outfit> only=stack"`, each run the median of 21 warm edits, three runs each of main (1a58451) and this branch in turn on an otherwise idle machine, median of the three in ms with their range. Main's woman builds as parts.

| Build | Main, fennec sweater | Branch, fennec sweater | Main, fennec winter | Branch, fennec winter | Main, woman casual | Branch, woman casual |
|---|---|---|---|---|---|---|
| Whole build | 236 (176–244) | 265 (258–267) | 329 (320–345) | 402 (396–414) | 386 (382–394) | 340 (339–352) |
| Head edit | 54 (47–57) | 58 (55–62) | 103 (99–123) | 151 (151–160) | 202 (194–216) | 135 (129–144) |
| Front-leg edit | 79 (79–80) | 94 (92–98) | 100 (97–103) | 150 (148–153) | | |
| Torso edit, full cell | 182 (175–189) | 207 (207–209) | 272 (272–289) | 350 (342–354) | 310 (309–330) | 286 (286–335) |
| Torso edit, coarse cell | 86 (85–86) | 95 (94–95) | 126 (124–129) | 147 (145–147) | 119 (115–122) | 122 (122–127) |

The woman's casual outfit builds faster than main's parts build, its coarse torso edit alike. The fennec's edits still regress against main, by 4 to 15 ms with the sweater and 21 to 78 ms in winter, the garments' own work.

The open-end rule reads the parts coming out through a garment's ends, so a head edit cuts the garments over the torso's end again on their kept surfaces. Three more runs each of main and the branch with it, on a machine shared with other work (every figure about half again the table's), hold the same ratios: the fennec's winter head edit 219 ms against main's 154 (1.42 times, 1.47 in the table), its front-leg edit 216 against 140 (1.54, 1.50), the sweater alone and the woman's casual outfit within the noise of main. The log is the scratchpad's `phase3-finish/ab4.log`.

**Open.** The jacket's sleeves running into the body under the arms; the scarf up the woman's neck and the sliver under the coat's collar; the cap's side cuff; an edit equal to a fresh build where a field is steeper than `LIPSCHITZ`; the edit regressions.

## Speed round 2 results

Every example renders as main does: 147 images of the 13 examples, every outfit, the named poses and the face, hand and foot close-ups, rendered fresh from main and from the branch with `look ... outfits=all poses=all close=face,hand,foot`, compared byte for byte in every channel: 0 differ. "Edit equals fresh whole build" passes at the four cells of `OneSurfaceEditCellsTest` and in the stack tests.

**How it was measured.** `measure ... only=stack` times each edit at eight sizes of the part in turn, each about 2% from the one before and none alike, so every run builds a size none of the seven builds before it was built at, as a drag does; nothing the builders keep holds more than the last build. Each timing is the median, 90th percentile and worst of 21 warm runs. Undo is timed as the build back to the size before the last edit; an outfit switch as the build to the example's next outfit and back. The profiles are `profile=true jfr=<folder>`, 40 edits under the flight recorder each, rolled up by stage (the innermost stage class on each sampled stack) and by method.

**The profiles, fennec sweater, before** (main, samples on all threads):

| Stage | Head | Front leg |
|---|---|---|
| Surface meshing, the body's and the garments' | 65% | 68% |
| Eyes | 10% | 8% |
| Hidden skin | 9% | 6% |
| Paint | 8% | 3% |
| Surface finishing | 5% | 7% |
| Garment cut | 0% | 4% |
| Weights | 1% | 2% |

Hot methods, self: `SweptSolid.pairField` 34–35%, `PairGrid.cellAt` 9–11%, `PlaneFit.add` 5–6% (smoothing coverage near its zero, on one thread inside the hidden skin's and the cut's batches), `SweptSolid.read` 3–4%. The main thread held smoothing and the chart while the pool waited. GC: 16 and 26 collections, 75 and 168 ms of pauses over the 40 edits. Stage medians, one after another, sweater: head surface 42, paint 9, eyes 3, hidden skin 14 ms; front leg surface 49, garment meshed 19, finished 7, cut 12, hidden skin 14 ms.

**After** (at the joins' commit, before the two smallest levers):

| Stage | Head | Front leg |
|---|---|---|
| Surface meshing, the body's and the garments' | 66% | 69% |
| Eyes | 12% | 5% |
| Paint | 7% | 5% |
| Surface finishing | 7% | 11% |
| Hidden skin | 3% | 1% |
| Garment cut | 0% | 2% |
| Weights | 1% | 2% |

Field reads (`pairField`, `cellAt`, `read`, `placeAt`, `scan`) are 53% of all samples; `PlaneFit` has gone from the top. GC: 6 and 8 collections, 43 and 68 ms of pauses. Stage medians: hidden skin 6 ms on either edit, garment cut 5.5 ms.

**What was changed, one commit each.**

| Lever | What it does | Gain, from the stage medians |
|---|---|---|
| Hidden skin and garment cut from kept coverage (lever 2) | Coverage read on a mesh is kept per surface and cover (`CoverageMemo`); the next read takes it at every vertex the mesh's change keeps with the same runs and reads only the rest. Smoothing it near its zero runs as parallel pieces, step by step (`NearZeroSmoothing`), without allocating per vertex | hidden skin 14 to 6 ms on every edit; cut 12 to 5.5 ms on a leg edit |
| Garments beside one another | Every garment still being made gives its next batch each round, so the winter outfit's four garments mesh, finish and cut at once instead of in turn | winter edits, see the table |
| Seams and quads in pieces; shares without sorting | The mesher checks neighbouring blocks' seams in parallel pieces, a block quadded again copies its placing arrays in its own piece, and a vertex's four heaviest shares are picked in place in the order a stable sort gives | 1–3 ms of one-thread work an edit |
| Joins smoothed in parallel steps | The sixteen join-smoothing steps each run over every loosely held vertex in pieces, not one piece per join group, where one large group held a thread | about 1 ms of the body's finishing, 2 of a garment's |
| Coverage kept where moved parts are not read | Where the parts coming out through a garment's holes or ends moved, a vertex keeps its coverage unless the last read or this one reads those parts there | the head edits that recut garments over the torso's end |

**Timings,** `./gradlew :app:measure -Pmeasure="example=<example> outfits=<outfit> only=stack"`, three runs each of main (5f3bd3c, with this round's `measure` and nothing else) and the branch in turn on an otherwise idle machine. Each cell: the median of the three runs' medians with their range, the median of their 90th percentiles, and the worst single build of all three, in ms.

Fennec, sweater:

| Build | Main | Branch |
|---|---|---|
| Whole build | 264 (263–272) / 302 / 338 | 272 (269–280) / 314 / 370 |
| Head edit | 51 (51–59) / 62 / 76 | 50 (48–54) / 69 / 78 |
| Front-leg edit | 105 (101–112) / 122 / 144 | 94 (89–100) / 128 / 165 |
| Torso edit, full cell | 216 (213–216) / 243 / 296 | 210 (210–217) / 247 / 284 |
| Torso edit, coarse cell | 99 (97–99) / 110 / 128 | 93 (92–98) / 100 / 120 |
| Undo, head | 55 (55–57) / 65 / 69 | 53 (51–57) / 60 / 72 |
| Undo, front leg | 98 (95–101) / 113 / 124 | 90 (87–92) / 112 / 150 |
| Undo, torso | 215 (212–216) / 249 / 256 | 196 (196–205) / 241 / 261 |
| Outfit switch to the next | 207 (200–213) / 225 / 249 | 137 (136–150) / 167 / 211 |
| Outfit switch back | 22 (22–22) / 24 / 34 | 22 (21–22) / 27 / 40 |

Fennec, winter:

| Build | Main | Branch |
|---|---|---|
| Whole build | 400 (399–423) / 447 / 527 | 352 (348–362) / 434 / 474 |
| Head edit | 145 (144–191) / 158 / 228 | 115 (114–117) / 137 / 168 |
| Front-leg edit | 144 (143–145) / 152 / 162 | 108 (105–111) / 123 / 133 |
| Torso edit, full cell | 344 (339–348) / 357 / 379 | 336 (316–338) / 359 / 420 |
| Torso edit, coarse cell | 152 (152–154) / 164 / 213 | 129 (126–135) / 155 / 169 |
| Undo, head | 147 (146–177) / 153 / 209 | 115 (111–117) / 133 / 154 |
| Undo, front leg | 145 (142–148) / 151 / 157 | 111 (110–116) / 134 / 151 |
| Undo, torso | 349 (347–360) / 376 / 403 | 326 (325–330) / 356 / 457 |
| Outfit switch to the next | 94 (94–96) / 100 / 103 | 94 (92–96) / 113 / 133 |
| Outfit switch back | 182 (180–183) / 187 / 194 | 155 (149–155) / 164 / 528 |

Woman, casual:

| Build | Main | Branch |
|---|---|---|
| Whole build | 337 (335–362) / 355 / 418 | 325 (321–328) / 383 / 454 |
| Head edit | 130 (128–130) / 141 / 158 | 108 (108–110) / 142 / 172 |
| Arm edit | 125 (120–126) / 139 / 160 | 94 (90–96) / 116 / 136 |
| Torso edit, full cell | 289 (281–290) / 331 / 353 | 257 (250–268) / 326 / 357 |
| Torso edit, coarse cell | 124 (122–125) / 140 / 155 | 111 (111–113) / 137 / 148 |
| Undo, head | 131 (129–133) / 138 / 157 | 107 (105–107) / 138 / 168 |
| Undo, arm | 124 (115–125) / 138 / 172 | 95 (95–97) / 115 / 129 |
| Undo, torso | 283 (282–286) / 308 / 341 | 247 (244–247) / 311 / 326 |
| Outfit switch to the next | 91 (91–92) / 102 / 107 | 73 (66–74) / 81 / 89 |
| Outfit switch back | 117 (112–121) / 133 / 144 | 84 (83–86) / 103 / 135 |

**Against the goal.** Head and front-leg (or arm) edits, median against main: the fennec sweater's head edit 50 against 51 ms, at the 50 aimed for but no faster than main within the noise, its front leg 94 against 105; the winter head 115 against 145 and front leg 108 against 144; the woman's head 108 against 130 and arm 94 against 125. The 90th percentiles and the worst runs fall with the medians on the winter fennec and the woman's arm, and stay at main's on the sweater, where a few runs in each set of 21 took up to 165 ms against main's 144 on the front leg; what makes those runs slow was not found. One winter outfit switch back took 528 ms, once in 63. The torso edits gain 2–11% and the whole builds hold within the noise. The 50 ms goal is met only by the sweater's head edit at its median; the front leg misses by 44 ms with the sweater and 58 in winter, the woman by 44 to 58.

**Levers not taken.**

- **Finishing per block** (lever 1). Its smoothing takes 3 and 16 steps along the quads' sides, so a vertex's shading depends on everything within 19 rings, in the order of its neighbours' links; keeping it exactly needs that whole neighbourhood unchanged and its links in the same order, plus the contour edge map and join curves kept per block. Not built; the finishing is now 7–11% of samples.
- **Garment vertices from the body's placed vertex** (lever 3). A vertex pushed out by the ease lands where the grown field's line search would only when the grown field is the body's moved uniformly, which a blend between differently eased branches breaks; telling where it is the same needs the line search's reads. Not built.
- **Cuffs only where an edit reaches** (lever 4). Already so: a cuff is part of its garment's field, and the garment's mesher reads again only the blocks the changed members reach. The winter head edit's garment time is the cap itself, grown over the head that changed.
- **Read less** (lever 5). The channels' exact masks are per sub-block, and they are also what lending to garments and keeping channels across edits trust; reading exactly only at the grid points that change the union needs exactness per grid point through all three. Sub-block middles taken from channels save under 4% of reads, since a changed member's middles are read before its channel. Not built.
- **Moving rigidly moved parts** (lever 6). A paw moved by a leg edit moves off the grid: its channels must be read at the new grid points, and its vertices placed fresh, to equal a fresh build. Left out.
- **A cache of recent builds** (lever 7). Not built: undo costs about an edit, and switching back to an outfit already reuses each garment kept whole by the garment builder.

**What would close the gap.** Field reads are half of all samples, on the body and again on each garment an edit reaches. In order of likely gain: exactness per grid point in the channels, so only grid points where a member can change the union are read (about the part A estimate of a third of channel reads); a garment's placing seeded from the body's placed vertex and checked by one read, falling back to the line search; finishing kept per block with the 19-ring rule above; and the GPU spike's batched reads for what is left.

## Drag feel results

**What a drag does.** While a gesture is open the body meshes at twice the cell (`COARSE_FACTOR`), and everything grown from that mesh follows it: each garment meshes at the body's coarse cell, and paint, eyes, hidden skin and surface work are laid on the coarse surface. The weights and the posed surface wait for the gesture's end unless a pane draws them (`skinShown`). A pose drag leaves the placed character as it is and keeps the full surface. Each node of the one surface's stack keeps its last `KEPT_BUILDS` (4) settled builds by input, so undo, redo and an outfit worn again take them as they were; results made while a gesture is open are not kept. Releasing builds the full stack again.

**How it was measured.** `./gradlew :app:drag` opens the example as the app does, on `Dispatchers.Default` with every node the app makes, and plays a 24-move Move drag of a forelimb's socket (mirror on) through one transaction; each move is timed until both graphs settle. Every play starts a tenth of a step past the one before, so no move repeats an input any cache holds. One warm play, then five timed; each cell is the median, 90th percentile and worst in ms, on an 8-core machine with nothing else running. Repeating the same command moves the median of a later move by up to ±10 ms.

| Character, outfit | Each later move | Shown frame | First move | Release | Undo | Redo | Outfit switch, there / back |
|---|---|---|---|---|---|---|---|
| Fennec, bare, front leg | 64 / 94 / 112 | 43 | 65 | 76 | 25 | 30 | |
| Fennec, winter, front leg | 78 / 101 / 129 | 77 | 115 | 285 | 54 / 203 | 27 | 65 / 66 |
| Fennec, winter, knee pose | 8 / 9 / 17 | 2 | 18 | 81 | 4 | 10 | |
| Woman, casual, arm | 114 / 131 / 171 | 98 | 158 | 245 | 80 / 212 | 28 | 59 / 123 |
| Person, summer, arm | 110 / 159 / 215 | 99 | 164 | 230 | 101 / 251 | 84 | 64 / 181 |

Against main (b01ca55, the same harness): the winter fennec's front-leg move 310 ms, release 1520, undo and redo 1986 and 2011, outfit switch 2319; the woman's arm move 250.

**Against the goal.** No dressed character's move settles under 50 ms; the bare fennec's shown frame does. Per node, for a winter fennec front-leg move (median of 96 moves, each node's own run, with everything else running beside it): the coarse body mesh 25 ms, then the garments 42 (their first read of the field alone 22, re-reading the whole of both front boots and a third of the sweater), then the hidden skin 8; beside them the surface finishing 9, the paint 10–13, the surface work landed on the field 20, the fur after it 24–28, and the parts' socket creases 22. The longest chains are mesh, garments, hidden skin (about 75 ms) and mesh, surface work, fur (about 73 ms).

**Levers tried and not kept.**

- A coarser drag cell. At three and four times the body's cell the garments re-read 222 blocks instead of 566 but each block costs more, and the body mesh stays at about 25 ms: later moves 59 and 73 ms against 58–82 at twice, within the noise.
- Gathers that run only once all their sources are settled (the hidden skin, the shown paint and the fur, which each run twice a move, once on the garments or surface work of the move before). Later moves 68 and 69 ms against 70 and 76 with the same build: within the noise.

**What would close the gap** is the list in the speed round 2 results: field reads are the body mesh and each garment's mesh, and a moved boot or sleeve is read whole. Reading fewer grid points, garments placed from the body's vertices, moving rigidly moved parts without meshing them again, or the GPU reads of `gpu-field-spike`. Each changes the mesher or adds a GPU path, and waits for a decision. Fur and surface work have no coarse form yet; a thinned fur while dragging is a look decision.

**Identity.** The 147 images of the 13 examples (every outfit, the named poses, the face, hand and foot close-ups), rendered from this branch and from main and compared in RGBA byte for byte: every image equals main's. One image, `woman-dressed/winter-rest`, differs by one pixel between two renders of main itself, and the branch's equals one of them; across three renders of the branch, `woman-twin-dressed/beach-rest` once differed from main by two pixels and equalled it on the other two.

**Release equals a fresh build.** `look close=release` renders the Move drag's last move, the release, and a session opened afresh on the released document. The winter fennec and the casual woman: the release equals the fresh build in RGBA, whole figure and face. The summer person with an arm's socket moved along the torso: see "Drag speed results".

## Drag speed results

**Surfaces shown with their own paint and dress.** The graph settles a build's surface before the paint and dress laid on it, so after an edit or an outfit switch the newest surface can meet the last build's dress or paint. A dress (`SurfaceDress.on`) and the paint (`HeldPaint.on`) name the surface they are laid on. The panes show a surface only with the paint and dress laid on it, and keep the last surface that had both until then (`LastPaneSurface`); the skin and posed nodes keep their last result while the dress or paint is still another surface's, and the shown paint keeps the whole paint while the dress is. Paint kept to the skin a pose shows is cut on the rest mesh's triangles, which its candidates index, and laid on the posed mesh, which leaves out the quads where parts touch. `SurfacePairingTest` plays outfit switches on the test dispatcher and checks every pairing the panes and the posed node see, and a pose that parts quads.

**Release equals a fresh build.** `look close=release`: the winter fennec and the casual woman's release equals the fresh build in RGBA, whole figure and face. The summer person with an arm's socket moved along the torso builds afresh; its face equals the release, its whole figure differs from the release in 501 bytes of RGBA; both show the forearms inside the shirt.

**Identity.** The 147 images of the 13 examples, rendered from this branch and compared in RGBA with main's: 142 equal. The four `fennec-dressed/hooded-pose-walk-*` images differ: over the whole figure. In main's first frame the fennec stands unposed with its hood cut ragged and its ears' outlines apart from them; in this branch's it walks with the hood whole. `woman-dressed/winter-rest` differs by one pixel, the pixel two renders of main differ by.

**Ceilings of the two drag levers.** Measured with throwaway changes, each later move of a 24-move Move drag of the front leg, three timed plays, median / 90th percentile / worst in ms:

| Character, outfit | As built | No fur while a gesture is open | No line search placing garment vertices while coarse | Both |
|---|---|---|---|---|
| Fennec, winter, front leg | 78 / 129 / 188 | 78 / 110 / 131 | 82 / 123 / 146 | 52 / 77 / 92 |
| Woman, casual, arm | 120 / 165 / 187 | | | 116 / 157 / 186 |

Each lever alone leaves the other chain as long (body mesh, garments, hidden skin; body mesh, surface work, fur). Both at their limit (no fur at all, garment vertices left where relaxation puts them, off the surface) bring the fennec to 52 ms, with the body mesh at 18 ms and the garments still about 30 ms after it, most of it their blocks' field reads. A thinned fur and a garment placing that is checked and falls back cost more than either limit, so neither lever reaches 50 ms on the fennec. The woman has no fur and her move is set by the coarse body mesh (52 ms) and the posed surface and lines after it (103 and 120 ms), which neither lever touches.

A garment vertex seeded from the body's placed vertex pushed out by the ease and confirmed by one read lies on the garment's surface, but not where the line search from the vertex's relaxed start lands, so its renders cannot equal the line search's; only a form used while a gesture is open, where the full build follows the release, keeps the rest renders as they are.

Neither lever is built.

## Drag posed results

**What a drag does to the posed chain.** While a pane draws the posed surface and a gesture meshes the coarse surface, the skin and the posed surface it makes are marked coarse (`HeldSkin.coarse`, `HeldPosedSurface.coarse`). The coarse surface is posed without the search for skin a pose pushes through a garment: its dress keeps the skin shown at rest (`PosedHiding.kept`), so a sliver of skin can show at a cuff or hem until the release. The pose's lines are outlines only (`outlinedLines`): a part the pose moves has its grids found on the posed body, at plain weight and without the runs the one surface outlines itself, with no crease and no mark weights; a part it leaves keeps its lines at rest. A shown pose at rest skins and poses nothing, as `skinShown` already says. Releasing builds the full chain, which equals a fresh build (`DragPosedTest`). Outside a drag the body's vertices are posed in pieces in parallel, and each garment is posed and pushed clear of what hangs inside it beside the others.

**Ceilings.** Measured with throwaway switches in one run, plays of each alternated: no weights made afresh (every vertex on the root bone, which also leaves the surface unposed), no posed hiding, the rest lines as the posed lines, and all three. The rest of the chain stays as long as it was with any one of them alone; all three together cut the process CPU of each later move by 29% and end the posed chain about 140 ms after the surface instead of 300 (the machine was loaded about four times over, so every number was about four times its quiet value).

**Copied weights are not built.** Each coarse vertex taking the weights of the nearest vertex of the last full skin, found through a grid of buckets, cost about five times what making the weights afresh does: the weights are read through the mesh's own owners and joins and take 2–5 ms, so they are no lever.

**How it was measured.** The machine was shared with another session's builds throughout, so each case ran in one JVM with the drag-time forms switched off and on, play by play: six timed plays of each, 24 moves each, median / 90th percentile / worst of each later move in ms, and the process CPU time of a move (all threads), which the other load moves less. Off stands for main: the same build with the coarse forms and the parallel posing switched off.

| Character, outfit | Each later move, off | Each later move, on | CPU a move, off / on |
|---|---|---|---|
| Woman, casual, arm | 385 / 647 / 1030 | 291 / 568 / 926 | 672 / 547 |
| Fennec, winter, front leg | 148 / 221 / 300 | 142 / 221 / 375 | 438 / 453 |
| Person, summer, arm | 175 / 225 / 290 | 140 / 177 / 280 | 516 / 406 |
| Fennec, winter, knee pose | 9.6 / 12.6 / 25.8 | 9.5 / 12.7 / 26.3 | 16 / 16 |

The winter fennec's shown pose is its rest pose, so neither its Move drag nor its knee drag runs the posed chain's drag-time forms; its numbers move within the noise.

**The woman's chain.** When each watched node last finished in a later move, median ms, off then on: the coarse body mesh 182 and 158, the surface 225 and 190, the dress 290 and 256, the skin 300 and 264, the posed surface 345 and 289, the posed lines 384 and 291. After the dress, the posed chain took 94 ms and takes 35. Summed over a move, the posed node's body posing takes 91 ms, its garments and their push clear of what hangs inside them 38 (71 in turn), the posed hiding 3 (45), and the posed lines 2 (363); the posed node and the lines run four to eight times a move, once for each source that arrives. Her move is now set by the coarse body mesh and the garments meshed on it, and after them the posing of the body.

**What it looks like.** `look close=release` on the casual woman: the frame mid-drag differs from main's mid-drag frame in one pixel, since the panes line a part the one surface holds by its own contour, joins and hems and never drew the creases and runs the full posed lines find for it. The release equals the fresh build in RGBA for the casual woman and the winter fennec, whole figure and face.

**Identity.** The 147 images of the 13 examples, rendered from this branch and compared in RGBA with main's: 146 equal; `woman-dressed/winter-foot` differed by two pixels in one render of the branch and equalled main's in a second, as two renders of main equal each other.

**What would shorten it further.** The skin and the posed surface run again when the rig of a move arrives, well before its surface, and pose the last surface once more for nothing; waiting for a rig and a surface placed from the same character would save that work. A garment's push clear of what hangs inside it could wait for the release as the hiding does. The full posed lines find grids and creases for every moved part the one surface holds, which the panes do not draw; finding them only where something reads them would shorten the release and every pose drag too.

## GPU reads results

**Ceiling.** With a throwaway hook, every swept-solid read of one 24-move Move drag was recorded into a hash table and the same drag replayed with each read answered from it (about 50–100 ns a lookup against 0.5–1.2 µs a read), plain and replayed plays alternating in one JVM, four of each:

| Character, outfit | Reads a move | Each later move, read / replayed | CPU a move, read / replayed |
|---|---|---|---|
| Fennec, winter, front leg | about 300,000 | 128 / 100 ms | 406 / 328 ms |
| Woman, casual, arm | about 240,000 | 153 / 108 ms | 500 / 359 ms |

Reads at no cost would take 22–29% off a move.

**The seam.** `FieldReads` (`geom`): `read(batch: FieldBatch)`, a batch of queries in, each a member of the batch's `members`, a body point and whether it is placed, and each one's field, u and v out; `gathers` says whether a reader collects points into batches, and `generation` changes when what answers them does. `CpuFieldReads` reads each point where it is asked, through the member's own solid. `GpuFieldReads` (`field-gpu`) reads swept solids, plain or clipped, mirrored and eased, in one WGSL dispatch per batch on a headless device of its own, with the spike's kernel and a member table, its solids held on the device from batch to batch. The mesher asks through `FieldAsk` a round at a time; with a gathering reader, `ReadRounds` runs its pieces as coroutines that stop at each round, the mesher gives each round's answering as a batch of its own, and the garments' builder answers all its garments' rounds as one. Rounds too small to be worth a batch (a block's middle, a vertex's line search past its first two reads, its gradient and second search) are read on the CPU where asked, in every run alike, rather than by a size threshold, so an edit still meshes as a fresh build does. The session takes its reader from `Workers.fieldReads` beside its dispatcher; the app reads on the CPU unless launched with `-Preads=gpu`. `GpuFieldReads` opens its device at the first batch, drives it from a thread of its own above the pool's, and on a failed open or dispatch reads that batch and every later one on the CPU, stops gathering and changes its generation, so each mesher drops what it kept. `check` uses the CPU's and opens no GPU. See `docs/hld/architecture.md`, Field reads.

**How it was measured.** `./gradlew :app:drag -Pdrag="example=<example> outfits=<outfit> drags=<drag> reads=cpu,gpu runs=6"` opens one session reading on the CPU and one on the GPU in one JVM and plays each gesture on them in turn, play by play: one warm play each, then six timed, 23 later moves a play; median / 90th percentile / worst of each later move in ms, the process's CPU time over a move (Windows counts it in 15.6 ms steps), and the paired difference, GPU less CPU, move by move. The machine was shared with another session: three of its JVMs were up throughout, mostly idle (load 0–11% at the start of each case), and its renders, if any, shared the GPU. The GPU is the machine's integrated Intel Arc 140V, which shares power and memory with the CPU.

| Case | CPU reads | GPU reads | GPU less CPU, paired median (10th to 90th) | CPU a move, CPU / GPU |
|---|---|---|---|---|
| Woman, casual, arm | 105.9 / 136.0 / 187.3 | 103.1 / 135.8 / 165.0 | −3.9 (−28.6 to +23.1) | 578 / 500 |
| Fennec, winter, front leg | 90.7 / 123.7 / 158.0 | 87.2 / 113.4 / 146.6 | 0.0 (−24.4 to +21.0) | 516 / 422 |
| Person, summer, arm | 99.9 / 135.6 / 240.0 | 103.2 / 132.0 / 182.5 | +1.2 (−23.0 to +25.0) | 563 / 422 |
| Fennec, winter, knee pose | 80.9 / 87.1 / 93.2 | 82.0 / 87.6 / 90.3 | +0.6 (−6.6 to +8.6) | 203 / 219 |

An earlier run of the same four cases, before a gathering piece stopped asking again for a round it had no new points in, gave paired medians of +1.3, +0.6, +0.8 and 0.0 ms and took 62–109 ms of CPU off a move: the paired difference stays within ±4 ms, its 10th to 90th percentiles within ±29 ms. Releases, CPU then GPU: the woman 301 and 314 ms, the fennec 380 and 368, the person 287 and 269; each GPU set held one release of 500–810 ms, where the CPU's worst was 354–460. A knee pose reads no field: its 81–83 ms is the posed surface and lines, as on main with the single-session harness (69 ms there).

**Work counts.** Over each case's seven plays with their releases and undos, the GPU read 38.2 million queries in 2,129 dispatches for the woman (5.7 s reading, 3.8 s of it on the device), 39.1 million in 2,156 for the fennec (6.8 s, 5.3 s) and 35.1 million in 1,584 for the person (5.5 s, 3.7 s): about 18,000–22,000 queries and 1.8–2.5 ms a dispatch. On an idle machine the same device reads 300,000 queries in 10 ms; beside eight busy CPU threads a dispatch takes two to three times as long, and a round trip about 1 ms. What each part of the design is worth, measured as each was added in turn: the garments' meshers answering their rounds together halve the dispatches; tiny rounds read where asked halve them again, and take the fennec's paired difference from +8 to −3 ms; the device driven from a thread above the pool's cuts device time by 30%.

**Where the moves go.** When each watched node last finished in a later move, median ms, CPU then GPU: the woman's surface mesh 57 and 47, dress 90 and 87, posed surface 104 and 101, posed lines 106 and 103; the fennec's surface mesh 30 and 29, dress 87 and 85, fur 87 and 84; the person's surface mesh 43 and 38, dress 87 and 88, posed lines 100 and 103. The GPU takes 94–141 ms of CPU off each move (16–25%) and up to 10 ms off the body's mesh, but on either reader a move ends at the same nodes: the woman's and the person's at the posed surface and lines after the dress, the fennec's at the garments and, beside them, the fur after the surface work. Reads do not set a move's time on this machine.

**Identity.** CPU path: the 147 images of the 13 examples (every outfit, the named poses, the face, hand and foot close-ups) rendered from this branch and from main and compared in RGBA byte for byte: all 147 equal. `SurfaceMesherTest` meshes with the CPU's reads gathered into batches and checks the mesh equals the one read point by point.

**GPU path.** An edit built again by kept builders equals a fresh build on the same reads (`GpuReadsSmokeTest`, the winter fennec's front leg grown 3%, body and garments, at the full cell and the drag's coarse one), and `look close=release reads=gpu` gives a release equal to the fresh build in RGBA for the casual woman and the winter fennec, whole figure and face. Parity, GPU against CPU on the same batches (`-Pslow`): `GpuFieldReadsTest` on three made-up runs (one mirrored, one clipped and eased), 20,000 reads, the field within 1.8e-7 and v within 1.2e-7, no sign apart; `GpuReadsSmokeTest` on every batch of the winter fennec's builds, 2.3 million reads, the field within 4.3e-6, 217 signs apart all within 3e-8 of zero, and of 477,000 placed reads 347 apart in v and 3,378 in u by over 1e-3 (548 of them within 0.01 of the surface; median 0.03, largest 0.40), where two pairs of rings give the same field and the GPU's scan and the CPU's pair grid take different ones. The test holds the field to 1e-5, sign changes to within 1e-6 of zero, and placed reads apart to one in 1,000 in v, one in 100 in u and one in 500 in u near the surface. The 147 images on the GPU path against the CPU's: 87 differ, in 4,077 pixels in all of 1.08 million an image, a median of 18 among those that differ; the worst are the summer person (929, 720 and 349 pixels: the mark on the forearm drawn a few pixels over with a stray fleck beside it, and the sleeve's cuff lines a pixel over), the winter fennec's foot close-up (268: the boot's shading edge) and its walk poses (81–93).

**Not built.** The whole placing on the GPU in one kernel (line search, union and coordinates), which would take the placing's three rounds to one and its CPU tails with them; the CPU's pair grid on the GPU; reads outside the mesher (coverage for the hidden skin and the cut, anchors), still read one point at a time on the CPU.

## Hair shell results

The woman and the dressed woman wear hair as hair parts: a `hair-cap` in her field, a fall of five `hair` clumps and a `hair` lock at each temple, each clump and lock its own mesh. The twin wears a cap, a fall and two locks of the same templates ("Twin results").

**What is built.**

- **The hair role and two templates.** `Role.HAIR`, labelled Hair with tile art of its own. `hair-cap`: a skull run with no joint, so it rides the bone of the part it sits on, in the field. `hair`: a generic tube resting down, `"surface": "separate"`, a pointed end and a chain of three sway joints (`root`, `sway` at 0.35, `tip` at 0.7). Both are data over existing blocks; a mane on a creature is the same parts on its neck's sockets.
- **Weights.** A field vertex won by a hair run weighs only that run and its socket parent's (`SurfaceSkin`'s lineage), so the cap over the nape follows the head and not the neck or torso. A hair part gives its parent no rim (`Weights`), so the scalp never follows the hair; a hair part of its own follows its own chain, the head's bone fading in over its root band only.
- **The push.** In each pose, after the hanging branches are pushed, each hair part of its own is pushed out of the posed parts other than hair, the part it sits on and that part's other children (`withHairPushed`, `StrandPush` in `geom`): each strand vertex reads its eight nearest body points and needs to stand `HAIR_CLEARANCE` (0.012) off each along its normal; a ring moves whole by its largest need; the strand swings as a rod from its root ring; three passes, each from where the last left it. On a one surface a body point a worn garment covers at its u stands out by the garments' eases (`wornOver`), so the fall lies over the coat; on a parts-built character the garment branches are placed parts and push the hair themselves. At rest nothing is pushed: the rest shape is drawn clear of the body.
- **Clumps and tufts.** The fall is five clumps on sockets round the back of the head (the middle one, an inner pair and an outer pair, each shorter and narrower), each ending in the point a tuft at the end of its drawn Top gives it, so its hem breaks into points; two strand marks are inked down each clump, the crown's strands kept.

**Against the asks,** by eye on the sheets in the scratchpad's `hair-shell/`, main above the branch in each: `woman-full-body` (front, 3/4, side, back, back 3/4), `woman-face`, `woman-head-turn` and `woman-head-turn-views` (look left, look right, tilt), `woman-body-pose` (the torso bent and the arm raised), `woman-walk` (walk and salute), `woman-dressed-<outfit>` for every outfit.

| Ask | Result |
|---|---|
| The body does not drag the hair | Passes: with the torso bent, main's fall bends with the back; the branch's hangs from the head. `HairRigTest` finds no hair vertex of its own weighted to anything but its chain and the head, and the head only inside the root band; every cap vertex in the field follows the head's bone alone |
| A head turn slides the fall over the shoulder | Passes: turned 60 degrees either way, main's fall lies in the back and shoulder; the branch's clumps lie over the shoulder. `HairRigTest` turns the head 70 degrees each way and finds no hair vertex inside the torso's solid by more than 0.004 |
| Locks and strands, not a helmet | Better: the fall's hem breaks into five points and the clumps' overlap creases ink between them; the cap is still one smooth shape over the skull |
| Garments wear over the hair | Passes: the beanie still covers the cap; the winter coat and every top lie under the fall |
| Other examples as main | Passes: dragon, fennec, fennec-dressed, fennec-winged, koi, lizard, toucan, woman-twin and woman-twin-dressed, 83 images, none differ in any channel |

**Open.**

- The cap is the drawn crown in the field, standing off the skull by its drawing, not an offset grown from the head as a garment's branch is. Growing it from the head needs a part built over its parent's built surface, which the slice build does not do.
- Hair has no weight: the fall turns rigidly with the head and the push only moves it out of the body, so in a bend or a tilt it does not hang toward the ground. Its chain's joints are there for a later sway or a hang solve.
- The push reads the parts' own posed meshes, not the one surface's field, so on a one surface it is clear of the parts the field is joined from, within their meshes' error of the field (0.004 in the test).
- The clumps part a little at the hem, showing the coat between them from behind; closer sockets or wider clumps close it, a matter of taste.

## Koi and lizard results

The koi and the lizard build on one surface from their first command, with painted eyes in place of blob eyes. Neither needed a part redrawn.

**What is built.**

- **The koi.** Its body is the field. `fin` and `sheet` carry `"surface": "separate"`, so the dorsal, pectoral and anal fins and the tail fin are thin pieces sunk into the body as the fennec's ears are; the mark changes no build, so neither template takes a new version. Its eyes are painted on the body itself (a fish has no head part): a gold ring round a large dark iris and a pale highlight, a lid line, no lashes or brow (`koi/KoiFace.kt`).
- **The lizard.** Body, head, legs, paddle feet and tail are all in the field; it has no toes or claws to mark. The tail's socket blend is widened to 0.1 and its join unlined, so the body's rear flows into the tail instead of stepping down to it; the other joins keep their crease line. Its eyes are painted at the head's face landmark: a yellow iris filling the opening, a dark round pupil, a lid line (`lizard/LizardFace.kt`).
- **Named poses** for both, for the look command: the koi's `swim-0` to `swim-3` (a side-to-side wave down the body and the tail fin) and `rise`; the lizard's `walk-0` to `walk-3` (legs in diagonal pairs, spine and tail swaying) and `bask` (head up, tail lifted and curled).
- **Upright by Move.** `PartRoles.upright` reads the root part's template as Move places it, so the woman, whose torso stands up by its Move rest, names her limbs Left foot and Right foot in Pose. A limb's chain ends at the bone its hand or paw hangs from, so her arm, which has a shoulder joint of its own before the elbow, is named by her hand, bends at the elbow and shows the hand's Curl.

**Against the asks,** by eye on the sheets in the scratchpad's `creatures-1/`, main above the branch: `koi-as-saved`, `koi-face`, `lizard-as-saved`, `lizard-as-saved-face`, `lizard-as-saved-foot`, `lizard-hand`; the branch's poses in `koi-poses` and `lizard-poses`.

| Ask | Result |
|---|---|
| Builds cleanly on one surface | Passes: no holes, melted parts or broken paint in any view or pose; the koi's silhouette is smoother than its parts build, and the lizard's head, legs and feet join with a crease line where they meet |
| Joins | The lizard's tail flows out of the body; at a sharply bent tail joint (`bask`) a small kink shows under the root |
| Toes at the body's cell | The lizard's feet are paddles with no toes, which read as a clean foot shape at the body's cell |
| Other examples as main | Passes: cat, cat-dressed, dragon, fennec, fennec-dressed, fennec-winged, toucan, woman, woman-dressed, woman-twin and woman-twin-dressed, 202 images, none differ in any channel |

**Drag timings** (`drag`, median of each later move, 90th percentile in brackets): the koi's pectoral move 7.9 ms (9.3), a pose drag on its body's middle joint 7.5 (9.3); the lizard's front-leg move 25.4 (50.1), its knee pose drag 18.5 (20.6).

**Open.**

- The lizard has no toes or claws: its feet are drawn paddles. Toes and claws would be a redraw on `paw` 5 or `foot` 4, waiting on finer cells for digits.
- The koi's tail fin shows two small ink marks near its root, as its parts build does.
- In the lizard's foot close-ups, the view from above sits inside the leg and shows the ankle's crease through it in broken arcs.

## Adaptive cells results

**What is built.** Digits mesh at a quarter of the body's cell and everything else at the body's cell (see `docs/hld/architecture.md`, Cell levels). A digit is a run of a part's row of toes or fingers, a `digits` or `toeRow` block's (`isDigitRun`), so the fennec's and the cat's paw toes and the woman's fingers go finer, and her palms, feet, hair and every other run stay at the body's cell; a row that builds no digits, as under a shoe or a glove, leaves nothing finer. `AdaptiveMesher` runs one `SurfaceMesher` a level over its `MeshRegion`, the octants a finer level takes holding no vertex, the grid points a block shares with a coarser level blended from the coarser grid's, and `LevelSeams` joins neighbouring levels with one face across each crossed finer edge of the boundary. The mesh holds its quads first and the seams' lone triangles after them (`SurfaceMesh.quadCount`), and the shading fan, shading smoothing, charts, skin contacts, posing and hanging push take both.

**Mesh invariants.** `AdaptiveMesherTest`: a palm with three digits closer than the body's cell, the digits two levels finer, meshes closed and manifold (every edge on two triangles, run opposite ways), every vertex on the field, every triangle facing out, its quads first, each two triangles sharing a diagonal, and lone triangles after them; at one level the digits melt together and two levels finer each digit's side facing the next is meshed; an edit meshed again by kept meshers equals the same field meshed whole and keeps vertices; with every member at level 0 the mesh is the single mesher's. The fennec, the cat and the woman: 0 open edges and 0 edges on more than two triangles.

**Cost.** Measured in one JVM with a throwaway switch of the digits' level, alternated build by build and play by play. Another session was working on the machine: the load was 2 to 11% around the fresh builds and 7 to 54% around the drags, so the drags' medians are noisier than the paired differences.

Fresh surface build (`SurfaceBuilder` whole, eight threads, median of 10 after 2 warm):

| Example | Digits at the body's cell | Digits at a quarter |
|---|---|---|
| Fennec | 33,030 vertices; 208 ms; CPU 1,250 | 72,786; 333 ms (+60%); CPU 1,906 |
| Cat | 35,300; 447 ms; CPU 1,438 | 81,806; 793 ms (+77%); CPU 2,500 |
| Woman | 30,022; 157 ms; CPU 922 | 50,796; 224 ms (+43%); CPU 1,281 |
| Woman, dressed, as saved | 29,738; 149 ms; CPU 953 | 50,512; 227 ms (+52%); CPU 1,297 |
| Fennec, dressed, as saved | 32,214; 141 ms | 32,214; 136 ms |

`./gradlew :app:drag`, two sessions played in turn, four timed plays of 23 later moves: each later move median / 90th percentile / worst in ms, and the paired difference move by move.

| Case | Digits at the body's cell | Digits at a quarter | Paired |
|---|---|---|---|
| Fennec, winter, front-leg move | 81.6 / 106.0 / 138.0 | 82.6 / 104.0 / 152.8 | +0.6 (the boots' paws build no toes) |
| Fennec, winter, knee pose | 7.5 / 8.2 / 9.0 | 7.2 / 8.3 / 20.4 | -0.3 |
| Woman, casual, arm move | 144.5 / 160.3 / 181.9 | 181.8 / 244.9 / 451.3 | +37.4; CPU a move 672 to 969; release 281 to 401 |
| Fennec, bare, front-leg move | 92.4 / 124.6 / 232.6 | 152.7 / 188.0 / 284.5 | +59.4; CPU a move 375 to 641; release 148 to 261 |
| Fennec, bare, knee pose | 9.3 / 10.5 / 14.6 | 9.1 / 10.4 / 12.9 | -0.1 |

**What it looks like.** The fennec's paws show four separate toes from the front, side and below, each with its own outline and claw, and keep them through the walk; at the body's cell they read as scallops and melt together underneath. The woman's fingers are separate, each with its own outline and its nail painted whole, where at the body's cell they merge into a ragged mitten with nails cut into slivers. The cat's mitten paws still read as mittens, with shallow lobes at the front and cleaner sock and bean edges; small flecks of the sock's edge remain on the front and the sole. No seam, step or shading change shows where the cell changes. No example puts a garment over digits: boots, shoes and gloves set their rows to none.

**Images changed.** Of 321 images of 13 examples (every outfit, the named poses, face, hand, foot, hands and feet close-ups), compared in RGB with digits at the body's cell, 131 differ, each a whole figure, pose, foot close-up or face of: the cat; the dressed cat's bare, Hotdog and Vampire outfits; the fennec; the dressed fennec's bare and hooded outfits; the winged fennec; the woman; and the dressed woman's bare, beach, casual, dress, goth and office outfits. The faces differ in 16 to 73 pixels: a paw or hand at the frame's edge, and on the cat's side view the cream's edge on the cheek a pixel over. Unchanged: the dragon, koi, lizard, toucan, the twin and the dressed twin, the winter fennec and the winter woman, and every hand and foot template close-up.

**Open.** Digits at a quarter add about 60 ms to the bare fennec's front-leg move and 77% to the cat's fresh build. The cat's paws keep small sock flecks. The palm and the rest of a hand at the body's cell show a slightly stepped outline beside the finer fingers in the hand close-up from outside.

## Toucan and dragon results

The toucan and the dragon build on one surface from their first command, with painted eyes in place of drawn starter eyes. Neither needed a part redrawn.

**What is built.**

- **The toucan.** Torso, head, legs and feet are in the field; the feet's four toes are digits of a `toeRow`, so they mesh at a quarter of the body's cell. `beak`, `beak-lower` and `tail-feathers` carry `"surface": "separate"`, so the beak's two halves are hard pieces sunk into the head, inked by the overlap crease, and the tail is a thin piece, as the feathered wings already were; no character on one surface used these templates, so none takes a new version. The bird foot has no claws to mark. Its eyes are painted at the bird head's side eye landmark, in the middle of the pale eye-ring regions: a dark green iris filling a small round opening, a black pupil, a pale highlight and a lid line (`toucan/ToucanFace.kt`). The mouth group stays, so opening it still swings the lower beak.
- **The dragon.** Torso, head, tail, legs and paws are in the field (it has no neck part; the head sits on the torso's neck socket, as the fennec's does). `wing-membrane`, `horn` and `spike` carry `"surface": "separate"`: the membrane wings are thin pieces and the horns and the back and tail spikes hard ones; the ears were separate already. Its claws are `paw` 5's own, grown past each toe's tip in the paw's field at a quarter cell and painted in the `claw` slot, as the fennec's are, not separate pieces: paw 5 offers no tip socket to sit a claw part on. Its eyes are painted at the head's eye landmark: a narrow slanted amber eye with an upright slit pupil, a small highlight and a brow line (`dragon/DragonFace.kt`), on two new palette slots, `iris` and `pupil`.
- **Named poses** for the look command: the toucan's `walk-0` to `walk-3` (the legs swinging one against the other and folding at the heel, the head bobbing, the tail tipping) and `call` (head back, beak open, tail cocked); the dragon's `walk-0` to `walk-3` (legs in diagonal pairs, the tail swaying, the head nodding) and `roar` (head thrown up, wings raised, tail lifted and curled).

**Against the asks,** by eye on the sheets in the scratchpad's `creatures-2/`, main above the branch: `toucan-as-saved`, `toucan-as-saved-face`, `toucan-as-saved-foot`, `dragon-as-saved`, `dragon-as-saved-face`, `dragon-as-saved-foot`, `dragon-hand`; the branch's poses in `toucan-poses` and `dragon-poses`.

| Ask | Result |
|---|---|
| Builds cleanly on one surface | Passes: no holes, melted parts or broken paint in any view or pose. The toucan's head flows into its torso from the side with a crease line in front; the dragon's legs, paws and tail join with crease lines where they meet the body |
| Hard and thin parts | The beak's halves keep their hard edge and dark tips, inked where they leave the head; horns, spikes and the membrane wings sit as before |
| Toes and claws | The toucan's toes and the dragon's three toes and claws read as separate digits at a quarter cell |
| Other examples as main | Passes: cat, cat-dressed, fennec, fennec-dressed, fennec-winged, koi, lizard, woman, woman-dressed, woman-twin and woman-twin-dressed, 210 images, none differ in any channel |

**Drag timings** (`drag`, median of each later move, 90th percentile in brackets): the toucan's leg move 67.7 ms (91.1), a pose drag on its heel 23.4 (26.9); the dragon's front-leg move 141.3 (176.9), its knee pose drag 61.4 (77.1).

**Open.**

- The dragon's moves are slow for a bare character, as the cat's are: its paws' long toes and claws at a quarter cell.
- The dragon's claws are field points, not separate hard parts; a separate claw would need a tip socket on paw 5.
- In the dragon's foot close-ups one view sits inside the leg and shows its flat end with a ragged rim, as the lizard's does.
- The roar's open jaw does not show in the renders.

## Twin results

The twin and the dressed twin build on one surface from the same drawings, so every example builds on one surface. The engine is main's.

**What is built.**

- **One surface.** `twinOneSurface()` (`twin/TwinSurface.kt`) is the woman's: the switch, a narrow blend under the jaw (0.015) and where the cap meets the head (0.012), the joins of head, neck, arms, hands, legs and feet unlined. Her hands (`hand` 5) and feet (`foot` 4) are digit rows, so her fingers and toes mesh at a quarter of the body's cell with no change.
- **Painted eyes** (`TWIN_EYE`, `twin/TwinFace.kt`) in place of the drawn eye groups, matched to them as the woman's were: an almond opening lifted toward its outer corner, the olive iris most of its height with a round pupil and a small highlight, a lid line, the wing as the lash flick and a brow, at the middle of the drawn opening. The lips, nostrils, eyeshadow and blush stay drawn, and the face looks the outfits wear still redraw them. The brow takes the eye's ink colour, not her brown brow slot, since a painted eye has one ink.
- **Hair on the hair shell.** The cap is a `hair-cap` in her field, riding the head's bone; the fall and the two locks are `hair` parts on their own chains, pushed out of the body in each pose; each on its socket and drawing, the `hair` template's chain of three joints its bones. The locks end just above the shoulder, 0.15 of the head's units long: a lock whose tip lies in the shoulder is swung out from the face as a rod by the push in every pose.
- **Named poses** for the look command besides the reference's walk, wave and shades-up: `look-left` and `look-right` (the head turned 60 degrees), `tilt` (25 degrees toward her left shoulder) and `bend` (the chest bent 25 degrees forward at two spine joints).

**Against the asks,** by eye on the sheets in the scratchpad's `twin/`, main above the branch: `twin-bare-full`, `twin-bare-close` (face, hand, foot), `twin-bare-poses-1` to `-3`, `dressed-<outfit>`, `dressed-<outfit>-close` and `dressed-<outfit>-poses-1` to `-3` for beach, lawyer, dress and goth, `back-bare`, `back-dressed`, `back-look-left`, `twin-bend`, `compare-dress`.

| Ask | Result |
|---|---|
| Builds cleanly on one surface | Passes: no holes, melted parts or broken paint in any view, outfit or pose; 0 open edges and 0 edges on more than two triangles (`measure`) |
| Painted eyes match the drawn ones | Passes from the front and three-quarter: the same almond, iris, pupil and lifted outer corner; the highlight and the wing are smaller. From the side one eye is seen edge-on, as on every painted head |
| Fingers and toes | Read as separate digits with their nails, as main's |
| Hair | The locks hang beside the face; turned 60 degrees either way the fall slides over the shoulder; bent forward it hangs from the head. The cap keeps main's shape |
| Every outfit wears cleanly | Beach, lawyer and goth pass at rest and in every pose. Dress passes in shape: the skirt hangs and flares clear of the legs in the walk, but below its upper band it shades dark, as the woman's hanging skirt does, and the top's short sleeves are plain, not puffed, since a sleeve grown from the arm takes its ease and nothing more. Goth shows a thin pale line at each wrist where the sleeve ends |
| Other examples as main | Passes: cat, cat-dressed, dragon, fennec, fennec-dressed, fennec-winged, koi, lizard, toucan, woman and woman-dressed, 190 images, none differ in any channel |

**Compare,** `./gradlew :app:compare -Pcompare="example=woman-twin-dressed reference=<the reference renders>"`, silhouette IoU, main (parts) / branch:

| Shot | Whole | Head | Torso | Arms | Legs |
|---|---|---|---|---|---|
| beach-threeq | 0.918 / 0.917 | 0.888 / 0.890 | 0.993 / 0.993 | 0.862 / 0.864 | 0.893 / 0.889 |
| lawyer-threeq | 0.896 / 0.889 | 0.860 / 0.859 | 0.913 / 0.908 | 0.884 / 0.866 | 0.905 / 0.899 |
| dress-threeq | 0.843 / 0.836 | 0.894 / 0.904 | 0.750 / 0.758 | 0.799 / 0.638 | 0.874 / 0.905 |
| goth-threeq | 0.927 / 0.928 | 0.898 / 0.904 | 0.994 / 0.995 | 0.887 / 0.883 | 0.905 / 0.906 |
| beach-front | 0.937 / 0.944 | 0.881 / 0.896 | 0.978 / 0.978 | 0.873 / 0.930 | 0.938 / 0.932 |
| lawyer-side | 0.851 / 0.842 | 0.882 / 0.889 | 0.882 / 0.860 | - | 0.791 / 0.790 |
| dress-side | 0.838 / 0.848 | 0.906 / 0.919 | 0.743 / 0.738 | - | 0.883 / 0.906 |
| goth-front | 0.924 / 0.939 | 0.877 / 0.899 | 0.985 / 0.987 | 0.868 / 0.923 | 0.906 / 0.916 |
| pose-wave-lawyer | 0.792 / 0.790 | 0.623 / 0.621 | 0.917 / 0.916 | 0.880 / 0.879 | 0.804 / 0.802 |
| pose-walk-dress | 0.645 / 0.675 | 0.875 / 0.864 | 0.696 / 0.681 | 0.393 / 0.306 | 0.685 / 0.801 |
| pose-walk-dress-side | 0.660 / 0.741 | 0.858 / 0.852 | 0.633 / 0.627 | - | 0.642 / 0.791 |
| pose-walk-goth | 0.843 / 0.840 | 0.880 / 0.880 | 0.982 / 0.983 | 0.663 / 0.651 | 0.838 / 0.837 |
| pose-walk-beach | 0.820 / 0.823 | 0.871 / 0.871 | 0.829 / 0.830 | - | 0.797 / 0.801 |

Close-ups, whole only: face-natural 0.881 / 0.886, face-goth 0.888 / 0.891, face-lawyer 0.851 / 0.855, face-beach-shades 0.883 / 0.883, face-beach-shadesup 0.879 / 0.878, hand-rest 0.792 / 0.835, hand-wave 0.741 / 0.748, foot-beach 0.930 / 0.910, foot-lawyer 0.845 / 0.824, foot-dress 0.902 / 0.905, foot-goth 0.887 / 0.892. **Mean 0.849 / 0.855.**

On the branch the skirt's flare lies within the reference's where main's overshoots it, and the walk's legs show below it, so the dress walk scores higher. The dress shots' arms band is lower on the branch (0.638 against 0.799 in three-quarter, 0.306 against 0.393 in the walk): the plain sleeves are narrower than main's puffed ones and the arm beside the skirt reads less. Other bands lower by more than 0.02: foot-beach and foot-lawyer (0.020, 0.021) and lawyer-side's torso (0.022).

**Drag timings** (`drag`, median of each later move, 90th percentile in brackets, main's parts build after the slash): the bare twin's arm move 145.1 ms (194.6) / 70.8 (86.0), a knee pose drag 57.1 (63.9) / 26.1 (29.0); in lawyer, the arm move 161.4 (195.0) / 171.7 (198.2), the knee pose drag 89.4 (101.5) / 35.3 (40.6). A release takes 209 ms bare and 466 in lawyer, where the parts build's takes about 20.

**Open.**

- The bare twin's moves and every pose drag are slower than her parts build's; the dressed arm move is about as slow. The levers are those of "Drag speed results".
- A hanging skirt shades dark below its upper band, the woman's too.
- The dress's puffed sleeves are plain; a puff would need a sleeve shape of its own over the arm.
- Her brows take the ink colour; a painted eye has no brow colour of its own.
- The locks are shorter than the reference's, to stay clear of the shoulder; a lock that lies on the shoulder needs the push to bend it rather than swing it whole.
- A small notch shows under the chin in profile, where the jaw's blend meets the neck; a wider blend did not change it.

## Garments 2 results

The cat's two outfits made real: a free cape and a round bun, through four general garment features and two cat fixes.

**What is built.**

- **Open branches read their drawings and a range past their part's ends.** A branch drawn, or running more than 0.08 in u past an end of its part (`drawsShell`), reads a shell; a branch part of the way round builds that shell the whole way round and its coverage cuts it to its v range as it cuts the grown run, so the shell is a closed solid in the field and the opening is a level curve. `WornShellTest`.
- **A Front outline is a branch's section** (`ShellReach.section`), drawn in -1 to 1 as a part's is: the sausage is round over the cat's egg-sectioned body, the bun a full roll, the cape a bell.
- **Drawn ends past a level end.** Where a branch's own range runs past an end of its part that does not point down at rest (`endPointsDown`, more than 30 degrees below level), its shell takes the drawing's size there exactly and closes to a point past the drawing, so the cape's trail rounds off; below an end that points down a ring keeps at least the ease, so skirts are as they were.
- **Hanging follows world down.** A branch the whole way round hangs free and flares (`hemFlare`, which reads rings across world down) only past an end of its part that points down (`WornCover.hangsFree`): past a level torso's rump the sausage runs on and no sheet spreads. An open branch with a shell hangs over what comes out of its part (`hangsOver`): in each pose it is pushed out of the legs and the tail on its range along their normals (`hangingPushed`), as a skirt is out of the legs.
- **A garment's own regions and brush strokes are painted** (`garmentShapes`, `GarmentPaints` in the garments node, `SurfaceGarment.paint`), over its colour, kept to what of it is drawn, posed and hidden with it; the mustard is one brush stroke zigzagging along the sausage's top. `GarmentPaintTest`. A garment's marks are not drawn.
- **The cat.** Vampire: a cape over the back and flanks (v ±0.40) from past the rump (u -0.41, where its drawing ends) to the neck, drawn high over the shoulders, flaring over the hips and trailing behind, with a bell section, a 0.35 collar the head comes up through and a slit the tail comes through. Hotdog: a round sausage from the rump to the chest with the mustard stroke, under a puffy bun open over the back (v 0.22 to 0.78) from behind the rump to the chest, its back end round over the sausage's; the legs come out under the bun, which hides them inside it, with no holes. The cat's white is a slot of its own, `white` (entry 15).

**Asks.**

| Ask | Status |
|---|---|
| 1. Open garments read drawings and a range past their ends | Built |
| 2. Hanging on a level body | Built: hanging and flare only past an end that points down; open shells pushed out of legs and tail. A cape's flanks hang as drawn and ride the spine; they are not re-hung toward world down in a pose that tilts the body |
| 3. A garment's marks and regions painted | Regions and brush strokes built; marks not drawn |
| 4. A garment's own section | Built (Front outline). The twin's dress top: not tried; its sleeves are branches the whole way round without a drawing, so a puff needs a drawn Side and Front on the sleeve branch, which now shapes it |
| 5a. Paw 5's toes take the plan's base slot | Does not reproduce: with the cat's white renamed `white`, every paw image equals main's pixel for pixel; the cat's white is its own slot. A few specks of the root's slot remain where three runs' sections meet (entry 15), as on main |
| 5b. Thin lines from the nose bridge to the eyes | Fixed: the cat's top whisker ("Look fixes results") |
| 5c. Eyes near the nose from the side | Source found, not fixed: the eye's anchor (u 0.72, v 0.17) lies inside the muzzle's blend and lands on the muzzle's flank. Moving it back (u 0.64 to 0.68) clears the side view but the painted eye then grows or leaves the front view, so the anchor stays |
| 6. The outfits | Vampire reads as a cape in every view and walk frame. Hotdog reads as a sausage in a bun, mustard painted along the sausage showing between the bun's halves; the bun's edges are smooth level curves, with no leg holes and no sausage end out past it; its rim is a cut edge, not rolled. Small left: a dark dot of ink in front of the chest from the side, and a small hook at the top of each half's front from the front |
| 7. Why the cat is slow | See below |

**Sheets** in the scratchpad's `garments-2/sheets/`: `<outfit>-full` (display pose and rest from the front, three-quarter and side, main above the branch), `-face`, `-walk-0` to `-walk-3` and `-back` (from behind, at rest and in two walk frames) for vampire, hotdog and bare.

**Other examples.** Rendered on main and the branch with `look ... outfits=all poses=all`, compared in RGB: woman-dressed, 78 images, none differ. Fennec-dressed, 22 images, 12 differ by about 1,200 to 3,400 pixels each, every hooded and winter image: the rim of the hood's face opening, the ear cuffs and the sweater's neck lie and shade a little differently, in no direction that reads as better or worse. Rendering is deterministic: the same example rendered twice gives equal images on main and on the branch; an earlier 8 to 33 pixel difference was between two renders at different commits. The differences are the branch's rules. Main took any branch the whole way round reaching past an end of its part at all as hanging free, so a cuff (a branch from -1 in u up its part, as round the ears coming out of the hood and the cap) flared and pushed as a skirt's hem; the branch hangs only a garment's own branch past an end that points down (`hangsFree`). With main's rule put back for cuffs, every hooded image equals main's; the winter images keep about 500 pixels at the sweater's neck, whose drawn branch ends near the torso's neck end, where its shell now holds the drawing's size past the body's end. Woman-twin-dressed, 45 images, 11 differ, every lawyer image: the blazer's range starts below her torso's start (`BLAZER_FROM`), and an open branch running past its part's end now reads a shell there, so its hem lies at the drawn range, a little lower and straight, where main's closed over the torso's end; judged an improvement. The cat: every vampire and hotdog image changes, as built; bare cat images are unchanged.

**Timings and the slowness.** `drag example=<x> drags=move:front-leg-l,pose:front-leg-l:knee runs=9`, the bare cat on the branch against the bare fennec, median of 207 later moves: the cat's front-leg move 142.4 ms (90th 170.8), the fennec's 106.6 (87.5 until shown); a knee pose drag 13.8 against the fennec's. When each node settled, in ms into the move: the cat's surface mesh at 97 against the fennec's 68, its surface at 110 against 78, its paint at 123 against 87, its posed surface at 141 against 87. Two causes: the mesh takes about 29 ms more, which is the paws' digit rows meshed at a quarter cell, the cat's four thick toes merged into a mitten (`merge` 1.4, `toeThickness` 1.15) filling more of that fine region than the fennec's slim toes; and the cat's display pose curls its tail, so after every edit the whole surface is posed again (posed surface and posed surface work, about 18 ms after the paint), where the fennec's display pose leaves its surface at rest and its posed surface settles with its paint. Neither fix is small: the first is a finer-cell budget for merged digits, the second posing only the parts a display pose moves. Not built.

**Open.** A rolled rim for open edges; garment marks; hanging re-read toward world down in posed tilts; a range running on past where its drawing ends, which closes to a point and leaves a speck of ink there (so the cape's and the sausage's ranges end with their drawings, and the dot at the hotdog's chest may be the sausage's); the eye anchor; the paw specks.

## Look fixes results

Five look items read again on main's renders; one fixed, four left open with their sources.

| Item | Source | Status |
|---|---|---|
| The cat's thin lines from the nose bridge to the eyes | Not a contour: the cat's top whisker, a mark on the muzzle at v 0.15 to 0.17, the muzzle's upper flank, which runs back along the bridge's side up to the eyes' inner corners. Left out, the lines go; with the muzzle's run blend widened (`RUN_BLEND_SHARE` 0.2 or 0.3) they go too, but every mark anchored on the muzzle moves and the whiskers cross the eyes, so the blend stays | Fixed in the fixture: the top whisker runs level along the whisker pad, 0.04 above the middle one (`CatFace.kt`). `CatFaceMarksTest` holds every whisker point more than 0.03 from the eyes' lids (0.034 now, 0.024 before) |
| The woman's lines across the forehead after a release | Not reproduced: `look` renders of an arm's Move drag, each of its 24 moves played, then the release, the release at rest, undo and redo, on CPU and GPU reads, show the hairline and brows alone. Not the cat's kind of line, which was a mark | Open: needs the app's steps that show it |
| The woman's winter scarf as a cowl, and the sliver under the coat's collar | The scarf's torso branch (0.92 to 1) stands off the coat's ease and the dark top's as well as its own (`fittedOver`), 0.092 in all, and grows that far over the torso's closed end, which stays closed because the scarf covers the neck too; the coat under it is open at that end and cut short of its plane, so past the plane the scarf stands 0.047 further off than anything under it, and rises round the neck. The sliver is the dark top showing between the coat's cut edge and the scarf | Open: needs a stand-off that holds only where the layers under it reach, a member grown by less past an open end of an under layer |
| The fennec cap's ear cuff, ragged from the side | Unchanged by the garments 2 round's hanging rule: the side view still shows red shards up each ear's root | Open |
| The cat's eyes near the nose from the side | The eye's anchor (u 0.72, v 0.17) lies on the skull's front, which the muzzle joins. Moved back to u 0.66 with its facing turned 20 degrees toward the front, the eyes leave the front view; resolved along the skull's own normal rather than the field's gradient, they grow rounder and further apart from the front and sit no further back from the side | Open; the anchor stays |
| Paw specks where three runs meet (example-limits entry 15) | Not tried | Open |

**Images changed,** `look example=cat,cat-dressed outfits=all poses=all close=face,foot` against main, compared in RGB: 28 of 32 differ, every cat and cat-dressed view and face (718 to 1,497 pixels each, the top whisker); the four foot close-ups are equal. No engine code changes, so no other example's images change.

**Sheets** in the scratchpad's `look-fixes/sheets/`: `cat-face` (front, three-quarter and side, main above the branch), `cat-full` (display pose and walk-1), `cat-dressed-face` (vampire and hotdog), `cat-whisker-source` (main, the top whisker left out, the wider run blend, and the two eye tries), `woman-release-not-reproduced` (an arm's Move drag at its last move, after the release, at rest, after undo and redo, and on GPU reads) and `open-scarf-and-cap` (main's winter woman and winter fennec faces).

## Risks and open questions

| Risk or question | Recommendation |
|---|---|
| The contour on a dual mesh steps at cell scale where the grid runs across the silhouette, where lofted rings ran along it | Judge on the proof's sheets. If it shows, place vertices by relaxation before projection, and if still needed run a pass of isotropic remeshing near the silhouette; failing that, interpolate the contour across faces from per-vertex facing (Hertzmann and Zorin, 2000) and cut the fill to it |
| The paws' toes and the fingers need finer cells than the body | Digits mesh at a quarter of the body's cell (Adaptive cells results) |
| A torso edit misses 50 ms | Decision 4; member channels make the full-detail rebuild after the drag about a whole build |
| Linear blend skinning collapses a fillet at deep bends | Judge the walk in the proof; implicit skinning in phase 6 |
| Coordinates past a member's end ring (a fillet below a leg's first ring) are an extrapolation | They are only read where the member has a share, which ends within its blend; check paint on fillets in the proof |
| A swept solid is only near a distance where a run swells or bends hard, which loosens block tests and projection | Keep the 1.5 half-diagonal margin; two projection steps; the spike's thin-triangle share stayed under 1.5% |
| Two sheets of surface in one cell (toes, fingers, a tail beside a leg) | Manifold split from the start |
| Regions drawn across parts are cut by surface curves, a new kind of cut | Build it in phase 2 against the fennec's cream and socks; keep within-part shapes on the coordinate cut, which is the common case |
| Field reads dominate | Member channels and projection from corner values; field-read speedups from the fennec test kept |
| Garments over a pose: the garment is skinned, not meshed again, so a deep bend can push skin through it | As now: garments keep about half their offset on the inside of a bend; hiding runs again per pose |
| A hair shell is not built yet | When it is, it is a garment-like offset of the head |

## Docs that change if this is approved

These state the opposite of this plan and are rewritten in phase 5 (the HLD) and on approval (the plan document): the decided Part representation item; the data model's introduction (one representation, fixed topology); Fixed topology per template version; Where parts meet, where a socket's border stays inked by default, which this plan keeps; the shared-format paragraph; Surface work stays attached; and face marks switching angle versions, which the painted eyes replace. Each part is still described by a few drawn curves: outlines become slices and slices sweep a solid, and a stored (u, v) keeps its meaning. Only the generated mesh stops having a fixed topology.

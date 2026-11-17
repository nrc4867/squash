# The surface

A character's body is one seamless surface, meshed from the joined solids of its parts. Each part's drawn outlines sweep a solid, each solid joins the solid of the part it sits on by a smooth union, and the whole is meshed at once. A join has no edge, no seam and no buried point. Parts are not meshes of their own; they are coordinates on this surface. Thin and hard parts are the exception and stay meshes of their own. [data-model.md](data-model.md) holds the entities, [architecture.md](architecture.md) the graph, the field reads and the cell levels, [look.md](look.md) how the surface is shaded, coloured and inked.

## The field

The character's field is `JoinedField` over its members, in `geom/field`, built by `CharacterField` in `character/surface`.

- **Members are runs.** Each swept run of a part (a head's skull and muzzle, each digit of a paw or hand) is its own member, a `SweptSolid` read from the run's slices. A part's later runs join its first run by the part's `merge` parameter, else a share of the least extent of the joining run's box (`RUN_BLEND_SHARE`), and the part's first run joins the first run of the part its socket is on by the socket's blend: `PartInstance.blend`, else a quarter of the run's least box extent (`SOCKET_BLEND_SHARE`). Siblings never blend: two legs, or a tail and a hind leg, meet by a plain union.
- **Cuts stay inside a part.** A part's own cut, such as a paw's flat sole, is a smooth intersection applied to that part's field before the union, so it flattens nothing else.
- **Each member's field is distance-like**: negative inside, near the true distance close to the surface. It is read only near the surface.
- **Which parts are in the field.** A part whose template says `"surface": "field"` (the default), that no garment owns, and each of its swept runs that no block of its template marks `separate`. A part marked `separate` builds as its own mesh.

## The mesher

`AdaptiveMesher` (`geom/surface`) is a dual mesher on a sparse grid of blocks of 8 by 8 by 8 cells: one vertex in each cell the surface crosses, one quad across each grid edge it crosses, a manifold split for a cell that holds two sheets of surface (two toes in one cell each get a vertex). A vertex starts at the mean of its cell's edge crossings, is relaxed toward its neighbours inside the cell, and is projected onto the field along its gradient. The quads are near-square and the vertices inside their cells, which is the mesh the contour and the colour cuts read. The methods are those of surface nets (Gibson), dual contouring and its manifold form (Ju, Losasso, Schaefer, Warren; Schaefer, Ju, Warren) and, for the contour, Hertzmann and Zorin.

- **The cell** is `CELL_SHARE` (0.05) of the least half-extent of the root part's first run, so it does not change with what the character wears; the fennec's body meshes at 0.012, about 35,000 vertices. Digits take a quarter of it (see Cell levels in architecture.md).
- **Blocks are the unit of an edit.** A vertex is identified by its cell, so a block an edit did not reach keeps every vertex and every per-block cache. An edit meshes again only the blocks within the changed members' old and new boxes grown by their reach, and each block keeps every member's own field at its grid points, so only the changed member is read again and the union recombined (`BlockReuse`, `ChannelSource`).
- **A coarse surface while a gesture is open.** While a part is dragged the surface meshes at twice the cell (`COARSE_FACTOR`), and the full surface follows when the gesture ends. A gesture that leaves the placed character as it was, such as a pose drag, keeps its full mesh.
- **Reads** go through the `FieldReads` seam, on the CPU or on the GPU (architecture.md, Field reads). Batches of blocks are meshed in parallel on the graph's dispatcher and a stale run stops between batches.

## Parts as coordinates

Every vertex of the surface holds, for the at most four members with the largest shares of the surface there (`SurfaceShares`, `VertexCoordinates`):

- **Its share**: how much of the surface there is that member's. Across a smooth union of a child with its parent and blend k, the child's share is the union's own mixing weight, clamp(0.5 + 0.5 (d_parent - d_child) / k, 0, 1), and the parent holds what its children leave. Shares sum to one, and change exactly where the surface changes from one part's shape to the other's. A part's share is the sum of its runs'.
- **Its place on the member**: u along the member's spine and v around it, found from the member's swept solid, which gives for any point the two rings whose planes hold it, how far it lies between them and the direction round the ring. Past an end ring u carries on along the end ring's axis, so a point on a join below a leg's first ring reads a u a little under 0.

A stored anchor is `(part, patch, u, v, offset)`. The patch is the member: a run by its name (`body`, `muzzle`, `pad:<name>`, a digit `toes:i`). u and v are template coordinates, defined by the rings swept from the drawing, so a redraw moves the surface and leaves each anchor meaning the same place on the part. Nothing stores a vertex, a cell or a mesh; every vertex's coordinates are computed again from the solids.

**An anchor becomes a position on the field, not on the mesh** (`FieldAnchors`): it starts at its point on its member's own swept surface at (u, v), moves along the joined field's gradient to where the field is zero, and off by its offset along the normal there. On a part's own share it lands where it would without the join, moved a little by the blend; where parts blend it lands on the blend. Resolved surface work therefore does not wait on meshing. Picking goes the other way: a ray hits a triangle of the surface, the part is the member with the largest share at the hit, and the anchor is that member's (u, v) interpolated across the triangle, with v unwrapped across its seam and a pole read in a small flat chart round it.

Sockets and landmarks read the parent's own swept surface at (u, v), so placing parts does not wait on the character's mesh.

## Surface work on the surface

- **Brush strokes** measure their radius and fade by distance in space. A stroke resolves its anchors on the field, and each vertex's opacity is its distance from the resolved curve, cut along each triangle where that distance crosses the radius. No chart is involved, so a stroke crosses a join as it crosses the middle of a part.
- **Shapes within one part** (areas a template names, pads, nails, beans, regions drawn on one part): each vertex where the part has a share takes its signed distance from the shape in the part's own (u, v), scaled to body units by the part's length per u and its girth, and the shape is cut where that crosses zero. v wraps across its seam; near a pole the test is made in the pole's flat chart.
- **Shapes across parts** (a sock from paw to leg, a face mask from muzzle to skull): the border's anchors resolve onto the field, the curve between them is laid on the surface, and the mesh is cut along it, the inside the side the border's order gives.
- **A part's base colour** is the part's own section: everything where its share is the largest, cut along the curve where its share equals its neighbour's.
- **Marks** resolve their anchors on the field and lay the curve between them on the surface. A floating stroke keeps its frame from the part's coordinates at its root.
- **Fur** roots resolve as anchors; grain reads the owning part's frame at the root, blended by share across a join; the fur rule's spacing walks the surface.
- **Line weights** act by distance in space. **The face map** casts its rays at the field.

## Ink

- **The contour** is found on the character's mesh, the garments' meshes and the separate parts' meshes, along edges between faces toward and away from the camera, interpolated across faces from per-vertex facing. The body has no buried corners, cast points or end caps, so no exception for them exists. The ribbon's smoothing of the edge path applies.
- **Joins** are inked by default: the line is the curve where the two parts' shares are equal, eased and faded where the surface turns away. The joined part's `join.ink` parameter set to 0 turns it off.
- **Separate parts** meeting the body draw the overlap crease: the curve on the part where it leaves the body's field.
- **Hems and openings** are a garment mesh's open edges, which lie on smooth level curves of its coverage; their ink is on by the garment's hem switch.
- **Region borders** are their cut curves.

## Shading

A vertex's shading normal (`SurfaceShading`) is each owning run's own shading normal at the vertex's (u, v), read from that part's slices and weighed by the run's share and by how near the vertex lies to the run's own surface. Where a run's own shading holds a vertex whole, the vertex shades exactly as that part does; across a join the joined surface's own normal takes over, so two parts' normals are never mixed into a normal of neither. Normals are then smoothed along the quads (`ShadingSmoothing`), more across joins than inside a part. The shadow edge therefore runs over a join as one curve. Far-part darkening is a per-vertex amount, the shares of the paired parts on the far side of the body's plane (`CharacterSurface.positiveZ` and `negativeZ`), so a far leg darkens and its join with the body ramps.

## Eyes

An eye is painted on the head's surface (`PaintedEye`, `SurfaceEyes`), a parametric eye whose shapes are made from an `EyeLook` instead of drawn per view.

- **Where it sits.** Its middle is an anchor on the head, resolved on the field, and its facing is the surface normal there turned `lookOut` degrees outward. The head's eye landmark is the default place; the panes' handles move, size and turn it.
- **How it is painted.** Each shape is an oval in the plane square to the facing, its chart running along the head's coordinates toward the front and the top, and each vertex facing the eye is projected along the facing into that plane and painted where it falls inside. The paint foreshortens and wraps as the head turns and never stands past the head's outline, since nothing stands off the surface.
- **Its look.** Lengths are shares of the head's own u and v at the eye's middle, so an eye keeps its look on a larger or smaller head: opening width and height, lower lid and front narrowing, opening tilt and whole-eye tilt, look out, iris size, ratio and offset, round or slit pupil, highlight size and offset, and a lid line, a lash flick and a brow as ink lines. The white, iris, pupil and highlight name palette slots, and an optional front look takes over as a view turns to the front (`fromFront`).
- **Gaze** moves the iris, pupil and highlight within the opening in the eye's plane, clipped by the opening: `gazeForward` and `gazeUp` are shares of the opening's half width and half height.
- A mirrored eye is painted again on the other side of the head's plane, its anchor's v read as 1 - v.

## Thin and hard parts

Some parts are thinner than any useful cell, or need edges the field rounds off: ears, membrane wings, fins, loose ends and hair's locks and clumps (thin); claws, nails, horns, a beak, teeth and parts a garment owns (hard). A template marks such a part, or a block of it, `"surface": "separate"`. A separate part is a mesh of its own built by its blocks (a lofted run, `membrane`, `pad`), placed at its socket, read on the parent's own swept surface and sunk into the body by the socket's sink, so its root lies inside the body's field and the body hides it. Its ink is the overlap crease. Its rig is its own bones, with the parent's bone fading in toward the root. A pad with a thickness, such as nose leather, is a small mesh laid on the surface.

## Creatures

A creature needs no join rule, only parts with solids, blends at its sockets and a separate mark on the parts that are thin or hard.

- **Paws and feet** (`paw`, `foot`): the palm and each toe are members of the part, the toes joined to the palm by `merge`, so a mitten is a large merge and separate toes a small one. Claws are hard parts on the toes' tips. Beans and the main pad are areas in the part's own coordinates, one chart over palm and toes. The sole's flat cut applies to the part's own field.
- **Hands** (`hand`): fingers are members joined to the palm by the web's blend; nails are hard parts or painted areas on the fingertips.
- **Hair**: `hair-cap` is a run in the field riding the head's bone; `hair` parts (a fall, locks) are separate meshes pushed out of the body in each pose.
- The dragon's wings, the koi's fins and tail fin and the toucan's wings and tail are thin; the toucan's beak and every horn and claw are hard.

## Garments on the surface

A garment covers parts over ranges of their coordinates, as a branch does. Its surface is the zero level of g(p) = min(f_covered(p) - ease(p), drawn(p)) (`GarmentField`, `SurfaceGarment`).

- **f_covered** is the joined field of the runs it covers, with their blends, narrowed in the garment's ease so a sleeve stays apart from the body under the arm. A sleeve joins the body's run as a leg joins a torso.
- **ease(p)** is the branch's ease at the vertex's coordinates on the part under it. A drawn Side or Top outline that stands off further than the ease becomes a swept solid of the branch's own (drawn(p)), joined by a plain union, so a skirt hanging free from the waist is the drawn solid below the body.
- **Layers**: an outer garment's covered field is the inner garment's surface, so eases add.
- The garment is meshed by the same mesher over its covered region, and each of its vertices carries the body's coordinates at the point it grows from.
- **Coverage** (`Coverage`) is one smooth function on the garment's surface: for each run the garment covers, the signed distance, in body units, inside its u range (and its v range for a branch part of the way round), and for each part it does not cover, minus the distance into that part's share; the share-weighted sum is zero along the hem, the neck opening and the cuffs, which therefore follow smooth curves and not cells. Where a part comes out through the garment, its share opens the hole and a cuff is a band beside it whose ease grows by the cuff's size.
- **Hidden skin** (`HiddenSkin`): a body triangle is hidden where its corners lie inside the garment's coverage by more than the hiding margin, so skin seen through an opening still shows. Posed, the test runs again on the posed meshes.
- Owned parts and loose ends are separate parts.

## The rig on the surface

A vertex's weights (`SurfaceSkin`) are each part's share of it times that part's own weights at the vertex's (u, v). Across a join the shares give the parent-to-child blend. A vertex keeps at most four bones. Only runs in the line of joins through the vertex's own run count (the runs it grows from, those growing from it and those growing from the same run), so a vertex on one paw never follows the other paw however near it lies. A hair vertex follows only its own run and the part it sits on. A part's own weights are the rig's for that part, with its joint and seam bands and its painted seams (data-model.md, The rig). Garments take the body's weights at the coordinates they grow from; separate parts keep their own.

The rig follows the assembly, so an edit builds weights again for the new mesh and a pose only skins. Skinning is linear blend on the CPU and gives a posed mesh with the same vertices in the same order, which everything reading the pose (posed surface work, lines, fur, colour cuts, hiding) reads by vertex. Quads whose corners belong to runs no line of joins relates are contacts, and a pose that parts them leaves them out.

Implicit skinning (Vaillant, Barthe, Guennebaud, Cani, Rohmer, Wyvill, Gourmel, Paulin, 2013) is the method held for deep bends if linear blend collapses a join: skin by the bones, then move each vertex back onto the zero level of the posed members' fields composed by their joins. Each member's solid moves rigidly with its bone, so the posed field is the same field read in each bone's space.

## The derived-data graph

| Node | Holds |
| --- | --- |
| Slice row (per part) | Drawing to slices |
| Part mesh (per part) | The part's patches as a lofted mesh: what separate parts draw, and what sockets, anchors and the field's solids are swept from |
| Assembly | Parts placed by their sockets on each other's swept surfaces |
| Character field | Members, blends and parents from the assembly, keyed by each member's solid and place |
| Character mesh and surface | The mesher's blocks and member channels; vertices, quads, coordinates, shares, shading normals, lines (`characterMeshNode`, `characterSurfaceNode`) |
| Garment surfaces | Per worn garment, its field and mesh over the covered blocks and its coverage cut, innermost first |
| Surface work | Anchors resolved on the character field; it does not wait on the mesh |
| Colour | Shapes cut on the surface mesh per block, by coordinates or by surface curves |
| Lines | The surface, the garments' meshes and the separate parts' meshes |
| Rig and posed | Weights per surface vertex from shares and coordinates; the posed surface, garments and separate parts |

A vertex's identity is its cell, so a block an edit did not reach keeps every vertex and every cache kept per block.

## Template sampling

A field template's `topology` block (`rings`, `segments`, `focus`) sets how finely the drawing is sampled into the part's slices and so into its solid; it is not the mesh's topology. Changing the counts changes the solid only by sampling, so a template version that changes them does not move anchors, which are template coordinates and not vertices. `focus` samples a stretch of u more finely, as a face needs. A separate part's counts are the grid of its own mesh.

# Data model

One representation runs the whole pipeline: a part is an axis with a row of slices. The outlines size the slices, the mesh is generated from them with fixed topology, weights and marks address them, the body curve re-samples them, and contact flattens them. Every entity below is immutable Kotlin data in `commonMain`. Storage maps it and never shapes it.

## Entities

| Entity | Holds | Kind |
| --- | --- | --- |
| Part template | The recipe for a part type: axis, open end, ring and segment counts, blocks, parameter ranges, joints, sockets, tagged areas, which view sets what | Shipped data file, versioned |
| Body plan | A graph of sockets from a root spine, pair ids for mirrored sockets, the rest pose | Shipped preset, versioned |
| Character | Body plan, part instances, the data each mirrored pair shares (outlines and params by pair id), slots, palette, face sets, and the library version it came from | Document; also its own library file |
| Part instance | Template id and version, the socket it sits on, its own outlines and params while unpaired, pair id, side and that side's overrides | Document |
| Surface anchor | A point on a part's surface as (part, patch name, u along the axis, v around it, offset) | Document, inside paint, marks, line weights and touch-ups |
| Mark | A polyline of anchors, a width profile, an ink flag, per-angle versions keyed by camera bin, and the expression set it belongs to | Document |
| Patches | One or more named surfaces per part, each a fixed grid of points, closed in v (a tube) or open (a sheet); a swept patch also keeps its slices: for each u a frame on the part's midline, height, width, a closed section spline and a proxy normal | Derived |
| Part mesh | One grid per patch; every vertex tagged with part, patch, bone, u and v | Derived |
| Rig | Bones from template joints, weights seeded from part ownership and blended only at seams, stretch weights | Derived |
| Pose | Per-bone transform and stretch, the body curve kept as a curve, the planted set, touch-up deltas in anchor space | Document, one per shot key |
| Palette | Named color slots per character (fur, cream, pads, nose, inner ear, ink by default); parts, regions, paint and template color fields name a slot, never a color | Document, on the character; defaults from the body plan |
| Mark group | Marks with named roles, a draw order, clipping between roles, and controls (open, expression blend, dilation). Eyes and mouths are mark groups | Document |
| Eye | A mark group on an eye region or a sphere part: travel limits, pupil shape, convergence limit, camera bias, size compensation, lid-flatten flag | Document, on the character |
| Gaze | Target (scene point, camera, an anchor on another character, or free), per-eye offsets, open and dilation values, lead frames, auto-blink flag | Document, one per shot key beside the pose |
| Expression set | Marks per role with angle versions, plus a small face pose (ears, jaw, brows) | Document, on the character |
| Grain | A direction field in anchor space per part, default along the axis; combed overrides stored as vectors | Document, on the part instance |
| Effect mark | A mark pinned to a landmark or drawn in camera space, keyed over time; sweat drops, shock lines, blush, speed lines, tears | Document, per shot |
| Scene | Set, backdrop, light, shading limits | Document |
| Shot | Camera, keys (poses by time), timing per character | Document |
| Project | The cast (character copies, each with its library version) and scenes | Document file |
| Library | Characters, later objects and sets, each as a chain of versions | Library files |

Identity and versions: every entity has a ULID. A part instance pins its template id and version, and a character pins its body plan version, so a shipped template can change without silently changing saved characters. A template version that changes its ring or segment count is a migration, since anchors would otherwise land on different vertices.

The top three layers (library, project, character and what they hold) are document data. The derived entities are rebuilt from them and cached by inputs. Every piece of surface work reaches the mesh through an anchor, which is why paint, marks and touch-ups survive a refit.

## Part template and part instance

The template is the recipe, in `character`, and the instance is what the user drew, in `document`. Slices are derived from the two and never saved, so a saved character is only outlines, parameters and anchors.

```kotlin
// Shipped with the app, versioned and immutable: a changed template is a new version file.
data class PartTemplate(
    val id: TemplateId, val version: Int, val name: String,   // "leg"
    val axis: AxisSpec,                // rest direction and optional bend before anything is drawn; the built axis is a spline through the Side midline
    val openEnd: End?,                 // the end that sits on the socket plane; null for a closed part
    val rings: Int, val segments: Int, // fixed topology of the body patch: rings along u; segments = control points of every section spline
    val blocks: List<Block>,           // Tube, Cap, Skull, ToeRow, Pad, Joint, Socket, Area, Landmark; joints, sockets and areas are blocks
    val params: Map<String, ParamSpec>, // toes 0 or 3..5, spread 0..40 deg, size; a new parameter's default reproduces the old build
    val views: Map<View, Sets>,        // SIDE sets height and the axis, TOP sets width, FRONT sets the cap or the section
    val section: SectionProfile,       // default closed section spline along u (circle, egg, keel, lens), used where no view sets it
)

// A block owns its behaviour, so a new block kind is one class and a regenerated schema.
// Nothing downstream switches on block kind; it reads the patches, joints, sockets and areas blocks produce.
sealed interface Block {
    fun build(ctx: BuildContext): List<Patch>
    fun lint(ctx: LintContext): List<Finding>
    fun joints(): List<JointSpec>
    fun sockets(): List<SocketSpec>
    fun areas(): List<AreaSpec>
    fun run(): Span?                   // the stretch of the axis its run of slices covers, if it builds one
    fun patchNames(): List<PatchName>  // the patches it builds, for the lint
    fun capShape(end: End): CapShape?  // the shape this block closes an end with, if it closes it
}

// In the document. Everything the user chose for one part.
data class PartInstance(
    val id: PartId,
    val template: TemplateRef,             // id and version, pinned
    val socket: SocketRef,                 // parent part and socket name; null for the root torso
    internal val own: PartData,            // the part's values while it has no pair; empty while it has one
    val pair: PairId?, val side: Side?,    // the side stays when a pair breaks, since the part still sits there
    internal val overrides: PartData,      // this side's values, replacing the ones they name in the pair's; empty with no pair
    val grain: Grain,                      // direction field in anchor space, default along the axis
)

data class PartData(
    val outlines: Map<View, OutlineCurve>, // cubic Beziers in the part's own axes, open at the socket end
    val params: Map<String, Float>,
)

data class Character(
    // ...
    val parts: List<PartInstance>,
    internal val pairs: Map<PairId, PartData>, // what each pair shares, held for exactly the pairs its parts name
) {
    fun resolved(part: PartInstance): PartData // the pair's values with the side's overrides on top, or the part's own
}

// Derived. A part is one or more patches; the mesh is a pure function of them.
data class Patch(
    val name: String,                  // "body", "membrane", "pad:palm"; anchors name it
    val nu: Int, val nv: Int,          // fixed grid for this template version
    val closedV: Boolean,              // a tube is closed in v, a sheet is open
    val slices: List<Slice>?,          // present for swept patches: the body, a toe, a muzzle
    val points: FloatArray,            // nu * nv * 3
)
data class Slice(
    val u: Float,
    val frame: Frame,                  // centre and orientation: on the midline in the plane a = constant when drawn, on the axis spline when not
    val height: Float, val width: Float,
    val section: ClosedSpline,         // exactly `segments` control points, sampled at fixed v, scaled by height and width
    val proxyNormalFrame: Mat3,        // along the axis spline's tangent at u, whichever way the ring stands
)

// Where surface work lives. Survives refits because patch, u and v are template coordinates, not positions.
data class Anchor(val part: PartId, val patch: String = "body", val u: Float, val v: Float, val offset: Float = 0f)
```

Rules the code keeps:

- **Outlines live in the part's own axes.** `a` runs along the rest direction from the socket end, `b` toward v = 0 (body up, or body forward for a part whose rest direction is within 45 degrees of vertical), `c` toward v = 0.25. A Side outline's points are (a, b), a Top outline's (a, -c), a Front outline's (c, b) looking down the axis. Patches are built in the same axes, in body-space orientation, with the socket plane at a = 0; see Placing parts. `PartBuilder.axes` gives the axes and `PartAxes.pointOnView` a view point's position.
- **Views set slices, nothing fits.** u runs linearly over the Side outline's range along a; an open start begins at the socket plane instead, and an open end finishes there. At each u a slice lies in its plane a = constant, and the outermost two crossings of that line give its centre (their midpoint) and height (their distance), so the rings' ends are the drawing's crossings: a drawn Side outline is the mesh's silhouette, up to the straight run between one ring and the next. Rings stand on parallel planes, so they cannot cross however quickly the midline moves, and the axis follows the drawing, so a digitigrade leg bends at the hock because it was drawn that way. An open outline counts as closed by the chord between its ends. A tip is a crossing with no gap, so a ring there has no height beyond a floor of 1/500 of the length, and a closed end's last ring reaches the drawn tip. A row of toes stands on the same planes. An undrawn part's rings follow the axis tangent. The Top outline's crossings give width only; its midpoint is not read. The Front outline shapes the cap, blending in over the last sixth of the axis, or, where the template says `section`, the whole cross-section, which is how a chest gets a keel or a flat side. A missing view takes the template's section profile and the other view's extent, so the section keeps its proportions; the view present rules u. A Front outline alone changes the section and leaves the sizes to the template. `axis.length` sets an undrawn part's length, and the `size` parameter multiplies both that and a drawing, so a locked pane's map divides a stroke by it and the stroke builds under the pointer at any size. An outline with no extent, or wholly behind the socket plane, sets nothing.
- **A spine and width writes outlines and nothing else.** A centre stroke in Side and a half-width along it make a Side outline from the spine's offsets and a Top outline with that width along a, through one `SetOutlines`, so one undo step. The stroke takes Draw outline's Stabilizer and Snap to guides: it trails the pointer on the same string, and its far end, the end away from the socket plane, catches a guide while the rest of it stretches to follow, so no other point of a centre line is pulled onto a guide that describes the part's edge. The width drag follows the pointer directly. The spine is not stored; what remains are ordinary curves. A closed end rounds off to nothing within its own reach, so Side and Top close where the spine does, and Top takes Side's reach, so the two agree where the part starts and ends. An offset that doubles back across planes a = constant is written as drawn; the preview marks it, since the outermost crossings are all a slice reads.
- **A drawn view holds when another is drawn.** Only Spine and width writes two curves in one command, and a stroke on one view leaves the others' curves alone. A build reads Side's and Top's extents at each a, whichever rings land there, so a drawn Side or Top keeps its silhouette over the reach they share. Past Side's reach the part stops, so a longer Top is cut there and marked as a disagreement, and past Top's reach it keeps Top's last width. A Front keeps its shape, and Side and Top rule its size. A paw's waist before its toes sits at a fraction of its length, so a Side of another length slides it.
- **Refits are local.** A slice reads only the crossings at its own u, so changing a span of a Side or Top outline moves only slices within one table station (1/128 of the length) of that span. A slice's proxy normal frame reads the axis tangent, and so its neighbouring knots, which reaches two axis knots (1/32) further and moves no vertex. Two edits reach further by their nature: moving the outline's far end along a rescales u for every slice, since the ring count is fixed, and a Front `section` outline applies to every slice.
- **Everything is a spline.** Outlines are cubic Béziers. The axis is a C1 cubic spline through the Side midline at fixed knots, with Catmull-Rom tangents so that moving a point changes the curve only within two spans of it, and it stays in the plane of a and b. Joints and an undrawn part's rings sit on it; a drawn ring's centre is the midline itself, and the tangent sets only its proxy normal frame. Every cross-section is a closed cubic B-spline with exactly `segments` control points, sampled at fixed v, so a circle, an egg, a keel and a drawn chest outline are one type with different control polygons, and the mesh topology never changes. A part is a lofted surface: sections stacked along the axis.
- **Views disagree where one passes another's extent.** Side and Top compare where the part starts and ends along u, Front compares against Side's height and Top's width, and a Side narrower than Front never counts, since a cap is meant to be smaller. The highlight marks the stretch past the limit, which is a comparison of intervals per stroke.
- **Fixed topology per template version.** Ring i maps to u, segment j maps to v, so an anchor reaches its vertex neighbourhood by arithmetic. Changing rings or segments is a new version and a migration.
- **Open ends close at the socket plane.** The open end's slice sits on the parent's surface at the socket, and the parent's slices are untouched. A stroke that closes anyway is cut at that plane. A closed stroke short of the plane opens around its nearest point, no deeper than it lies from the plane, and reaches the plane along x from there. An open stroke's ends reach the plane along x; if that would cross the stroke, the stroke counts as closed by its chord and opens the same way. With Close end off a stroke's ends stay where they are, and only what passes the plane is cut.
- **Smooth runs from a light blur to a strong one.** The filter's passes run as 8 per unit of Smooth up to the default, 45%, then climb as a cube to 512 at 100%, a blur of 32 screen pixels standard deviation on samples 2 pixels apart, whatever the zoom. A stroke's corners hold still through the blur only if they are sharp across 4 samples and still turn 60 degrees across a window as wide as the blur, so jitter and round bends are taken and a real corner stays.
- **The Smooth tool brushes an outline in place.** A drag works on the curve as samples 2 screen pixels apart, each dab pulling those inside the brush toward a blur along the curve as wide as half the brush's radius, by the strength and how near the brush's centre they lie. Every section outside the stretch that moved keeps its values, the sections at its edges are cut where it starts and stops, and the stretch is fitted anew from the old curve's directions there, so the outline can gain or lose sections. An open outline's ends do not move and its new points keep to the kept side of the socket plane; a closed outline's seam moves as one point. A corner inside the brush rounds. A straight run has nothing to move and writes nothing.
- **A held stroke becomes a clean shape.** A stroke whose pointer rests is read as a line along its chord, or, if it comes back to its start, as the ellipse of the same area moments or the capsule on the same axis, whichever it strays from least, and stays freehand if it fits none. Where a view has a socket plane and Close end is on, an open stroke whose ends both pass the plane or come within closing reach of it, with nothing else of it on the part's side, is read as the ellipse or capsule that a cut at the plane would leave: the line from the middle of its mouth through the centre of what it encloses is a diameter of the ellipse and the axis of the capsule, and the ellipse's centre is searched along it, so a U that flares or narrows towards the plane still reads. The whole shape reaches behind the plane and takes the usual cut. Short of the plane, in Front or with Close end off, such a stroke stays freehand. The rest is timed around the wait for the next pointer event. Until release the pointer moves a line's end or sizes a closed shape, and the shape then takes the path a stroke takes, so its ends are handled alike and it is stored as an ordinary outline.
- **Start from template makes an ordinary outline.** It reads the outermost crossings of the part's swept patches, as built with nothing drawn in that view, and fits them as a stroke is fitted, so what follows is an edit like any other. An outline sets only what its view reads, so the view's silhouette comes back within tolerance and the others can move: a Top alone sets a straight axis, which loses a muzzle's drop in Side; a Front sets only a section's shape; and a drawn paw with toes shares its width among them where undrawn ones fan out, with a waist where its tube closes. A paw with no toes comes back as it was.
- **Heads are blocks too.** Skull is a block whose slices run front to back, with a muzzle Tube and eye and ear sockets. Face marks anchor to Skull's u and v like any other mark.
- **Mirroring is shared data plus overrides.** A pair stores one set of outlines and params; each side's overrides replace the values they name. `Character.resolved` is the only way to read a part's values, since the fields behind it are internal to `document`, and it is what the slice row node builds from, so both sides rebuild when the pair changes and a part rebuilds only when what it resolves to changes. `SetOutline` and `SetParameter` write the pair for a paired part, or with `thisSide` that side's override; clearing an override shows the pair's value again. Breaking a pair copies the shared data, under each side's overrides, into both and clears the pair id; each part keeps its side. A pair's data is created with its first part and goes with its last.
- **Poses stay re-editable.** A pose keeps the body curve as a curve and the planted set as flags, so re-sampling after a character update uses the same inputs. Touch-ups are deltas in anchor space, which is why they survive Update from library.
- **Detail beyond the sweep is anchored, not modelled.** Branching shapes are other blocks or parts. Grooves and muscle lines are a later shape touch-up layer: push and pull offsets stored in anchor space at build time, the same mechanism as per-shot touch-ups, so they survive refits.

## Placing parts

One graph node assembles the character from the built parts, so `Settled` covers it. Nothing is stored: where a part sits is derived every time from its socket and its parent's built surface.

- **The root sits at the origin**, along the plan's rest, which lint holds equal to its template's. Body space has +X forward, +Y up, and -Z the left side.
- **A socket reads the built surface.** A child's socket plane sits at its socket's (u, v) on the parent's built patches, so a redrawn parent carries its children with it. A paw on a shorter leg rises with the leg's end.
- **A part sits slightly inside its parent**, so no gap shows. It sinks back along its own axis, or along the surface's normal where the socket says `outward: normal`, by a fraction of its template's first ring, or of its widest where it has no open end. The depth reads the template, never the drawing, so a stroke on a part cannot move the plane it is drawn from.
- **Placing is translation.** A part keeps its rest direction whatever it sits on; only pose rotates it.
- **A part can be moved from where its template puts it.** The Move tool writes parameters under `move.`, so pairs, overrides, undo and saving treat them like any other. A socket's place is stored on its parent, `move.socket.<name>.u` and `.v`, which is why an empty socket and the planned part on it move too. A single parent has one socket for both its children, so with Mirror off a side writes `move.socket.<name>.left.u` and so on, which win over the shared place, and a move of both clears them. A stored v always reads as the left side's. A part's own angles are `move.outward`, away from the body's plane on the left and mirrored on the right, and `move.lean`, toward the part's up, each up to 60 degrees. They turn the template's rest axis before anything builds, keeping the up it had, so drawings keep their meaning, the panes tilt as they do for an ear or a tail, and the topology does not change. `move.joint.<name>` sets where a joint between a part's ends sits. An undrawn part's bend follows the joint on it, and a drawn part keeps its silhouette, its rings shifting along it to keep one on the joint.
- **The right side mirrors across the body's plane.** Z and the triangle winding flip, and the socket's v reads 1 - v on an unmirrored parent. A mirrored parent already reflects what sits on it, so a socket on it keeps its v. Both sides build from the one `Character.resolved` of the pair.
- **An empty socket shows what it would take.** The plan's empty sockets each place a planned part, its first `takes` template built with nothing drawn, recursively, stopping where a plan would loop. So a character with only a torso shows the whole body in its rest pose. A planned part is never in the document. A character's floor lies under the lowest vertex of everything shown.

The node holds, per part, its mesh, silhouettes and extents, each derived in the graph from the part's build.

## How a pane looks at a part

A locked pane looks straight along the normal of its view's plane, so an outline's plane is never foreshortened and the map from plane to pane is a rotation and a scale. Rings stand on planes a = constant, so a Side or Top outline drawn there is the mesh's silhouette on screen. `PartAxes.framing` says which pane holds which view, the arrangement that shows every view's plane the most squarely; `PartAxes.basis` gives the pane's axes.

- **A label names the direction, not the outline.** A Sheet pane's label, and Single's Snap view, HUD and thumbnails, name the direction looked from, so Top is always the top-left pane. The drawers' outline rows and the status bar name outlines, so a leg's or an ear's Top pane writes its Front outline, and its Front pane its Top outline.
- **The camera tilts with the part.** Each pane looks along the normal of its view's plane, from the side of its plain body view, with that body view's up made as up as the plane lets it be. A part square to the body gets the plain body views exactly. A tail 37 degrees above horizontal runs diagonally in Side, whose plane is the body's, runs straight across in Top, seen from 37 degrees off vertical, and shows Front from below the ground, looking up its axis. An ear 16 degrees off vertical stands straight in Side, leans in Top, whose plane is the body's front, and shows Front from 16 degrees off vertical.
- **Everything goes through the one camera.** The selection, the ghosts, their depth order, the template hint, the outlines, a tool's strokes and handles all do. Silhouettes are derived along the part's own axes, so a hint is the silhouette of the view's own camera. The ground is the disc under the character as the camera's tilt shows it, never flatter than the thin ellipse a level camera has, and is always drawn behind. The pane looking along the body's up has none.
- **Each pane shows the part's middle at its own middle.** That point lines up across panes, and every pane keeps one scale. Other points line up only as far as the two planes share a direction: a plane tilted from its neighbour's shows the same length longer than the neighbour's projection of it, so a tilted part gives up the row or column it does not share. The ear's height direction is one screen axis in Front and Side, so that column holds; its Top leans, so the row with Side does not hold. The tail keeps neither. A stretched pane that held the alignment would be foreshortened again, and a stroke would not land.
- **Guides are the part's own lines.** A guide or a disagreement's limit is the line square to a part direction at a point of the outline's extent, and it is drawn at whatever angle that makes on screen. In the tail's Side the guides run on the diagonal.
- **Where a stroke cannot land by construction, the view sets something else.** The axis stays in the plane of a and b, so Top's and Front's midpoints in c are not read: a lopsided Top stroke builds evenly about the axis. A Front outline alone leaves the sizes to the template, and a `cap` Front shapes only the last sixth of the axis, so it reaches a paw's tube when the paw has no toes and nothing on a paw with them, whose tube stops short and whose toes keep their own section.

## Adding parts without refactoring

A new part is one of three things, and each slots in without touching what exists or what is saved.

| New part is | What changes | Old characters |
| --- | --- | --- |
| A template file over existing blocks (arm, hand, hoof, nose, horn) | Nothing in code. The part picker lists templates from the folder and the Part drawer renders parameters from the template's metadata | Untouched; they pin their own template ids and versions |
| A block kind (membrane, socket row, per-digit overrides) | One sealed subclass with its `build`, `lint`, `joints`, `sockets` and `areas`, and the schema regenerates. No `when` over block kinds exists outside the block classes and the schema generator, so nothing else changes | Untouched; their files never mention the kind |
| A surface that is not a sweep (a membrane between fingers, a sphere eye) | A block whose `build` returns a patch that is open in v, or closed with poles. Anchors, marks, weights and lines already address patches by name, u and v | Untouched; their anchors name the body patch |

Four rules make saved characters keep working, from the first commit:

1. **Template versions are immutable and ship forever.** A changed template is a new version file; the old file stays and old characters keep building with it. An upgrade to the new version is offered, never applied by itself.
2. **A new parameter's default reproduces the old build exactly.** The golden tests build every shipped template version with default parameters and compare against its stored fixture, so a default that moves a vertex fails the build.
3. **References are by name, never by index.** Poses name bones by part and joint name, anchors name patches, slots name sockets, marks name landmarks. Adding a joint, a patch or a socket to a new template version shifts nothing stored.
4. **Documents carry a format number and a migration chain.** Each step from one format to the next is one function with a fixture saved in the older format. A document from a newer app is refused with a clear message, never half-read. Shipped templates stay strictly parsed; documents tolerate only what a migration has accounted for.

## Part template file format

Templates and body plans are JSON files, one per template, shipped under `templates/` with a JSON Schema beside them under `schema/`, and parsed by kotlinx.serialization into the sealed classes above. JSON is the only text format with a `commonMain` parser, a schema language that IntelliJ validates as you type, and a diff that reads in a pull request.

The `templates/` folder is the single source. The build copies it into `character`'s resources with a generated index of its files, and the app loads them from there through `TemplateFolder.shipped()`, so a new file needs no code change. Lint, golden and schema tests read the folder itself, and a test holds the resources equal to it.

| Format | Why not |
| --- | --- |
| YAML | No `commonMain` parser; indentation errors are silent |
| TOML | Nested arrays of blocks read badly |
| Kotlin DSL | Code, not data: nothing outside the app can validate or diff it, and a user-saved template would need a compiler |
| SQLite | Not diffable or hand-editable; right for the library and caches, wrong for shipped recipes |

### One source of truth

The Kotlin model is the truth and the schema is generated from it, never written by hand. The generator in `schema-gen` walks the kotlinx.serialization `SerialDescriptor` tree, which is the same structure the parser reads, so the schema cannot accept what the parser rejects or the other way round. `@SerialName` on each sealed subclass gives the `kind` discriminator and the subclasses give the `oneOf`; nullability and defaults give `required`; two annotations in the `annotations` module, `@Range` and `@Doc`, give `minimum`, `maximum` and `description`. The `$param` union is a value class with its own serializer and schema fragment (a number, or a string matching `^\$[a-z][a-zA-Z0-9]*$`).

The `generateSchemas` task writes the files under `schema/`. They are committed so IntelliJ and CI validate without a build, and a unit test regenerates and diffs them so a stale schema fails the build. victools is the fallback if the descriptor walk hits a wall; it works from reflection, so it would need Kotlin metadata for nullability. Generating the model from the schema is rejected: generators produce poor Kotlin, lose sealed classes and value classes, and move the truth into a file no IDE can refactor.

### Readable

One file per part. Comments and trailing commas are allowed (kotlinx `allowComments` and `allowTrailingComma`). Every block has a `kind` and most have a `name`, so a file reads as a parts list. Angles are degrees in files and radians in code. Positions are fractions: `u` runs 0 to 1 along the axis from the socket end, `v` runs 0 to 1 around it with 0 on top and 0.5 underneath. Lengths are fractions of the part's own axis length, so a template never states an absolute size; the drawing or the `size` parameter does.

### Fast

A template is a few kilobytes. All of them parse once at startup into immutable data and are keyed by content hash, so an edited file invalidates exactly the derived caches that read it. Parsing cost is nothing beside building slices, and the format never touches the build.

### Verifiable

| Layer | Catches | Runs |
| --- | --- | --- |
| JSON Schema | Structure, required keys, ranges, enum values, `$param` reference syntax | Generated from the model and committed; validates in the editor and in CI over every shipped file; CI fails if stale |
| Typed parse | Unknown `kind`, unknown keys, wrong types; sealed classes make every kind exhaustive in code | On load; a failure names file, path and line |
| Semantic lint | `u` ranges inside 0 to 1, joints ordered along the axis, exactly one run reaching the open end when `openEnd` is set, sockets of templates and plans where a run reaches, no two blocks building a patch of one name, a views map where each reading has one view, some view sets height or width, cap and section are not both set, and only Front sets a section or cap, paired sockets symmetric, `$param` names exist, a parameter's `values` ascending, holding its default and in place of `min` and `max`, a tube's `fills` naming a toe row that ends past it, ring count enough for the joint count | On load and in CI |
| Golden build | Each template built with default parameters and no outlines: watertight except the open end, no degenerate triangles, every vertex of every patch matches the fixture to 1/10000 of the axis length; random parameters inside their ranges, or among their values, also build. A missing fixture fails; `./gradlew :character:desktopTest -PupdateGoldens` writes fixtures for review in the diff | JUnit, when a template or builder changes |

### Extensible

`format` is the grammar version of the file and changes rarely. `version` is the template's own and changes when its topology or joints change, since saved characters pin it. Blocks are a tagged union on `kind`: a new block kind is a sealed subclass and a builder function, and the schema regenerates from it. Parsing is strict, so a typo is an error and not a silently ignored key, except keys starting with `x-`, which are reserved for experiments and ignored by the app. Parameters are referenced as `"$name"` strings anywhere a number is allowed, which the schema checks by pattern and the lint by name.

### Examples

```json
// templates/leg.part.json
{
  "$schema": "../schema/part-template.schema.json",
  "format": 1,
  "id": "leg",
  "version": 1,
  "name": "Leg",
  "axis": { "rest": [0, -1, 0], "length": 0.5, "bend": "$stance", "bendAt": 0.55 },  // from the socket end toward the tip, in body space
  "openEnd": "start",                       // u = 0 sits on the parent's surface at the socket
  "topology": { "rings": 48, "segments": 24 },
  "views": { "side": "height", "top": "width", "front": "cap" },
  "section": "circle",                      // default section profile: circle, egg, keel, or control points
  "params": {
    "size":      { "default": 1.0,  "min": 0.4, "max": 2.5 },
    "thickness": { "default": 0.28, "min": 0.1, "max": 0.6 },
    "stance":    { "default": 0.0,  "min": 0.0, "max": 0.25 }
  },
  "blocks": [
    { "kind": "tube",   "u": [0.0, 1.0], "radius": "$thickness" },
    { "kind": "joint",  "name": "hip",   "u": 0.0,  "hinge": "x", "limits": [-60, 90] },
    { "kind": "joint",  "name": "knee",  "u": 0.55, "hinge": "x", "limits": [0, 140] },
    { "kind": "joint",  "name": "ankle", "u": 1.0,  "hinge": "x", "limits": [-30, 60], "stretch": true },
    { "kind": "socket", "name": "paw",   "u": 1.0,  "v": 0.5, "outward": "normal" },
    { "kind": "area",   "name": "knee",  "u": [0.5, 0.6], "v": [0, 1], "tag": "rigid" }
  ]
}
```

A paw adds the blocks that draw what the user never does, driven by parameters the Part drawer exposes. It is closed, with no socket plane, so a drawing reaches behind its socket and can take in the end of the leg. It starts with no toes, a plain rounded block to draw over, and `fills` carries its tube to the end of the row until the row has toes:

```json
"params": {
  "toes":   { "default": 0,  "values": [0, 3, 4, 5] },
  "spread": { "default": 18, "min": 0, "max": 40, "unit": "deg" },
  "pads":   { "default": true }
},
"blocks": [
  { "kind": "tube",   "u": [0.0, 0.6], "radius": 0.22, "fills": "toes" },
  { "kind": "toeRow", "name": "toes", "u": [0.55, 1.0], "count": "$toes", "spread": "$spread", "radius": 0.09, "rings": 16, "segments": 12 },
  { "kind": "pad",    "name": "palm", "u": 0.45, "v": 0.5, "size": 0.22, "if": "$pads", "color": "pad", "rings": 7, "segments": 12 },
  { "kind": "pad",    "name": "toe",  "on": "toes", "v": 0.5, "size": 0.08, "if": "$pads", "color": "pad", "rings": 7, "segments": 12 },
  { "kind": "cap",    "end": "end" }
]
```

A head is a `skull` block instead of a tube, with a muzzle and the landmarks face marks anchor to:

```json
"openEnd": null,
"blocks": [
  { "kind": "skull",    "u": [0.0, 0.75], "width": 0.82, "depth": 0.78 },
  { "kind": "tube",     "name": "muzzle", "u": [0.6, 1.0], "radius": 0.2, "taper": 0.7, "drop": 0.08 },
  { "kind": "pad",      "name": "nose",   "u": 0.95, "v": 0.0, "size": 0.07, "color": "nose", "rings": 7, "segments": 12 },
  { "kind": "joint",    "name": "jaw",    "u": 0.6, "v": 0.5, "hinge": "x", "limits": [0, 35] },
  { "kind": "socket",   "name": "ear",    "u": 0.25, "v": 0.12, "pair": "x", "outward": "normal" },
  { "kind": "landmark", "name": "eye",    "u": 0.5,  "v": 0.2,  "pair": "x" },
  { "kind": "landmark", "name": "nose",   "u": 1.0,  "v": 0.0 }
]
```

A body plan is the same grammar with its own block kinds, so it shares the parser, schema and lint:

```json
// templates/plans/canine-quadruped.plan.json
{
  "format": 1, "id": "canine-quadruped", "version": 1, "name": "Canine quadruped",
  "root": { "template": "torso", "rest": [1, 0, 0] },
  "sockets": [
    { "name": "neck",      "on": "torso", "u": 0.92, "v": 0.04, "takes": ["head"] },
    { "name": "front-leg", "on": "torso", "u": 0.82, "v": 0.42, "pair": "x", "takes": ["leg"] },
    { "name": "back-leg",  "on": "torso", "u": 0.18, "v": 0.42, "pair": "x", "takes": ["leg"] },
    { "name": "tail",      "on": "torso", "u": 0.03, "v": 0.02, "takes": ["tail"] },
    { "name": "ruff",      "on": "torso", "u": 0.9,  "v": 0.5, "takes": ["ruff"], "optional": true }
  ],
  "slots": [ { "name": "paw-claws", "on": "paw", "takes": ["claws"], "default": null } ]
}
```

### Parameters

A parameter has a `default`, a number or a flag, and optionally `unit`. A number either moves between `min` and `max`, on `step` if it has one, or takes only the ascending `values` it lists, which stand in for the limits. The Part drawer's slider runs from the lowest to the highest and lands on the nearest allowed value, so a list with gaps skips them, and Tool knows reads a list by its stretches: `[0, 3, 4, 5]` as 0, 3–5.

### Block reference

Every block takes `kind`, an optional `name`, `if` (a boolean `$param`) and `x-` keys. Blocks build in file order, so a toe row or a pad reads the runs listed before it. The axis takes `rest`, an undrawn `length`, and an optional `bend` and `bendAt`, which set the shape of the undrawn axis: how far it sits toward the underside at that u, which also moves the joints along it.

| Kind | Fields | Builds |
| --- | --- | --- |
| `tube` | `u` range, `radius`, `taper`, `section` override (a named profile or control points), `cup`, `drop`, `fills` | A run of slices; the views resize them. `cup` curls the section's sides toward v = 0; `drop` lowers the undrawn run toward the underside. `fills` names a toe row: while the row has no toes the run reaches the row's end, on the same rings and segments, and closes there as the part's own end, undrawn or drawn |
| `cap` | `end` (start or end), `shape` (round, flat, pointed) | Closes the runs' ends on that side that are not on a socket. An end with no cap that is not the open end closes flat. A drawing shapes the part's own ends, closing only the last ring. Where another run covers a run's inner end, the earlier run keeps the drawing's size to its end and the later rises to it from inside, so the two read as one surface; an inner end nothing covers still rounds off |
| `skull` | `u` range, `width`, `depth` | A rounded block whose slices run front to back: an ellipsoid when undrawn, the `body` patch |
| `toeRow` | `u` range, `count`, `spread` (degrees of the outermost toe from the axis), `radius`, `v`, `rings`, `segments` | A row of small closed tubes fanned across the end from the run below them, patches `name:0` on, each on a grid of `rings` by `segments`; a `count` of 0 builds none. A drawn Side fills each toe to the outline; a drawn Top shares its width among them. Vertices take the bone at their u; toe bones arrive with the rig |
| `pad` | `u`, `v` or `on` (a toeRow listed earlier), `size`, `color`, `rings`, `segments` | A raised closed lens following the surface on a grid of `rings` by `segments`, a patch named `pad:name` or `pad:name:i` per toe, so none on a row with no toes, with no slices. `v` defaults to underneath. Its vertices take the bone at its site |
| `joint` | `u`, optional `v`, `hinge` axis, `limits`, `stretch` | A bone boundary; bones are the spans between joints in `u` order |
| `socket` | `u`, `v`, `outward` (axis or normal: which way the child sinks into this part), `pair`, `takes` | A child attachment point; `pair` makes a mirrored twin. A plan's socket takes `outward` too |
| `area` | `u` range, `v` range, `tag` (rigid, soft, inner) | A tagged region weights and shading can read |
| `landmark` | `u`, `v`, `pair` | A named surface point face marks and sets anchor to |

A user-saved template, which comes later, writes the same file from a built part, so there is one format and one validator for shipped and saved templates alike.

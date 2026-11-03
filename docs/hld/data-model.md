# Data model

One representation runs the whole pipeline: a part is an axis with a row of slices. The outlines size the slices, the mesh is generated from them with fixed topology, weights and marks address them, the body curve re-samples them, and contact flattens them. Every entity below is immutable Kotlin data in `commonMain`. Storage maps it and never shapes it.

## Entities

| Entity | Holds | Kind |
| --- | --- | --- |
| Part template | The recipe for a part type: axis, open end, ring and segment counts, blocks, parameter ranges, joints, sockets, tagged areas, which view sets what | Shipped data file, versioned |
| Body plan | A graph of sockets from a root spine, pair ids for mirrored sockets, the rest pose | Shipped preset, versioned |
| Character | Body plan, part instances, slots, palette, face sets, and the library version it came from | Document; also its own library file |
| Part instance | Template id and version, the socket it sits on, per-view outline curves, parameter values, pair id and per-side overrides | Document |
| Surface anchor | A point on a part's surface as (part, patch name, u along the axis, v around it, offset) | Document, inside paint, marks, line weights and touch-ups |
| Mark | A polyline of anchors, a width profile, an ink flag, per-angle versions keyed by camera bin, and the expression set it belongs to | Document |
| Patches | One or more named surfaces per part, each a fixed grid of points, closed in v (a tube) or open (a sheet); a swept patch also keeps its slices: for each u a frame on the axis spline, height, width, a closed section spline and a proxy normal | Derived |
| Part mesh | One grid per patch; every vertex tagged with part, patch, bone, u and v | Derived |
| Rig | Bones from template joints, weights seeded from part ownership and blended only at seams, stretch weights | Derived |
| Pose | Per-bone transform and stretch, the body curve kept as a curve, the planted set, touch-up deltas in anchor space | Document, one per shot key |
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

The template is the recipe and the instance is what the user drew. Slices are derived from the two and never saved, so a saved character is only outlines, parameters and anchors.

```kotlin
// Shipped with the app, versioned and immutable: a changed template is a new version file.
data class PartTemplate(
    val id: TemplateId, val version: Int, val name: String,   // "leg"
    val axis: AxisSpec,                // rest direction before anything is drawn; the built axis is a spline through the Side midline
    val openEnd: End?,                 // the end that sits on the socket plane; null for a closed part
    val rings: Int, val segments: Int, // fixed topology of the body patch: rings along u; segments = control points of every section spline
    val blocks: List<Block>,           // Tube, Cap, Skull, ToeRow, Pad, Joint, Socket, Area, Landmark; joints, sockets and areas are blocks
    val params: Map<String, Range>,    // toes 3..5, spread 0..40 deg, size; a new parameter's default reproduces the old build
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
}

// In the document. Everything the user chose for one part.
data class PartInstance(
    val id: PartId,
    val template: TemplateRef,             // id and version, pinned
    val socket: SocketRef,                 // parent part and socket name; null for the root torso
    val outlines: Map<View, OutlineCurve>, // cubic Beziers in the part's own axes, open at the socket end
    val params: Map<String, Float>,
    val pair: PairId?, val side: Side?,     // mirrored pairs share outlines and params
    val overrides: Overrides,              // per-side outline and param deltas layered on the shared ones
)

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
    val frame: Frame,                  // centre and orientation on the axis spline at u
    val height: Float, val width: Float,
    val section: ClosedSpline,         // exactly `segments` control points, sampled at fixed v, scaled by height and width
    val proxyNormalFrame: Mat3,
)

// Where surface work lives. Survives refits because patch, u and v are template coordinates, not positions.
data class Anchor(val part: PartId, val patch: String = "body", val u: Float, val v: Float, val offset: Float = 0f)
```

Rules the code keeps:

- **Views set slices, nothing fits.** At each u the Side outline's two crossings give the slice's centre (their midpoint) and height (their distance), so the axis follows the drawing and a digitigrade leg bends at the hock because it was drawn that way. The Top outline's crossings give width. The Front outline shapes the cap, or, where the template says `section`, the cross-section itself, which is how a chest gets a keel or a flat side. A missing view takes the template's section profile and the other view's extent.
- **Everything is a spline.** Outlines are cubic Béziers. The axis is a cubic spline through the Side midline. Every cross-section is a closed cubic B-spline with exactly `segments` control points, sampled at fixed v, so a circle, an egg, a keel and a drawn chest outline are one type with different control polygons, and the mesh topology never changes. A part is a lofted surface: sections swept along the axis.
- **Views can only disagree along the axis.** Height and width are independent, so the only conflict is where a part starts and ends along u. The highlight marks where one view extends past the other's ends, which is a comparison of two intervals per stroke.
- **Fixed topology per template version.** Ring i maps to u, segment j maps to v, so an anchor reaches its vertex neighbourhood by arithmetic. Changing rings or segments is a new version and a migration.
- **Open ends close at the socket plane.** The open end's slice is placed on the parent's surface at the socket, and the parent's slices are untouched. A stroke that closes anyway is cut at that plane.
- **Heads are blocks too.** Skull is a block whose slices run front to back, with a muzzle Tube and eye and ear sockets. Face marks anchor to Skull's u and v like any other mark.
- **Mirroring is shared data plus overrides.** A pair stores one set of outlines and params; each side's overrides are deltas. Breaking a pair copies the shared data into both and clears the pair id.
- **Poses stay re-editable.** A pose keeps the body curve as a curve and the planted set as flags, so re-sampling after a character update uses the same inputs. Touch-ups are deltas in anchor space, which is why they survive Update from library.
- **Detail beyond the sweep is anchored, not modelled.** Branching shapes are other blocks or parts. Grooves and muscle lines are a later shape touch-up layer: push and pull offsets stored in anchor space at build time, the same mechanism as per-shot touch-ups, so they survive refits.

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
| Semantic lint | `u` ranges inside 0 to 1, joints ordered along the axis, exactly one open end when `openEnd` is set, sockets on the surface, paired sockets symmetric, `$param` names exist, ring count enough for the joint count | On load and in CI |
| Golden build | Each template built with default parameters and no outlines: watertight except the open end, no degenerate triangles, bounds and vertex count match the fixture; random parameters inside their ranges also build | JUnit, when a template or builder changes |

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
  "axis": { "rest": [0, -1, 0] },          // from the socket end toward the tip, in body space
  "openEnd": "start",                       // u = 0 sits on the parent's surface at the socket
  "topology": { "rings": 24, "segments": 16 },
  "views": { "side": "height", "top": "width", "front": "cap" },
  "section": "circle",                      // default section profile: circle, egg, keel, or control points
  "params": {
    "size":      { "default": 1.0,  "min": 0.4, "max": 2.5 },
    "thickness": { "default": 0.28, "min": 0.1, "max": 0.6 }
  },
  "blocks": [
    { "kind": "tube",   "u": [0.0, 1.0], "radius": "$thickness" },
    { "kind": "joint",  "name": "hip",   "u": 0.0,  "hinge": "x", "limits": [-60, 90] },
    { "kind": "joint",  "name": "knee",  "u": 0.55, "hinge": "x", "limits": [0, 140] },
    { "kind": "joint",  "name": "ankle", "u": 1.0,  "hinge": "x", "limits": [-30, 60], "stretch": true },
    { "kind": "socket", "name": "paw",   "u": 1.0,  "v": 0.5, "outward": "axis" },
    { "kind": "area",   "name": "knee",  "u": [0.5, 0.6], "v": [0, 1], "tag": "rigid" }
  ]
}
```

A paw adds the blocks that draw what the user never does, driven by parameters the Part drawer exposes:

```json
"params": {
  "toes":   { "default": 4,  "min": 3, "max": 5, "step": 1 },
  "spread": { "default": 18, "min": 0, "max": 40, "unit": "deg" },
  "pads":   { "default": true }
},
"blocks": [
  { "kind": "tube",   "u": [0.0, 0.6], "radius": 0.3 },
  { "kind": "toeRow", "name": "toes", "u": [0.55, 1.0], "count": "$toes", "spread": "$spread", "radius": 0.09 },
  { "kind": "pad",    "name": "palm", "u": 0.45, "v": 0.5, "size": 0.22, "if": "$pads", "color": "pad" },
  { "kind": "pad",    "name": "toe",  "on": "toes", "v": 0.5, "size": 0.08, "if": "$pads", "color": "pad" },
  { "kind": "cap",    "end": "end" }
]
```

A head is a `skull` block instead of a tube, with a muzzle and the landmarks face marks anchor to:

```json
"openEnd": null,
"blocks": [
  { "kind": "skull",    "u": [0.0, 0.75] },
  { "kind": "tube",     "name": "muzzle", "u": [0.6, 1.0], "radius": 0.35, "taper": 0.6 },
  { "kind": "joint",    "name": "jaw",    "u": 0.6, "v": 0.5, "hinge": "x", "limits": [0, 35] },
  { "kind": "socket",   "name": "ear",    "u": 0.25, "v": 0.12, "pair": "x", "outward": "normal" },
  { "kind": "landmark", "name": "eye",    "u": 0.5,  "v": 0.2,  "pair": "x" },
  { "kind": "landmark", "name": "nose",   "u": 1.0,  "v": 0.0 }
]
```

A body plan is the same grammar with its own block kinds, so it shares the parser, schema and lint:

```json
// plans/canine-quadruped.plan.json
{
  "format": 1, "id": "canine-quadruped", "version": 1, "name": "Canine quadruped",
  "root": { "template": "torso", "rest": [1, 0, 0] },
  "sockets": [
    { "name": "neck",      "on": "torso", "u": 1.0,  "v": 0.1, "takes": ["head"] },
    { "name": "front-leg", "on": "torso", "u": 0.85, "v": 0.35, "pair": "x", "takes": ["leg"] },
    { "name": "back-leg",  "on": "torso", "u": 0.15, "v": 0.35, "pair": "x", "takes": ["leg"] },
    { "name": "tail",      "on": "torso", "u": 0.0,  "v": 0.0, "takes": ["tail"] },
    { "name": "ruff",      "on": "torso", "u": 0.9,  "v": 0.5, "takes": ["ruff"], "optional": true }
  ],
  "slots": [ { "name": "paw-claws", "on": "paw", "takes": ["claws"], "default": null } ]
}
```

### Block reference

Every block takes `kind`, an optional `name`, `if` (a boolean `$param`) and `x-` keys.

| Kind | Fields | Builds |
| --- | --- | --- |
| `tube` | `u` range, `radius`, `taper`, `section` override (a named profile or control points) | A run of slices; the views resize them |
| `cap` | `end` (start or end), `shape` (round, flat, pointed) | Closes an end that is not a socket |
| `skull` | `u` range, `width`, `depth` | A rounded block whose slices run front to back |
| `toeRow` | `u` range, `count`, `spread`, `radius`, `v` | A row of small tubes fanned across the end, each with its own bone |
| `pad` | `u`, `v` or `on` (a toeRow name), `size`, `color` | A raised area tagged with a palette slot |
| `joint` | `u`, optional `v`, `hinge` axis, `limits`, `stretch` | A bone boundary; bones are the spans between joints in `u` order |
| `socket` | `u`, `v`, `outward` (axis or normal), `pair`, `takes` | A child attachment point; `pair` makes a mirrored twin |
| `area` | `u` range, `v` range, `tag` (rigid, soft, inner) | A tagged region weights and shading can read |
| `landmark` | `u`, `v`, `pair` | A named surface point face marks and sets anchor to |

A user-saved template, which comes later, writes the same file from a built part, so there is one format and one validator for shipped and saved templates alike.

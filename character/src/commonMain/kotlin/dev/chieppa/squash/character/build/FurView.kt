package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

private const val CLIP_COMPONENTS = 4

/** Past this share of the view running along up, the sideways direction is found from another axis. */
private const val SIDE_FROM_UP = 0.9f

/**
 * What a view's fur reads of its camera: a [matrix] from body space to clip space, column by column, on a [width] by [height] surface.
 * A perspective camera has an [eye]; a camera with none looks along [toViewer] from everywhere.
 */
data class FurCamera(val matrix: List<Float>, val width: Int, val height: Int, val eye: Vec3?, val toViewer: Vec3) {
    /** From [point] toward the viewer, with length 1. */
    fun toViewerFrom(point: Vec3): Vec3 = eye?.let { (it - point).normalized() } ?: toViewer

    /** Where [point] shows, in pixels from the surface's top-left corner, or null behind the camera. */
    fun toPixel(point: Vec3): Vec2? {
        val w = clip(point, CLIP_COMPONENTS - 1)
        if (w <= 0f) return null
        return Vec2((clip(point, 0) / w + 1f) / 2f * width, (1f - clip(point, 1) / w) / 2f * height)
    }

    /** How many pixels one body unit across the view spans at [point], or null when it lies behind the camera. */
    fun pixelsPerUnitAt(point: Vec3): Float? {
        val at = toPixel(point) ?: return null
        val side = toViewer cross if (kotlin.math.abs(toViewer.y) < SIDE_FROM_UP) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        val beside = toPixel(point + side.normalized()) ?: return null
        return kotlin.math.hypot(at.x - beside.x, at.y - beside.y)
    }

    private fun clip(point: Vec3, row: Int): Float =
        matrix[row] * point.x + matrix[CLIP_COMPONENTS + row] * point.y + matrix[2 * CLIP_COMPONENTS + row] * point.z + matrix[3 * CLIP_COMPONENTS + row]
}

/**
 * One fur tuft as a view shows it: from the two corners of its base along [left] and [right], which meet at [tip]. Each edge runs base to tip,
 * for the ink. [run] is the tuft on the body from its root to its tip, the same from every camera, and its tip shows where the root plus [run]
 * shows. [size] is its share of its full length, which its ink follows.
 */
data class FurWedge(
    val id: MarkId,
    val root: Vec3,
    val normal: Vec3,
    val slot: SlotName?,
    val left: List<Vec3>,
    val right: List<Vec3>,
    val size: Float,
    val run: Vec3,
) {
    val tip: Vec3 get() = left.last()
}

/** A part's strokes in one slot, as one mesh to fill. */
class FurFill(val slot: SlotName?, val mesh: RenderMesh)

/** What one part's fur shows in a view. */
class PartFurView(val wedges: List<FurWedge>, val fills: List<FurFill>)

/** What every part's fur shows in a view, furthest part first. */
class FurView(val parts: Map<PartId, PartFurView>) {
    companion object {
        val EMPTY = FurView(emptyMap())
    }
}

/** A wedge's width at its base, as a share of its length. */
private const val BASE_SHARE = 0.6f

/** How far up its length a wedge's edges bend, and how far they bow out from the surface there, as a share of its base. */
private const val BEND_AT = 0.5f
private const val HOOK_SHARE = 0.16f

/** How much of a tuft's length must run across the view for it to show its whole width. */
private const val FULL_WIDTH_FROM = 0.35f

/** Points along one edge of a wedge. */
private const val EDGE_STEPS = 4

/** How many pixels make one body unit in a rule's length and spacing, which hold on the body from every camera at every zoom. */
private const val RULE_PIXELS_PER_UNIT = 280f

/** Where a stroke's size starts, as its room against the spacing: it reaches full size at a room of the spacing itself. */
private const val LEAST_ROOM_SHARE = 0.7f

/** A stroke smaller than this share of its length is not drawn, too short to read. */
private const val LEAST_SIZE = 0.08f

/** How far every tuft rises from the surface beyond its grain, so its tip clears the silhouette. */
private const val OUTWARD_LEAN = 0.3f

/** A stroke's length varies by this share either way, from its id, so a row does not read as a saw. */
private const val LENGTH_SPREAD = 0.3f
private const val SPREAD_STEPS = 64

/** How far back a wedge sits, as a share of how far its root lies before the rim it clears: at the rim's depth, so the surface inside the rim covers it and the part's outline shell, just past the rim, does not. */
private const val RIM_MARGIN = 1.05f

/**
 * How far before the rim a wedge sits, as a share of the figure's radius: clear of the contour line drawn along the rim, so a wedge covers
 * the line where it breaks through, and too little for the surface to show it inside the rim.
 */
private const val BEFORE_RIM_SHARE = 0.0095f

private const val FLOATS_PER_CORNER = RenderMesh.FLOATS_PER_VERTEX
private const val INDICES_PER_STEP = 6
private const val MIN_LENGTH = 1e-5f

/**
 * What [fur] shows through [camera]. A tuft is a fixed shape on the body, a blade from its root along its grain, so an orbit shows it turn as
 * the body turns: only the side its width faces, and how far back it sits, read the camera. It sits back along each point's line of sight, past
 * the rim that makes the part's outline, so that part's surface covers all of it but what breaks the silhouette, while it stays in front of
 * what lies behind the part.
 */
fun furView(fur: BodyFur, camera: FurCamera, checkCancelled: () -> Unit = {}): FurView {
    val views = fur.parts.mapValues { (_, part) ->
        checkCancelled()
        val wedges = shownWedges(part, camera, fur.figureRadius * BEFORE_RIM_SHARE)
        PartFurView(wedges, wedges.groupBy { it.slot }.map { (slot, held) -> FurFill(slot, wedgeMesh(held)) })
    }
    return FurView(views.entries.sortedBy { (_, view) -> nearness(view, camera) }.associate { it.key to it.value })
}

/** How far toward the viewer a part's strokes lie, on average. */
private fun nearness(view: PartFurView, camera: FurCamera): Float =
    if (view.wedges.isEmpty()) 0f else view.wedges.map { it.root dot camera.toViewer }.average().toFloat()

private fun shownWedges(part: PartFur, camera: FurCamera, beforeRim: Float): List<FurWedge> {
    val rule = part.rule
    val spacing = rule.spacingPixels / RULE_PIXELS_PER_UNIT
    val rims = part.rims.mapNotNull { rim ->
        val center = camera.toPixel(rim.center) ?: return@mapNotNull null
        val perUnit = camera.pixelsPerUnitAt(rim.center) ?: return@mapNotNull null
        SeenRim(rim, center, rim.radius * perUnit)
    }
    return part.roots.mapNotNull { root ->
        val size = sizeWithRoom(root.room / spacing)
        if (size < LEAST_SIZE) return@mapNotNull null
        val toViewer = camera.toViewerFrom(root.position)
        val facing = root.normal dot toViewer
        val at = camera.toPixel(root.position) ?: return@mapNotNull null
        val out = camera.toPixel(root.position + (root.normal - toViewer * facing))?.let { Vec2(it.x - at.x, it.y - at.y) } ?: Vec2(0f, 0f)
        val ownRim = -root.radius * facing
        val length = rule.lengthPixels / RULE_PIXELS_PER_UNIT * size * spreadOf(root.id)
        val run = runOf(root, length)
        val tipAt = camera.toPixel(root.position + run) ?: return@mapNotNull null
        val rim = outlineRim(rims, at, out)?.let { (it.center - root.position) dot toViewer } ?: ownRim
        val covering = rims.filter { pixelsBetween(it.center, tipAt) < it.radius }.minOfOrNull { (it.rim.center - root.position) dot toViewer } ?: rim
        tuft(root, run, length, size, toViewer, camera, beforeRim + minOf(ownRim, rim, covering) * RIM_MARGIN)
    }
}

/** A rim as a camera shows it: a circle of [radius] pixels at [center]. */
private class SeenRim(val rim: FurRim, val center: Vec2, val radius: Float)

/** Of [rims], the one a line from [at] heading along [out] leaves last: the one that makes the part's outline that way. */
private fun outlineRim(rims: List<SeenRim>, at: Vec2, out: Vec2): FurRim? {
    val length = kotlin.math.hypot(out.x, out.y)
    if (length < MIN_LENGTH) return null
    var furthest = Float.NEGATIVE_INFINITY
    var outline: FurRim? = null
    for (seen in rims) {
        val x = seen.center.x - at.x
        val y = seen.center.y - at.y
        val ahead = (x * out.x + y * out.y) / length
        val room = seen.radius * seen.radius - (x * x + y * y - ahead * ahead)
        if (room < 0f) continue
        val leaves = ahead + kotlin.math.sqrt(room)
        if (leaves > furthest) {
            furthest = leaves
            outline = seen.rim
        }
    }
    return outline
}

private fun smoothStep(share: Float): Float {
    val held = share.coerceIn(0f, 1f)
    return held * held * (3f - 2f * held)
}

private fun sizeWithRoom(room: Float): Float = smoothStep((room - LEAST_ROOM_SHARE) / (1f - LEAST_ROOM_SHARE))

private fun spreadOf(id: MarkId): Float {
    val step = (id.value.hashCode() and Int.MAX_VALUE) % SPREAD_STEPS
    return 1f + LENGTH_SPREAD * (2f * step / (SPREAD_STEPS - 1) - 1f)
}

/** The way a tuft of [length] stands from its root: along the grain and rising from the surface, a little under [length] long. */
private fun runOf(root: FurRoot, length: Float): Vec3 = (root.direction + root.normal * OUTWARD_LEAN) * (length / (1f + OUTWARD_LEAN))

/**
 * The tuft of [length] on the body at [root], running along [run]: a blade from a base sunk into the surface to its tip, bowed away from the
 * surface. Its width turns to the viewer, narrowing to a point as the tuft runs straight at them. It shows through [camera] where it stands and
 * lies [back] along each point's line of sight.
 */
private fun tuft(root: FurRoot, run: Vec3, length: Float, size: Float, toViewer: Vec3, camera: FurCamera, back: Float): FurWedge? {
    if (run.length < MIN_LENGTH) return null
    val seen = run - toViewer * (run dot toViewer)
    val crossing = seen cross toViewer
    if (crossing.length < MIN_LENGTH) return null
    val width = length * BASE_SHARE * smoothStep(seen.length / run.length / FULL_WIDTH_FROM) / 2f
    val half = crossing.normalized() * width
    val base = root.position - root.normal * (length * BASE_SHARE / 2f)
    val tip = root.position + run
    val middle = (base + tip) * BEND_AT + root.normal * (length * BASE_SHARE * HOOK_SHARE)
    fun shown(point: Vec3) = point + camera.toViewerFrom(point) * back
    return FurWedge(
        root.id, root.position, root.normal, root.slot,
        edge(base + half, middle + half * BEND_AT, tip).map(::shown),
        edge(base - half, middle - half * BEND_AT, tip).map(::shown),
        size, run,
    )
}

private fun edge(base: Vec3, bend: Vec3, tip: Vec3): List<Vec3> = List(EDGE_STEPS + 1) { step ->
    val t = step.toFloat() / EDGE_STEPS
    base * ((1f - t) * (1f - t)) + bend * (2f * t * (1f - t)) + tip * (t * t)
}

/** [wedges] as strips between their edges, each corner carrying the stroke's normal, so a wedge takes the tone of the surface at its root. */
private fun wedgeMesh(wedges: List<FurWedge>): RenderMesh {
    val cornersEach = (EDGE_STEPS + 1) * 2
    val vertices = FloatArray(wedges.size * cornersEach * FLOATS_PER_CORNER)
    val indices = IntArray(wedges.size * EDGE_STEPS * INDICES_PER_STEP)
    var at = 0
    var written = 0
    wedges.forEachIndexed { w, wedge ->
        val first = w * cornersEach
        for (step in 0..EDGE_STEPS) {
            at = vertices.putCorner(at, wedge.left[step], wedge.normal)
            at = vertices.putCorner(at, wedge.right[step], wedge.normal)
        }
        for (step in 0 until EDGE_STEPS) {
            val low = first + step * 2
            intArrayOf(low, low + 1, low + 2, low + 1, low + 3, low + 2).forEach { indices[written++] = it }
        }
    }
    return RenderMesh(vertices, indices)
}

private fun FloatArray.putCorner(at: Int, position: Vec3, normal: Vec3): Int {
    var o = at
    for (v in listOf(position, normal, normal)) {
        this[o++] = v.x
        this[o++] = v.y
        this[o++] = v.z
    }
    return o
}

private fun pixelsBetween(a: Vec2, b: Vec2): Float = kotlin.math.hypot(a.x - b.x, a.y - b.y)

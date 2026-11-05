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
 * A perspective camera has an [eye]; a camera with none looks along [toViewer] from everywhere. [density] says how many of its pixels make one
 * of the rule's.
 */
data class FurCamera(val matrix: List<Float>, val width: Int, val height: Int, val eye: Vec3?, val toViewer: Vec3, val density: Float = 1f) {
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
 * One fur stroke as a view shows it, in body space: from the two corners of its base along [left] and [right], which meet at [tip].
 * Each edge runs base to tip, for the ink; its fill lies [fillBehind] further from the viewer. [size] is its share of its full length, which
 * its ink follows, and which only a change of zoom changes.
 */
data class FurWedge(
    val id: MarkId,
    val root: Vec3,
    val normal: Vec3,
    val slot: SlotName?,
    val left: List<Vec3>,
    val right: List<Vec3>,
    val size: Float,
    val fillBehind: Vec3,
) {
    val tip: Vec3 get() = left.last()
}

/** A part's strokes in one slot, as one mesh to fill. */
class FurFill(val slot: SlotName?, val mesh: RenderMesh)

/** What one part's fur shows in a view. */
class PartFurView(val wedges: List<FurWedge>, val fills: List<FurFill>)

/** What every part's fur shows in a view. */
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

/** How sharply a wedge's bow swings to its other side as the surface turns under it, so it never snaps there. */
private const val HOOK_TURN = 5f

/** Points along one edge of a wedge. */
private const val EDGE_STEPS = 4

/**
 * How many pixels a body unit spans at the figure for strokes to show their rule's whole length and spacing. A smaller view scales both down
 * in proportion, so a tuft keeps its size against the part and the same strokes show; a closer one keeps them in pixels, so more strokes show,
 * as a line keeps its weight.
 */
private const val FULL_SCALE_PIXELS_PER_UNIT = 280f

/** Where a stroke's size starts, as its room against the spacing: it reaches full size at a room of the spacing itself. */
private const val LEAST_ROOM_SHARE = 0.7f

/** Where along the band, from the silhouette to its edge, a stroke starts drawing back behind the rim to stand hidden at the edge. */
private const val DRAW_BACK_FROM = 0.75f

/** A stroke smaller than this share of its length is not drawn, too short to read. */
private const val LEAST_SIZE = 0.08f

/** How far every stroke leans outward from the surface, so its tip clears the silhouette. */
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

/** How far behind its ink a wedge's fill sits, in pixels, so ink wins over its own fill. */
private const val FILL_BEHIND_INK_PIXELS = 0.5f

private const val FLOATS_PER_CORNER = RenderMesh.FLOATS_PER_VERTEX
private const val INDICES_PER_STEP = 6
private const val MIN_LENGTH = 1e-5f

/**
 * What [fur] shows through [camera]: of each part's strokes those whose root faces the viewer, or away, by under their rule's show near, and
 * that have room for the spacing at this zoom. Turning the camera changes neither a stroke's length nor which strokes have room, so a stroke
 * holds its size through an orbit: only the part's rim hides it. A wedge turns its width to the viewer and runs along the grain as the viewer
 * sees it. It sits back along its line of sight, past the rim of the part it roots on, so that part's surface covers all of it but what breaks
 * the silhouette, while it stays in front of what lies behind the part. Near the band's edge it draws back behind the rim, so it comes and goes
 * unseen.
 */
fun furView(fur: BodyFur, camera: FurCamera, checkCancelled: () -> Unit = {}): FurView {
    val figure = camera.pixelsPerUnitAt(fur.figureCenter)?.takeIf { it > 0f } ?: return FurView.EMPTY
    val scale = (figure / (FULL_SCALE_PIXELS_PER_UNIT * camera.density)).coerceAtMost(1f)
    return FurView(
        fur.parts.mapValues { (_, part) ->
            checkCancelled()
            val wedges = shownWedges(part, camera, scale, figure, fur.figureRadius * BEFORE_RIM_SHARE)
            PartFurView(wedges, wedges.groupBy { it.slot }.map { (slot, held) -> FurFill(slot, wedgeMesh(held)) })
        },
    )
}

private fun shownWedges(part: PartFur, camera: FurCamera, scale: Float, figure: Float, beforeRim: Float): List<FurWedge> {
    val rule = part.rule
    val spacing = rule.spacingPixels * camera.density * scale / figure
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
        if (kotlin.math.abs(facing) >= rule.showNear) return@mapNotNull null
        val at = camera.toPixel(root.position) ?: return@mapNotNull null
        val drawnBack = smoothStep((kotlin.math.abs(facing) / rule.showNear - DRAW_BACK_FROM) / (1f - DRAW_BACK_FROM))
        val out = camera.toPixel(root.position + (root.normal - toViewer * facing))?.let { Vec2(it.x - at.x, it.y - at.y) } ?: Vec2(0f, 0f)
        val ownRim = -root.radius * facing
        val rimDepth = outlineRim(rims, at, out)?.let { minOf((it.center - root.position) dot toViewer, ownRim) } ?: ownRim
        wedge(root, rule.lengthPixels * camera.density * scale * size * spreadOf(root.id), size, drawnBack, toViewer, at, camera, beforeRim + rimDepth * RIM_MARGIN)
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

/** The grain at [root] as the viewer sees it, foreshortened, leaning outward from the surface. How far it runs outward depends only on how far the root lies from the rim, so a stroke clears the edge alike from every side. */
private fun seenDirection(root: FurRoot, toViewer: Vec3): Vec3 {
    val flat = root.direction - toViewer * (root.direction dot toViewer)
    val outward = root.normal - toViewer * (root.normal dot toViewer)
    return flat + outward * OUTWARD_LEAN
}

private fun wedge(root: FurRoot, lengthPixels: Float, size: Float, drawnBack: Float, toViewer: Vec3, at: Vec2, camera: FurCamera, back: Float): FurWedge? {
    val seen = seenDirection(root, toViewer)
    val crossing = seen cross toViewer
    if (crossing.length < MIN_LENGTH || lengthPixels <= 0f) return null
    val along = seen.normalized()
    val across = crossing.normalized()
    val behind = toViewer * back
    val perUnit = camera.toPixel(root.position + behind + across)?.let { pixelsBetween(it, at) }?.takeIf { it > 0f } ?: return null
    val length = lengthPixels * seen.length / (1f + OUTWARD_LEAN) / perUnit
    val halfBase = across * (length * BASE_SHARE / 2f)
    val origin = root.position + behind - tuck(root, toViewer, halfBase) - behindRim(root, toViewer, along * length) * drawnBack
    val tip = origin + along * length
    val hook = across * (length * BASE_SHARE * HOOK_SHARE * ((across dot root.normal) * HOOK_TURN).coerceIn(-1f, 1f))
    val middle = origin + along * (length * BEND_AT) + hook
    return FurWedge(root.id, root.position, root.normal, root.slot, edge(origin + halfBase, middle + halfBase * BEND_AT, tip), edge(origin - halfBase, middle - halfBase * BEND_AT, tip), size, toViewer * -(FILL_BEHIND_INK_PIXELS / perUnit))
}

/** How far inward a wedge moves for the outer corner of its base to sit where its root does, with no blunt corner past the rim. */
private fun tuck(root: FurRoot, toViewer: Vec3, halfBase: Vec3): Vec3 {
    val outward = root.normal - toViewer * (root.normal dot toViewer)
    if (outward.length < MIN_LENGTH) return Vec3.ZERO
    val out = outward.normalized()
    return out * kotlin.math.abs(halfBase dot out)
}

/** How far inward a wedge running along [run] moves for its tip to sit no further out than its root. */
private fun behindRim(root: FurRoot, toViewer: Vec3, run: Vec3): Vec3 {
    val outward = root.normal - toViewer * (root.normal dot toViewer)
    if (outward.length < MIN_LENGTH) return Vec3.ZERO
    val out = outward.normalized()
    return out * (run dot out).coerceAtLeast(0f)
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
            at = vertices.putCorner(at, wedge.left[step] + wedge.fillBehind, wedge.normal)
            at = vertices.putCorner(at, wedge.right[step] + wedge.fillBehind, wedge.normal)
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

package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3

private const val CLIP_COMPONENTS = 4

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

    private fun clip(point: Vec3, row: Int): Float =
        matrix[row] * point.x + matrix[CLIP_COMPONENTS + row] * point.y + matrix[2 * CLIP_COMPONENTS + row] * point.z + matrix[3 * CLIP_COMPONENTS + row]
}

/**
 * One fur stroke as a view shows it, in body space: from the two corners of its base along [left] and [right], which meet at [tip].
 * Each edge runs base to tip, for the ink; its fill lies [fillBehind] further from the viewer. [size] is its share of its full length, as
 * it grows entering the band, which its ink follows.
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

/** Points along one edge of a wedge. */
private const val EDGE_STEPS = 4

/** Where a stroke reaches its full size as its root turns away from the band's edge, as a share of the band. */
private const val FULL_SIZE_AT = 0.8f

/**
 * How many pixels a body unit spans at the root for a stroke to show its rule's whole length. A smaller view shrinks it in proportion, so
 * a tuft keeps its size against the part; a closer one keeps it, as a line keeps its weight.
 */
private const val FULL_LENGTH_PIXELS_PER_UNIT = 280f

/** How far past the silhouette a root's stroke lasts, as the cosine by which it faces away, shrinking to nothing there. */
private const val BEHIND_BAND = 0.15f

/** How close on screen a full stroke leaves no room for the next, as a share of the spacing; from there to the spacing, room opens smoothly. */
private const val NO_ROOM_SHARE = 0.5f

/** A stroke smaller than this share of its length is not drawn, too short to read. */
private const val LEAST_SIZE = 0.08f

/** How far every stroke leans outward from the surface, so its tip clears the silhouette. */
private const val OUTWARD_LEAN = 0.3f

/** How much further a grain running toward the viewer turns outward, so a stroke stays a stroke on a foreshortened part. */
private const val FORESHORTENED_OUTWARD = 0.5f

/** A stroke's length varies by this share either way, from its id, so a row does not read as a saw. */
private const val LENGTH_SPREAD = 0.3f
private const val SPREAD_STEPS = 64

/** How far back a wedge sits, as a share of how far its root lies before the part's rim: at the rim's depth, so the surface inside the rim covers it and the part's outline shell, just past the rim, does not. */
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
 * What [fur] shows through [camera]: of each part's strokes those whose root faces the viewer by under their rule's show near. Strokes
 * take room on screen in the order [bodyFur] gives them, one that seeds from their ids, so what falls where varies from stroke to stroke
 * and holds as the view turns. A stroke's size is smooth in the camera: it grows from nothing at the band's inner edge, shrinks to
 * nothing just past the silhouette, and shrinks by as much of the room as those before it, within the spacing, take in proportion to
 * their own size. So no stroke pops as the view turns. A wedge turns its width to the viewer and runs along the grain as the viewer sees
 * it. It sits back along its line of sight, past the rim of the part it roots on, so that part's surface covers all of it but what
 * breaks the silhouette, while it stays in front of what lies behind the part.
 */
fun furView(fur: BodyFur, camera: FurCamera, checkCancelled: () -> Unit = {}): FurView = FurView(
    fur.parts.mapValues { (_, part) ->
        checkCancelled()
        val wedges = shownWedges(part, camera, fur.figureRadius * BEFORE_RIM_SHARE)
        PartFurView(wedges, wedges.groupBy { it.slot }.map { (slot, held) -> FurFill(slot, wedgeMesh(held)) })
    },
)

private fun shownWedges(part: PartFur, camera: FurCamera, beforeRim: Float): List<FurWedge> {
    val rule = part.rule
    val room = RoomOnScreen(rule.spacingPixels * camera.density)
    return part.roots.mapNotNull { root ->
        val toViewer = camera.toViewerFrom(root.position)
        val facing = root.normal dot toViewer
        if (facing <= -BEHIND_BAND || facing >= rule.showNear) return@mapNotNull null
        val at = camera.toPixel(root.position) ?: return@mapNotNull null
        val size = sizeIn(facing, rule.showNear) * room.at(at)
        if (size < LEAST_SIZE) return@mapNotNull null
        room.take(at, size)
        wedge(root, rule.lengthPixels * camera.density * size * spreadOf(root.id), size, facing, toViewer, at, camera, beforeRim)
    }
}

private fun smoothStep(share: Float): Float {
    val held = share.coerceIn(0f, 1f)
    return held * held * (3f - 2f * held)
}

private fun sizeIn(facing: Float, showNear: Float): Float =
    smoothStep((showNear - facing) / (showNear * FULL_SIZE_AT)) * smoothStep((facing + BEHIND_BAND) / BEHIND_BAND)

private fun spreadOf(id: MarkId): Float {
    val step = (id.value.hashCode() and Int.MAX_VALUE) % SPREAD_STEPS
    return 1f + LENGTH_SPREAD * (2f * step / (SPREAD_STEPS - 1) - 1f)
}

/** The grain at [root] as the viewer sees it, turning outward from the surface as much as the grain runs toward the viewer. */
private fun seenDirection(root: FurRoot, toViewer: Vec3): Vec3 {
    val flat = root.direction - toViewer * (root.direction dot toViewer)
    val outward = root.normal - toViewer * (root.normal dot toViewer)
    return flat + outward * (OUTWARD_LEAN + FORESHORTENED_OUTWARD * (1f - flat.length).coerceAtLeast(0f))
}

private fun wedge(root: FurRoot, lengthPixels: Float, size: Float, facing: Float, toViewer: Vec3, at: Vec2, camera: FurCamera, beforeRim: Float): FurWedge? {
    val seen = seenDirection(root, toViewer)
    val crossing = seen cross toViewer
    if (crossing.length < MIN_LENGTH || lengthPixels <= 0f) return null
    val along = seen.normalized()
    val across = crossing.normalized()
    val perUnit = camera.toPixel(root.position + across)?.let { pixelsBetween(it, at) }?.takeIf { it > 0f } ?: return null
    val length = lengthPixels * (perUnit / (FULL_LENGTH_PIXELS_PER_UNIT * camera.density)).coerceAtMost(1f) / perUnit
    val behind = toViewer * (beforeRim - root.radius * facing * RIM_MARGIN)
    val halfBase = across * (length * BASE_SHARE / 2f)
    val origin = root.position + behind - tuck(root, toViewer, halfBase)
    val tip = origin + along * length
    val hook = across * (length * BASE_SHARE * HOOK_SHARE * if ((across dot root.normal) >= 0f) 1f else -1f)
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

/** The strokes that have taken room on screen so far, looked up by the cells of a grid as wide as the spacing. */
private class RoomOnScreen(private val spacing: Float) {
    private class Taken(val at: Vec2, val size: Float)

    private val cells = HashMap<Long, MutableList<Taken>>()

    /** The share of a stroke's size that those so far leave it at [at]. */
    fun at(at: Vec2): Float {
        val column = kotlin.math.floor(at.x / spacing).toInt()
        val row = kotlin.math.floor(at.y / spacing).toInt()
        var room = 1f
        for (x in column - 1..column + 1) {
            for (y in row - 1..row + 1) {
                cells[key(x, y)]?.forEach { room *= 1f - it.size * crowding(pixelsBetween(it.at, at)) }
            }
        }
        return room
    }

    fun take(at: Vec2, size: Float) {
        cells.getOrPut(key(kotlin.math.floor(at.x / spacing).toInt(), kotlin.math.floor(at.y / spacing).toInt())) { ArrayList() } += Taken(at, size)
    }

    private fun crowding(distance: Float): Float = 1f - smoothStep((distance / spacing - NO_ROOM_SHARE) / (1f - NO_ROOM_SHARE))

    private fun key(column: Int, row: Int) = (column.toLong() shl Int.SIZE_BITS) xor (row.toLong() and 0xFFFFFFFFL)
}

private fun pixelsBetween(a: Vec2, b: Vec2): Float = kotlin.math.hypot(a.x - b.x, a.y - b.y)

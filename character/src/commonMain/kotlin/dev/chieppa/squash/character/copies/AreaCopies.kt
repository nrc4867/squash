package dev.chieppa.squash.character.copies

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.model.CopySpec
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sqrt

/** One copy picked over an area: where on the parent, how it leans and turns, its size and its shade. */
internal class AreaPick(val u: Float, val v: Float, val lean: Float, val leanAround: Float, val turn: Float, val scale: Float, val shade: Int)

/**
 * Copies scattered over an area of a part: places picked by surface area, so a copy is as likely on a wide stretch as on a narrow one,
 * by the spec's seed, each kept only where it stands at least the spacing from every one kept before it; then each placed on the
 * surface along its normal, leaning and turning by its jitter.
 */
internal object AreaCopies {
    /** Cells along and round the area that the surface's area is read in. */
    private const val CELLS_U = 24
    private const val CELLS_V = 48

    /** How many tries a copy gets per copy asked for before the area counts as full. */
    private const val TRIES_PER_COPY = 30

    /** How far either side of a place, in u, the surface is read for which way it runs. */
    private const val STEP = 0.01f

    private const val TURN = (2.0 * PI).toFloat()

    fun pick(spec: CopySpec, parent: PlacedPart, flipV: Boolean, shades: Int): List<AreaPick> {
        if (spec.rows > 0) return inRows(spec, parent, flipV, shades)
        val area = spec.area
        val spanU = area.toU - area.fromU
        val spanV = if (area.aroundWhole) 1f else area.toV - area.fromV
        val weights = FloatArray(CELLS_U * CELLS_V)
        var total = 0f
        for (i in 0 until CELLS_U) for (j in 0 until CELLS_V) {
            val u0 = area.fromU + spanU * i / CELLS_U
            val u1 = area.fromU + spanU * (i + 1) / CELLS_U
            val v0 = area.fromV + spanV * j / CELLS_V
            val v1 = area.fromV + spanV * (j + 1) / CELLS_V
            val a = point(parent, u0, v0, flipV, spec.patch)
            val b = point(parent, u1, v0, flipV, spec.patch)
            val c = point(parent, u0, v1, flipV, spec.patch)
            val d = point(parent, u1, v1, flipV, spec.patch)
            val cell = if (a == null || b == null || c == null || d == null) 0f else (((b - a) cross (c - a)).length + ((b - d) cross (c - d)).length) / 2f
            total += cell
            weights[i * CELLS_V + j] = total
        }
        if (total <= 0f || spec.count <= 0) return emptyList()
        val random = CopyRandom(spec.seed)
        val kept = ArrayList<AreaPick>(spec.count)
        val near = NearGrid(spec.spacing)
        var tries = spec.count * TRIES_PER_COPY
        while (kept.size < spec.count && tries-- > 0) {
            val cell = weights.binaryFirstAtLeast(random.next() * total)
            val u = area.fromU + spanU * ((cell / CELLS_V) + random.next()) / CELLS_U
            val v = area.fromV + spanV * ((cell % CELLS_V) + random.next()) / CELLS_V
            val lean = spec.leanJitter * sqrt(random.next())
            val leanAround = random.next() * TURN
            val turn = spec.turnJitter * random.either()
            val size = 1f + spec.sizeJitter * random.either()
            val shade = floor(random.next() * (shades + 1)).toInt().coerceAtMost(shades)
            val at = point(parent, u, v, flipV, spec.patch) ?: continue
            if (!near.free(at)) continue
            near.add(at)
            kept += AreaPick(u, v, lean, leanAround, turn, spec.size * edgeShare(spec, u, v) * size, shade)
        }
        return kept
    }

    /**
     * Copies in [CopySpec.rows] rows evenly along the area, each row [CopySpec.count] evenly round it, every other row shifted round by the
     * spec's stagger of the gap; the seed picks each one's jitter alone. A row round the whole part closes on itself.
     */
    private fun inRows(spec: CopySpec, parent: PlacedPart, flipV: Boolean, shades: Int): List<AreaPick> {
        val area = spec.area
        if (spec.count <= 0) return emptyList()
        val spanV = if (area.aroundWhole) 1f else area.toV - area.fromV
        val random = CopyRandom(spec.seed)
        val picks = ArrayList<AreaPick>(spec.rows * spec.count)
        for (row in 0 until spec.rows) {
            val u = if (spec.rows == 1) (area.fromU + area.toU) / 2f else area.fromU + (area.toU - area.fromU) * row / (spec.rows - 1)
            val shift = if (row % 2 == 1) spec.stagger else 0f
            for (column in 0 until spec.count) {
                val across = when {
                    area.aroundWhole -> (column + shift) / spec.count
                    spec.count == 1 -> 0.5f
                    else -> ((column + shift) / (spec.count - 1)).coerceAtMost(1f)
                }
                val v = area.fromV + spanV * across
                val lean = spec.leanJitter * sqrt(random.next())
                val leanAround = random.next() * TURN
                val turn = spec.turnJitter * random.either()
                val size = 1f + spec.sizeJitter * random.either()
                val shade = floor(random.next() * (shades + 1)).toInt().coerceAtMost(shades)
                point(parent, u, v, flipV, spec.patch) ?: continue
                picks += AreaPick(u, v, lean, leanAround, turn, spec.size * edgeShare(spec, u, v) * size, shade)
            }
        }
        return picks
    }

    fun place(rule: CopyRule, parent: PlacedPart): List<CopyPlace> = rule.picks.mapNotNull { pick ->
        val v = wrapped(if (rule.flipV) 1f - pick.v else pick.v)
        val at = parent.pointOn(pick.u, v, rule.spec.patch) ?: return@mapNotNull null
        val normal = parent.normalAt(pick.u, v, rule.spec.patch) ?: return@mapNotNull null
        val out = (parent.outwardAt(pick.u, v, rule.spec.tilt, rule.spec.patch) ?: normal).normalized()
        val ahead = parent.pointOn((pick.u + STEP).coerceAtMost(1f), v, rule.spec.patch)
        val behind = parent.pointOn((pick.u - STEP).coerceAtLeast(0f), v, rule.spec.patch)
        val runs = if (ahead != null && behind != null) ahead - behind else Vec3.Y
        val droop = rule.spec.droop * DEGREES_TO_RADIANS
        val stands = if (droop == 0f) out else drooped(out, runs, droop)
        val leanAxis = squareTo(stands, runs).turnedAbout(stands, pick.leanAround)
        val along = stands.turnedAbout(leanAxis, pick.lean * DEGREES_TO_RADIANS)
        val facing = if (droop == 0f) runs else normal
        val up = squareTo(along, facing).turnedAbout(along, pick.turn * DEGREES_TO_RADIANS)
        val turn = (frameOf(along, up, rule.mirrored) * rule.definition.transposed).scaled(pick.scale)
        val origin = at + normal.normalized() * rule.spec.outset - along * (rule.depth * rule.spec.sink * pick.scale)
        CopyPlace(origin, turn, shadeSlot(rule.shades, pick.shade))
    }

    /** The share of the copies' size at ([u], [v]): 1 inside the area, falling to its falloff over its outer edge share. */
    private fun edgeShare(spec: CopySpec, u: Float, v: Float): Float {
        if (spec.falloff == 1f) return 1f
        val area = spec.area
        val halfU = (area.toU - area.fromU) / 2f
        val inU = if (halfU <= 0f) 1f else minOf(u - area.fromU, area.toU - u) / halfU
        val halfV = (area.toV - area.fromV) / 2f
        val inV = if (area.aroundWhole || halfV <= 0f) 1f else minOf(v - area.fromV, area.toV - v) / halfV
        val inside = minOf(inU, inV)
        val edge = spec.edge.coerceAtLeast(1e-3f)
        return spec.falloff + (1f - spec.falloff) * smooth(inside / edge)
    }

    private fun point(parent: PlacedPart, u: Float, v: Float, flipV: Boolean, patch: String?): Vec3? =
        parent.pointOn(u.coerceIn(0f, 1f), wrapped(if (flipV) 1f - v else v), patch)

    /** The first index whose running total reaches [value]. */
    private fun FloatArray.binaryFirstAtLeast(value: Float): Int {
        var low = 0
        var high = size - 1
        while (low < high) {
            val mid = (low + high) ushr 1
            if (this[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }
}

/** [v] taken into 0 to 1, as round a part. */
internal fun wrapped(v: Float): Float = v - floor(v)

/** The points kept so far, in cells as wide as [spacing], for asking whether a new one stands far enough from all of them. */
private class NearGrid(private val spacing: Float) {
    private val cells = HashMap<Triple<Int, Int, Int>, MutableList<Vec3>>()

    private fun cellOf(p: Vec3) = Triple(floor(p.x / spacing).toInt(), floor(p.y / spacing).toInt(), floor(p.z / spacing).toInt())

    fun free(p: Vec3): Boolean {
        if (spacing <= 0f) return true
        val (x, y, z) = cellOf(p)
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            cells[Triple(x + dx, y + dy, z + dz)]?.forEach { if ((it - p).length < spacing) return false }
        }
        return true
    }

    fun add(p: Vec3) {
        if (spacing > 0f) cells.getOrPut(cellOf(p)) { ArrayList() } += p
    }
}

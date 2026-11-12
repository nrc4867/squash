package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A primitive joined to what is listed before it by a smooth union of radius [blend]; the first's blend is not read. */
data class Blended(val primitive: Primitive, val blend: Float = 0f)

/** A flat floor at height [y] in the part's own axes: the solid is cut there by a smooth intersection of radius [blend]. */
data class Floor(val y: Float, val blend: Float)

/**
 * A turn of everything past [at] along x about the z axis through [at], by [radians] at full weight, eased in from [from] to
 * [to] along x: a forefoot laid flat on the ground while the foot behind it points down.
 */
data class Bend(val at: Vec3, val radians: Float, val from: Float, val to: Float)

/**
 * A cupping of [BlendedField.parts]: a point [halfWidth] up or down from [middle] reads the solid [depth] further toward the part's
 * side, the share growing as the square of how far up it lies, so the solid's edges come forward toward its negative side, a palm's.
 */
data class Cup(val depth: Float, val middle: Float, val halfWidth: Float)

/**
 * The digits joined to a [BlendedField]: each solid joined to those before it by its own blend, and the whole row to the field's
 * parts by a smooth union of [blend], or of the solid's own in [joins] where the solid nearest a point has one, so webs and roots fill in
 * near the parts and nowhere else.
 */
class FieldDigits(val parts: List<Blended>, val blend: Float, val joins: FloatArray? = null) {
    init {
        require(joins == null || joins.size == parts.size) { "${joins?.size} joins for ${parts.size} solids" }
    }

    /** The largest blend joining any solid to the field's parts. */
    val widest: Float get() = joins?.maxOrNull()?.let { maxOf(it, blend) } ?: blend
}

/**
 * A solid as one distance-like field in a part's own axes: [parts] joined in order by smooth unions, cupped by [cup], with [digits]
 * joined to them, cut by [floor], with space bent by [bend] before it is read. Negative inside.
 */
class BlendedField(
    val parts: List<Blended>,
    val floor: Floor? = null,
    val bend: Bend? = null,
    val digits: FieldDigits? = null,
    val cup: Cup? = null,
) {
    init {
        require(parts.isNotEmpty()) { "A field needs a primitive" }
    }

    fun value(p: Vec3): Float {
        val q = bend?.let { unbent(p, it) } ?: p
        val c = cup?.let { cupped(q, it) } ?: q
        var field = parts[0].primitive.distance(c)
        for (i in 1 until parts.size) field = smoothMin(field, parts[i].primitive.distance(c), parts[i].blend)
        digits?.let { row ->
            val (value, nearest) = digitsValue(q, row, field)
            field = smoothMin(field, value, row.joins?.getOrNull(nearest) ?: row.blend)
        }
        val cut = floor ?: return field
        return smoothMax(field, cut.y - q.y, cut.blend)
    }

    /** This field with no digits joined to it. */
    fun withoutDigits(): BlendedField = if (digits == null) this else BlendedField(parts, floor, bend, null, cup)

    /** The digits' own field at [q] and which solid lies nearest, skipping a capsule that cannot come within reach of it or [palm]. */
    private fun digitsValue(q: Vec3, digits: FieldDigits, palm: Float): Pair<Float, Int> {
        var field = Float.MAX_VALUE
        var nearest = -1
        var nearestValue = Float.MAX_VALUE
        val reach = digits.widest
        digits.parts.forEachIndexed { i, part ->
            val primitive = part.primitive
            if (primitive is Capsule) {
                val bound = primitive.lowerBound(q)
                if (bound >= field + part.blend && bound >= palm + reach) return@forEachIndexed
            }
            val d = primitive.distance(q)
            if (d < nearestValue) {
                nearestValue = d
                nearest = i
            }
            field = if (field == Float.MAX_VALUE) d else smoothMin(field, d, part.blend)
        }
        return field to nearest
    }

    private fun cupped(q: Vec3, cup: Cup): Vec3 {
        val across = ((q.y - cup.middle) / cup.halfWidth).coerceIn(-CUP_REACH, CUP_REACH)
        return Vec3(q.x, q.y, q.z + cup.depth * across * across)
    }

    /** The field's gradient at [p] by central differences [step] apart. */
    fun gradient(p: Vec3, step: Float): Vec3 {
        val dx = value(Vec3(p.x + step, p.y, p.z)) - value(Vec3(p.x - step, p.y, p.z))
        val dy = value(Vec3(p.x, p.y + step, p.z)) - value(Vec3(p.x, p.y - step, p.z))
        val dz = value(Vec3(p.x, p.y, p.z + step)) - value(Vec3(p.x, p.y, p.z - step))
        return Vec3(dx, dy, dz)
    }

    private val all: List<Blended> get() = parts + digits?.parts.orEmpty()

    /** The least corner of a box holding the solid, widened by the largest blend and the cup. */
    val low: Vec3 by lazy {
        val reach = reach()
        Vec3(all.minOf { it.primitive.low.x } - reach, all.minOf { it.primitive.low.y } - reach, all.minOf { it.primitive.low.z } - reach - cupReach())
    }

    /** The greatest corner of a box holding the solid, widened by the largest blend and the cup. */
    val high: Vec3 by lazy {
        val reach = reach()
        Vec3(all.maxOf { it.primitive.high.x } + reach, all.maxOf { it.primitive.high.y } + reach, all.maxOf { it.primitive.high.z } + reach + cupReach())
    }

    private fun cupReach(): Float = cup?.let { kotlin.math.abs(it.depth) * CUP_REACH * CUP_REACH } ?: 0f

    private fun reach(): Float {
        val bent = bend?.let { (high0() - it.at).length * kotlin.math.abs(sin(it.radians)) } ?: 0f
        return maxOf(all.maxOf { it.blend }, digits?.widest ?: 0f) + bent
    }

    private fun high0(): Vec3 = Vec3(all.maxOf { it.primitive.high.x }, all.maxOf { it.primitive.high.y }, all.maxOf { it.primitive.high.z })

    private companion object {
        /** How far out, in half widths, the cup keeps growing. */
        const val CUP_REACH = 1.5f
    }

    private fun unbent(p: Vec3, bend: Bend): Vec3 {
        val weight = smoothstep(min(bend.from, bend.to), max(bend.from, bend.to), p.x)
        if (weight == 0f) return p
        val angle = -bend.radians * weight
        val dx = p.x - bend.at.x
        val dy = p.y - bend.at.y
        val c = cos(angle)
        val s = sin(angle)
        return Vec3(bend.at.x + dx * c - dy * s, bend.at.y + dx * s + dy * c, p.z)
    }
}

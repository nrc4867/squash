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
 * A solid as one distance-like field in a part's own axes: [parts] joined in order by smooth unions, cut by [floor], with space bent by
 * [bend] before it is read. Negative inside.
 */
class BlendedField(val parts: List<Blended>, val floor: Floor? = null, val bend: Bend? = null) {
    init {
        require(parts.isNotEmpty()) { "A field needs a primitive" }
    }

    fun value(p: Vec3): Float {
        val q = bend?.let { unbent(p, it) } ?: p
        var field = parts[0].primitive.distance(q)
        for (i in 1 until parts.size) field = smoothMin(field, parts[i].primitive.distance(q), parts[i].blend)
        val cut = floor ?: return field
        return smoothMax(field, cut.y - q.y, cut.blend)
    }

    /** The field's gradient at [p] by central differences [step] apart. */
    fun gradient(p: Vec3, step: Float): Vec3 {
        val dx = value(Vec3(p.x + step, p.y, p.z)) - value(Vec3(p.x - step, p.y, p.z))
        val dy = value(Vec3(p.x, p.y + step, p.z)) - value(Vec3(p.x, p.y - step, p.z))
        val dz = value(Vec3(p.x, p.y, p.z + step)) - value(Vec3(p.x, p.y, p.z - step))
        return Vec3(dx, dy, dz)
    }

    /** The least corner of a box holding the solid, widened by the largest blend. */
    val low: Vec3 by lazy {
        val reach = reach()
        Vec3(parts.minOf { it.primitive.low.x } - reach, parts.minOf { it.primitive.low.y } - reach, parts.minOf { it.primitive.low.z } - reach)
    }

    /** The greatest corner of a box holding the solid, widened by the largest blend. */
    val high: Vec3 by lazy {
        val reach = reach()
        Vec3(parts.maxOf { it.primitive.high.x } + reach, parts.maxOf { it.primitive.high.y } + reach, parts.maxOf { it.primitive.high.z } + reach)
    }

    private fun reach(): Float {
        val bent = bend?.let { (high0() - it.at).length * kotlin.math.abs(sin(it.radians)) } ?: 0f
        return parts.maxOf { it.blend } + bent
    }

    private fun high0(): Vec3 = Vec3(parts.maxOf { it.primitive.high.x }, parts.maxOf { it.primitive.high.y }, parts.maxOf { it.primitive.high.z })

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

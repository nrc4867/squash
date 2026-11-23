package dev.chieppa.squash.recipe.figure

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField

/** The shortest and longest step a ray takes through the field, in body units. */
private const val LEAST_STEP = 0.0005
private const val LONGEST_STEP = 0.03

/** A member whose box lies further than this from a point, in body units, is read there by its box alone. */
private const val FAR_MEMBER = 0.02f

/** A share of the field's value a ray steps by: under 1, since the field is only near the true distance close to the surface. */
private const val STEP_SHARE = 0.8

/**
 * The silhouette a body's joined field casts in a figure's front or side view, read straight from the field with no mesh and no render:
 * a view place is inside when a ray through it along the view's depth meets the field below 0.
 */
class FieldSilhouette(private val field: JoinedField, private val rootHeight: Double) : Silhouette {
    private val members = field.members
    private val owns = FloatArray(members.size)

    /** How far past the members' boxes a smooth union can reach. */
    private val margin = members.maxOfOrNull { it.blend } ?: 0f

    /** The joined field at body point [p]: a member further than its blend from [p] counts by the distance to its box, which it lies at least as far as. */
    fun valueAt(p: Vec3): Float {
        members.forEachIndexed { i, m ->
            val box = m.boxDistance(p)
            owns[i] = if (box > FAR_MEMBER + margin) box else m.own(p)
        }
        return field.union(owns, p)
    }

    override fun inside(view: FigureView, q: P2): Boolean {
        if (view == FigureView.TOP) return insideFromAbove(q)
        val y = (q.y - rootHeight).toFloat()
        val across = q.x.toFloat()
        var from = Float.MAX_VALUE
        var to = -Float.MAX_VALUE
        for (m in members) {
            val (lowAcross, highAcross) = if (view == FigureView.FRONT) -m.high.z to -m.low.z else m.low.x to m.high.x
            if (y < m.low.y - margin || y > m.high.y + margin || across < lowAcross - margin || across > highAcross + margin) continue
            if (view == FigureView.FRONT) {
                from = minOf(from, m.low.x)
                to = maxOf(to, m.high.x)
            } else {
                from = minOf(from, m.low.z)
                to = maxOf(to, m.high.z)
            }
        }
        var t = from.toDouble()
        while (t <= to) {
            val p = if (view == FigureView.FRONT) Vec3(t.toFloat(), y, -across) else Vec3(across, y, t.toFloat())
            val f = valueAt(p)
            if (f < 0f) return true
            t += (f * STEP_SHARE).coerceIn(LEAST_STEP, LONGEST_STEP)
        }
        return false
    }

    /** Whether a ray straight down through the top view's place [q] meets the field below 0. */
    private fun insideFromAbove(q: P2): Boolean {
        val forward = q.y.toFloat()
        val right = -q.x.toFloat()
        var from = Float.MAX_VALUE
        var to = -Float.MAX_VALUE
        for (m in members) {
            if (forward < m.low.x - margin || forward > m.high.x + margin || right < m.low.z - margin || right > m.high.z + margin) continue
            from = minOf(from, m.low.y)
            to = maxOf(to, m.high.y)
        }
        var t = from.toDouble()
        while (t <= to) {
            val f = valueAt(Vec3(forward, t.toFloat(), right))
            if (f < 0f) return true
            t += (f * STEP_SHARE).coerceIn(LEAST_STEP, LONGEST_STEP)
        }
        return false
    }
}

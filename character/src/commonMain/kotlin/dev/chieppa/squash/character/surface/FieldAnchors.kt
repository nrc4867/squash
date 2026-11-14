package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** How many steps along the field's gradient an anchor takes at most to reach the surface. */
private const val MOST_STEPS = 8

/** How near zero, as a share of the field's cell, the field must be for an anchor to stop stepping. */
private const val CLOSE_SHARE = 1e-3f

/** The step of the central differences the gradient is read with, as a share of the field's cell. */
private const val GRADIENT_SHARE = 0.25f

private const val TINY = 1e-12f

/** How much the field must change along a line, per unit of its length, for a point to be moved onto the surface along that line. */
private const val LEAST_SLOPE = 0.2f

/**
 * Anchors resolved on a character's field rather than on any mesh: an anchor `(part, patch, u, v, offset)` starts at its point on its
 * run's own swept surface at (u, v), in body space, then moves along the joined field's gradient to where the field is zero, and off by its
 * offset along the normal there. On a part's own share it lands where it would without the join, moved a little by the blend; on a fillet
 * it lands on the fillet. Null for an anchor naming a part or patch the field does not hold.
 */
class FieldAnchors(private val field: CharacterField) {
    private val close = field.cell * CLOSE_SHARE
    private val step = field.cell * GRADIENT_SHARE

    fun resolve(anchor: Anchor): ResolvedPoint? {
        val run = field.runs.firstOrNull { it.partId == anchor.part && it.patch.name == anchor.patch } ?: return null
        val start = run.part.surface.pointAt(anchor.copy(offset = 0f)) ?: return null
        val (position, normal) = onSurface(start.position + run.part.origin, start.normal)
        return ResolvedPoint(anchor, position + normal * anchor.offset, normal)
    }

    /**
     * [from] moved along the field's gradient there to where the field is zero, by false position along that line, with the surface's
     * normal where it lands, or [fallback] where the field has no gradient.
     */
    fun onSurface(from: Vec3, fallback: Vec3): Pair<Vec3, Vec3> {
        val start = field.field.value(from)
        val direction = gradient(from)
        val squared = direction dot direction
        if (squared < TINY || abs(start) <= close) return from to (if (squared < TINY) fallback else direction.normalized())
        val unit = direction * (1f / sqrt(squared))
        var near = 0f
        var nearValue = start
        var far = -start / sqrt(squared)
        var farValue = field.field.value(from + unit * far)
        var steps = 0
        while (abs(farValue) > close && steps < MOST_STEPS && farValue != nearValue) {
            val next = far - farValue * (far - near) / (farValue - nearValue)
            near = far
            nearValue = farValue
            far = next
            farValue = field.field.value(from + unit * far)
            steps++
        }
        val p = from + unit * far
        val normal = gradient(p)
        return p to if ((normal dot normal) > TINY) normal.normalized() else fallback
    }

    /**
     * [from] moved along the line through it along [direction] to where the field is zero, by false position, with the surface's normal
     * where it lands; as [onSurface] where the field barely changes along that line.
     */
    fun onSurfaceAlong(from: Vec3, direction: Vec3): Pair<Vec3, Vec3> {
        val start = field.field.value(from)
        if (abs(start) <= close) return onSurface(from, direction)
        val unit = direction.normalized()
        val slope = gradient(from) dot unit
        if (abs(slope) < LEAST_SLOPE) return onSurface(from, direction)
        var near = 0f
        var nearValue = start
        var far = -start / slope
        var farValue = field.field.value(from + unit * far)
        var steps = 0
        while (abs(farValue) > close && steps < MOST_STEPS && farValue != nearValue) {
            val next = far - farValue * (far - near) / (farValue - nearValue)
            near = far
            nearValue = farValue
            far = next
            farValue = field.field.value(from + unit * far)
            steps++
        }
        if (abs(farValue) > close) return onSurface(from, direction)
        val p = from + unit * far
        val normal = gradient(p)
        return p to if ((normal dot normal) > TINY) normal.normalized() else direction
    }

    /** The joined field's gradient at [p], by central differences. */
    fun gradient(p: Vec3): Vec3 {
        val f = field.field
        val x = f.value(p + Vec3(step, 0f, 0f)) - f.value(p - Vec3(step, 0f, 0f))
        val y = f.value(p + Vec3(0f, step, 0f)) - f.value(p - Vec3(0f, step, 0f))
        val z = f.value(p + Vec3(0f, 0f, step)) - f.value(p - Vec3(0f, 0f, step))
        return Vec3(x, y, z) * (0.5f / step)
    }
}

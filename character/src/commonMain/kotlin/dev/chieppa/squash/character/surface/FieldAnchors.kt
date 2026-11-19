package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** How many steps along the field's gradient an anchor takes at most to reach the surface. */
private const val MOST_STEPS = 8

/** How near zero, as a share of the cell its run meshes at, the field must be for an anchor to stop stepping. */
private const val CLOSE_SHARE = 1e-3f

/** The step of the central differences the gradient is read with, as a share of the cell its run meshes at. */
private const val GRADIENT_SHARE = 0.25f

/** How many cells from its own run's surface, past the blends it joins by, a place resolved on its own run may land. */
private const val OWN_CELLS = 2f

/** How much of a smooth union's radius it bulges the surface out past the members it joins. */
private const val BLEND_BULGE = 0.25f

private const val TINY = 1e-12f

/** How much the field must change along a line, per unit of its length, for a point to be moved onto the surface along that line. */
private const val LEAST_SLOPE = 0.2f

/**
 * Anchors resolved on a character's field rather than on any mesh: an anchor `(part, patch, u, v, offset)` starts at its point on its
 * run's own swept surface at (u, v), in body space, then moves along the joined field's gradient to where the field is zero, and off by its
 * offset along the normal there. On a part's own share it lands where it would without the join, moved a little by the blend; where
 * parts blend it lands on the blend. Null for an anchor naming a part or patch the field does not hold. The gradient is read at the scale
 * of the cell the anchor's run meshes at (see [digitLevels]), so a face's anchors read its field as finely as its mesh is made.
 */
class FieldAnchors(private val field: CharacterField) {
    private val levels by lazy { digitLevels(field) }

    /** The cell run [part]'s patch [patch] meshes at: the field's cell halved once for each of its level's steps; the field's own for a run it does not hold. */
    fun cellOf(part: PartId, patch: String): Float {
        val run = field.runs.indexOfFirst { it.partId == part && it.patch.name == patch }
        return if (run < 0) field.cell else field.cell / (1 shl levels[run])
    }

    fun resolve(anchor: Anchor): ResolvedPoint? {
        val run = field.runs.firstOrNull { it.partId == anchor.part && it.patch.name == anchor.patch } ?: return null
        val start = run.part.surface.pointAt(anchor.copy(offset = 0f)) ?: return null
        val (position, normal) = onSurface(start.position + run.part.origin, start.normal, cellOf(anchor.part, anchor.patch))
        return ResolvedPoint(anchor, position + normal * anchor.offset, normal)
    }

    /**
     * [anchor] as [resolve] places it, but only where it lands on its own run's surface or the blend that joins it: null where the join
     * hides the place under another part's cloth, as a place on an arm's side next to the torso is, so no line is drawn on that part's
     * outside instead.
     */
    fun resolveOnOwn(anchor: Anchor): ResolvedPoint? {
        val point = resolve(anchor) ?: return null
        val run = field.runs.indexOfFirst { it.partId == anchor.part && it.patch.name == anchor.patch }
        val member = field.field.members[run]
        val slack = field.field.reaches[run] * BLEND_BULGE + cellOf(anchor.part, anchor.patch) * OWN_CELLS
        val on = point.position - point.normal * anchor.offset
        val start = field.runs[run].part.surface.pointAt(anchor.copy(offset = 0f)) ?: return null
        val buried = field.field.value(start.position + field.runs[run].part.origin) < -(member.ease + slack)
        val moved = (on - (start.position + field.runs[run].part.origin)).length
        return point.takeIf { !buried && abs(member.own(on)) <= slack + field.field.reaches[run] && moved <= member.ease + slack * 2f }
    }

    /**
     * [from] moved along the field's gradient there to where the field is zero, by false position along that line, with the surface's
     * normal where it lands, or [fallback] where the field has no gradient; the gradient read at the scale of [cell].
     */
    fun onSurface(from: Vec3, fallback: Vec3, cell: Float = field.cell): Pair<Vec3, Vec3> {
        val close = cell * CLOSE_SHARE
        val start = field.field.value(from)
        val direction = gradient(from, cell)
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
        val normal = gradient(p, cell)
        return p to if ((normal dot normal) > TINY) normal.normalized() else fallback
    }

    /**
     * [from] moved along the line through it along [direction] to where the field is zero, by false position, with the surface's normal
     * where it lands; as [onSurface] where the field barely changes along that line. The gradient is read at the scale of [cell].
     */
    fun onSurfaceAlong(from: Vec3, direction: Vec3, cell: Float = field.cell): Pair<Vec3, Vec3> {
        val close = cell * CLOSE_SHARE
        val start = field.field.value(from)
        if (abs(start) <= close) return onSurface(from, direction, cell)
        val unit = direction.normalized()
        val slope = gradient(from, cell) dot unit
        if (abs(slope) < LEAST_SLOPE) return onSurface(from, direction, cell)
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
        if (abs(farValue) > close) return onSurface(from, direction, cell)
        val p = from + unit * far
        val normal = gradient(p, cell)
        return p to if ((normal dot normal) > TINY) normal.normalized() else direction
    }

    /** The joined field's gradient at [p], by central differences a share of [cell] apart. */
    fun gradient(p: Vec3, cell: Float = field.cell): Vec3 {
        val step = cell * GRADIENT_SHARE
        val f = field.field
        val x = f.value(p + Vec3(step, 0f, 0f)) - f.value(p - Vec3(step, 0f, 0f))
        val y = f.value(p + Vec3(0f, step, 0f)) - f.value(p - Vec3(0f, step, 0f))
        val z = f.value(p + Vec3(0f, 0f, step)) - f.value(p - Vec3(0f, 0f, step))
        return Vec3(x, y, z) * (0.5f / step)
    }
}

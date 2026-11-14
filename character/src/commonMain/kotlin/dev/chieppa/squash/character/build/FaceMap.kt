package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Below this a triangle seen from ahead has no area and is passed over. */
private const val MIN_AREA = 1e-12f

/** Where a ray from straight ahead meets a head first, at a place on its flat face that [flat] gives each point in. */
internal fun interface FaceCast {
    fun hit(x: Float, y: Float, flat: (Vec3) -> Triple<Float, Float, Float>): Anchor?
}

/**
 * A head's face as a flat drawing, seen from straight ahead: x to the character's left, y up, in the units its outlines are drawn in,
 * from the part's origin. A place on it maps to the anchor on the body patch that a ray from straight ahead meets first, so what is
 * drawn on the flat face lands where a front view would show it. Built from the head as drawn, it holds for that drawing; anchors keep
 * their u and v when the head is drawn again, as every mark's do. A face map of a head on a one surface casts its rays at that surface.
 */
class FaceMap internal constructor(facing: Vec3, private val scale: Float, private val cast: FaceCast) {
    private val ahead = facing.normalized()
    private val up = (Vec3.Y - ahead * (Vec3.Y dot ahead)).normalized()
    private val left = up cross ahead

    /** The anchor at ([x], [y]) on the flat face; with [left] false, at the mirror place on the right. Null off the face. */
    fun at(x: Float, y: Float, left: Boolean = true): Anchor? = hit(if (left) x else -x, y)

    /** As [at], for a place known to lie on the face. */
    fun on(x: Float, y: Float, left: Boolean = true): Anchor = requireNotNull(at(x, y, left)) { "($x, $y) lies off the face" }

    /** A closed ring of [points] places round ([x], [y]), [rx] across and [ry] up, its first repeated last. */
    fun ring(x: Float, y: Float, rx: Float, ry: Float, left: Boolean, points: Int = RING_POINTS): List<Anchor> = List(points + 1) { i ->
        val angle = 2.0 * PI * (i % points) / points
        on(x + rx * cos(angle).toFloat(), y + ry * sin(angle).toFloat(), left)
    }

    private fun flat(p: Vec3) = Triple((p dot left) / scale, (p dot up) / scale, p dot ahead)

    private fun hit(x: Float, y: Float): Anchor? = cast.hit(x, y, ::flat)

    companion object {
        const val RING_POINTS = 16

        /** The face of [part], a [template] head built from [data], or null when it builds no body patch. */
        fun of(part: PartId, template: PartTemplate, data: PartData): FaceMap? {
            val body = PartBuilder.build(template, data).firstOrNull { it.name == BODY_PATCH } ?: return null
            val scale = data.params[SIZE_PARAM] ?: template.defaults[SIZE_PARAM] ?: 1f
            return FaceMap(PartBuilder.facing(template), scale, GridCast(part, body))
        }
    }
}

/** Rays cast at the triangles of [patch]'s grid, [part]'s body patch built alone. */
private class GridCast(private val part: PartId, private val patch: Patch) : FaceCast {
    override fun hit(x: Float, y: Float, flat: (Vec3) -> Triple<Float, Float, Float>): Anchor? {
        var best: Anchor? = null
        var nearest = Float.NEGATIVE_INFINITY
        val us = patch.ringUs
        val columns = patch.segments
        for (ring in 0 until patch.nu - 1) {
            for (column in 0 until columns) {
                val next = (column + 1) % patch.nv
                val corners = listOf(ring to column, ring to next, ring + 1 to next, ring + 1 to column)
                val flats = corners.map { (r, c) -> flat(patch.point(patch.index(r, c))) }
                val uvs = listOf(us[ring] to column, us[ring] to column + 1, us[ring + 1] to column + 1, us[ring + 1] to column)
                for (triangle in TRIANGLES) {
                    val (a, b, c) = triangle.map { flats[it] }
                    val area = (b.first - a.first) * (c.second - a.second) - (c.first - a.first) * (b.second - a.second)
                    if (area * area < MIN_AREA) continue
                    val wb = ((x - a.first) * (c.second - a.second) - (c.first - a.first) * (y - a.second)) / area
                    val wc = ((b.first - a.first) * (y - a.second) - (x - a.first) * (b.second - a.second)) / area
                    val wa = 1f - wb - wc
                    if (wa < 0f || wb < 0f || wc < 0f) continue
                    val depth = wa * a.third + wb * b.third + wc * c.third
                    if (depth <= nearest) continue
                    nearest = depth
                    val (ua, ub, uc) = triangle.map { uvs[it] }
                    val u = wa * ua.first + wb * ub.first + wc * uc.first
                    val v = (wa * ua.second + wb * ub.second + wc * uc.second) / patch.nv
                    best = Anchor(part, BODY_PATCH, u, v - floor(v))
                }
            }
        }
        return best
    }

    private companion object {
        val TRIANGLES = listOf(listOf(0, 1, 2), listOf(0, 2, 3))
    }
}

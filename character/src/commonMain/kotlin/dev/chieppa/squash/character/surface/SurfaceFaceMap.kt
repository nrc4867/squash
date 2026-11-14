package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.FaceCast
import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD
import kotlin.math.floor

/** Below this a triangle seen from ahead has no area and is passed over. */
private const val MIN_AREA = 1e-12f

/**
 * The face of [part], a head on this one surface drawn at [scale], its size, as a [FaceMap]: its rays meet the surface's triangles where
 * the head's body run holds every corner, and a hit reads that run's (u, v) across the triangle. Null for a part the surface does not hold.
 */
fun CharacterSurface.faceMap(part: PartId, scale: Float): FaceMap? {
    val member = field.runs.indexOfFirst { it.partId == part && it.patch.name == BODY_PATCH }.takeIf { it >= 0 } ?: return null
    val placed = field.runs[member].part
    return FaceMap(placed.surface.shown(PartBuilder.facing(placed.template)), scale, SurfaceCast(this, member, placed.origin))
}

private class SurfaceCast(private val surface: CharacterSurface, private val member: Int, private val origin: Vec3) : FaceCast {
    private val mesh = surface.mesh
    private val closed = surface.field.runs[member].patch.closedV

    override fun hit(x: Float, y: Float, flat: (Vec3) -> Triple<Float, Float, Float>): Anchor? {
        val t = mesh.triangles
        var best: Anchor? = null
        var nearest = Float.NEGATIVE_INFINITY
        val slots = IntArray(3)
        for (tri in 0 until mesh.triangleCount) {
            var held = true
            for (c in 0 until 3) {
                val slot = slotOf(t[tri * 3 + c])
                if (slot < 0) held = false else slots[c] = slot
            }
            if (!held) continue
            val (a, b, c) = (0 until 3).map { flat(mesh.position(t[tri * 3 + it]) - origin) }
            val area = (b.first - a.first) * (c.second - a.second) - (c.first - a.first) * (b.second - a.second)
            if (area * area < MIN_AREA) continue
            val wb = ((x - a.first) * (c.second - a.second) - (c.first - a.first) * (y - a.second)) / area
            val wc = ((b.first - a.first) * (y - a.second) - (x - a.first) * (b.second - a.second)) / area
            val wa = 1f - wb - wc
            if (wa < 0f || wb < 0f || wc < 0f) continue
            val depth = wa * a.third + wb * b.third + wc * c.third
            if (depth <= nearest) continue
            nearest = depth
            val weights = floatArrayOf(wa, wb, wc)
            var u = 0f
            var v = 0f
            val first = mesh.heldV[t[tri * 3] * HELD + slots[0]]
            for (k in 0 until 3) {
                val at = t[tri * 3 + k] * HELD + slots[k]
                u += mesh.heldU[at] * weights[k]
                var around = mesh.heldV[at]
                if (closed && around - first > 0.5f) around -= 1f
                if (closed && first - around > 0.5f) around += 1f
                v += around * weights[k]
            }
            best = Anchor(surface.field.runs[member].partId, BODY_PATCH, u, v - floor(v))
        }
        return best
    }

    private fun slotOf(v: Int): Int {
        for (k in 0 until HELD) if (mesh.held[v * HELD + k] == member) return k
        return -1
    }
}

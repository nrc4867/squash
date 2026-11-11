package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How far a winding number must reach for a point to be inside. */
private const val INSIDE_WINDING = 0.5

/**
 * Whether points lie inside a part's whole mesh, every face it has hidden or not, by the generalized winding number: the solid angle
 * its faces cover seen from the point, over a whole sphere's. An open tube still reads its inside, as its ends add little.
 */
class Winding(part: PlacedPart) {
    private val corners: DoubleArray
    private val low: Vec3
    private val high: Vec3

    init {
        val mesh = part.surface.mesh
        val faces = part.hiding?.indices ?: mesh.indices
        corners = DoubleArray(faces.size * 3)
        var lx = Float.POSITIVE_INFINITY; var ly = Float.POSITIVE_INFINITY; var lz = Float.POSITIVE_INFINITY
        var hx = Float.NEGATIVE_INFINITY; var hy = Float.NEGATIVE_INFINITY; var hz = Float.NEGATIVE_INFINITY
        faces.forEachIndexed { i, vertex ->
            val p = part.point(vertex)
            corners[i * 3] = p.x.toDouble(); corners[i * 3 + 1] = p.y.toDouble(); corners[i * 3 + 2] = p.z.toDouble()
            lx = min(lx, p.x); ly = min(ly, p.y); lz = min(lz, p.z)
            hx = max(hx, p.x); hy = max(hy, p.y); hz = max(hz, p.z)
        }
        low = Vec3(lx, ly, lz)
        high = Vec3(hx, hy, hz)
    }

    fun inside(p: Vec3): Boolean =
        p.x in low.x..high.x && p.y in low.y..high.y && p.z in low.z..high.z && number(p) > INSIDE_WINDING

    /** The winding number at [p], by Van Oosterom and Strackee's solid angle of each triangle. */
    fun number(p: Vec3): Double {
        var sum = 0.0
        var i = 0
        while (i < corners.size) {
            val ax = corners[i] - p.x; val ay = corners[i + 1] - p.y; val az = corners[i + 2] - p.z
            val bx = corners[i + 3] - p.x; val by = corners[i + 4] - p.y; val bz = corners[i + 5] - p.z
            val cx = corners[i + 6] - p.x; val cy = corners[i + 7] - p.y; val cz = corners[i + 8] - p.z
            val la = sqrt(ax * ax + ay * ay + az * az); val lb = sqrt(bx * bx + by * by + bz * bz); val lc = sqrt(cx * cx + cy * cy + cz * cz)
            val det = ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)
            val div = la * lb * lc + (ax * bx + ay * by + az * bz) * lc + (bx * cx + by * cy + bz * cz) * la + (cx * ax + cy * ay + cz * az) * lb
            sum += 2.0 * atan2(det, div)
            i += 9
        }
        return sum / (4.0 * PI)
    }
}

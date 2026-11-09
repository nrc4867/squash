package dev.chieppa.squash.geom

/** How many points round a ring its section is read at for the inside test. */
private const val SECTION_POINTS = 32

/**
 * Whether a point lies inside a shell lofted from slices: between two of its rings along their axis, and inside the section of the
 * nearer, scaled to its height and width. Built from [patch]'s slices placed by [place] for a point and [turn] for a direction, so a
 * shell built in a part's own space answers for points in body space. A patch with no slices holds nothing inside.
 */
class ShellInside(patch: Patch, place: (Vec3) -> Vec3 = { it }, turn: (Vec3) -> Vec3 = { it }) {
    private val rings: List<Ring> = patch.slices.orEmpty().map { slice ->
        val frame = slice.frame
        Ring(place(frame.center), turn(frame.tangent), turn(frame.side), turn(frame.up), slice.height / 2f, slice.width / 2f, slice.section.sample(SECTION_POINTS))
    }

    private class Ring(val center: Vec3, val tangent: Vec3, val side: Vec3, val up: Vec3, val halfHeight: Float, val halfWidth: Float, val section: List<Vec2>)

    /** Whether [point] lies inside. */
    operator fun contains(point: Vec3): Boolean {
        for (i in 0 until rings.size - 1) {
            val a = rings[i]
            val b = rings[i + 1]
            val ahead = (point - a.center) dot a.tangent
            val behind = (point - b.center) dot b.tangent
            if (ahead < 0f || behind > 0f) continue
            val ring = if (ahead <= -behind) a else b
            if (ring.halfHeight <= 0f || ring.halfWidth <= 0f) continue
            val offset = point - ring.center
            val x = (offset dot ring.side) / ring.halfWidth
            val y = (offset dot ring.up) / ring.halfHeight
            if (insidePolygon(Vec2(x, y), ring.section)) return true
        }
        return false
    }

    private fun insidePolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

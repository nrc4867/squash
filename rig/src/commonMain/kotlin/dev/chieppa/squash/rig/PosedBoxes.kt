package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3

/**
 * Each member of a [PosedField]'s box as a pose takes it, the box round its rest box carried by each of its pieces' [maps] ([PosedField.maps]), grown by
 * [cell].
 */
internal class PosedBoxes(field: PosedField, maps: Array<Affine?>, cell: Float) {
    private val count = field.members.size
    private val lows = FloatArray(count * 3)
    private val highs = FloatArray(count * 3)

    init {
        for (m in 0 until count) {
            val member = field.members[m]
            var low = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            var high = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (piece in field.pieces[m]) {
                val map = maps[piece.slot]
                for (corner in 0 until 8) {
                    val q = Vec3(
                        if (corner and 1 == 0) member.low.x else member.high.x,
                        if (corner and 2 == 0) member.low.y else member.high.y,
                        if (corner and 4 == 0) member.low.z else member.high.z,
                    )
                    val p = map?.invoke(q) ?: q
                    low = Vec3(minOf(low.x, p.x), minOf(low.y, p.y), minOf(low.z, p.z))
                    high = Vec3(maxOf(high.x, p.x), maxOf(high.y, p.y), maxOf(high.z, p.z))
                }
            }
            lows.put(m * 3, low - Vec3(cell, cell, cell))
            highs.put(m * 3, high + Vec3(cell, cell, cell))
        }
    }

    /** How far posed point [p] lies outside member [m]'s posed box; 0 inside it. */
    fun distance(m: Int, p: Vec3): Float {
        val dx = maxOf(lows[m * 3] - p.x, 0f, p.x - highs[m * 3])
        val dy = maxOf(lows[m * 3 + 1] - p.y, 0f, p.y - highs[m * 3 + 1])
        val dz = maxOf(lows[m * 3 + 2] - p.z, 0f, p.z - highs[m * 3 + 2])
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

}

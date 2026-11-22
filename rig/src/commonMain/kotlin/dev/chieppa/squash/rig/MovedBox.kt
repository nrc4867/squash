package dev.chieppa.squash.rig

/**
 * The box round every vertex of a posed surface that the pose moved from where it rests, at rest and posed, grown by [reach]; null where
 * the pose moved none. A point outside it lies further than [reach] from any vertex the pose moved.
 */
internal fun movedBox(rest: FloatArray, posed: FloatArray, reach: Float): FloatArray? {
    val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    var any = false
    for (v in 0 until minOf(rest.size, posed.size) / 3) {
        if (!moved(rest, posed, v)) continue
        any = true
        for (k in 0 until 3) {
            box[k] = minOf(box[k], rest[v * 3 + k], posed[v * 3 + k])
            box[k + 3] = maxOf(box[k + 3], rest[v * 3 + k], posed[v * 3 + k])
        }
    }
    if (!any) return null
    for (k in 0 until 3) {
        box[k] -= reach
        box[k + 3] += reach
    }
    return box
}

/** Whether vertex [v] lies elsewhere in [posed] than in [rest]. */
internal fun moved(rest: FloatArray, posed: FloatArray, v: Int): Boolean =
    rest[v * 3] != posed[v * 3] || rest[v * 3 + 1] != posed[v * 3 + 1] || rest[v * 3 + 2] != posed[v * 3 + 2]

/** Whether vertex [v] of [points] lies inside [box]. */
internal fun inBox(box: FloatArray, points: FloatArray, v: Int): Boolean =
    points[v * 3] >= box[0] && points[v * 3] <= box[3] && points[v * 3 + 1] >= box[1] && points[v * 3 + 1] <= box[4] && points[v * 3 + 2] >= box[2] && points[v * 3 + 2] <= box[5]

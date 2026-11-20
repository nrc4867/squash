package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3

/** How far, in root radii and longest first, a run on its parent's closed end may carry on past its root into the parent. */
val ROOT_REACHES: List<Float> = listOf(1f, 0.75f, 0.5f, 0.25f)

/**
 * How far a run whose first ring is [ring] carries on past it, back along [back] (a unit vector away from the run), into a parent whose
 * field [parent] gives: the longest of [ROOT_REACHES] of [radius] at which the ring moved back that far lies wholly inside the parent;
 * 0 where none does.
 */
fun rootReach(ring: List<Vec3>, back: Vec3, radius: Float, parent: (Vec3) -> Float): Float {
    if (radius <= 0f || ring.isEmpty()) return 0f
    val share = ROOT_REACHES.firstOrNull { share -> ring.all { parent(it + back * (radius * share)) < 0f } } ?: return 0f
    return share * radius
}

/**
 * The run of rings [points] with its first ring laid again [reach] back along its first tangent, and the same for [tangents], [ups],
 * [us] and [middles], the new ring's u where the run's u carries on to there, so a run's solid goes on into its parent past its root
 * unchanged instead of ending flat there; the run as given where [reach] is 0 or it has fewer than two rings.
 */
class RootedRings(points: List<List<Vec3>>, tangents: List<Vec3>, ups: List<Vec3>, us: FloatArray, middles: List<Vec3?>?, reach: Float) {
    val points: List<List<Vec3>>
    val tangents: List<Vec3>
    val ups: List<Vec3>
    val us: FloatArray
    val middles: List<Vec3?>?

    init {
        if (reach <= 0f || points.size < 2) {
            this.points = points
            this.tangents = tangents
            this.ups = ups
            this.us = us
            this.middles = middles
        } else {
            val back = tangents[0].normalized() * reach
            val gap = (centroid(points[1]) - centroid(points[0])).length
            val perU = if (us[1] > us[0] && gap > 0f) gap / (us[1] - us[0]) else 1f
            this.points = listOf(points[0].map { it - back }) + points
            this.tangents = listOf(tangents[0]) + tangents
            this.ups = listOf(ups[0]) + ups
            this.us = FloatArray(us.size + 1) { if (it == 0) us[0] - reach / perU else us[it - 1] }
            this.middles = middles?.let { listOf(it[0]?.let { m -> m - back }) + it }
        }
    }

    private fun centroid(ring: List<Vec3>): Vec3 = ring.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / ring.size)
}

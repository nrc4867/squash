package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

/**
 * How deep a head is along a line, as it lies shut: read from the points of its swept runs, [points], those near the line counting, and
 * [spacing], how far apart those points lie along a ring, the least a line is read across. What a tooth is held short of, so a long one
 * stays inside the head shut over it, and moved in from, so the lip in front of it covers it.
 */
class HeadRoom(private val points: List<Vec3>, private val spacing: Float) {
    /** How far from [from] along the unit [way] the head's surface lies furthest, read within [reach] of the line; 0 where none lies near it. */
    fun depthFrom(from: Vec3, way: Vec3, reach: Float): Float = along(from, way, reach).maxOrNull() ?: 0f

    /** How far from [from] along the unit [way] the head's surface lies nearest, read within [reach] of the line; 0 where none lies near it. */
    fun clearFrom(from: Vec3, way: Vec3, reach: Float): Float = along(from, way, reach).minOrNull() ?: 0f

    private fun along(from: Vec3, way: Vec3, reach: Float): List<Float> {
        val near = maxOf(reach, spacing)
        return points.mapNotNull { p ->
            val d = p - from
            val along = d dot way
            if (along > 0f && (d - way * along).length <= near) along else null
        }
    }

    companion object {
        /** The room in a head built of [runs], each point of their grids. */
        fun of(runs: List<Patch>): HeadRoom {
            val points = runs.flatMap { run -> List(run.nu * run.nv) { run.point(it) } }
            val gaps = runs.flatMap { run -> (0 until run.nu).flatMap { ring -> (0 until run.nv - 1).map { k -> (run.point(run.index(ring, k + 1)) - run.point(run.index(ring, k))).length } } }
                .filter { it > 0f }.sorted()
            return HeadRoom(points, if (gaps.isEmpty()) 0f else gaps[gaps.size / 2])
        }
    }
}

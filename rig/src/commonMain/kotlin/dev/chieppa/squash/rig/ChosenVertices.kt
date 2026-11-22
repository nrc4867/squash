package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.geom.Vec3

/** How far past its blend's reach, in cells, a member's posed box may lie from a moved vertex and still be read. */
private const val TRIM_CELLS = 6f

/** Vertices a piece of the choosing takes. */
private const val CHOOSE_CHUNK = 2048

/**
 * The corners of a surface a pose refits, [moved]: those near a bend, whose bones or the member pieces round them take them to different
 * places. Members that a pose lays against each other without a join between them meet by their plain union as linear blend leaves them.
 */
internal class ChosenVertices private constructor(
    private val members: VertexMembers,
    val moved: IntArray,
    private val boxes: PosedBoxes,
    private val reaches: FloatArray,
    private val cell: Float,
) {
    /**
     * The members moved corner [v], posed by linear blend at [p], reads: its own, less those whose posed box lies further from [p] than
     * their blend's reach and [TRIM_CELLS] cells, which cannot change its field there.
     */
    fun setOf(v: Int, p: Vec3): IntArray {
        val all = members.sets.copyOfRange(v * MOST_MEMBERS, v * MOST_MEMBERS + members.counts[v])
        return all.filter { m -> boxes.distance(m, p) <= reaches[m] + TRIM_CELLS * cell }.toIntArray().takeIf { it.isNotEmpty() } ?: all
    }

    companion object {
        /** The corners of [surface] that [frames] bends, which a refit moves, or null for none; each piece of the choosing run by [runAll]. */
        suspend fun of(surface: SkinSurface, field: PosedField, members: VertexMembers, frames: BoneFrames, runAll: RunAll): ChosenVertices? {
            val corners = surface.vertexCount
            val transforms = frames.transforms
            val maps = field.maps(transforms)
            val chosen = BooleanArray(corners)
            runAll(chunked(corners, CHOOSE_CHUNK) { from, until ->
                for (v in from until until) chosen[v] = members.bendsAt(v, surface.positions.vec(v * 3), transforms, maps)
            })
            val moved = (0 until corners).filter { chosen[it] }.toIntArray()
            if (moved.isEmpty()) return null
            return ChosenVertices(members, moved, PosedBoxes(field, maps, surface.cell), surface.field.reaches, surface.cell)
        }
    }
}

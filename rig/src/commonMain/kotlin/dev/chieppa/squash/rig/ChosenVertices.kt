package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.geom.Vec3

/** How far past its blend's reach, in cells, a member's posed box may lie from a moved vertex and still be read. */
private const val TRIM_CELLS = 6f

/** Vertices a piece of the choosing takes. */
private const val CHOOSE_CHUNK = 2048

/**
 * The corners of a surface a pose refits, [moved]: those near a bend, whose bones take them to different places, or where a member
 * piece moving apart from them comes near enough to change their field, unless [rigid] keeps them where linear blend puts them. Members that a pose lays against each other without a join
 * between them meet by their plain union as linear blend leaves them. [reads] counts the solid reads the choosing made.
 */
internal class ChosenVertices private constructor(
    private val members: VertexMembers,
    val moved: IntArray,
    private val boxes: PosedBoxes,
    private val reaches: FloatArray,
    private val cell: Float,
    val reads: Long,
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
        /**
         * The corners of [surface] that [frames] bends, posed by linear blend at [positions] in a field [posed], which a refit moves, or
         * null for none, never one [rigid] flags; each piece of the choosing run by [runAll].
         */
        suspend fun of(surface: SkinSurface, field: PosedField, members: VertexMembers, rigid: BooleanArray, frames: BoneFrames, positions: FloatArray, posed: PosedField.Posed, runAll: RunAll): ChosenVertices? {
            val corners = surface.vertexCount
            val transforms = frames.transforms
            val maps = field.maps(transforms)
            val chosen = BooleanArray(corners)
            val reads = LongArray((corners + CHOOSE_CHUNK - 1) / CHOOSE_CHUNK)
            runAll(chunked(corners, CHOOSE_CHUNK) { from, until ->
                val count = LongArray(1)
                for (v in from until until) chosen[v] = !rigid[v] && members.bendsAt(v, surface.positions.vec(v * 3), positions.vec(v * 3), transforms, maps, posed, count)
                reads[from / CHOOSE_CHUNK] = count[0]
            })
            val moved = (0 until corners).filter { chosen[it] }.toIntArray()
            if (moved.isEmpty()) return null
            return ChosenVertices(members, moved, PosedBoxes(field, maps, surface.cell), surface.field.reaches, surface.cell, reads.sum())
        }
    }
}

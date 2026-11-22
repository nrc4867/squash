package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.RunAll
import dev.chieppa.squash.character.surface.chunked
import dev.chieppa.squash.geom.Vec3

/** The most members of other parts a vertex is read against for contact. */
private const val MOST_CONTACTS = 2

/** How near, in cells, a member of another part must come to a posed vertex to be read against it for contact. */
private const val CONTACT_CELLS = 2f

/** How far past its blend's reach, in cells, a member's posed box may lie from a moved vertex and still be read. */
private const val TRIM_CELLS = 6f

/** Vertices a piece of the choosing takes. */
private const val CHOOSE_CHUNK = 2048

/**
 * The corners of a surface a pose refits, [moved]: those near a bend, and those it brings within [CONTACT_CELLS] of a member of another
 * part that lay further than that from them at rest, a limb pressed against the body or another limb, each with up to [MOST_CONTACTS]
 * such members to read as well as its own. [reads] counts the solid reads the choosing made.
 */
internal class ChosenVertices private constructor(
    private val members: VertexMembers,
    val moved: IntArray,
    private val contacts: Map<Int, IntArray>,
    private val boxes: PosedBoxes,
    private val reaches: FloatArray,
    private val cell: Float,
    val reads: Long,
) {
    val inContact: Int get() = contacts.size

    /**
     * The members moved corner [v], posed by linear blend at [p], reads: its own and those it meets in contact, less those whose posed box
     * lies further from [p] than their blend's reach and [TRIM_CELLS] cells, which cannot change its field there.
     */
    fun setOf(v: Int, p: Vec3): IntArray {
        val all = contacts[v] ?: members.sets.copyOfRange(v * MOST_MEMBERS, v * MOST_MEMBERS + members.counts[v])
        return all.filter { m -> boxes.distance(m, p) <= reaches[m] + TRIM_CELLS * cell }.toIntArray().takeIf { it.isNotEmpty() } ?: all
    }

    companion object {
        /** The corners of [surface] that [frames] moves to [positions] by linear blend which a refit moves, or null for none. */
        suspend fun of(surface: SkinSurface, field: PosedField, members: VertexMembers, frames: BoneFrames, positions: FloatArray, posed: PosedField.Posed, runAll: RunAll): ChosenVertices? {
            val corners = surface.vertexCount
            val cell = surface.cell
            val reaches = surface.field.reaches
            val transforms = frames.transforms
            val maps = field.maps(transforms)
            val boxes = PosedBoxes(field, maps, cell)
            val chosen = BooleanArray(corners)
            val found = arrayOfNulls<IntArray>(corners)
            val reads = LongArray((corners + CHOOSE_CHUNK - 1) / CHOOSE_CHUNK)
            runAll(chunked(corners, CHOOSE_CHUNK) { from, until ->
                val count = LongArray(1)
                val near = IntArray(MOST_CONTACTS)
                for (v in from until until) {
                    val rest = surface.positions.vec(v * 3)
                    chosen[v] = members.bendsAt(v, rest, transforms, maps)
                    val winner = surface.winners[v]
                    if (winner < 0) continue
                    val p = positions.vec(v * 3)
                    var n = 0
                    for (m in boxes.near[winner]) {
                        if (n == MOST_CONTACTS) break
                        if (members.has(v, m) || !boxes.holds(m, p)) continue
                        if (posed.own(m, p, count) > CONTACT_CELLS * cell) continue
                        if (field.members[m].own(rest) <= reaches[m] + CONTACT_CELLS * cell) continue
                        near[n++] = m
                    }
                    if (n == 0) continue
                    chosen[v] = true
                    val own = members.counts[v]
                    found[v] = IntArray(own + n) { i -> if (i < own) members.sets[v * MOST_MEMBERS + i] else near[i - own] }
                }
                reads[from / CHOOSE_CHUNK] = count[0]
            })
            val moved = (0 until corners).filter { chosen[it] }.toIntArray()
            if (moved.isEmpty()) return null
            val contacts = HashMap<Int, IntArray>()
            for (v in moved) found[v]?.let { contacts[v] = it }
            return ChosenVertices(members, moved, contacts, boxes, reaches, cell, reads.sum())
        }
    }
}

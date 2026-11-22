package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surface.HELD

/** The most members a vertex's field is read from, besides those it meets in contact. */
internal const val MOST_MEMBERS = 8

/** How far past its blend's reach, in cells, a member's field must lie from a vertex at rest to be left out of its reads. */
private const val NEAR_CELLS = 4f

/** How far apart, as a share of the field's cell, two maps may take a vertex and still count as moving it as one. */
private const val RIGID_SHARE = 0.01f

/**
 * The members each corner of [surface] reads its posed field from, [MOST_MEMBERS] a corner from its first in [sets], [counts] of them:
 * those holding a share of it, those one of its bones moves that come near it at rest, and their parents.
 */
internal class VertexMembers(private val surface: SkinSurface, private val skin: SurfaceSkin, private val field: PosedField) {
    val sets = IntArray(surface.vertexCount * MOST_MEMBERS)
    val counts = IntArray(surface.vertexCount)

    /** For each corner, how near a member must come to it to change its field: the widest blend or reach among its members, and a cell. */
    private val within = FloatArray(surface.vertexCount)

    init {
        val members = field.members
        val reaches = surface.field.reaches
        val cell = surface.cell
        for (v in 0 until surface.vertexCount) {
            val at = v * MOST_MEMBERS
            val rest = surface.positions.vec(v * 3)
            var count = 0
            fun add(m: Int, always: Boolean) {
                if (m < 0 || count == MOST_MEMBERS) return
                for (i in 0 until count) if (sets[at + i] == m) return
                if (!always && members[m].own(rest) > reaches[m] + NEAR_CELLS * cell) return
                sets[at + count++] = m
            }
            for (k in 0 until HELD) if (surface.heldShares[v * HELD + k] > 0f) add(surface.held[v * HELD + k], always = true)
            for (slot in 0 until MAX_INFLUENCES) {
                val bone = skin.bones[v * MAX_INFLUENCES + slot]
                if (bone >= 0 && skin.weights[v * MAX_INFLUENCES + slot] > 0f) field.membersOf[bone].forEach { add(it, always = false) }
            }
            val direct = count
            for (i in 0 until direct) add(members[sets[at + i]].parent, always = false)
            counts[v] = count
            var widest = 0f
            for (i in 0 until count) widest = maxOf(widest, reaches[sets[at + i]], members[sets[at + i]].blend)
            within[v] = widest + cell
        }
    }

    /**
     * Whether corner [v], at rest at [point] and posed by linear blend at [blended], lies at a bend of [transforms]: its bones take it to
     * different places, or a piece of one of its members that reaches it moves apart from them by its map in [maps] ([PosedField.maps])
     * and that member, [posed], lies near enough to [blended] to change its field there; [reads] counts the solid reads.
     */
    fun bendsAt(v: Int, point: Vec3, blended: Vec3, transforms: List<Affine>, maps: Array<Affine?>, posed: PosedField.Posed, reads: LongArray): Boolean {
        var first: Vec3? = null
        fun differs(to: Vec3): Boolean {
            val was = first ?: run { first = to; return false }
            return (to - was).length > surface.cell * RIGID_SHARE
        }
        for (slot in 0 until MAX_INFLUENCES) {
            val bone = skin.bones[v * MAX_INFLUENCES + slot]
            if (bone >= 0 && skin.weights[v * MAX_INFLUENCES + slot] > 0f && differs(transforms[bone](point))) return true
        }
        for (i in 0 until counts[v]) {
            val m = sets[v * MOST_MEMBERS + i]
            val apart = field.pieces[m].any { piece -> piece.cut(point) <= piece.rounding && differs(maps[piece.slot]?.invoke(point) ?: point) }
            if (apart && posed.own(m, blended, reads) <= within[v]) return true
        }
        return false
    }
}

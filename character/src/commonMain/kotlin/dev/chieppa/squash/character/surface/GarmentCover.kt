package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.branchOver
import dev.chieppa.squash.character.build.throughHoles
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId

/** How far past each end of its range, in its part's u, a branch drawn or hanging free is built, so its hem is cut along its wall. */
private const val SHELL_PAST = 0.08f

/** Where a cuff branch starts along the part it rings, well inside the branch it comes out of. */
private const val CUFF_FROM = -1f

/**
 * The parts a garment covers on a one surface and how, as the document says: each branch's part, and the other side of its pair, which
 * the branch covers mirrored, by the branch; and each part's [twins].
 */
data class GarmentCover(val garment: Garment, val branches: Map<PartId, GarmentBranch>, val twins: Map<PartId, PartId> = emptyMap()) {
    companion object {
        /** What [garment] covers on [character], its branches read on both sides of a pair. */
        fun of(garment: Garment, character: Character): GarmentCover {
            val branches = LinkedHashMap<PartId, GarmentBranch>()
            for (branch in garment.branches) {
                branches[branch.part] = branch
                val part = character.part(branch.part) ?: continue
                character.twinOf(part)?.let { twin -> branches.putIfAbsent(twin.id, branch) }
            }
            val twins = character.parts.mapNotNull { part -> character.twinOf(part)?.let { part.id to it.id } }.toMap()
            return GarmentCover(garment, branches, twins)
        }
    }
}

/**
 * A garment as it is worn over a placed body: [branches] over the parts it covers, each standing off the garments [under] it, and over
 * each part that comes out through it with a cuff, a cuff branch from inside the garment to past where the part comes out by the cuff's
 * size, the parts in [cuffs]; [holes], the parts that come out through it with none, which open the garment where they lie inside it;
 * and [shells], each branch the garment draws standing off its ease or hanging free past its part's end, as its shell over its part.
 */
class WornCover(
    val cover: GarmentCover,
    val branches: Map<PartId, GarmentBranch>,
    val cuffs: Set<PartId>,
    val holes: Set<PartId>,
    val shells: Map<PartId, PlacedPart>,
) {
    val garment: Garment get() = cover.garment

    /** The branch over [part], one of the garment's own or a cuff's; null where the garment does not cover it. */
    fun branchOf(part: PartId): GarmentBranch? = branches[part]

    /** What equals for the same branches, cuffs, holes and shells, the shells by their objects. */
    val key: List<Any> = listOf(cover, branches, cuffs, holes, shells.mapValues { Same(it.value.surface) })

    companion object {
        /** [cover] worn over [body] on the garments [under] it, the innermost first. */
        fun of(cover: GarmentCover, body: AssembledCharacter, under: List<GarmentCover>, shells: ShellCache): WornCover {
            val garment = cover.garment
            val fitted = cover.branches.mapValues { (part, branch) -> branch.fittedOver(part, under) }
            val branches = LinkedHashMap(fitted)
            val cuffs = HashSet<PartId>()
            val holes = HashSet<PartId>()
            val built = HashMap<PartId, PlacedPart>()
            for ((part, branch) in fitted) {
                val covered = body.part(part) ?: continue
                val comes = body.parts.any { child ->
                    val spec = child.socketSpec
                    child.socket?.parent == part && child.id !in cover.branches && child.owner == null && spec != null && spec.u in branch.from..branch.to
                }
                if (comes) {
                    val placed = shells.over(garment, branch, covered) ?: continue
                    val holed = throughHoles(garment, placed, body, cover.twins) {}
                    val cuffed = holed.cuffs.mapNotNull { it.covering }
                    for (cuff in cuffed) {
                        cuffs += cuff.part
                        branches.putIfAbsent(cuff.part, GarmentBranch(cuff.part, CUFF_FROM, cuff.to, branch.ease))
                    }
                    holes += holed.through.filter { through -> cuffed.none { it.part == through } }
                }
                if (branch.allRound && (branch.outlines.isNotEmpty() || branch.from < -SHELL_PAST || branch.to > 1f + SHELL_PAST)) {
                    shells.over(garment, branch.copy(from = branch.from - SHELL_PAST, to = branch.to + SHELL_PAST), covered)?.let { built[part] = it }
                }
            }
            return WornCover(cover, branches, cuffs, holes - cuffs, built)
        }

        /** This branch over [part] standing off the garments [under] it: each one's branch the whole way round the part over any of its span adds its ease. */
        private fun GarmentBranch.fittedOver(part: PartId, under: List<GarmentCover>): GarmentBranch {
            val below = under.sumOf { inner ->
                inner.branches[part]?.takeIf { it.allRound && it.from < to && it.to > from }?.ease?.toDouble() ?: 0.0
            }.toFloat()
            return if (below == 0f) this else copy(ease = ease + below)
        }
    }
}

/** Branch shells built over placed parts, kept while the branch and the part's surface and place are the same. */
class ShellCache {
    private var kept: Map<List<Any?>, PlacedPart?> = emptyMap()
    private var used = HashMap<List<Any?>, PlacedPart?>()

    /** [garment]'s [branch] built over [covered]. */
    fun over(garment: Garment, branch: GarmentBranch, covered: PlacedPart): PlacedPart? {
        val key = listOf(garment.id, branch, Same(covered.surface), covered.origin, covered.turn)
        return used.getOrPut(key) { if (key in kept) kept[key] else branchOver(garment, branch, covered) }
    }

    /** Keeps only what the last resolution used. */
    fun settle() {
        kept = used
        used = HashMap()
    }
}

/** Equal only to a [Same] of the same object, which must not hash by its contents. */
internal class Same(private val of: Any) {
    override fun equals(other: Any?): Boolean = other is Same && other.of === of

    override fun hashCode(): Int = of.hashCode()
}

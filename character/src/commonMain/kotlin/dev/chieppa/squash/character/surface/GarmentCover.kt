package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.SHELL_PAST
import dev.chieppa.squash.character.build.endPointsDown
import dev.chieppa.squash.character.build.branchOver
import dev.chieppa.squash.character.build.throughHoles
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId


/** How near an end of its parent's run, in u, a part's socket lies for the part to come out through that end. */
private const val END_SOCKET = 0.02f

/** Where a cuff branch starts along the part it rings, well inside the branch it comes out of. */
private const val CUFF_FROM = -1f

/**
 * The parts a garment covers and how, as the document says: each branch's part, and the other side of its pair, which
 * the branch covers mirrored, by the branch standing off by the garment's fit there (see [Garment.easeOf]); each part's [twins]; and what
 * the garment [prints] on its cloth, mirrored sides among them.
 */
data class GarmentCover(
    val garment: Garment,
    val branches: Map<PartId, GarmentBranch>,
    val twins: Map<PartId, PartId> = emptyMap(),
    val prints: GarmentPrints = GarmentPrints(),
    val seeThrough: List<AreaSeeThrough?> = emptyList(),
) {
    /** Whether any of its cloth shows what lies under it, so it hides nothing. */
    val isSeeThrough: Boolean get() = seeThrough.any { it != null }

    /** Each covered part's painted cloth (see [clothOf]); a part left out is not covered. */
    val cloth: Map<PartId, ClothField> by lazy { clothOf(garment, branches, twins) }

    /** This cover without what its garment prints or the colours and see-through of its areas, which neither its cloth's shape nor its cut read. */
    val unprinted: GarmentCover get() =
        if (prints.isEmpty && seeThrough.isEmpty() && garment.unprinted === garment) this else copy(garment = garment.unprinted, prints = GarmentPrints(), seeThrough = emptyList())

    companion object {
        /** What [garment] covers on [character], its branches read on both sides of a pair. */
        fun of(garment: Garment, character: Character): GarmentCover {
            val branches = LinkedHashMap<PartId, GarmentBranch>()
            for (held in garment.branches) {
                val branch = held.copy(ease = garment.easeOf(held))
                branches[branch.part] = branch
                val part = character.part(branch.part) ?: continue
                character.twinOf(part)?.let { twin -> branches.putIfAbsent(twin.id, branch) }
            }
            val twins = character.parts.mapNotNull { part -> character.twinOf(part)?.let { part.id to it.id } }.toMap()
            return GarmentCover(garment, branches, twins, GarmentPrints.of(garment, character), seeThroughOf(garment, character.palette))
        }
    }
}

/**
 * A garment as it is worn over a placed body: [branches] over the parts it covers, each standing off the garments [under] it, and over
 * each part that comes out through it with a cuff, a cuff branch from inside the garment to past where the part comes out by the cuff's
 * size, the parts in [cuffs]; [holes], the parts that come out through its wall with none, which open the garment where they lie inside
 * it; [openEnds], for each part, the ends of it (low, high) its cloth reaches through which a part the garment does not cover comes out;
 * and [shells], each branch the garment draws standing off its ease or running past its part's end ([drawsShell]), as its shell the whole
 * way round its part over the rows its cloth runs ([alongOf]), which coverage cuts to the cloth; [hangsFree], the parts whose cloth runs
 * past an end of the part that points down at rest, as a skirt below an upright torso does, not along a body lying level; [piece], the
 * part at the root of this piece where the garment is worn in pieces that meet nowhere (see [pieces]), else null; [cloth], the painted
 * cloth over each part it covers, a cuff's being its band, which is all coverage reads of what it covers; a branch's range is read only
 * where it runs past its part's end. The parts that come out, with a cuff or none, and those that hang inside are read over the region
 * each part's cells paint ([clothBranch]).
 */
class WornCover(
    val cover: GarmentCover,
    val branches: Map<PartId, GarmentBranch>,
    val cuffs: Set<PartId>,
    val holes: Set<PartId>,
    val shells: Map<PartId, PlacedPart>,
    val piece: PartId? = null,
    val openEnds: Map<PartId, OpenEnds> = emptyMap(),
    val hangsFree: Set<PartId> = emptySet(),
    val cloth: Map<PartId, ClothField> = cover.cloth,
    val eases: Map<PartId, ClothEase> = emptyMap(),
) {
    val garment: Garment get() = cover.garment

    /** The painted cloth over [part] as a smooth field, or null where the garment does not cover it. */
    fun clothOf(part: PartId): ClothField? = cloth[part]

    /** The parts that come out through the ends of its branches' parts that it reaches. */
    val outThroughEnds: Set<PartId> get() = openEnds.values.flatMapTo(HashSet()) { it.parts }

    /**
     * The parts whose branch hangs over what comes out of them, pushed clear of it in each pose: a branch that [hangsFree], or one part
     * of the way round with a shell of its own, as a cape over a back or a bun open over it.
     */
    val hangsOver: Set<PartId> get() = hangsFree + shells.keys.filter { cloth[it]?.reach?.allRound == false }

    /** Whether any branch [hangsOver] what comes out of its part. */
    val hanging: Boolean get() = hangsOver.isNotEmpty()

    /** How far the garment stands off [part] at each place on it; the branch's ease everywhere where it holds none, null where it does not cover it. */
    fun easeOf(part: PartId): ClothEase? = eases[part] ?: branches[part]?.let { ClothEase.flat(it.ease) }

    /** The branch over [part], one of the garment's own or a cuff's; null where the garment does not cover it. */
    fun branchOf(part: PartId): GarmentBranch? = branches[part]

    /** What equals for the same branches, cuffs, holes, shells, piece and open ends, the shells by their objects, whatever its cloth prints. */
    val key: List<Any?> = listOf(cover.unprinted, branches, cuffs, holes, shells.mapValues { Same(it.value.surface) }, piece, openEnds, hangsFree)

    /**
     * What equals for the same garment grown the same way, whatever comes out through it: the garment but what it covers, the branches,
     * a painted part's over the stepped range its cloth's [ClothReach] grows over, the whole way round (see [garmentField]), the cuffs,
     * shells and piece. So painting more or less of a painted part within the same steps keeps the grown surface, and only its cut changes.
     */
    val shapeKey: List<Any?> = listOf(
        cover.unprinted.copy(garment = cover.garment.unprinted.copy(areas = emptyList(), branches = emptyList(), changes = emptyMap(), joins = emptySet(), holes = emptyList()), branches = emptyMap()),
        branches.mapValues { (part, branch) -> cloth[part]?.reach?.let { branch.copy(from = it.from, to = it.to, fromV = 0f, toV = 1f) } ?: branch },
        cuffs,
        shells.mapValues { Same(it.value.surface) },
        piece,
        eases,
    )

    companion object {
        /** [cover] worn over [body] on the garments [under] it, the innermost first. */
        fun of(cover: GarmentCover, body: AssembledCharacter, under: List<GarmentCover>, shells: ShellCache): WornCover {
            val garment = cover.garment
            val eases = clothEases(cover, body, under)
            val fitted = cover.branches.mapValues { (part, branch) -> branch.copy(ease = checkNotNull(eases[part]).least) }
            val branches = LinkedHashMap(fitted)
            val cloth = HashMap(cover.cloth)
            val cuffs = HashSet<PartId>()
            val holes = HashSet<PartId>()
            val built = HashMap<PartId, PlacedPart>()
            val open = HashMap<PartId, OpenEnds>()
            val free = HashSet<PartId>()
            for ((part, branch) in fitted) {
                val covered = body.part(part) ?: continue
                val reach = cover.cloth[part]?.reach ?: continue
                val worn = clothBranch(branch, reach)
                val along = worn.from..worn.to
                val comes = body.parts.any { child ->
                    val spec = child.socketSpec
                    child.socket?.parent == part && child.id !in cover.branches && child.owner == null && spec != null && spec.u in along
                }
                if (comes) {
                    val placed = shells.over(garment, worn, covered) ?: continue
                    val holed = throughHoles(garment, placed, body, cover.twins) {}
                    val cuffed = holed.cuffs.mapNotNull { it.covering }
                    for (cuff in cuffed) {
                        cuffs += cuff.part
                        if (cuff.part in branches) continue
                        val band = GarmentBranch(cuff.part, CUFF_FROM, cuff.to, branch.ease)
                        branches[cuff.part] = band
                        cloth[cuff.part] = cuffCloth(band)
                    }
                    holes += holed.through.filter { through -> cuffed.none { it.part == through } }
                }
                val ends = outThroughEnd(body, part, reach, cover)
                if (ends.isNotEmpty()) open[part] = ends
                val ran = branch.copy(from = worn.from, to = worn.to)
                if (ran.drawsShell) {
                    val whole = ran.copy(from = ran.from - SHELL_PAST, to = ran.to + SHELL_PAST, fromV = 0f, toV = 1f)
                    shells.over(garment, whole, covered)?.let { built[part] = it }
                }
                if (reach.allRound && hangsDownPast(ran, covered)) free += part
            }
            return WornCover(cover, branches, cuffs, holes - cuffs, built, openEnds = open, hangsFree = free, cloth = cloth, eases = eases.filterValues { !it.uniform })
        }
    }
}

/** Whether this branch reads a shell of its own: drawn standing off its ease, or running past an end of its part. */
val GarmentBranch.drawsShell: Boolean get() = outlines.isNotEmpty() || from < -SHELL_PAST || to > 1f + SHELL_PAST

/** Whether [branch] runs past an end of [covered] whose way out points down at rest, so cloth past it hangs under its own weight. */
internal fun hangsDownPast(branch: GarmentBranch, covered: PlacedPart): Boolean =
    (branch.from < -SHELL_PAST && covered.endPointsDown(high = false)) || (branch.to > 1f + SHELL_PAST && covered.endPointsDown(high = true))

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

/** Which ends of [part]'s run, low and high, its cloth reaches and a part [cover] does not cover comes out through, and those [parts]. */
data class OpenEnds(val low: Boolean, val high: Boolean, val parts: Set<PartId> = emptySet())

/** The ends of [part]'s run that its cloth, [reach], reaches with a part on a socket there that [cover] does not cover; empty with none. */
private fun outThroughEnd(body: AssembledCharacter, part: PartId, reach: ClothReach, cover: GarmentCover): OpenEnds {
    val sockets = body.parts.mapNotNull { child ->
        val spec = child.socketSpec ?: return@mapNotNull null
        (child.id to spec.u).takeIf { child.socket?.parent == part && child.id !in cover.branches && child.owner == null && spec.patch == null }
    }
    val low = sockets.filter { reach.reachesLow && it.second <= END_SOCKET }
    val high = sockets.filter { reach.reachesHigh && it.second >= 1f - END_SOCKET }
    return OpenEnds(low.isNotEmpty(), high.isNotEmpty(), (low + high).mapNotNullTo(HashSet()) { it.first })
}

private fun OpenEnds.isNotEmpty(): Boolean = low || high

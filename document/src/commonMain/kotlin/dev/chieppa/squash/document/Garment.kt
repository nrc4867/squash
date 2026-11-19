package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** How far a branch stands off its part's surface where nothing drawn takes it further, in body units. */
const val DEFAULT_EASE = 0.03f

/** How far a garment stands off the body where nothing drawn takes it further, snug, in body units: a new garment's fit. */
const val SNUG_FIT = 0.015f

/** The first layer, nearest the skin. */
const val FIRST_LAYER = 1

/** What starts every garment-made part id; a part of the document never starts with it. */
const val GARMENT_PART_PREFIX = "garment/"

/**
 * One garment of a character's wardrobe: the [slot] it fills, its [layer] counted out from the skin, the [branches] it is built
 * from, one per covered part, which [joins] are inked, whether its edges are ([inkHem]), its [color], what it does to the body parts
 * under it ([changes], by part) and its [looseEnds]. [fit] is how far it stands off the body, with [partFits] overriding it on a part
 * or pair by the pair's leader; with no fit each branch stands off by its own ease. The [areas] painted on the body and its [holes]
 * say which parts it covers and how far, written into its branches, changes and inked joins; a branch whose part no area paints has its
 * box painted ([withBoxesPainted]), so every garment's cloth is its painted cells. [tucks] are the Tuck choices made for the small parts
 * inside it. Its [regions] and [marks] are printed on its cloth, in the (u, v) of the body parts under it as a part's own are, and show
 * only where the cloth does. See HLD: data-model, Garments.
 */
@Serializable
data class Garment(
    val id: GarmentId,
    val name: String,
    val slot: GarmentSlotId? = null,
    val layer: Int = FIRST_LAYER,
    val branches: List<GarmentBranch> = emptyList(),
    val joins: Set<PartId> = emptySet(),
    val inkHem: Boolean = true,
    val color: SlotName? = null,
    val changes: Map<PartId, BodyPartChange> = emptyMap(),
    val looseEnds: List<LooseEnd> = emptyList(),
    val fit: Float? = null,
    val partFits: Map<PartId, Float> = emptyMap(),
    val tucks: List<TuckChoice> = emptyList(),
    val areas: List<GarmentArea> = emptyList(),
    val holes: List<GarmentHole> = emptyList(),
    val regions: List<Region> = emptyList(),
    val marks: List<Mark> = emptyList(),
) {
    init {
        require(branches.distinctBy { it.part }.size == branches.size) { "Garment ${id.value} covers a part twice" }
        require(areas.distinctBy { it.id }.size == areas.size) { "Garment ${id.value} names an area twice" }
        require(holes.distinctBy { it.part }.size == holes.size) { "Garment ${id.value} has two holes round one part" }
        require(looseEnds.distinctBy { it.id }.size == looseEnds.size) { "Garment ${id.value} names a loose end twice" }
        require(regions.distinctBy { it.id }.size == regions.size) { "Garment ${id.value} names a region twice" }
        require(marks.distinctBy { it.id }.size == marks.size) { "Garment ${id.value} names a mark twice" }
        require(marks.all { it.kind == MarkKind.LINE }) { "Garment ${id.value} holds a mark that is not a line" }
    }

    fun branch(part: PartId): GarmentBranch? = branches.firstOrNull { it.part == part }

    /** How far [branch] stands off its part: its part's own fit, else the garment's, else the branch's own ease. */
    fun easeOf(branch: GarmentBranch): Float = partFits[branch.part] ?: fit ?: branch.ease

    fun area(id: GarmentAreaId): GarmentArea? = areas.firstOrNull { it.id == id }

    /** This garment without what is printed on its cloth or the colours of its areas: what its cloth's shape and cut read. */
    val unprinted: Garment get() = if (regions.isEmpty() && marks.isEmpty() && areas.none { it.color != null }) this else copy(regions = emptyList(), marks = emptyList(), areas = areas.map { it.copy(color = null) })

    /**
     * This garment with the box of each branch whose part no area paints painted as cells ([boxCells]) into its first area, made as
     * [FIRST_AREA] where it has none. The branches are left as they are: each box lies within half a cell of its cells' edges.
     */
    fun withBoxesPainted(): Garment {
        val unpainted = branches.filter { branch -> areas.none { branch.part in it.cells } }
        if (unpainted.isEmpty()) return this
        val cells = unpainted.associate { it.part to boxCells(it) }
        val first = areas.firstOrNull() ?: GarmentArea(FIRST_AREA, "")
        val painted = first.copy(cells = first.cells + cells.mapValues { runsOfCells(it.value) })
        return copy(areas = listOf(painted) + areas.drop(1))
    }

    /** This garment with [part]'s branch changed by [change]. */
    internal fun withBranch(part: PartId, change: (GarmentBranch) -> GarmentBranch): Garment {
        require(branch(part) != null) { "Garment ${id.value} does not cover ${part.value}" }
        return copy(branches = branches.map { if (it.part == part) change(it) else it })
    }
}

/**
 * The garment's surface over one covered [part], from [from] to [to] along it in u, which may run past 1 to continue past the
 * part's end or below 0 to run on before its start, and from [fromV] to [toV] round it, the whole way round when they span 1 or more, else a region whose open edges are
 * hems; v is read round the part's own v, so a range may start below 0 to cross it. Its rings are the part's slices grown by [ease];
 * its own Side and Top [outlines] take them further where they stand off, and its [surface] work is anchored on it. On a paired part
 * it covers both sides, the right one mirrored.
 */
@Serializable
data class GarmentBranch(
    val part: PartId,
    val from: Float = 0f,
    val to: Float = 1f,
    val ease: Float = DEFAULT_EASE,
    val outlines: Map<View, OutlineCurve> = emptyMap(),
    val surface: SurfaceWork = SurfaceWork(),
    val fromV: Float = 0f,
    val toV: Float = 1f,
) {
    init {
        require(from < to) { "Branch on ${part.value} runs from $from to $to" }
        require(fromV < toV) { "Branch on ${part.value} runs round from $fromV to $toV" }
        require(ease >= 0f) { "Branch on ${part.value} has a negative ease" }
    }

    /** Whether it goes the whole way round its part. */
    val allRound: Boolean get() = toV - fromV >= 1f
}

/**
 * One painted area of a garment, named [name]: the cells of each part's chart painted, by part, a pair's leader holding both sides, as
 * runs of cells row by row, the first run not painted (HLD: data-model, Garments), shown in its own palette slot [color], or the
 * garment's where it has none. Areas of one garment may touch along a seam.
 */
@Serializable
data class GarmentArea(val id: GarmentAreaId, val name: String, val cells: Map<PartId, List<Int>> = emptyMap(), val color: SlotName? = null)

/** The id of a garment's first area, which the boxes of branches no area paints are painted into. */
val FIRST_AREA = GarmentAreaId("area-1")

/** A hole of a garment round the root of [part], a pair's leader, which comes out through it bare, or in a cuff band with [cuff]. */
@Serializable
data class GarmentHole(val part: PartId, val cuff: Boolean = false)

/** The part id a branch of [garment] over [part] is built, rigged and anchored as. */
fun branchPartId(garment: GarmentId, part: PartId): PartId = PartId("$GARMENT_PART_PREFIX${garment.value}/${part.value}")

/** The part id the cuff of [garment] round [part], where it comes out through the garment, is built, rigged and anchored as. */
fun cuffPartId(garment: GarmentId, part: PartId): PartId = PartId("$GARMENT_PART_PREFIX${garment.value}/${part.value}$CUFF_SUFFIX")

private const val CUFF_SUFFIX = "/cuff"

/** The part id the loose end [end] of [garment] is built, rigged and posed as. */
fun looseEndPartId(garment: GarmentId, end: LooseEndId): PartId = PartId("$GARMENT_PART_PREFIX${garment.value}$LOOSE_END_INFIX${end.value}")

private const val LOOSE_END_INFIX = "/end/"

/** Whether [part] names a garment's cuff. */
fun isCuffPart(part: PartId): Boolean = isBranchPart(part) && part.value.endsWith(CUFF_SUFFIX)

/** Whether [part] names a garment's branch or cuff rather than a part of the document. */
fun isBranchPart(part: PartId): Boolean = part.value.startsWith(GARMENT_PART_PREFIX)

/**
 * A short chain of [bones] carrying a loose part of a garment's [branch], from its hem when [fromHem], at [v] around it: posed by
 * hand as a tail is, with [followThrough], its [spring] and whether it collides with the body kept for a settle.
 */
@Serializable
data class LooseEnd(
    val id: LooseEndId,
    val branch: PartId,
    val v: Float = 0.5f,
    val bones: Int = DEFAULT_LOOSE_BONES,
    val fromHem: Boolean = true,
    val followThrough: Boolean = true,
    val spring: Float = DEFAULT_SPRING,
    val collideBody: Boolean = true,
) {
    init {
        require(bones >= 1) { "Loose end ${id.value} has no bones" }
    }

    companion object {
        const val DEFAULT_LOOSE_BONES = 5
        const val DEFAULT_SPRING = 0.45f
    }
}

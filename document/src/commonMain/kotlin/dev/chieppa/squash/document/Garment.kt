package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** How far a branch stands off its part's surface where nothing drawn takes it further, in body units. */
const val DEFAULT_EASE = 0.03f

/** How wide the fillet where two branches of a garment meet, in body units. */
const val DEFAULT_GARMENT_FILLET = 0.35f

/** The first layer, nearest the skin. */
const val FIRST_LAYER = 1

/** What starts every garment-made part id; a part of the document never starts with it. */
const val GARMENT_PART_PREFIX = "garment/"

/**
 * One garment of a character's wardrobe: the [slot] it fills, its [layer] counted out from the skin, the [branches] it is built
 * from, one per covered part, which [joins] are inked, whether its hem and cuffs are, the [fillet] at its joins, its [color], what
 * it does to the body parts under it ([changes], by part) and its [looseEnds]. See HLD: data-model, Garments.
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
    val inkCuffs: Boolean = true,
    val fillet: Float = DEFAULT_GARMENT_FILLET,
    val color: SlotName? = null,
    val changes: Map<PartId, BodyPartChange> = emptyMap(),
    val looseEnds: List<LooseEnd> = emptyList(),
) {
    init {
        require(branches.distinctBy { it.part }.size == branches.size) { "Garment ${id.value} covers a part twice" }
        require(looseEnds.distinctBy { it.id }.size == looseEnds.size) { "Garment ${id.value} names a loose end twice" }
    }

    fun branch(part: PartId): GarmentBranch? = branches.firstOrNull { it.part == part }

    /** This garment with [part]'s branch changed by [change]. */
    internal fun withBranch(part: PartId, change: (GarmentBranch) -> GarmentBranch): Garment {
        require(branch(part) != null) { "Garment ${id.value} does not cover ${part.value}" }
        return copy(branches = branches.map { if (it.part == part) change(it) else it })
    }
}

/**
 * The garment's surface over one covered [part], from [from] to [to] along it in u, which may run past 1 to continue past the
 * part's end, and from [fromV] to [toV] round it, the whole way round when they span 1 or more, else a region whose open edges are
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

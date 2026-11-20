package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** How far a branch stands off its part's surface where nothing drawn takes it further, in body units. */
const val DEFAULT_EASE = 0.012f

/** How far a garment stands off the body where nothing drawn takes it further, snug, in body units: a new garment's fit. */
const val SNUG_FIT = 0.006f

/** A garment's ink weight where it sets none: its silhouette and hems as a share of the skin's line weight, thinner than skin. */
const val CLOTH_INK_WEIGHT = 0.7f

/** The most a garment's ink weight is, as a share of the skin's line weight. */
const val MOST_CLOTH_INK_WEIGHT = 2f

/** The most a garment's hem roll stands off past the cloth, in body units. */
const val MOST_HEM_ROLL = 0.03f

/** The furthest down its part, in u, a garment drops a seam. */
const val MOST_DROP = 0.5f

/** The thickest an area's fold stands out over the cloth under it, in body units. */
const val MOST_FOLD = 0.03f

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
 * only where the cloth does. [inkWeight] is the weight of its silhouette and hems as a share of the skin's line weight, and [hemRoll]
 * how far its hems, neck openings and cuffs roll out past the cloth, in body units. [creases], [wrinkles] and [drape] are its strengths of
 * joint creases, wrinkles where it bunches and short folds gathered above its hems, times the character's cloth strength (HLD: look, Creases and wrinkles).
 * [eases] gives a part, by a pair's leader, a stand-off that varies along and round it, over its fit; [hang] lets the cloth over an
 * upright part fall straight from its widest place rather than follow the body in; [drops] moves the seam where a part's cloth meets the
 * cloth of the part it grows from down that part, in its u, as a dropped shoulder's. See HLD: data-model, Garments.
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
    val inkWeight: Float = CLOTH_INK_WEIGHT,
    val hemRoll: Float = 0f,
    val creases: Float = DEFAULT_CREASE_STRENGTH,
    val wrinkles: Float = DEFAULT_CREASE_STRENGTH,
    val drape: Float = DEFAULT_CREASE_STRENGTH,
    val eases: Map<PartId, EaseProfile> = emptyMap(),
    val hang: ClothHang? = null,
    val drops: Map<PartId, Float> = emptyMap(),
) {
    init {
        require(fit == null || fit >= 0f) { "Garment ${id.value} has a negative fit" }
        require(partFits.values.all { it >= 0f }) { "Garment ${id.value} has a negative part fit" }
        require(inkWeight in 0f..MOST_CLOTH_INK_WEIGHT) { "Garment ${id.value} has an ink weight outside 0 to $MOST_CLOTH_INK_WEIGHT" }
        require(hemRoll in 0f..MOST_HEM_ROLL) { "Garment ${id.value} has a hem roll outside 0 to $MOST_HEM_ROLL" }
        require(listOf(creases, wrinkles, drape).all { it in 0f..MOST_CREASE_STRENGTH }) { "Garment ${id.value} has a crease strength outside 0 to $MOST_CREASE_STRENGTH" }
        require(branches.distinctBy { it.part }.size == branches.size) { "Garment ${id.value} covers a part twice" }
        require(areas.distinctBy { it.id }.size == areas.size) { "Garment ${id.value} names an area twice" }
        require(holes.distinctBy { it.part }.size == holes.size) { "Garment ${id.value} has two holes round one part" }
        require(looseEnds.distinctBy { it.id }.size == looseEnds.size) { "Garment ${id.value} names a loose end twice" }
        require(regions.distinctBy { it.id }.size == regions.size) { "Garment ${id.value} names a region twice" }
        require(marks.distinctBy { it.id }.size == marks.size) { "Garment ${id.value} names a mark twice" }
        require(marks.all { it.kind == MarkKind.LINE }) { "Garment ${id.value} holds a mark that is not a line" }
        require(drops.values.all { it in 0f..MOST_DROP }) { "Garment ${id.value} drops a seam outside 0 to $MOST_DROP" }
    }

    fun branch(part: PartId): GarmentBranch? = branches.firstOrNull { it.part == part }

    /** How far [branch] stands off its part where its ease is the same everywhere: its part's own fit, else the garment's, else the branch's own ease. */
    fun easeOf(branch: GarmentBranch): Float = partFits[branch.part] ?: fit ?: branch.ease

    /** How far [branch] stands off its part along and round it: its part's ease profile, else [easeOf] everywhere. */
    fun profileOf(branch: GarmentBranch): EaseProfile = eases[branch.part] ?: EaseProfile.flat(easeOf(branch))

    fun area(id: GarmentAreaId): GarmentArea? = areas.firstOrNull { it.id == id }

    /**
     * This garment without what is printed on its cloth, the colours and see-through of its areas, its ink weight or its crease strengths: what its cloth's
     * shape and cut read.
     */
    val unprinted: Garment get() =
        if (regions.isEmpty() && marks.isEmpty() && areas.none { it.color != null || it.opacity != null || it.net != null } && inkWeight == CLOTH_INK_WEIGHT && creasesAtDefault) this
        else copy(
            regions = emptyList(), marks = emptyList(), areas = areas.map { it.copy(color = null, opacity = null, net = null) }, inkWeight = CLOTH_INK_WEIGHT,
            creases = DEFAULT_CREASE_STRENGTH, wrinkles = DEFAULT_CREASE_STRENGTH, drape = DEFAULT_CREASE_STRENGTH,
        )

    private val creasesAtDefault: Boolean
        get() = creases == DEFAULT_CREASE_STRENGTH && wrinkles == DEFAULT_CREASE_STRENGTH && drape == DEFAULT_CREASE_STRENGTH

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
 * garment's where it has none. Areas of one garment may touch along a seam. [gather] pulls its cloth in to that ease, as ribbing;
 * [fold] makes it cloth turned back over the garment at its hem, as a rolled cuff or a turtleneck's fold-over: it stands out by that
 * thickness over the cloth beside it, its far edge inked, and [color] is the cloth's back face. [opacity], 0 to 1, makes it sheer:
 * how much of the skin under it the cloth hides where it faces the viewer, times its slot's own opacity, hiding more as it turns away;
 * [net] makes it a net of threads whose holes show the skin (HLD: look, See-through cloth).
 */
@Serializable
data class GarmentArea(
    val id: GarmentAreaId,
    val name: String,
    val cells: Map<PartId, List<Int>> = emptyMap(),
    val color: SlotName? = null,
    val gather: Float? = null,
    val fold: Float? = null,
    val opacity: Float? = null,
    val net: ClothNet? = null,
) {
    init {
        require(gather == null || gather >= 0f) { "Area ${id.value} gathers to a negative ease" }
        require(fold == null || fold in 0f..MOST_FOLD) { "Area ${id.value} folds outside 0 to $MOST_FOLD" }
        require(opacity == null || opacity in 0f..1f) { "Area ${id.value} has an opacity outside 0 to 1" }
    }

    /** Whether its cloth shows what lies under it: sheer or a net. */
    val seeThrough: Boolean get() = (opacity != null && opacity < 1f) || net != null
}

/**
 * How cloth over an upright part hangs below the widest place of it the cloth covers: straight down, or out by [flare] for each unit it
 * falls, standing off the body by at most [limit] more than its ease where the body narrows under it, in body units.
 */
@Serializable
data class ClothHang(val limit: Float, val flare: Float = 0f) {
    init {
        require(limit >= 0f) { "A hang's limit is negative" }
        require(flare in 0f..MOST_FLARE) { "A hang's flare lies outside 0 to $MOST_FLARE" }
    }

    companion object {
        /** The most a hang flares out for each unit it falls. */
        const val MOST_FLARE = 0.5f
    }
}

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

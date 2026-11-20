package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * A character: its parts, in [pairs] the data each mirrored pair shares, held for exactly the pairs its parts name, its
 * [palette], its [expressions], its [shading], its [creases], the [lineRules] that weigh its lines, the [traces] drawn over in its views,
 * the [pose] it stands in, the [displayPose] under it, its [wardrobe], its [paintedEyes] and its [membranes], skin spanned between parts. The pose is the character's one working pose until shots hold a
 * pose per key. The display pose is saved with the character and shows it as it is meant to be seen, a biped's arms brought down from
 * the rest it is built in: a bone the working pose leaves at rest stands as the display pose holds it, see [shownPose]. Its body is one surface meshed from its parts' joined solids.
 * See HLD: data-model.
 */
@Serializable
data class Character(
    val id: CharacterId,
    val name: String,
    val bodyPlan: BodyPlanRef,
    val parts: List<PartInstance> = emptyList(),
    internal val pairs: Map<PairId, PartData> = emptyMap(),
    val palette: Palette = Palette(),
    val expressions: List<ExpressionSet> = emptyList(),
    val shading: CharacterShading = CharacterShading(),
    val creases: CharacterCreases = CharacterCreases(),
    val lineRules: LineRules = LineRules(),
    val shownExpression: ExpressionId? = null,
    val traces: Map<View, Trace> = emptyMap(),
    val pose: Pose = Pose(),
    val displayPose: Pose = Pose(),
    val wardrobe: Wardrobe = Wardrobe(),
    val paintedEyes: List<PaintedEye> = emptyList(),
    val membranes: List<Membrane> = emptyList(),
) {
    init {
        val named = parts.mapNotNullTo(mutableSetOf()) { it.pair }
        require(pairs.keys == named) { "Pair data for ${pairs.keys.map { it.value }}, parts name ${named.map { it.value }}" }
        require(parts.none { isBranchPart(it.id) }) { "A part id starts as a garment's branch does" }
        val byId = parts.associateBy { it.id }
        for (part in parts) {
            val owner = part.garment
            require(owner == null || wardrobe.garment(owner) != null) { "Part ${part.id.value} is owned by ${owner?.value}, which the wardrobe does not hold" }
            val parentOwner = part.socket?.parent?.let(byId::get)?.garment
            require(parentOwner == null || parentOwner == owner) { "Part ${part.id.value} sits on a part ${parentOwner?.value} owns" }
        }
    }

    fun part(id: PartId): PartInstance? = parts.firstOrNull { it.id == id }

    /** The parts [garment] owns. */
    fun ownedBy(garment: GarmentId): List<PartInstance> = parts.filter { it.garment == garment }

    /** The pose the character shows: its working [pose], with each bone it leaves out standing as [displayPose] holds it. */
    val shownPose: Pose get() = shownWith(null)

    /** [shownPose] with [held], a pose a view shows for a moment, in place of the working pose while it is not null. */
    fun shownWith(held: Pose?): Pose = (held ?: pose).over(displayPose)

    /** The other side of [part]'s pair, or null for an unpaired part or a pair with one side placed. */
    fun twinOf(part: PartInstance): PartInstance? = part.pair?.let { pair -> parts.firstOrNull { it.pair == pair && it.id != part.id } }

    /** The part's effective values: its pair's, with anchors read for its side and its overrides on top, or its own when unpaired. */
    fun resolved(part: PartInstance): PartData {
        val pair = part.pair ?: return part.own
        return sharedReadBy(pairs.getValue(pair), part).overlaidWith(part.overrides)
    }

    /** The outlines and parameters of [resolved], without reading the surface work beside them. */
    fun resolvedShape(part: PartInstance): PartData {
        val pair = part.pair ?: return part.own.shape
        return pairs.getValue(pair).shape.overlaidWith(part.overrides.shape)
    }

    /**
     * What an edit of [part] starts from: with [thisSide], what the side shows, which is what its override will become;
     * otherwise what its pair shares, which an override on this side can hide. An unpaired part has one place for both.
     */
    fun editBase(part: PartInstance, thisSide: Boolean): PartData = when {
        part.pair == null -> part.own
        thisSide -> resolved(part)
        else -> sharedReadBy(pairs.getValue(part.pair), part)
    }

    /** The values [part] holds for itself over its pair's. */
    fun overridesOf(part: PartInstance): PartData = part.overrides

    /** The part's effective values, or null if it is not in this character. */
    fun resolved(id: PartId): PartData? = part(id)?.let { resolved(it) }
}

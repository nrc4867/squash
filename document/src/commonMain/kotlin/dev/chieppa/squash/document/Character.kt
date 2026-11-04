package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * A character: its parts, in [pairs] the data each mirrored pair shares, held for exactly the pairs its parts name, its
 * [palette] and its [expressions].
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
) {
    init {
        val named = parts.mapNotNullTo(mutableSetOf()) { it.pair }
        require(pairs.keys == named) { "Pair data for ${pairs.keys.map { it.value }}, parts name ${named.map { it.value }}" }
    }

    fun part(id: PartId): PartInstance? = parts.firstOrNull { it.id == id }

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

package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** A character: its parts and, in [pairs], the data each mirrored pair shares, held for exactly the pairs its parts name. */
@Serializable
data class Character(
    val id: CharacterId,
    val name: String,
    val bodyPlan: BodyPlanRef,
    val parts: List<PartInstance> = emptyList(),
    internal val pairs: Map<PairId, PartData> = emptyMap(),
) {
    init {
        val named = parts.mapNotNullTo(mutableSetOf()) { it.pair }
        require(pairs.keys == named) { "Pair data for ${pairs.keys.map { it.value }}, parts name ${named.map { it.value }}" }
    }

    fun part(id: PartId): PartInstance? = parts.firstOrNull { it.id == id }

    /** The part's effective outlines and parameters: its pair's with its overrides on top, or its own when unpaired. */
    fun resolved(part: PartInstance): PartData {
        val pair = part.pair ?: return part.own
        return pairs.getValue(pair).overlaidWith(part.overrides)
    }

    /** The part's effective outlines and parameters, or null if it is not in this character. */
    fun resolved(id: PartId): PartData? = part(id)?.let { resolved(it) }
}

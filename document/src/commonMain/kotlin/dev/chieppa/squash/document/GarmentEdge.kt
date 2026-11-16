package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An edge of a garment drawn on the body: a hem along [curve], or with [hole] an opening, whose smaller side is never cloth.
 */
@Serializable
data class GarmentEdge(val curve: SurfaceCurve, val hole: Boolean = false)

/** A small part, or a part's digits, a garment can cover: smooth cloth over it when tucked, cloth following it when shaped. */
@Serializable
enum class TuckKind {
    @SerialName("toes") TOES,
    @SerialName("claws") CLAWS,
    @SerialName("fingers") FINGERS,
    @SerialName("ears") EARS,
    @SerialName("tail") TAIL,
    @SerialName("horns") HORNS,
    @SerialName("spikes") SPIKES,
    @SerialName("fins") FINS,
    ;

    /** Whether cloth goes smooth over it until a choice is made. */
    val tuckedFirst: Boolean get() = this != FINGERS && this != EARS && this != FINS

    /** Whether it is digits of its part, built by its part's parameters, rather than a part of its own. */
    val digits: Boolean get() = this == TOES || this == CLAWS || this == FINGERS
}

/** The Tuck choice made for [kind] on [part], the leader of a pair: [tucked], or shaped. */
@Serializable
data class TuckChoice(val part: PartId, val kind: TuckKind, val tucked: Boolean)

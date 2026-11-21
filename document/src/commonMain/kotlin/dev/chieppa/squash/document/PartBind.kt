package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which bones a part a garment owns moves with: [SINGLE] the one bone it sits on, as a rigid piece; [FOLLOW] the bones of the body
 * under it, weighted as the body's skin is weighted there, so it bends with a joint it spans. See HLD: data-model, A garment can own parts.
 */
@Serializable
enum class PartBind {
    @SerialName("single") SINGLE,
    @SerialName("follow") FOLLOW,
}

/** The least and most a part's stiffness takes. */
val STIFF_RANGE: ClosedFloatingPointRange<Float> = 0f..1f

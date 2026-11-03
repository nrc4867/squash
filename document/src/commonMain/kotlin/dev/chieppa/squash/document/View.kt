package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A locked view a part's outline is drawn in. */
@Serializable
enum class View {
    @SerialName("side") SIDE,
    @SerialName("top") TOP,
    @SerialName("front") FRONT,
}

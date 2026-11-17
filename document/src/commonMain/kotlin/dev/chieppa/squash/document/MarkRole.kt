package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a mark does in a mouth group. */
@Serializable
enum class MarkRole {
    @SerialName("lipLine") LIP_LINE,
    @SerialName("inner") INNER,
    @SerialName("teeth") TEETH,
    @SerialName("tongue") TONGUE,
    @SerialName("corners") CORNERS,
}

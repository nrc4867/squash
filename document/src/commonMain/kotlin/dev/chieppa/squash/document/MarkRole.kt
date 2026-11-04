package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a mark does in a group: the seven of an eye, then the five of a mouth. */
@Serializable
enum class MarkRole {
    @SerialName("lid") LID,
    @SerialName("white") WHITE,
    @SerialName("iris") IRIS,
    @SerialName("pupil") PUPIL,
    @SerialName("highlight") HIGHLIGHT,
    @SerialName("brow") BROW,
    @SerialName("lashes") LASHES,
    @SerialName("lipLine") LIP_LINE,
    @SerialName("inner") INNER,
    @SerialName("teeth") TEETH,
    @SerialName("tongue") TONGUE,
    @SerialName("corners") CORNERS,
}

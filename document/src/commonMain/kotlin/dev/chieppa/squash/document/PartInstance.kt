package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Side {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

/** One side's outline and parameter deltas, layered on what a mirrored pair shares. */
@Serializable
data class PartOverrides(
    val outlines: Map<View, OutlineCurve> = emptyMap(),
    val params: Map<String, Float> = emptyMap(),
)

/**
 * Everything the user chose for one part. Slices are derived from this and its template and are never saved.
 * [socket] is null for the root part.
 */
@Serializable
data class PartInstance(
    val id: PartId,
    val template: TemplateRef,
    val socket: SocketRef? = null,
    val outlines: Map<View, OutlineCurve> = emptyMap(),
    val params: Map<String, Float> = emptyMap(),
    val pair: PairId? = null,
    val side: Side? = null,
    val overrides: PartOverrides = PartOverrides(),
    val grain: Grain = Grain(),
)

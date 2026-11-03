package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

const val BODY_PATCH = "body"

/**
 * A point on a part's surface. Survives refits because patch, u and v are template coordinates, not positions.
 * See HLD: data-model, Part template and part instance.
 */
@Serializable
data class Anchor(
    val part: PartId,
    val patch: String = BODY_PATCH,
    val u: Float,
    val v: Float,
    val offset: Float = 0f,
)

package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A collision shape's kind. */
@Serializable
enum class ColliderShape {
    @SerialName("sphere") SPHERE,
    @SerialName("capsule") CAPSULE,
}

/**
 * A shape hair and loose parts are kept out of once physics drives them: a sphere round [from], or a capsule from [from] to [to], [radius]
 * thick, in body space at rest, riding the part [part] (and its bone there) as the body moves.
 */
@Serializable
@Doc("A collision shape on the body, a sphere or a capsule riding a part, which hair is kept out of once physics drives it.")
data class Collider(
    @Doc("Its name, such as head, neck or shoulder-l.")
    val name: String,
    @Doc("The part it rides, by id.")
    val part: PartId,
    val shape: ColliderShape,
    @Doc("A sphere's middle, or a capsule's first end, in body space at rest.")
    val from: LockPoint,
    @Doc("A capsule's second end, in body space at rest; none for a sphere.")
    val to: LockPoint? = null,
    @Doc("How thick it is, in body units.") @Range(min = 0.0)
    val radius: Float,
) {
    init {
        require(radius > 0f) { "A collider's radius is above 0, not $radius" }
        require((shape == ColliderShape.CAPSULE) == (to != null)) { "A capsule has two ends and a sphere one" }
    }
}

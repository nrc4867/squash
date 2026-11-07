package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Side {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
}

/**
 * One part on the character. Slices are derived from it and its template and are never saved. [socket] is null for the
 * root part. [own] holds a part's values while it has no [pair] and stays empty while it has one; [overrides] are the
 * values this [side] replaces in its pair's. Both stay inside this module: [Character.resolved], [Character.editBase]
 * and [Character.overridesOf] are how anything reads them. [side] outlasts a broken pair, since the part still sits on that side.
 * [role] is what the part is to the body, which both sides of a pair share; [roleGuessed] says it was guessed and not yet confirmed.
 * See HLD: data-model, Mirroring is shared data plus overrides, and Roles.
 */
@Serializable
data class PartInstance(
    val id: PartId,
    val template: TemplateRef,
    val socket: SocketRef? = null,
    internal val own: PartData = PartData(),
    val pair: PairId? = null,
    val side: Side? = null,
    internal val overrides: PartData = PartData(),
    val role: Role? = null,
    val roleGuessed: Boolean = false,
) {
    init {
        if (pair == null) {
            require(overrides == PartData()) { "Part ${id.value} has overrides but no pair" }
        } else {
            require(own == PartData()) { "Part ${id.value} is paired, so its values live on the pair" }
            requireNotNull(side) { "Part ${id.value} is paired but has no side" }
        }
    }
}

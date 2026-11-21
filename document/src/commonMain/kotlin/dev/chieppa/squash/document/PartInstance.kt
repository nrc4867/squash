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
 * [garment] is the garment that owns the part, which builds only while that garment is worn, or null for a part of the body.
 * [blend] is the radius, in body units, of the smooth union where the part meets the part its [socket] is on; null takes the part's own default.
 * [bind] and [stiff] are how a part a garment owns moves with the body: [bind] which bones, null for the rule that picks one by where it
 * sits, and [stiff], 0 to 1, how much a part bound to several bones keeps each stretch rigid, null for 0. Both sides of a pair share them.
 * See HLD: data-model, Mirroring is shared data plus overrides, Roles, and Garments.
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
    val garment: GarmentId? = null,
    val blend: Float? = null,
    val bind: PartBind? = null,
    val stiff: Float? = null,
) {
    init {
        if (pair == null) {
            require(overrides == PartData()) { "Part ${id.value} has overrides but no pair" }
        } else {
            require(own == PartData()) { "Part ${id.value} is paired, so its values live on the pair" }
            requireNotNull(side) { "Part ${id.value} is paired but has no side" }
        }
        require(stiff == null || stiff in STIFF_RANGE) { "Part ${id.value}'s stiffness $stiff is outside 0 to 1" }
    }
}

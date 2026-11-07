package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a part is to the body, from a closed list: what the pose tools, Add part and the rig read, never the template's id. A
 * typed template gives it; a part with none has it guessed from where it sits. See HLD: data-model, Roles.
 */
@Serializable
enum class Role {
    @SerialName("body") BODY,
    @SerialName("spine") SPINE,
    @SerialName("neck") NECK,
    @SerialName("head") HEAD,
    @SerialName("jaw") JAW,
    @SerialName("ear") EAR,
    @SerialName("arm") ARM,
    @SerialName("hand") HAND,
    @SerialName("leg") LEG,
    @SerialName("foot") FOOT,
    @SerialName("tail") TAIL,
    @SerialName("wing") WING,
    @SerialName("fin") FIN,
    @SerialName("horn") HORN,
    @SerialName("other") OTHER,
    ;

    companion object {
        /** The limbs a pose reaches with, from the body to their end. */
        val LIMBS: Set<Role> = setOf(LEG, ARM, WING)

        /** The limbs that stand on the ground, so a pose can plant them; a wing reaches and never plants. */
        val PLANTING: Set<Role> = setOf(LEG, ARM)

        /** What ends a limb: a foot or a hand, which a pose plants. */
        val LIMB_ENDS: Set<Role> = setOf(FOOT, HAND)
    }
}

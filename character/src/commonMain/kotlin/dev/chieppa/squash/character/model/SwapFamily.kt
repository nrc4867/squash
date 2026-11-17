package dev.chieppa.squash.character.model

import dev.chieppa.squash.document.Role

/**
 * Templates that stand in for one another on a socket, by role: any limb for a limb, any hand or foot for a hand or foot, and
 * otherwise one role each. A generic template, with no role or [Role.OTHER], is in none.
 */
enum class SwapFamily(val roles: Set<Role>) {
    LIMB(setOf(Role.ARM, Role.LEG)),
    LIMB_END(setOf(Role.HAND, Role.FOOT)),
    BODY(setOf(Role.BODY)),
    SPINE(setOf(Role.SPINE)),
    NECK(setOf(Role.NECK)),
    HEAD(setOf(Role.HEAD)),
    JAW(setOf(Role.JAW)),
    EAR(setOf(Role.EAR)),
    TAIL(setOf(Role.TAIL)),
    WING(setOf(Role.WING)),
    FIN(setOf(Role.FIN)),
    HORN(setOf(Role.HORN)),
    HAIR(setOf(Role.HAIR)),
    ;

    companion object {
        /** The family [role] is in, or null for none. */
        fun of(role: Role?): SwapFamily? = entries.firstOrNull { role in it.roles }
    }
}

/** Whether a part of this template and one of [other] stand in for one another: both typed, in one [SwapFamily]. */
fun PartTemplate.swapsWith(other: PartTemplate): Boolean = SwapFamily.of(role)?.let { it == SwapFamily.of(other.role) } == true

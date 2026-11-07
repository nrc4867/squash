package dev.chieppa.squash.character.role

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateRef
import kotlin.math.abs

/** How far down a part's rest must point for it to hang from the body as a limb, as the rest direction's downward share. */
private const val HANGS = 0.7f

/** How near an end of its parent a socket must sit for the part on it to continue that end, in u. */
private const val END = 0.15f

/** How closely a part's rest must follow its parent's, forward or back, to continue it, as the cosine between them. */
private const val ALONG = 0.5f

/** How near the top of its parent a socket must sit to be on its top, in v either side of 0. */
private const val TOP = 0.2f

/** Where a head's muzzle starts along it, in u. */
private const val MUZZLE = 0.6f

/** How near the underside a socket on the muzzle must sit to be the jaw, in v either side of 0.5. */
private const val UNDER = 0.2f

private const val MIDDLE = 0.5f

/**
 * The roles of [character]'s parts, reading their templates through [templates] and their sockets through [plan]. A part's role is its
 * own, else its template's; [guess] reads where a part sits for a part whose template gives none. See HLD: data-model, Roles.
 */
class PartRoles(private val character: Character, private val templates: (TemplateRef) -> PartTemplate?, private val plan: BodyPlan?) {
    /** [part]'s role: its own, else its template's, else null. */
    fun of(part: PartInstance): Role? = part.role ?: templates(part.template)?.role

    /** [part]'s role, guessed where neither it nor its template gives one. */
    fun resolved(part: PartInstance): Role = of(part) ?: guess(part)

    /** Whether [part]'s [resolved] role is a guess not yet confirmed: one stored as a guess, or one guessed now for want of any. */
    fun isGuess(part: PartInstance): Boolean = if (part.role != null) part.roleGuessed else of(part) == null

    /** Whether the character stands on four legs: a front limb, an [Role.ARM], bears weight. */
    val fourLegged: Boolean
        get() = character.parts.any { of(it) == Role.ARM && templates(it.template)?.axis?.bearsWeight == true }

    /**
     * The role a part from [template] takes on [socket]: the template's, where a leg on the front half of the body is an arm and a
     * foot on an arm a hand, so a four-legged plan's front limbs are its arms; or, for a template with none, the [guess].
     */
    fun placed(template: PartTemplate, socket: SocketRef?): Role {
        val probe = PartInstance(PROBE, template.ref, socket)
        val parent = socket?.let { character.part(it.parent) }
        return when (val role = template.role) {
            null -> guess(probe, template)
            Role.LEG -> if (parent != null && isForelimb(parent, socket.socket)) Role.ARM else Role.LEG
            Role.FOOT -> if (parent != null && resolved(parent) == Role.ARM) Role.HAND else Role.FOOT
            else -> role
        }
    }

    /**
     * What [part] most likely is from where it sits: the root is the body; on the body or a spine, a part hanging down is a leg, or an
     * arm on the front half, a part leaving the rear end along the body a tail and one leaving the front end along it a head; on a
     * neck, one leaving its end is a head; on a head, a part on its top is an ear and one under its muzzle a jaw; on a limb's end, a
     * foot, or a hand on an arm; on a tail's end, more tail. Anything else is [Role.OTHER].
     */
    fun guess(part: PartInstance, template: PartTemplate? = templates(part.template)): Role {
        val socket = part.socket ?: return Role.BODY
        val parent = character.part(socket.parent) ?: return Role.OTHER
        val parentTemplate = templates(parent.template) ?: return Role.OTHER
        val spec = specOn(parent, parentTemplate, socket.socket) ?: return Role.OTHER
        val rest = template?.axis?.rest?.toVec3()?.normalized() ?: return Role.OTHER
        val along = rest dot parentTemplate.axis.rest.toVec3().normalized()
        val atStart = spec.u <= END
        val atEnd = spec.u >= 1f - END
        return when (resolved(parent)) {
            Role.BODY, Role.SPINE -> when {
                -rest.y >= HANGS -> if (isForelimb(parent, socket.socket)) Role.ARM else Role.LEG
                atStart && along <= -ALONG -> Role.TAIL
                atEnd && along >= ALONG -> Role.HEAD
                else -> Role.OTHER
            }
            Role.NECK -> if (atEnd && along >= ALONG) Role.HEAD else Role.OTHER
            Role.HEAD -> when {
                spec.v <= TOP || spec.v >= 1f - TOP -> Role.EAR
                spec.u >= MUZZLE && abs(spec.v - MIDDLE) <= UNDER -> Role.JAW
                else -> Role.OTHER
            }
            Role.ARM -> if (atEnd) Role.HAND else Role.OTHER
            Role.LEG -> if (atEnd) Role.FOOT else Role.OTHER
            Role.TAIL -> if (atEnd && along >= ALONG) Role.TAIL else Role.OTHER
            else -> Role.OTHER
        }
    }

    /** Whether socket [name] on [parent] sits on the front half of a body part, ahead of its middle along its rest, or above it on an upright one. */
    private fun isForelimb(parent: PartInstance, name: String): Boolean {
        val template = templates(parent.template) ?: return false
        if (resolved(parent) != Role.BODY) return false
        val spec = specOn(parent, template, name) ?: return false
        val rest = template.axis.rest.toVec3()
        return (spec.u - MIDDLE) * (rest.x + rest.y) > 0f
    }

    /** Socket [name] on [parent], where the Move tool has put it. */
    private fun specOn(parent: PartInstance, template: PartTemplate, name: String): SocketSpec? {
        val spec = (plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }).firstOrNull { it.name == name } ?: return null
        val params = character.resolvedShape(parent).params
        return spec.copy(u = params[PlacementParams.socketU(name)] ?: spec.u, v = params[PlacementParams.socketV(name)] ?: spec.v)
    }

    private companion object {
        /** The id a part not yet placed is guessed under. */
        val PROBE = PartId("")
    }
}

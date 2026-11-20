package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef

/** The version of the hair-volume template a fitted mass takes. */
private const val MASS_VERSION = 1

/** The hair-volume template's length from back to front at size 1, in body units. */
private const val MASS_LENGTH = 0.3f

/** Where on the head a fitted mass's socket lies: up its length and round it, the middle of its back. */
private const val MASS_SOCKET_U = 0.62f
private const val MASS_SOCKET_V = 0.5f

/** How far a fitted mass stands off the head, as a share of the head's depth from back to front. */
private const val MASS_OUTSET = 0.07f

/**
 * A fitted mass's shape: a close cap over the skull whose front ends above the brow, under where the fringe locks root, and whose sides and
 * back stop at the ears and at the nape, so the locks cover its edges, and its hem is cut into points, so where it shows between them it
 * reads as more locks. Its crown is set by the head's height.
 */
private val MASS_SHAPE = mapOf(
    "width" to 0.42f, "back" to 0.55f, "sides" to 0.2f, "fringe" to -0.5f, "rise" to 0.25f, "flip" to 0f, "fullness" to 2.4f,
    "locks" to 24f, "lockDepth" to 0.12f, "lockJag" to 0.5f, "layers" to 0f, "lockLines" to 0f, "strands" to 0f,
)

/** A mass fitted to a head, ready to add: its [id] and the [commands] that add it, its socket first. */
class FittedMass(val id: PartId, val commands: List<DocumentCommand>)

/** The parameters of a mass fitted over a head [depth] deep from back to front and [height] tall. */
fun fittedMassShape(depth: Float, height: Float): Map<String, Float> {
    val outset = depth * MASS_OUTSET
    val length = depth + 2f * outset
    val crown = (height * (1f - MASS_SOCKET_U) + outset) / length
    return MASS_SHAPE + mapOf("size" to length / MASS_LENGTH, "crown" to crown)
}

/**
 * A hair mass of the hair-volume template fitted over [head] as built, on a socket of its own at the back of the head, sized by the head's
 * box and coloured as lock hair is, or [slot]; [params] given replace the fitted ones. Its id is [wanted], or the head's id with `-hair`,
 * numbered on where a part holds it. Null when the head builds no box. See HLD: parts, Hair locks.
 */
fun fittedMass(
    character: Character, head: PlacedPart, headId: PartId, wanted: PartId? = null, params: Map<String, Float> = emptyMap(), slot: SlotName? = null,
): FittedMass? {
    val box = head.box ?: return null
    val instance = character.part(headId) ?: return null
    val outset = box.size.x * MASS_OUTSET
    val socket = HandSockets.nextId(character.resolvedShape(instance).params)
    val id = freeId(character, wanted ?: PartId("${headId.value}-hair"))
    val color = slot ?: HAIR_SLOT.takeIf { hair -> character.palette.slots.any { it.name == hair } } ?: character.resolved(instance).surface.baseSlot
    return FittedMass(
        id,
        listOf(
            HandSockets.add(headId, socket, MASS_SOCKET_U, MASS_SOCKET_V, 0f, false, sink = 0f, outset = outset),
            AddPart(PartInstance(id, TemplateRef(TemplateId(MASS_TEMPLATE), MASS_VERSION), SocketRef(headId, HandSockets.name(socket)), role = Role.HAIR)),
            SetParameters(id, fittedMassShape(box.size.x, box.size.y) + params),
            SetBaseSlot(id, color),
        ),
    )
}

/** [wanted], or with a number after it, the first that no part of [character] holds. */
private fun freeId(character: Character, wanted: PartId): PartId =
    generateSequence(1) { it + 1 }.map { if (it == 1) wanted else PartId("${wanted.value}-$it") }.first { character.part(it) == null }

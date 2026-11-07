package dev.chieppa.squash.character.face

import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.model.LandmarkSpec
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.eyeGroup
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.document.mouthGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val EYE_LEFT = "eye-l"
private const val EYE_RIGHT = "eye-r"
private const val MOUTH_ID = "mouth"

/** Where a starter eye sits on the head and how wide its lid is, in u and v; the other eye mirrors it across the middle. */
private const val STARTER_U = 0.6f
private const val STARTER_V = 0.185f
private const val STARTER_HALF_U = 0.075f
private const val STARTER_HALF_V = 0.052f
private const val STARTER_WHITE = 1.15f
private const val STARTER_PUPIL = 0.45f
private const val RING_POINTS = 12
private const val LID_UNDER = 0.72f

/** Halfway round a part, past which a v lies on its right. */
private const val HALF_ROUND = 0.5f

/** The slots a starter eye fills its white and its pupil with, where the palette holds them. */
private val STARTER_WHITE_SLOT = SlotName("cream")
private val STARTER_PUPIL_SLOT = SlotName("ink")

/** A closed ring of places about [u], [v] on [part], [left] as drawn or mirrored across the middle. */
private fun ring(part: PartId, left: Boolean, u: Float, v: Float, halfU: Float, halfV: Float, under: Float = 1f): List<Anchor> = List(RING_POINTS + 1) { i ->
    val angle = 2.0 * PI * (i % RING_POINTS) / RING_POINTS
    val over = sin(angle).toFloat()
    val at = v - halfV * over * (if (over > 0f) 1f else under)
    Anchor(part, u = u - halfU * cos(angle).toFloat(), v = if (left) at else 1f - at)
}

/** One starter eye about [u] and [v] on the left: an inked lid, a filled white inside it and a filled pupil, enough for the eye's controls to move something at once. */
private fun starterEye(character: Character, part: PartId, id: String, left: Boolean, u: Float, v: Float): List<DocumentCommand> {
    val held = { slot: SlotName -> slot.takeIf { character.palette.color(it) != null } }
    val lid = Mark(MarkId("$id-lid"), ring(part, left, u, v, STARTER_HALF_U, STARTER_HALF_V, LID_UNDER))
    val white = Mark(MarkId("$id-white"), ring(part, left, u, v, STARTER_HALF_U * STARTER_WHITE, STARTER_HALF_V * STARTER_WHITE), ink = false)
    val pupil = Mark(MarkId("$id-pupil"), ring(part, left, u, v, STARTER_HALF_U * STARTER_PUPIL, STARTER_HALF_V * STARTER_PUPIL), ink = false)
    val drawn = mapOf(MarkRole.LID to GroupRole(MarkRole.LID, marks = listOf(lid.id)),
        MarkRole.WHITE to GroupRole(MarkRole.WHITE, ink = false, fill = held(STARTER_WHITE_SLOT), marks = listOf(white.id)),
        MarkRole.PUPIL to GroupRole(MarkRole.PUPIL, ink = false, fill = held(STARTER_PUPIL_SLOT), marks = listOf(pupil.id)))
    val empty = eyeGroup(MarkGroupId(id), id)
    val group = empty.copy(roles = empty.roles.map { drawn[it.role] ?: it })
    return listOf(lid, white, pupil).map { SetMark(part, it.id, it) } + SetMarkGroup(part, group.id, group)
}

/**
 * The command adding a pair of starter eyes to [part], or null when it has eyes. They sit about [at], a landmark placed on the part
 * by hand, with the other eye across the middle, or where a starter head's eyes sit when there is none.
 */
fun newEyesCommand(character: Character, part: PartId, at: LandmarkSpec? = null): DocumentCommand? {
    val groups = character.part(part)?.let { character.resolved(it) }?.surface?.groups ?: return null
    if (groups.any { it.isEye }) return null
    val u = at?.u ?: STARTER_U
    val v = at?.v?.let { if (it > HALF_ROUND) 1f - it else it } ?: STARTER_V
    return Together(starterEye(character, part, EYE_LEFT, left = true, u, v) + starterEye(character, part, EYE_RIGHT, left = false, u, v))
}

/** The landmark a part's eyes sit about: the first [template] holds by hand, else its own `eye` landmark, or null. */
fun eyeLandmark(template: PartTemplate): LandmarkSpec? {
    val landmarks = template.blocks.flatMap { it.landmarks() }
    return landmarks.firstOrNull { HandLandmarks.idOf(it.name) != null } ?: landmarks.firstOrNull { it.name == EYE_LANDMARK }
}

/** The landmark a template names for its eyes. */
private const val EYE_LANDMARK = "eye"

/** The command adding an empty mouth to [part]. */
fun newMouthCommand(part: PartId): DocumentCommand = SetMarkGroup(part, MarkGroupId(MOUTH_ID), mouthGroup(MarkGroupId(MOUTH_ID), MOUTH_ID))

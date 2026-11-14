package dev.chieppa.squash.screens.person

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetOneSurface
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SetSocketBlend
import dev.chieppa.squash.document.SlotName

/** How wide the blend under the person's chin is, so the head stays round over the torso. */
private const val CHIN_BLEND = 0.02f

/** How wide the blend where the hair meets the head is, so the hair's brim stands out over the brow as a cap of hair does. */
private const val HAIRLINE_BLEND = 0.006f

/** The painted eyes' id on the person's head. */
const val PERSON_EYES = "eyes"

/** Where the person's eye sits on the round head: about its eye landmark, as Add eyes places a starter eye. */
private const val EYE_U = 0.9f
private const val EYE_V = 0.21f

private val CREAM = SlotName("cream")
private val INK = SlotName("ink")

/**
 * The person's painted eye: a starter eye in the eye's parameters, a cream almond with an inked lid round it and a round ink pupil in
 * its middle, no iris, highlight, lashes or brow.
 */
val PERSON_EYE = EyeLook(
    width = 0.085f,
    height = 0.04f,
    lowerLid = 0.72f,
    frontNarrowing = -0.7f,
    tilt = -6f,
    irisSize = 0.033f,
    irisRatio = 0.8f,
    irisForward = -0.012f,
    irisUp = 0f,
    pupilSize = 1f,
    highlightSize = 0f,
    lashes = false,
    brow = false,
    white = CREAM,
    pupil = INK,
    ink = INK,
)

/** The person's eye as its front view shows it: wider and flatter, its front corner lower. */
val PERSON_FRONT_EYE = PERSON_EYE.copy(width = 0.088f, height = 0.054f, frontNarrowing = -0.5f, tilt = 6f, irisSize = 0.034f, irisRatio = 0.8f, irisForward = -0.01f)

/** The person's painted eyes, both sides. */
fun personPaintedEyes(look: EyeLook = PERSON_EYE, front: EyeLook? = PERSON_FRONT_EYE): List<DocumentCommand> = listOf(
    SetPaintedEye(PERSON_EYES, PaintedEye(PERSON_EYES, Anchor(HEAD, BODY_PATCH, EYE_U, EYE_V), look, front = front)),
)

/** The commands that build the person as one surface: the switch, narrow blends under the chin and at the hairline, and the painted eyes. */
fun personOneSurface(): List<DocumentCommand> = listOf(SetOneSurface(true), SetSocketBlend(HEAD, CHIN_BLEND), SetSocketBlend(HAIR, HAIRLINE_BLEND)) + personPaintedEyes()

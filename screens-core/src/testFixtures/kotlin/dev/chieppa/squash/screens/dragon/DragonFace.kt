package dev.chieppa.squash.screens.dragon

import dev.chieppa.squash.character.face.newMouthCommand
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName

/** The painted eyes' id on the dragon's head. */
const val DRAGON_EYES = "eyes"

val DRAGON_IRIS = SlotName("iris")
val DRAGON_PUPIL = SlotName("pupil")

/** Where the eye sits on the head, at its template's eye landmark: on the skull behind the snout, up toward the brow ridge. */
private const val EYE_U = 0.5f
private const val EYE_V = 0.2f

/** The dragon's eye: narrow and slanted, an amber iris filling it, an upright slit pupil, a small highlight and a lid line with a brow. */
val DRAGON_EYE = EyeLook(
    width = 0.07f,
    height = 0.045f,
    lowerLid = 0.8f,
    frontNarrowing = 0.25f,
    tilt = -8f,
    irisSize = 0.06f,
    irisRatio = 1f,
    irisForward = 0.006f,
    irisUp = 0f,
    pupilSize = 0.5f,
    slit = true,
    highlightSize = 0.25f,
    lashes = false,
    brow = true,
    browRaise = 0.01f,
    browTilt = -12f,
    white = DRAGON_IRIS,
    iris = DRAGON_IRIS,
    pupil = DRAGON_PUPIL,
    highlight = BELLY,
    ink = DRAGON_PUPIL,
)

/** The dragon's face: amber eyes painted on the head, and a mouth. */
fun dragonFace(): List<DocumentCommand> = listOf(
    SetPaletteSlot(DRAGON_IRIS, SlotColor(0.98f, 0.70f, 0.16f)),
    SetPaletteSlot(DRAGON_PUPIL, SlotColor(0.16f, 0.07f, 0.06f)),
    SetPaintedEye(DRAGON_EYES, PaintedEye(DRAGON_EYES, Anchor(HEAD, BODY_PATCH, EYE_U, EYE_V), DRAGON_EYE)),
    newMouthCommand(HEAD),
)

package dev.chieppa.squash.screens.toucan

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

/** The painted eyes' id on the toucan's head. */
const val TOUCAN_EYES = "eyes"

val TOUCAN_IRIS = SlotName("iris")

/** Where the eye sits on the head, at the bird head's side eye landmark, in the middle of its pale ring. */
const val TOUCAN_EYE_U = 0.6f
const val TOUCAN_EYE_V = 0.26f

/** The toucan's eye: small and round in its ring, a dark green iris filling it, a black pupil, a pale highlight and a lid line, no lashes or brow. */
val TOUCAN_EYE = EyeLook(
    width = 0.075f,
    height = 0.06f,
    lowerLid = 1f,
    frontNarrowing = 0f,
    irisSize = 0.11f,
    irisRatio = 1f,
    irisForward = 0f,
    irisUp = 0f,
    pupilSize = 0.45f,
    highlightSize = 0.3f,
    lashes = 0,
    brow = false,
    white = TOUCAN_IRIS,
    iris = TOUCAN_IRIS,
    pupil = BODY,
    highlight = BELLY,
    ink = BODY,
)

/** The face: eyes painted on the bird head's sides, and a mouth whose open swings the lower beak. */
fun toucanFace(): List<DocumentCommand> = listOf(
    SetPaletteSlot(TOUCAN_IRIS, SlotColor(0.16f, 0.30f, 0.26f)),
    SetPaintedEye(TOUCAN_EYES, PaintedEye(TOUCAN_EYES, Anchor(HEAD, BODY_PATCH, TOUCAN_EYE_U, TOUCAN_EYE_V), TOUCAN_EYE)),
    newMouthCommand(HEAD),
)

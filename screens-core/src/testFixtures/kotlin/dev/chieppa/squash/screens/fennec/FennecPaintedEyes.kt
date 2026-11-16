package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument

/** The painted eyes' id on the fennec's head. */
const val PAINTED_EYES = "eyes"

/** Where the fennec's eye sits on its skull, as its drawn three-quarter eye does. */
private const val EYE_U = 0.60f
private const val EYE_V = 0.185f

/** The fennec's default painted eye: its drawn three-quarter eye in the eye's parameters, white and highlight in cream, iris and ink its own. */
val FENNEC_EYE = EyeLook(white = CREAM, iris = IRIS, pupil = INK, highlight = CREAM, ink = INK)

/**
 * The fennec's painted eye as its front drawing shows it: an upright oval as tall as it is wide, even top and bottom, with the iris filling
 * most of it.
 */
val FENNEC_FRONT_EYE = FENNEC_EYE.copy(
    width = 0.058f,
    height = 0.088f,
    lowerLid = 1f,
    frontNarrowing = 0f,
    tilt = 20f,
    irisSize = 0.052f,
    irisRatio = 1.15f,
    irisForward = 0.01f,
    irisUp = -0.012f,
    highlightSize = 0.32f,
    lashes = false,
)

/** The dark-brown iris the fennec's eyes show. */
val DARK_IRIS = SlotName("dark-iris")

/** The fennec's painted eyes, both sides, in [look] at [place]. */
fun fennecPaintedEyes(look: EyeLook = FENNEC_EYE, place: Anchor = Anchor(HEAD, BODY_PATCH, EYE_U, EYE_V), front: EyeLook? = FENNEC_FRONT_EYE): List<DocumentCommand> = listOf(
    SetPaletteSlot(DARK_IRIS, SlotColor(0.30f, 0.17f, 0.09f)),
    SetPaintedEye(PAINTED_EYES, PaintedEye(PAINTED_EYES, place, look, front = front)),
)

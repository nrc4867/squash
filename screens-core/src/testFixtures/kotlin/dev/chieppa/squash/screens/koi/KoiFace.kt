package dev.chieppa.squash.screens.koi

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetPaintedEye

/** The painted eyes' id on the koi's body. */
const val KOI_EYES = "eyes"

/** Where the eye sits on the body, behind the snout and up toward the back. */
private const val EYE_U = 0.86f
private const val EYE_V = 0.2f

/** The koi's eye: a round gold ring about a large dark iris and pupil, a pale highlight, a thin lid line and no lashes or brow. */
val KOI_EYE = EyeLook(
    width = 0.035f,
    height = 0.06f,
    lowerLid = 1f,
    frontNarrowing = 0f,
    irisSize = 0.045f,
    irisRatio = 1f,
    irisForward = 0f,
    irisUp = 0f,
    pupilSize = 0.6f,
    highlightSize = 0.3f,
    lashes = 0,
    brow = false,
    white = EYE,
    iris = PUPIL,
    pupil = PUPIL,
    highlight = BELLY,
    ink = PATCH,
)

/** The koi's face: its eyes painted on the body. */
fun koiFace(): List<DocumentCommand> = listOf(
    SetPaintedEye(KOI_EYES, PaintedEye(KOI_EYES, Anchor(KOI_BODY, BODY_PATCH, EYE_U, EYE_V), KOI_EYE)),
)

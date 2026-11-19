package dev.chieppa.squash.screens.lizard

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetPaintedEye

/** The painted eyes' id on the lizard's head. */
const val LIZARD_EYES = "eyes"

/** Where the eye sits on the head, at its face landmark: halfway back and up toward the top. */
private const val EYE_U = 0.45f
private const val EYE_V = 0.2f

/** The lizard's eye: round, a yellow iris filling it, a dark round pupil, a small highlight and a lid line, no lashes or brow. */
val LIZARD_EYE = EyeLook(
    width = 0.09f,
    height = 0.07f,
    lowerLid = 1f,
    frontNarrowing = 0f,
    irisSize = 0.13f,
    irisRatio = 1f,
    irisForward = 0f,
    irisUp = 0f,
    pupilSize = 0.4f,
    highlightSize = 0.25f,
    lashes = 0,
    brow = false,
    white = EYE,
    iris = EYE,
    pupil = PUPIL,
    highlight = BELLY,
    ink = STRIPE,
)

/** The lizard's face: its eyes painted on the head. */
fun lizardFace(): List<DocumentCommand> = listOf(
    SetPaintedEye(LIZARD_EYES, PaintedEye(LIZARD_EYES, Anchor(HEAD, BODY_PATCH, EYE_U, EYE_V), LIZARD_EYE)),
)

package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.WidthStop

/** The painted eyes' id on the cat's head. */
const val CAT_EYES = "eyes"

/** Where the eye sits on the skull, just behind the muzzle and up toward the brow. */
private const val EYE_U = 0.72f
private const val EYE_V = 0.17f

/** The cat's eye: big and nearly round, a green iris filling it, an upright slit pupil, a bright highlight and a lash line. */
val CAT_EYE = EyeLook(
    width = 0.078f,
    height = 0.07f,
    lowerLid = 0.9f,
    frontNarrowing = 0.08f,
    tilt = 8f,
    irisSize = 0.062f,
    irisRatio = 1.1f,
    irisForward = 0.012f,
    irisUp = 0f,
    pupilSize = 0.45f,
    slit = true,
    highlightSize = 0.36f,
    brow = false,
    lashes = false,
    white = WHITE,
    iris = GREEN,
    pupil = CAT_INK,
    highlight = WHITE,
    ink = CAT_INK,
)

/** The eye seen from the front: rounder still. */
val CAT_FRONT_EYE = CAT_EYE.copy(width = 0.064f, height = 0.078f, lowerLid = 1f, frontNarrowing = 0f, irisRatio = 1.2f)

private val TAPER = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 0.25f)))
private val EVEN = MarkWidth(listOf(WidthStop(0f, 0.8f), WidthStop(1f, 0.8f)))

private fun muzzle(u: Float, v: Float) = Anchor(CAT_HEAD, MUZZLE, u, v)

/** A line on the muzzle through [points], drawn on the left and mirrored to the right. */
private fun line(id: String, width: MarkWidth, vararg points: Pair<Float, Float>): DocumentCommand =
    SetMark(CAT_HEAD, MarkId(id), Mark(MarkId(id), points.map { (u, v) -> muzzle(u, v) }, width, mirrored = true))

/** Three whiskers fanning back from the whisker pad, each tapering to its tip. */
private fun whiskers(): List<DocumentCommand> = listOf(
    line("whisker-1", TAPER, 0.86f to 0.19f, 0.66f to 0.20f, 0.48f to 0.21f),
    line("whisker-2", TAPER, 0.86f to 0.21f, 0.66f to 0.22f, 0.44f to 0.24f),
    line("whisker-3", TAPER, 0.86f to 0.25f, 0.66f to 0.29f, 0.48f to 0.33f),
)

/** The mouth: a short line down from the nose, parting into two small curves. */
private fun mouth(): List<DocumentCommand> = listOf(
    line("mouth", EVEN, 0.99f to 0.08f, 0.985f to 0.20f, 0.95f to 0.25f, 0.91f to 0.23f),
)

/** The cat's face: painted eyes, whiskers and mouth. */
fun catFace(): List<DocumentCommand> = whiskers() + mouth() +
    SetPaintedEye(CAT_EYES, PaintedEye(CAT_EYES, Anchor(CAT_HEAD, BODY_PATCH, EYE_U, EYE_V), CAT_EYE, front = CAT_FRONT_EYE))

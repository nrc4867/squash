package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.document.mouthGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/*
 * The painted face, read from the reference's numbers about its head centre, which lies HEAD_CENTRE above the neck's top: x to her left,
 * y up, in the head's drawn units.
 */

/** The place ([x], [y]) about the head centre, on the face map. */
private fun at(x: Float, y: Float, left: Boolean = true): Anchor = FACE.on(x, HEAD_CENTRE + y, left)

private fun ring(x: Float, y: Float, rx: Float, ry: Float, left: Boolean): List<Anchor> = FACE.ring(x, HEAD_CENTRE + y, rx, ry, left)

/** One eye's opening as the front sees it, about the head centre: its inner and outer corners, its line, and how far its upper lid opens. */
private object Eye {
    const val INNER = 0.0245f
    const val OUTER = 0.0629f
    const val LINE = -0.003f
    const val UPPER = 0.013f
    const val TILT = 0.0027f
    val middle: Float get() = (INNER + OUTER) / 2f
    val halfWidth: Float get() = (OUTER - INNER) / 2f
}

/** The painted eyes' id on her head. */
const val TWIN_EYES = "eyes"

/**
 * Her painted eye, matched to the reference's: an almond opening lifted toward its outer corner, an olive iris most of its height with a
 * round pupil and a small highlight toward the nose, a lid line, a wing flicking up from the outer corner and a brow arched over it.
 */
val TWIN_EYE = EyeLook(
    width = 0.036f,
    height = 0.047f,
    lowerLid = 0.74f,
    frontNarrowing = 0.1f,
    openingTilt = 10f,
    irisSize = 0.026f,
    irisRatio = 2.2f,
    irisForward = 0f,
    irisUp = 0f,
    pupilSize = 0.38f,
    highlightSize = 0.2f,
    highlightForward = 0.32f,
    highlightUp = -0.4f,
    browTilt = 0f,
    browRaise = 0.03f,
    white = EYE_WHITE,
    iris = IRIS,
    pupil = PUPIL,
    highlight = EYE_WHITE,
)

/** Her painted eyes, both sides, about the middle of her eye's opening on the face map. */
fun twinPaintedEyes(look: EyeLook = TWIN_EYE): List<DocumentCommand> =
    listOf(SetPaintedEye(TWIN_EYES, PaintedEye(TWIN_EYES, at(Eye.middle, Eye.LINE + Eye.TILT / 2f), look)))

private val FINE = MarkWidth(listOf(WidthStop(0f, 0.4f), WidthStop(0.5f, 0.7f), WidthStop(1f, 0.4f)))

/** The mouth line, curving up a little to each corner, and the lips' half width. */
private const val MOUTH = -0.0715f
private const val MOUTH_CURVE = 6f
private const val LIP_HALF = 0.0215f
private const val UPPER_LIP = 0.0064f
private const val LOWER_LIP = 0.0098f
private const val BOW = 0.0017f
private const val LIP_POINTS = 8

private fun mouthY(x: Float) = MOUTH + MOUTH_CURVE * x * x

private fun across(points: Int, edge: (Float) -> Float): List<Pair<Float, Float>> = List(points + 1) { i ->
    val x = -LIP_HALF + 2f * LIP_HALF * i / points
    x to edge(x)
}

private fun bulge(x: Float, half: Float, power: Float) = (1f - (x / half) * (x / half)).coerceAtLeast(0f).pow(power)

private fun mouth(): List<DocumentCommand> {
    val lipLine = Mark(MarkId("mouth-line"), across(LIP_POINTS) { mouthY(it) }.map { (x, y) -> at(x, y) }, FINE)
    val empty = mouthGroup(MarkGroupId("mouth"), "mouth")
    val group = empty.copy(roles = empty.roles.map { if (it.role == MarkRole.LIP_LINE) it.copy(marks = listOf(lipLine.id)) else it })
    return listOf(SetMark(HEAD, lipLine.id, lipLine), SetMarkGroup(HEAD, group.id, group))
}

private fun region(id: String, slot: SlotName, points: List<Anchor>) = Region(RegionId(id), points.dropLast(1), slot)

/** The lips in [slot]: the upper from the mouth line up over its bow, the lower from the mouth line down, as the reference paints them. */
internal fun lipRegions(slot: SlotName): List<Region> {
    val upperTop = across(LIP_POINTS) { mouthY(it) + UPPER_LIP * bulge(it, 0.0205f, 0.7f) - BOW * exp(-(it / 0.0035f) * (it / 0.0035f)) }
    val lowerBottom = across(LIP_POINTS) { mouthY(it) - LOWER_LIP * bulge(it, 0.0185f, 0.75f) }
    val line = across(LIP_POINTS) { mouthY(it) }
    fun closed(top: List<Pair<Float, Float>>, bottom: List<Pair<Float, Float>>) = (top + bottom.reversed() + top.first()).map { (x, y) -> at(x, y) }
    return listOf(region("lip-upper", slot, closed(upperTop, line)), region("lip-lower", slot, closed(line, lowerBottom)))
}

/** Eyeshadow in [slot] over each upper lid, from the lash line toward the brow. */
internal fun lidShadows(slot: SlotName): List<Region> = listOf(true, false).map { left ->
    region(if (left) "lid-shadow-l" else "lid-shadow-r", slot, ring(Eye.middle, Eye.LINE + Eye.UPPER * 0.9f, Eye.halfWidth * 1.15f, Eye.UPPER * 1.1f, left))
}

/** The blush in [slot], an ellipse on each cheek. */
internal fun blushRegions(slot: SlotName): List<Region> = listOf(true, false).map { left ->
    region(if (left) "blush-l" else "blush-r", slot, ring(0.05f, -0.047f, 0.016f, 0.0075f, left))
}

/** The nostrils, the lower half of a small ring each side under the nose's tip. */
private fun nostrils(): List<DocumentCommand> = listOf(true, false).map { left ->
    val points = List(5) { i ->
        val angle = PI + PI * i / 4
        at(0.0072f + 0.0034f * cos(angle).toFloat(), -0.0385f + 0.0034f * sin(angle).toFloat(), left)
    }
    val id = MarkId(if (left) "nostril-l" else "nostril-r")
    SetMark(HEAD, id, Mark(id, points, FINE))
}

/** The face drawn on the head: the nostrils, the mouth line and lips, eyeshadow and blush, in the natural look; the eyes are painted. */
fun twinFace(): List<DocumentCommand> = mouth() + nostrils() +
    (lipRegions(LIPS) + lidShadows(LID) + blushRegions(BLUSH)).map { SetRegion(HEAD, it.id, it) }

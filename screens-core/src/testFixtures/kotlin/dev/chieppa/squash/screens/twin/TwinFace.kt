package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.document.eyeGroup
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

/**
 * One eye's opening as the front sees it: the lids' corners from the eyeball's azimuth of 0.8 radians each side of its axis, turned out
 * 0.17, the upper lid 0.5 radians up and the lower 0.36 down at the middle, the outer corner raised; the iris and pupil about the eye's axis.
 */
private object Eye {
    const val RADIUS = 0.0272f
    const val INNER = 0.0245f
    const val OUTER = 0.0629f
    const val LINE = -0.003f
    const val UPPER = 0.013f
    const val LOWER = 0.0096f
    const val TILT = 0.0027f
    const val IRIS_X = 0.0451f
    const val IRIS = 0.0144f
    const val PUPIL = 0.0054f
    const val GLINT_DX = -0.0046f
    const val GLINT_DY = 0.0057f
    const val GLINT = 0.0027f
    val middle: Float get() = (INNER + OUTER) / 2f
    val halfWidth: Float get() = (OUTER - INNER) / 2f
}

private const val LID_POINTS = 16

/** The brow from its thick inner end to its tapering outer tip, and the wing flicking up from the outer corner by the look's length. */
private val BROW_LINE = listOf(0.017f to 0.047f, 0.044f to 0.0555f, 0.069f to 0.044f)
private const val WING = 0.6f
private val WING_LINE = listOf(Eye.OUTER - 0.0075f to Eye.LINE + Eye.TILT - 0.0005f, Eye.OUTER + 0.0035f + 0.006f * WING to Eye.LINE + Eye.TILT + 0.0028f + 0.0035f * WING)

private val LID_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.6f), WidthStop(0.25f, 1.6f), WidthStop(0.5f, 0.6f), WidthStop(0.75f, 0.35f), WidthStop(1f, 0.6f)))
private val BROW_WIDTH = MarkWidth(listOf(WidthStop(0f, 1.8f), WidthStop(0.35f, 1.4f), WidthStop(1f, 0.3f)))
private val WING_WIDTH = MarkWidth(listOf(WidthStop(0f, 1.4f), WidthStop(1f, 0.25f)))
private val SOLID = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f)))
private val FINE = MarkWidth(listOf(WidthStop(0f, 0.4f), WidthStop(0.5f, 0.7f), WidthStop(1f, 0.4f)))

/** The lid's almond from the inner corner over the top and back under, the outer corner a little higher. */
private fun lid(left: Boolean): List<Anchor> = List(LID_POINTS + 1) { i ->
    val angle = 2.0 * PI * (i % LID_POINTS) / LID_POINTS
    val along = -cos(angle).toFloat()
    val up = sin(angle).toFloat()
    val open = up * if (up > 0f) Eye.UPPER else Eye.LOWER
    at(Eye.middle + Eye.halfWidth * along, Eye.LINE + open + Eye.TILT * (along + 1f) / 2f, left)
}

private fun line(points: List<Pair<Float, Float>>, left: Boolean) = points.map { (x, y) -> at(x, y, left) }

private fun eye(name: String, left: Boolean): List<DocumentCommand> {
    fun mark(role: String, points: List<Anchor>, width: MarkWidth, ink: Boolean, locked: Boolean = false, slot: SlotName? = null) =
        Mark(MarkId("eye-$name-$role"), points, width, ink, cameraLocked = locked, slot = slot)
    val byRole = mapOf(
        MarkRole.LID to (mark("lid", lid(left), LID_WIDTH, ink = true) to null),
        MarkRole.WHITE to (mark("white", ring(Eye.middle, Eye.LINE + Eye.TILT / 2f, Eye.halfWidth * 1.1f, Eye.UPPER * 1.2f, left), SOLID, ink = false) to EYE_WHITE),
        MarkRole.IRIS to (mark("iris", ring(Eye.IRIS_X, Eye.LINE, Eye.IRIS, Eye.IRIS, left), SOLID, ink = false) to IRIS),
        MarkRole.PUPIL to (mark("pupil", ring(Eye.IRIS_X, Eye.LINE, Eye.PUPIL, Eye.PUPIL, left), SOLID, ink = false) to PUPIL),
        MarkRole.HIGHLIGHT to (mark("highlight", ring(Eye.IRIS_X + Eye.GLINT_DX, Eye.LINE + Eye.GLINT_DY, Eye.GLINT, Eye.GLINT * 1.15f, left), SOLID, ink = false, locked = true) to EYE_WHITE),
        MarkRole.BROW to (mark("brow", line(BROW_LINE, left), BROW_WIDTH, ink = true, slot = BROW) to null),
        MarkRole.LASHES to (mark("lashes", line(WING_LINE, left), WING_WIDTH, ink = true) to null),
    )
    val empty = eyeGroup(MarkGroupId("eye-$name"), "eye-$name")
    val group = empty.copy(
        roles = empty.roles.map { role ->
            val (mark, fill) = byRole.getValue(role.role)
            GroupRole(role.role, ink = mark.ink, fill = fill, marks = listOf(mark.id))
        },
    )
    return byRole.values.map { (mark, _) -> SetMark(HEAD, mark.id, mark) } + SetMarkGroup(HEAD, group.id, group)
}

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

/** The face: two eyes with lids, wings and brows, the nostrils, the mouth line and lips, eyeshadow and blush, in the natural look. */
fun twinFace(): List<DocumentCommand> = eye("l", left = true) + eye("r", left = false) + mouth() + nostrils() +
    (lipRegions(LIPS) + lidShadows(LID) + blushRegions(BLUSH)).map { SetRegion(HEAD, it.id, it) }

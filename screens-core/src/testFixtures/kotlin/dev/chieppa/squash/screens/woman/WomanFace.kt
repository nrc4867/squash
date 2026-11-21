package dev.chieppa.squash.screens.woman

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

/** The face drawn in steps of about the reach from the middle of the nose to the outer corner of an eye, from the middle of her eyes' height down the middle of her face. */
private const val STEP = 0.19f
private const val EYE_LINE = 0.205f

/** The place ([x], [y]) steps from the middle of the face, on the face map. */
private fun at(x: Float, y: Float, left: Boolean = true): Anchor = FACE.on(x * STEP, EYE_LINE + y * STEP, left)

private fun ring(x: Float, y: Float, rx: Float, ry: Float, left: Boolean): List<Anchor> =
    FACE.ring(x * STEP, EYE_LINE + y * STEP, rx * STEP, ry * STEP, left)

/** One eye on the face plane: its middle, and the lid's half width and how far it opens above it. */
private data class EyeShape(val x: Float, val y: Float, val halfWidth: Float, val over: Float)

private val EYE = EyeShape(x = 0.45f, y = 0.16f, halfWidth = 0.23f, over = 0.16f)

private val NOSE_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.3f), WidthStop(0.5f, 0.9f), WidthStop(1f, 0.3f)))

/** The painted eyes' id on her head. */
const val WOMAN_EYES = "eyes"

/**
 * Her painted eye: a hazel iris with a round pupil and a highlight in an almond opening lifted toward its outer corner, a lid line, a
 * lash flick at the outer corner and a brow arched level over it.
 */
val WOMAN_EYE = EyeLook(
    width = 0.037f,
    height = 0.049f,
    lowerLid = 0.8f,
    frontNarrowing = 0.1f,
    openingTilt = 15f,
    irisSize = 0.02f,
    irisRatio = 1.9f,
    irisForward = 0.002f,
    irisUp = -0.004f,
    pupilSize = 0.47f,
    highlightSize = 0.22f,
    highlightForward = 0.4f,
    highlightUp = -0.2f,
    browTilt = -30f,
    browRaise = 0.022f,
    white = EYE_WHITE,
    iris = HAZEL,
    pupil = INK,
    highlight = EYE_WHITE,
    ink = INK,
)

/** Her painted eyes, both sides, about the middle of her eye on the face map. */
fun womanPaintedEyes(look: EyeLook = WOMAN_EYE, front: EyeLook? = null): List<DocumentCommand> =
    listOf(SetPaintedEye(WOMAN_EYES, PaintedEye(WOMAN_EYES, at(EYE.x, EYE.y), look, front = front)))

/** The lip line, a soft closed smile across the middle under the nose, and the mouth group that holds it, its inside in [MOUTH_INSIDE]. */
private fun mouth(): List<DocumentCommand> {
    val lipLine = Mark(MarkId("mouth-line"), listOf(-0.2f to -0.720f, -0.1f to -0.750f, 0f to -0.755f, 0.1f to -0.750f, 0.2f to -0.720f).map { (x, y) -> at(x, y) })
    val empty = mouthGroup(MarkGroupId("mouth"), "mouth")
    val group = empty.copy(
        roles = empty.roles.map {
            when (it.role) {
                MarkRole.LIP_LINE -> it.copy(marks = listOf(lipLine.id))
                MarkRole.INNER -> it.copy(fill = MOUTH_INSIDE)
                else -> it
            }
        },
    )
    return listOf(SetMark(HEAD, lipLine.id, lipLine), SetMarkGroup(HEAD, group.id, group))
}

private fun region(id: String, slot: SlotName, points: List<Anchor>) = Region(RegionId(id), points.dropLast(1), slot)

private fun drawn(vararg points: Pair<Float, Float>) = points.map { (x, y) -> at(x, y) }

/** The lips in [slot], two regions about the lip line: a bowed upper lip and a fuller lower one. */
internal fun lipRegions(slot: SlotName): List<Region> = listOf(
    region("lip-upper", slot, drawn(-0.21f to -0.720f, 0f to -0.740f, 0.21f to -0.720f, 0.12f to -0.670f, 0.04f to -0.660f, 0f to -0.680f, -0.04f to -0.660f, -0.12f to -0.670f, -0.21f to -0.720f)),
    region("lip-lower", slot, drawn(-0.2f to -0.730f, -0.14f to -0.820f, 0f to -0.850f, 0.14f to -0.820f, 0.2f to -0.730f, 0f to -0.760f, -0.2f to -0.730f)),
)

/** Shadow in [slot] over each upper lid, from the lash line up toward the brow. */
internal fun lidShadows(slot: SlotName): List<Region> = listOf(true, false).map { left ->
    region(if (left) "lid-shadow-l" else "lid-shadow-r", slot, ring(EYE.x, EYE.y + EYE.over * 0.6f, EYE.halfWidth * 1.2f, EYE.over * 1.4f, left))
}

private fun lips(): List<DocumentCommand> = lipRegions(LIPS).map { SetRegion(HEAD, it.id, it) }

/** A short line under the nose and the soft fold down its side, marks of their own outside any group, over the nose the profile draws. */
private fun nose(): List<DocumentCommand> {
    val under = Mark(MarkId("nose-under"), listOf(-0.07f to -0.44f, 0f to -0.46f, 0.07f to -0.44f).map { (x, y) -> at(x, y) }, NOSE_WIDTH)
    val side = Mark(MarkId("nose-side"), listOf(0.11f to -0.22f, 0.13f to -0.33f, 0.11f to -0.43f).map { (x, y) -> at(x, y) }, NOSE_WIDTH)
    return listOf(SetMark(HEAD, under.id, under), SetMark(HEAD, side.id, side))
}

private fun cheeks(): List<DocumentCommand> = listOf(true, false).map { left ->
    region(if (left) "blush-l" else "blush-r", BLUSH, ring(0.5f, -0.3f, 0.15f, 0.08f, left)).let { SetRegion(HEAD, it.id, it) }
}

/** The face drawn on the head: a nose drawn in two lines, lips about the lip line and a blush on each cheek; the eyes are painted. */
fun womanFace(): List<DocumentCommand> = mouth() + lips() + nose() + cheeks()

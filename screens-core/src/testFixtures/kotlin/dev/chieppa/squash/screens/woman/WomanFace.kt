package dev.chieppa.squash.screens.woman

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
import kotlin.math.sin

private val face = FacePlane(HEAD)

/** One eye on the face plane: its middle, the lid's half width and how far it opens above and below, and the iris inside it. */
private data class EyeShape(
    val x: Float,
    val y: Float,
    val halfWidth: Float,
    val over: Float,
    val under: Float,
    val iris: Float,
    val tilt: Float,
)

private val EYE = EyeShape(x = 0.45f, y = 0.16f, halfWidth = 0.23f, over = 0.16f, under = 0.09f, iris = 0.12f, tilt = 0.03f)
private const val LID_POINTS = 16

/** The brow's run over the eye and the lashes flicking from its outer corner, on the face plane. */
private val BROW_LINE = listOf(0.22f to 0.41f, 0.38f to 0.48f, 0.56f to 0.48f, 0.72f to 0.4f)
private val LASH_LINE = listOf(0.64f to 0.22f, 0.72f to 0.27f, 0.78f to 0.33f)

private val LID_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.6f), WidthStop(0.25f, 1.6f), WidthStop(0.5f, 0.6f), WidthStop(0.75f, 0.35f), WidthStop(1f, 0.6f)))
private val BROW_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.9f), WidthStop(0.35f, 1.5f), WidthStop(1f, 0.4f)))
private val LASH_WIDTH = MarkWidth(listOf(WidthStop(0f, 1.4f), WidthStop(1f, 0.3f)))
private val SOLID = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f)))
private val NOSE_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.3f), WidthStop(0.5f, 0.9f), WidthStop(1f, 0.3f)))

/** The lid's almond, the outer corner a little higher than the inner, from the inner corner over the top and back under. */
private fun EyeShape.lid(left: Boolean): List<Anchor> = List(LID_POINTS + 1) { i ->
    val angle = 2.0 * PI * (i % LID_POINTS) / LID_POINTS
    val along = cos(angle).toFloat()
    val up = sin(angle).toFloat()
    val open = up * (if (up > 0f) over else under) * (1f - 0.15f * along)
    face.at(x - halfWidth * along, y + open + tilt * -along, left)
}

private fun EyeShape.white(left: Boolean) = face.ring(x, y + 0.01f, halfWidth * 1.1f, over * 1.2f, left)

private fun EyeShape.iris(left: Boolean) = face.ring(x - 0.01f, y + 0.005f, iris, iris * 1.1f, left)

private fun EyeShape.pupil(left: Boolean) = face.ring(x - 0.01f, y + 0.005f, iris * 0.5f, iris * 0.55f, left)

private fun EyeShape.highlight(left: Boolean) = face.ring(x + 0.025f, y + 0.045f, iris * 0.28f, iris * 0.28f, left)

private fun line(points: List<Pair<Float, Float>>, left: Boolean) = points.map { (x, y) -> face.at(x, y, left) }

/** One eye's seven marks and the group that holds them, the lid clipping the white, iris and pupil. */
private fun eye(name: String, left: Boolean): List<DocumentCommand> {
    fun mark(role: String, points: List<Anchor>, width: MarkWidth, ink: Boolean, locked: Boolean = false) =
        Mark(MarkId("eye-$name-$role"), points, width, ink, cameraLocked = locked)
    val byRole = mapOf(
        MarkRole.LID to (mark("lid", EYE.lid(left), LID_WIDTH, ink = true) to null),
        MarkRole.WHITE to (mark("white", EYE.white(left), SOLID, ink = false) to EYE_WHITE),
        MarkRole.IRIS to (mark("iris", EYE.iris(left), SOLID, ink = false) to HAZEL),
        MarkRole.PUPIL to (mark("pupil", EYE.pupil(left), SOLID, ink = false) to INK),
        MarkRole.HIGHLIGHT to (mark("highlight", EYE.highlight(left), SOLID, ink = false, locked = true) to EYE_WHITE),
        MarkRole.BROW to (mark("brow", line(BROW_LINE, left), BROW_WIDTH, ink = true) to null),
        MarkRole.LASHES to (mark("lashes", line(LASH_LINE, left), LASH_WIDTH, ink = true) to null),
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

/** The lip line, a soft closed smile across the middle under the nose, and the mouth group that holds it. */
private fun mouth(): List<DocumentCommand> {
    val lipLine = Mark(MarkId("mouth-line"), listOf(-0.2f to -0.6f, -0.1f to -0.63f, 0f to -0.635f, 0.1f to -0.63f, 0.2f to -0.6f).map { (x, y) -> face.at(x, y) })
    val empty = mouthGroup(MarkGroupId("mouth"), "mouth")
    val group = empty.copy(roles = empty.roles.map { if (it.role == MarkRole.LIP_LINE) it.copy(marks = listOf(lipLine.id)) else it })
    return listOf(SetMark(HEAD, lipLine.id, lipLine), SetMarkGroup(HEAD, group.id, group))
}

private fun region(id: String, slot: SlotName, points: List<Anchor>) = Region(RegionId(id), points.dropLast(1), slot)

private fun drawn(vararg points: Pair<Float, Float>) = points.map { (x, y) -> face.at(x, y) }

/** The lips in [slot], two regions about the lip line: a bowed upper lip and a fuller lower one. */
internal fun lipRegions(slot: SlotName): List<Region> = listOf(
    region("lip-upper", slot, drawn(-0.21f to -0.6f, 0f to -0.62f, 0.21f to -0.6f, 0.12f to -0.55f, 0.04f to -0.54f, 0f to -0.56f, -0.04f to -0.54f, -0.12f to -0.55f, -0.21f to -0.6f)),
    region("lip-lower", slot, drawn(-0.2f to -0.61f, -0.14f to -0.7f, 0f to -0.73f, 0.14f to -0.7f, 0.2f to -0.61f, 0f to -0.64f, -0.2f to -0.61f)),
)

/** Shadow in [slot] over each upper lid, from the lash line up toward the brow. */
internal fun lidShadows(slot: SlotName): List<Region> = listOf(true, false).map { left ->
    region(if (left) "lid-shadow-l" else "lid-shadow-r", slot, face.ring(EYE.x, EYE.y + EYE.over * 0.6f, EYE.halfWidth * 1.2f, EYE.over * 1.4f, left))
}

private fun lips(): List<DocumentCommand> = lipRegions(LIPS).map { SetRegion(HEAD, it.id, it) }

/** A short line under the nose's tip and the soft fold beside it, marks of their own outside any group. */
private fun nose(): List<DocumentCommand> {
    val under = Mark(MarkId("nose-under"), listOf(-0.07f to -0.25f, 0f to -0.28f, 0.07f to -0.25f).map { (x, y) -> face.at(x, y) }, NOSE_WIDTH)
    val side = Mark(MarkId("nose-side"), listOf(0.1f to -0.17f, 0.12f to -0.22f, 0.09f to -0.26f).map { (x, y) -> face.at(x, y) }, NOSE_WIDTH)
    return listOf(SetMark(HEAD, under.id, under), SetMark(HEAD, side.id, side))
}

private fun cheeks(): List<DocumentCommand> = listOf(true, false).map { left ->
    region(if (left) "blush-l" else "blush-r", BLUSH, face.ring(0.5f, -0.25f, 0.15f, 0.08f, left)).let { SetRegion(HEAD, it.id, it) }
}

/** The face: two hazel eyes with lids, lashes and brows, a nose drawn in two lines, lips about the lip line and a blush on each cheek. */
fun womanFace(): List<DocumentCommand> = eye("l", left = true) + eye("r", left = false) + mouth() + lips() + nose() + cheeks()

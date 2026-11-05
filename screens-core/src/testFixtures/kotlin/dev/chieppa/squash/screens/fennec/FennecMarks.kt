package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.GroupRole
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkVersion
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetFurRule
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.document.eyeGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

val BIN_FRONT = CameraBin("front")
val BIN_THREE_QUARTER = CameraBin("three-quarter")
val BIN_SIDE = CameraBin("side")

private const val RING_POINTS = 12
private const val LID_POINTS = 12

/** A place near an eye, as multiples of its lid's half width in u and in v from its centre. */
private data class Near(val u: Float, val v: Float)

/**
 * One eye as drawn for one camera bin, on the head's skull: where it sits, how wide its lid is in u and v, the iris's place inside
 * it, and where its brow and lashes run. A [crosswise] lid has its corners along v, as an eye seen from ahead does.
 */
private data class EyeDrawing(
    val u: Float,
    val v: Float,
    val halfU: Float,
    val halfV: Float,
    val irisU: Float,
    val irisV: Float,
    val irisRadiusU: Float,
    val irisRadiusV: Float,
    val browLine: List<Near>,
    val lashLine: List<Near>,
    val crosswise: Boolean = false,
)

private val SIDE_BROW = listOf(Near(-1.0f, -1.5f), Near(-0.1f, -2.05f), Near(0.8f, -2.1f))
private val SIDE_LASHES = listOf(Near(-0.95f, -0.1f), Near(-1.25f, -0.35f), Near(-1.45f, -0.75f))
private val FRONT_BROW = listOf(Near(-1.35f, 0.9f), Near(-1.6f, 0.05f), Near(-1.4f, -0.9f))
private val FRONT_LASHES = listOf(Near(-0.75f, 0.45f), Near(-1.05f, 0.75f), Near(-1.25f, 1.1f))

private val EYE_DRAWINGS = mapOf(
    BIN_SIDE to EyeDrawing(0.47f, 0.215f, 0.075f, 0.055f, 0.022f, 0f, 0.046f, 0.048f, SIDE_BROW, SIDE_LASHES),
    BIN_THREE_QUARTER to EyeDrawing(0.60f, 0.185f, 0.075f, 0.052f, 0.018f, -0.006f, 0.05f, 0.044f, SIDE_BROW, SIDE_LASHES),
    BIN_FRONT to EyeDrawing(0.60f, 0.170f, 0.075f, 0.075f, 0.012f, 0f, 0.058f, 0.048f, FRONT_BROW, FRONT_LASHES, crosswise = true),
)

/** The head's v on the eye's side: a left eye reads [v] as drawn, the other its mirror. */
private class EyeSide(val name: String, private val left: Boolean) {
    fun anchor(u: Float, v: Float) = Anchor(HEAD, BODY_PATCH, u, if (left) v else 1f - v)
}

private fun EyeDrawing.ring(side: EyeSide, centerU: Float, centerV: Float, radiusU: Float, radiusV: Float) = List(RING_POINTS) { i ->
    val angle = 2.0 * PI * i / RING_POINTS
    side.anchor(centerU + radiusU * cos(angle).toFloat(), centerV + radiusV * sin(angle).toFloat())
}

/** The lid's almond, from one corner over the top and under the bottom, its first place repeated last as a closed mark's is. */
private fun EyeDrawing.lid(side: EyeSide) = List(LID_POINTS + 1) { i ->
    val angle = 2.0 * PI * i / LID_POINTS
    val along = cos(angle).toFloat()
    val over = sin(angle).toFloat()
    val open = over * (if (over > 0f) 1f else 0.72f) * (1f - 0.18f * along)
    if (crosswise) side.anchor(u + halfU * open, v + halfV * along) else side.anchor(u - halfU * along, v - halfV * open)
}

private fun EyeDrawing.white(side: EyeSide) = ring(side, u, v, halfU * 1.2f, halfV * 1.3f)

private fun EyeDrawing.iris(side: EyeSide) = ring(side, u + irisU, v + irisV, irisRadiusU, irisRadiusV)

private fun EyeDrawing.pupil(side: EyeSide) = ring(side, u + irisU, v + irisV, irisRadiusU * 0.58f, irisRadiusV * 0.58f)

private fun EyeDrawing.highlight(side: EyeSide) = ring(side, u + irisU, v + irisV, irisRadiusU * 0.3f, irisRadiusV * 0.3f)

private fun EyeDrawing.placed(side: EyeSide, line: List<Near>) = line.map { side.anchor(u + halfU * it.u, v + halfV * it.v) }

private fun EyeDrawing.brow(side: EyeSide) = placed(side, browLine)

private fun EyeDrawing.lashes(side: EyeSide) = placed(side, lashLine)

private val LID_WIDTH = MarkWidth(listOf(WidthStop(0f, 0.5f), WidthStop(0.25f, 1.25f), WidthStop(0.5f, 0.5f), WidthStop(0.75f, 0.35f), WidthStop(1f, 0.5f)))
private val SOLID_WIDTH = MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f)))

/** A mark holding the three-quarter drawing as its own shape and each bin's as a version. */
private fun versioned(id: String, width: MarkWidth, ink: Boolean, locked: Boolean = false, shape: EyeDrawing.() -> List<Anchor>): Mark = Mark(
    MarkId(id),
    EYE_DRAWINGS.getValue(BIN_THREE_QUARTER).shape(),
    width,
    ink,
    cameraLocked = locked,
    versions = EYE_DRAWINGS.mapValues { (_, drawing) -> MarkVersion(drawing.shape(), width) },
)

/** One eye's seven marks and the group that holds them, the lid clipping the white, iris and pupil. */
private fun eye(side: EyeSide): List<DocumentCommand> {
    val name = side.name
    val lid = versioned("eye-$name-lid", LID_WIDTH, ink = true) { lid(side) }
    val white = versioned("eye-$name-white", SOLID_WIDTH, ink = false) { white(side) }
    val iris = versioned("eye-$name-iris", SOLID_WIDTH, ink = false) { iris(side) }
    val pupil = versioned("eye-$name-pupil", SOLID_WIDTH, ink = false) { pupil(side) }
    val highlight = versioned("eye-$name-highlight", SOLID_WIDTH, ink = false, locked = true) { highlight(side) }
    val brow = versioned("eye-$name-brow", MarkWidth(), ink = true) { brow(side) }
    val lashes = versioned("eye-$name-lashes", MarkWidth(), ink = true) { lashes(side) }
    val byRole = mapOf(
        MarkRole.LID to (lid to null),
        MarkRole.WHITE to (white to CREAM),
        MarkRole.IRIS to (iris to IRIS),
        MarkRole.PUPIL to (pupil to INK),
        MarkRole.HIGHLIGHT to (highlight to CREAM),
        MarkRole.BROW to (brow to null),
        MarkRole.LASHES to (lashes to null),
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

private fun mouth(id: String, v: (Float) -> Float) = Mark(
    MarkId(id),
    listOf(
        Anchor(HEAD, MUZZLE_PATCH, 0.985f, v(0.47f)),
        Anchor(HEAD, MUZZLE_PATCH, 0.90f, v(0.36f)),
        Anchor(HEAD, MUZZLE_PATCH, 0.78f, v(0.31f)),
        Anchor(HEAD, MUZZLE_PATCH, 0.68f, v(0.28f)),
    ),
    MarkWidth(listOf(WidthStop(0.08f, 1f), WidthStop(0.8f, 0.7f))),
)

/** A patch of stroke roots, staggered row by row, around v from [vFirst] to [vLast]. */
private fun furRows(part: PartId, name: String, rows: Int, columns: Int, uFirst: Float, uLast: Float, vFirst: Float, vLast: Float): List<Mark> =
    (0 until rows).flatMap { row ->
        (0 until columns).map { column ->
            val stagger = if (row % 2 == 0) 0f else 0.5f
            val u = uFirst + (uLast - uFirst) * row / (rows - 1)
            val v = vFirst + (vLast - vFirst) * (column + stagger) / columns
            Mark(MarkId("$name-$row-$column"), listOf(Anchor(part, BODY_PATCH, u, v - floor(v))), kind = MarkKind.FUR)
        }
    }

/**
 * The fur: a ruff on the chest, where the torso's template stands it outward, a tuft at the rump, and the tail's tufts, which are strokes
 * over its smooth outline. Bigger tufts on the tail than on the body.
 */
private fun fennecFur(): List<DocumentCommand> = listOf(
    SetFurRule(TORSO, FurRule(showNear = 0.8f, spacingPixels = 20f, lengthPixels = 30f)),
    AddMarks(TORSO, furRows(TORSO, "chest", rows = 14, columns = 36, uFirst = 0.66f, uLast = 0.97f, vFirst = 0.2f, vLast = 0.8f)),
    AddMarks(TORSO, furRows(TORSO, "rump", rows = 6, columns = 40, uFirst = 0.08f, uLast = 0.2f, vFirst = 0f, vLast = 1f)),
    SetFurRule(TAIL, FurRule(showNear = 0.8f, spacingPixels = 26f, lengthPixels = 40f)),
    AddMarks(TAIL, furRows(TAIL, "tail", rows = 20, columns = 40, uFirst = 0.22f, uLast = 0.8f, vFirst = 0f, vLast = 1f)),
)

/** The face's marks on the head and the fur. */
fun fennecMarks(): List<DocumentCommand> =
    eye(EyeSide("l", left = true)) + eye(EyeSide("r", left = false)) +
        SetMark(HEAD, MarkId("mouth-l"), mouth("mouth-l") { it }) + SetMark(HEAD, MarkId("mouth-r"), mouth("mouth-r") { 1f - it }) +
        fennecFur() + backWeight()

/** The heavier line along the back. */
val BACK_WEIGHT = LineWeightId("back")

/** A heavier line along the back, as the Lines board draws it. */
private fun backWeight(): List<DocumentCommand> {
    val id = BACK_WEIGHT
    val points = List(BACK_POINTS) { i -> Anchor(TORSO, BODY_PATCH, BACK_FROM + (BACK_TO - BACK_FROM) * i / (BACK_POINTS - 1), BACK_V) }
    return listOf(SetLineWeight(TORSO, id, LineWeight(id, points, BACK_RADIUS, BACK_AMOUNT)))
}

private const val BACK_POINTS = 8
private const val BACK_FROM = 0.2f
private const val BACK_TO = 0.8f
private const val BACK_V = 0f
private const val BACK_RADIUS = 0.2f
private const val BACK_AMOUNT = 0.6f

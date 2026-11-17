package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.ExpressionSet
import dev.chieppa.squash.document.FacePose
import dev.chieppa.squash.document.JointAngle
import dev.chieppa.squash.document.SetExpression
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetFurRule
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.document.mouthGroup
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

val BIN_FRONT = CameraBin("front")
val BIN_THREE_QUARTER = CameraBin("three-quarter")
val BIN_SIDE = CameraBin("side")

private const val RING_POINTS = 12

private fun lip(id: String, v: (Float) -> Float) = Mark(
    MarkId(id),
    listOf(
        Anchor(HEAD, MUZZLE_PATCH, 0.985f, v(0.40f)),
        Anchor(HEAD, MUZZLE_PATCH, 0.90f, v(0.34f)),
        Anchor(HEAD, MUZZLE_PATCH, 0.78f, v(0.31f)),
        Anchor(HEAD, MUZZLE_PATCH, 0.68f, v(0.28f)),
    ),
    MarkWidth(listOf(WidthStop(0.08f, 1f), WidthStop(0.8f, 0.7f))),
)

/** The mouth's two lip lines and the group that holds them, with a tongue behind them. */
private fun mouth(): List<DocumentCommand> {
    val left = lip("mouth-l") { it }
    val right = lip("mouth-r") { 1f - it }
    val tongue = Mark(
        MarkId("mouth-tongue"),
        List(RING_POINTS) { i ->
            val angle = 2.0 * PI * i / RING_POINTS
            Anchor(HEAD, MUZZLE_PATCH, 0.9f + 0.05f * cos(angle).toFloat(), 0.5f + 0.05f * sin(angle).toFloat())
        },
        ink = false,
    )
    val empty = mouthGroup(MarkGroupId("mouth"), "mouth")
    val group = empty.copy(
        roles = empty.roles.map { role ->
            when (role.role) {
                MarkRole.LIP_LINE -> role.copy(marks = listOf(left.id, right.id))
                MarkRole.INNER -> role.copy(ink = false, fill = NOSE)
                MarkRole.TONGUE -> role.copy(ink = false, fill = INNER_EAR, marks = listOf(tongue.id))
                else -> role
            }
        },
    )
    return listOf(left, right, tongue).map { SetMark(HEAD, it.id, it) } + SetMarkGroup(HEAD, group.id, group)
}

/** A short crease over the muzzle, a line of its own outside any group. */
private fun crease(id: String, v: (Float) -> Float) = Mark(
    MarkId(id),
    listOf(Anchor(HEAD, MUZZLE_PATCH, 0.62f, v(0.2f)), Anchor(HEAD, MUZZLE_PATCH, 0.68f, v(0.16f)), Anchor(HEAD, MUZZLE_PATCH, 0.74f, v(0.15f))),
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
    SetFurRule(TORSO, FurRule(spacingPixels = 20f, lengthPixels = 30f)),
    AddMarks(TORSO, furRows(TORSO, "chest", rows = 14, columns = 36, uFirst = 0.66f, uLast = 0.97f, vFirst = 0.2f, vLast = 0.8f)),
    AddMarks(TORSO, furRows(TORSO, "rump", rows = 6, columns = 40, uFirst = 0.08f, uLast = 0.2f, vFirst = 0f, vLast = 1f)),
    SetFurRule(TAIL, FurRule(spacingPixels = 26f, lengthPixels = 40f)),
    AddMarks(TAIL, furRows(TAIL, "tail", rows = 20, columns = 40, uFirst = 0.22f, uLast = 0.8f, vFirst = 0f, vLast = 1f)),
)

/** The face's marks on the head and the fur. */
fun fennecMarks(): List<DocumentCommand> =
    mouth() + SetMark(HEAD, MarkId("crease-l"), crease("crease-l") { it }) + SetMark(HEAD, MarkId("crease-r"), crease("crease-r") { 1f - it }) +
        fennecFur() + backWeight() + fennecExpressions()

/** Ears back and ears forward: a pose of the ears' base joints alone. */
val EARS_BACK = ExpressionId("ears-back")
val EARS_FORWARD = ExpressionId("ears-forward")
val HAPPY = ExpressionId("happy")
val SLEEPY = ExpressionId("sleepy")

private const val EARS_BACK_DEGREES = 32f
private const val EARS_FORWARD_DEGREES = -18f
private const val HAPPY_JAW_DEGREES = 6f

private fun earsAt(degrees: Float) = FacePose(listOf(EAR_L, EAR_R).map { JointAngle(it, EAR_BASE_JOINT, degrees) })

/** The fennec's expressions, shown by name in the Expressions strip. */
private fun fennecExpressions(): List<DocumentCommand> {
    return listOf(
        SetExpression(HAPPY, ExpressionSet(HAPPY, "Happy", pose = FacePose(earsAt(EARS_FORWARD_DEGREES).joints + JointAngle(HEAD, "jaw", HAPPY_JAW_DEGREES)))),
        SetExpression(SLEEPY, ExpressionSet(SLEEPY, "Sleepy", pose = earsAt(EARS_BACK_DEGREES / 2f))),
        SetExpression(EARS_BACK, ExpressionSet(EARS_BACK, "Ears back", pose = earsAt(EARS_BACK_DEGREES))),
        SetExpression(EARS_FORWARD, ExpressionSet(EARS_FORWARD, "Ears forward", pose = earsAt(EARS_FORWARD_DEGREES))),
    )
}

private const val EAR_BASE_JOINT = "base"

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

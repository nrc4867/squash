package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.MouthShape
import dev.chieppa.squash.geom.Vec2
import kotlin.math.sign
import kotlin.math.sqrt

/** How far a blend has to go before marks that cannot morph switch. */
private const val SWITCH_AT = 0.5f

/** How far a pupil's middle may sit from the lid's, as a share of the lid's reach each way. */
private const val LID_MARGIN = 0.7f

/** How far a mouth's corner moves around the muzzle at full shape, in v. */
private const val CORNER_LIFT = 0.07f

/** How far a grimace pulls the corners back along the muzzle, in u. */
private const val GRIMACE_PULL = 0.06f

/** How wide an O draws, as a share of the mouth. */
private const val O_WIDTH = 0.55f

/** The v of the middle of a part's underside, where a lower lip parts to. */
private const val UNDERSIDE_V = 0.5f

/** One mark of a role as a view shows it: its places in the patch's u and v, on the patch named [patch]. */
data class ShownShape(val source: ResolvedMark, val patch: String, val places: List<Vec2>, val width: MarkWidth) {
    fun withPlaces(moved: List<Vec2>) = copy(places = moved)
}

private fun ResolvedMark.shapeIn(bin: CameraBin): ShownShape {
    val points = pointsIn(bin)
    return ShownShape(this, points.first().anchor.patch, points.map { Vec2(it.anchor.u, it.anchor.v) }, widthIn(bin))
}

/**
 * [role]'s marks of [group] in [bin], each by its place in the expression: [own] at a [MarkControls.expressionBlend] of 0, [expressed] at 1, morphing between where both hold
 * as many places, and switching halfway where they do not. A role the expression does not name keeps its own.
 */
fun roleShapes(group: MarkGroup, role: MarkRole, own: Map<MarkId, ResolvedMark>, expressed: Map<MarkRole, List<ResolvedMark>>?, bin: CameraBin): List<ShownShape> {
    val base = group.roles.firstOrNull { it.role == role }?.marks.orEmpty().mapNotNull(own::get).map { it.shapeIn(bin) }
    val shown = expressed?.get(role)?.map { it.shapeIn(bin) } ?: return base
    val blend = group.controls.expressionBlend
    val morphs = base.size == shown.size && base.indices.all { base[it].places.size == shown[it].places.size }
    if (!morphs) return if (blend >= SWITCH_AT) shown else base
    return base.indices.map { i ->
        val from = base[i].places
        val to = shown[i].places
        shown[i].withPlaces(from.indices.map { from[it] + (to[it] - from[it]) * blend })
    }
}

/** The middle of these places, a last place on the first counted once. */
fun List<Vec2>.middle(): Vec2 {
    val held = if (size > 1 && first() == last()) dropLast(1) else this
    return Vec2(held.sumOf { it.x.toDouble() }.toFloat() / held.size, held.sumOf { it.y.toDouble() }.toFloat() / held.size)
}

/** This lid opened by [open]: at 0 every place lies on the line between its two furthest apart, its corners, and at 1 it is as drawn. */
fun openedLid(lid: List<Vec2>, open: Float): List<Vec2> {
    if (open >= 1f) return lid
    val first = lid.first()
    val second = lid.maxBy { (it - first).let { d -> d.x * d.x + d.y * d.y } }
    val along = second - first
    val square = along.x * along.x + along.y * along.y
    if (square == 0f) return lid
    return lid.map { place ->
        val off = place - first
        val on = first + along * ((off.x * along.x + off.y * along.y) / square)
        on + (place - on) * open.coerceAtLeast(0f)
    }
}

/** [offset] held so a pupil at [from] plus it stays inside [lid] by a margin: within an ellipse in the lid's reach, about its middle. */
fun heldInLid(from: Vec2, offset: Vec2, lid: List<Vec2>): Vec2 {
    val middle = lid.middle()
    val reachU = (lid.maxOf { it.x } - lid.minOf { it.x }) / 2f * LID_MARGIN
    val reachV = (lid.maxOf { it.y } - lid.minOf { it.y }) / 2f * LID_MARGIN
    if (reachU <= 0f || reachV <= 0f) return middle - from
    val at = from + offset - middle
    val out = sqrt(at.x * at.x / (reachU * reachU) + at.y * at.y / (reachV * reachV))
    return if (out <= 1f) offset else middle + at * (1f / out) - from
}

/** These places moved by [by]. */
fun List<Vec2>.movedBy(by: Vec2): List<Vec2> = map { it + by }

/** These places scaled by [scale] about their middle. */
fun List<Vec2>.scaledBy(scale: Float): List<Vec2> {
    val middle = middle()
    return map { middle + (it - middle) * scale }
}

/** The side of the underside these places lie on, and how far along each is from the end nearest it, 0 to 1. */
private fun List<Vec2>.fromFront(): List<Float> {
    val forward = kotlin.math.abs(first().y - UNDERSIDE_V) <= kotlin.math.abs(last().y - UNDERSIDE_V)
    return indices.map { i -> (if (forward) i else lastIndex - i).toFloat() / lastIndex.coerceAtLeast(1) }
}

/** A lip line as [controls] show it: its [MarkControls.mouthShape] and [MarkControls.width] taken about the end nearest the underside's middle. */
fun shapedLip(places: List<Vec2>, controls: MarkControls): List<Vec2> {
    val along = places.fromFront()
    val front = places[along.indexOf(0f).coerceAtLeast(0)]
    val across = when (controls.mouthShape) {
        MouthShape.O -> controls.width * O_WIDTH
        else -> controls.width
    }
    return places.indices.map { i ->
        val t = along[i]
        val side = sign(UNDERSIDE_V - places[i].y)
        val lift = when (controls.mouthShape) {
            MouthShape.SMILE -> CORNER_LIFT
            MouthShape.FROWN, MouthShape.GRIMACE -> -CORNER_LIFT
            MouthShape.O, null -> 0f
        }
        val pull = if (controls.mouthShape == MouthShape.GRIMACE) GRIMACE_PULL else 0f
        Vec2(front.x + (places[i].x - front.x) * across - pull * t * t, places[i].y - side * lift * t * t)
    }
}

/** The lower lip under the line [upper], opened by [open]: at the end nearest the underside's middle it has parted to that middle, and at the corner it has not parted. */
fun lowerLip(upper: List<Vec2>, open: Float): List<Vec2> {
    val along = upper.fromFront()
    return upper.indices.map { i -> Vec2(upper[i].x, upper[i].y + (UNDERSIDE_V - upper[i].y) * open * (1f - along[i])) }
}

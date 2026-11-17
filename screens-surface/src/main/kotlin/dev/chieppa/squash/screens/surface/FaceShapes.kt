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

/** How far a blend has to go before marks that cannot morph switch. */
private const val SWITCH_AT = 0.5f

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

package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.geom.Vec2

/** How far a blend has to go before marks that cannot morph switch. */
private const val SWITCH_AT = 0.5f

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
 * as many places, and switching halfway where they do not. A role the expression does not name keeps its own. [own] holds each mark by the mark it
 * comes from, so a mirrored mark brings its other side.
 */
fun roleShapes(group: MarkGroup, role: MarkRole, own: Map<MarkId, List<ResolvedMark>>, expressed: Map<MarkRole, List<ResolvedMark>>?, bin: CameraBin): List<ShownShape> {
    val base = group.roles.firstOrNull { it.role == role }?.marks.orEmpty().flatMap { own[it].orEmpty() }.map { it.shapeIn(bin) }
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

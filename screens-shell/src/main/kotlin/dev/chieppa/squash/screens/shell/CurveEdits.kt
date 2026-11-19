package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.AxisSpiral
import dev.chieppa.squash.character.build.MAX_CURVE_PLACES
import dev.chieppa.squash.character.build.MIN_CURVE_PLACES
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.geom.Vec3

/** How far past the last place, as a share of the step to it, a new place goes. */
private const val ADDED_REACH = 0.5f

/** Which of a place's three numbers a field edits. */
enum class PlaceAxis { ALONG, UP, SIDE }

/**
 * A part's curve as the Curve section shows it: its [places] (its own, else its template's resolved, else none), whether they are
 * its [own], and its [spiral], which gives the places in their stead.
 */
data class CurveRows(val places: List<Vec3>, val own: Boolean, val spiral: AxisSpiral?) {
    val canAdd: Boolean get() = spiral == null && places.size < MAX_CURVE_PLACES
    val canRemove: Boolean get() = spiral == null && own && places.size > MIN_CURVE_PLACES

    companion object {
        fun of(template: PartTemplate, params: Map<String, Float>): CurveRows {
            val spiral = AxisCurveParams.spiral(params)
            val own = AxisCurveParams.places(params)
            if (own.isNotEmpty()) return CurveRows(own, true, spiral)
            val values = template.defaults + params
            val shipped = template.axis.curve.map { Vec3(it.along.resolve(values), it.up.resolve(values), it.side.resolve(values)) }
            return CurveRows(shipped, false, spiral)
        }
    }
}

/** [places] with place [index]'s [axis] set to [value]. */
fun List<Vec3>.withPlace(index: Int, axis: PlaceAxis, value: Float): List<Vec3> = mapIndexed { i, p ->
    if (i != index) p else when (axis) {
        PlaceAxis.ALONG -> Vec3(value, p.y, p.z)
        PlaceAxis.UP -> Vec3(p.x, value, p.z)
        PlaceAxis.SIDE -> Vec3(p.x, p.y, value)
    }
}

/** [places] with one more at the end, on past the last as far as half the last step; a straight part's first two run along it. */
fun List<Vec3>.withPlaceAdded(): List<Vec3> = when (size) {
    0 -> listOf(Vec3(0.5f, 0f, 0f), Vec3(1f, 0f, 0f))
    1 -> this + (this[0] * 2f)
    else -> this + (last() + (last() - this[size - 2]) * ADDED_REACH)
}

/** The command that makes [places] the part's own curve, replacing every curve parameter it holds now, spiral included: one undo step. */
fun curveCommand(part: PartId, params: Map<String, Float>, places: List<Vec3>, thisSide: Boolean): SetParameters =
    SetParameters(part, AxisCurveParams.cleared(params) + AxisCurveParams.of(places), thisSide)

/** The command that clears the part's own curve, so it follows its template's again. */
fun curveCleared(part: PartId, params: Map<String, Float>, thisSide: Boolean): SetParameters =
    SetParameters(part, AxisCurveParams.cleared(params), thisSide)

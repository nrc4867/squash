package dev.chieppa.squash.character.build

import dev.chieppa.squash.geom.Vec3

private const val PREFIX = "curve."

/** The fewest places a part's own curve holds, besides its socket point. */
const val MIN_CURVE_PLACES = 2

/** The most places a part's own curve holds. */
const val MAX_CURVE_PLACES = 24

/**
 * The places a part's own curved axis passes through after its socket point, held in its parameters as `curve.<n>.along`, `.up` and
 * `.side` from 1, in the part's own axes as fractions of its length, as a template's curve places are. Any number replace its template's
 * curve, or curve a straight one; the curve through them is scaled to the part's length, so they set its shape and its size its reach.
 * See HLD: data-model, Part template and part instance.
 */
object AxisCurveParams {
    fun along(n: Int) = "$PREFIX$n.along"

    fun up(n: Int) = "$PREFIX$n.up"

    fun side(n: Int) = "$PREFIX$n.side"

    fun isCurve(name: String) = name.startsWith(PREFIX)

    /** The places [params] hold, in order, as along, up and side; empty when they hold fewer than [MIN_CURVE_PLACES]. */
    fun places(params: Map<String, Float>): List<Vec3> {
        if (params.keys.none(::isCurve)) return emptyList()
        val held = generateSequence(1) { it + 1 }.takeWhile { params.containsKey(along(it)) }.take(MAX_CURVE_PLACES)
            .map { Vec3(params.getValue(along(it)), params[up(it)] ?: 0f, params[side(it)] ?: 0f) }.toList()
        return if (held.size >= MIN_CURVE_PLACES) held else emptyList()
    }

    /** The parameters that hold [places], each along, up and side. */
    fun of(places: List<Vec3>): Map<String, Float> = places.withIndex().flatMap { (i, p) ->
        listOf(along(i + 1) to p.x, up(i + 1) to p.y, side(i + 1) to p.z)
    }.toMap()
}

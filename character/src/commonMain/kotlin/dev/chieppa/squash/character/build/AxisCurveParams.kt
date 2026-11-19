package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Membrane
import dev.chieppa.squash.character.block.ToeRow
import dev.chieppa.squash.character.block.Tube
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.spiralPlaces

private const val PREFIX = "curve."
private const val SPIRAL = "${PREFIX}spiral."

/** The fewest places a part's own curve holds, besides its socket point. */
const val MIN_CURVE_PLACES = 2

/** The most places a part's own curve holds. */
const val MAX_CURVE_PLACES = 24

/** The most turns a part's own spiral makes, either way. */
const val MAX_SPIRAL_TURNS = 4f

/** The spiral a part's axis coils in: [turns] about a line toward its side, [pitch] of a coil's diameter sideways a turn, its coil ending [shrink] of its start. */
data class AxisSpiral(val turns: Float, val pitch: Float = 0f, val shrink: Float = 1f) {
    /** The places this spiral passes through after the socket point, in the part's own axes. */
    fun places(): List<Vec3> = spiralPlaces(turns, pitch, shrink)
}

/**
 * The places a part's own curved axis passes through after its socket point, held in its parameters as `curve.<n>.along`, `.up` and
 * `.side` from 1, in the part's own axes as fractions of its length, as a template's curve places are. Any number replace its template's
 * curve, or curve a straight one; the curve through them is scaled to the part's length, so they set its shape and its size its reach.
 * A spiral, `curve.spiral.turns`, `.pitch` and `.shrink`, gives the places in their stead. See HLD: data-model, Part template and part instance.
 */
object AxisCurveParams {
    const val SPIRAL_TURNS = "${SPIRAL}turns"
    const val SPIRAL_PITCH = "${SPIRAL}pitch"
    const val SPIRAL_SHRINK = "${SPIRAL}shrink"

    fun along(n: Int) = "$PREFIX$n.along"

    fun up(n: Int) = "$PREFIX$n.up"

    fun side(n: Int) = "$PREFIX$n.side"

    fun isCurve(name: String) = name.startsWith(PREFIX)

    /** Whether [name] is one of a spiral's parameters. */
    fun isSpiral(name: String) = name.startsWith(SPIRAL)

    /** The places [params] hold, in order, as along, up and side; empty when they hold fewer than [MIN_CURVE_PLACES]. */
    fun places(params: Map<String, Float>): List<Vec3> {
        if (params.keys.none(::isCurve)) return emptyList()
        val held = generateSequence(1) { it + 1 }.takeWhile { params.containsKey(along(it)) }.take(MAX_CURVE_PLACES)
            .map { Vec3(params.getValue(along(it)), params[up(it)] ?: 0f, params[side(it)] ?: 0f) }.toList()
        return if (held.size >= MIN_CURVE_PLACES) held else emptyList()
    }

    /** The spiral [params] hold, or null where they hold none or one of no turns. */
    fun spiral(params: Map<String, Float>): AxisSpiral? {
        val turns = params[SPIRAL_TURNS]?.coerceIn(-MAX_SPIRAL_TURNS, MAX_SPIRAL_TURNS)?.takeIf { it != 0f } ?: return null
        return AxisSpiral(turns, params[SPIRAL_PITCH] ?: 0f, (params[SPIRAL_SHRINK] ?: 1f).coerceIn(0.05f, 2f))
    }

    /** The places a part's own curve passes through: its spiral's, else its own places; empty where it has neither. */
    fun ownPlaces(params: Map<String, Float>): List<Vec3> = spiral(params)?.places() ?: places(params)

    /** The parameters that hold [places], each along, up and side. */
    fun of(places: List<Vec3>): Map<String, Float> = places.withIndex().flatMap { (i, p) ->
        listOf(along(i + 1) to p.x, up(i + 1) to p.y, side(i + 1) to p.z)
    }.toMap()

    /** The parameters that hold [spiral]. */
    fun of(spiral: AxisSpiral): Map<String, Float> =
        mapOf(SPIRAL_TURNS to spiral.turns, SPIRAL_PITCH to spiral.pitch, SPIRAL_SHRINK to spiral.shrink)

    /** Every curve parameter [params] hold set to null, so a command sending them with new ones replaces the whole curve. */
    fun cleared(params: Map<String, Float>): Map<String, Float?> = params.keys.filter(::isCurve).associateWith { null }
}

/**
 * Whether this template's parts sweep along their axis, every run of them a [Tube], so a curve through places gives their path, drawn
 * or not. A template with a skull, a field, digits, a toe row or a membrane does not, and a curve on it is ignored.
 */
val PartTemplate.sweeps: Boolean
    get() = blocks.any { it is Tube } && blocks.all { it.run() == null || it is Tube } &&
        blocks.none { it is Membrane || it is ToeRow }

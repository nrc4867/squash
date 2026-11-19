package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CreaseLine
import dev.chieppa.squash.geom.TriangleLines
import kotlin.math.cos
import kotlin.math.sin

/** The least weight a crease's ink has anywhere along it for it to be drawn. */
private const val LEAST_INK = 0.08f

/** The least weight a wave's ink has somewhere along it, so a faint crest leaves no tick. */
private const val LEAST_WAVE_INK = 0.3f

/** Where a wave's crest is close enough for its ink, as the cosine of its phase there. */
private const val CREST_COSINE = 0.2f

/**
 * The lines where [values] cross zero over the triangles of [lines] that [included] keeps, each with [weights] read along it, a line whose
 * weight nowhere reaches [LEAST_INK] left out.
 */
internal fun inkAlong(lines: TriangleLines, values: FloatArray, weights: FloatArray, included: (Int) -> Boolean): List<CreaseLine> =
    lines.zeroCurvesCarrying(values, weights, included).filter { curve -> curve.carried.any { it >= LEAST_INK } }.map { CreaseLine(it.points, it.carried) }

/**
 * The ink along the crest of a wave of [phase] over the corners of [lines], weighted by [weights], where the crest lies in triangles all of
 * whose corners [shown] keeps: a line along each crest, where the phase is a whole number of turns.
 */
internal fun crestInk(lines: TriangleLines, phase: FloatArray, weights: FloatArray, shown: (Int) -> Boolean): List<CreaseLine> {
    if (weights.none { it >= LEAST_WAVE_INK }) return emptyList()
    val crossing = FloatArray(phase.size) { sin(phase[it]) }
    val crest = BooleanArray(phase.size) { cos(phase[it]) > CREST_COSINE }
    val triangles = lines.triangles
    return inkAlong(lines, crossing, weights) { t ->
        val a = triangles[t * 3]
        val b = triangles[t * 3 + 1]
        val c = triangles[t * 3 + 2]
        crest[a] && crest[b] && crest[c] && shown(a) && shown(b) && shown(c) && maxOf(weights[a], weights[b], weights[c]) >= LEAST_INK
    }.filter { line -> line.weights.any { it >= LEAST_WAVE_INK } }
}

/**
 * The ink of joint creases over the corners of [lines], from [field]: each crease's middle line, and at a deep bend a shorter line either
 * side of it over the crease's middle stretch, where all three corners of a triangle lie on the same crease and [shown] keeps them.
 */
internal fun jointInk(lines: TriangleLines, field: JointCreaseField, shown: (Int) -> Boolean): List<CreaseLine> {
    val triangles = lines.triangles
    val owner = field.owner
    val main = FloatArray(field.ink.size) { inkWeight(field.ink[it], MAIN_FROM, MAIN_RANGE) }
    val sides = FloatArray(field.ink.size) { inkWeight(field.middle[it], SIDE_FROM, SIDE_RANGE) * SIDE_SHARE }
    fun one(t: Int, weights: FloatArray): Boolean {
        val a = triangles[t * 3]
        val b = triangles[t * 3 + 1]
        val c = triangles[t * 3 + 2]
        if (a >= owner.size || b >= owner.size || c >= owner.size) return false
        val o = owner[a]
        return o >= 0 && owner[b] == o && owner[c] == o && shown(a) && shown(b) && shown(c) && maxOf(weights[a], weights[b], weights[c]) >= LEAST_INK
    }
    if (main.none { it >= LEAST_INK }) return emptyList()
    val middle = inkAlong(lines, field.along, main) { one(it, main) }
    if (sides.none { it >= LEAST_INK }) return middle
    val before = inkAlong(lines, FloatArray(field.along.size) { field.along[it] + SIDE_AT }, sides) { one(it, sides) }
    val after = inkAlong(lines, FloatArray(field.along.size) { field.along[it] - SIDE_AT }, sides) { one(it, sides) }
    return middle + before + after
}

/** A crease's ink weight from its strength round the limb: none below [from], rising to full over [range]. */
private fun inkWeight(strength: Float, from: Float, range: Float): Float = ((strength - from) / range).coerceIn(0f, 1f)

/** How a crease's middle line weighs by its strength. */
private const val MAIN_FROM = 0.05f
private const val MAIN_RANGE = 0.6f

/** How the shorter lines either side of a deep crease weigh by its strength, where they lie in its widths, and how heavy they are at most. */
private const val SIDE_FROM = 0.6f
private const val SIDE_RANGE = 0.35f
private const val SIDE_AT = 1.15f
private const val SIDE_SHARE = 0.6f

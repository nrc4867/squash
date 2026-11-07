package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamBrush
import dev.chieppa.squash.document.SeamSide
import dev.chieppa.squash.document.SeamStroke
import dev.chieppa.squash.geom.Vec3

/** How many body units one unit of a seam stroke's radius is on the seam of [child]: that part's axis length, or 1 with none. */
fun seamStrokeScale(character: AssembledCharacter, child: PartId): Float =
    character.part(child)?.surface?.axisLength?.takeIf { it > 0f } ?: 1f

/** Where [anchor] lands on [character], in body space, or null when its part or patch is not there. */
fun AssembledCharacter.positionOf(anchor: Anchor): Vec3? {
    val part = part(anchor.part) ?: return null
    return part.surface.pointAt(anchor)?.let { part.origin + it.position }
}

/**
 * The painted seams of [character]: [strokes] by the part whose seam they paint, replayed in order over each seam's guessed
 * band on every rebuild. A paint stroke moves the share toward its side's bone, an erase hands it back to the guess, and a
 * smooth evens it toward the mean around each of its points. Each acts at its strength in its middle, fading to nothing at its
 * edge. A stroke whose anchors all miss acts nowhere. See HLD: data-model, The rig.
 */
class StrokePaint(character: AssembledCharacter, strokes: Map<PartId, List<SeamStroke>>) : SeamPaint {
    private val laid: Map<PartId, List<LaidStroke>> = strokes.mapValues { (child, list) ->
        val scale = seamStrokeScale(character, child)
        list.mapNotNull { stroke ->
            val points = stroke.points.mapNotNull(character::positionOf)
            if (points.isEmpty()) null else LaidStroke(stroke, points, stroke.radius * scale)
        }
    }.filterValues { it.isNotEmpty() }

    override fun shares(seam: Seam, at: SeamVertices, guessed: FloatArray): FloatArray {
        val strokes = laid[seam.child] ?: return guessed
        var shares = guessed
        for (stroke in strokes) shares = stroke.replay(shares, guessed, at)
        return shares
    }
}

/** A stroke with its anchors landed in body space and its radius in body units. */
private class LaidStroke(val stroke: SeamStroke, val points: List<Vec3>, val radius: Float) {
    private val low = Vec3(points.minOf { it.x } - radius, points.minOf { it.y } - radius, points.minOf { it.z } - radius)
    private val high = Vec3(points.maxOf { it.x } + radius, points.maxOf { it.y } + radius, points.maxOf { it.z } + radius)

    fun replay(shares: FloatArray, guessed: FloatArray, at: SeamVertices): FloatArray {
        val next = shares.copyOf()
        val other = if (at.onChild) SeamSide.PARENT else SeamSide.CHILD
        val means = if (stroke.brush == SeamBrush.SMOOTH) pointMeans(shares, at) else null
        for (vertex in shares.indices) {
            val position = at.position(vertex)
            if (!inBox(position)) continue
            val (distance, nearest) = distanceTo(position)
            if (distance >= radius) continue
            val amount = stroke.strength * (1f - smoothStep(distance / radius))
            val share = shares[vertex]
            next[vertex] = when (stroke.brush) {
                SeamBrush.PAINT -> if (stroke.toward == other) share + (1f - share) * amount else share * (1f - amount)
                SeamBrush.ERASE -> share + (guessed[vertex] - share) * amount
                SeamBrush.SMOOTH -> share + (checkNotNull(means)[nearest] - share) * amount
            }
        }
        return next
    }

    private fun inBox(p: Vec3) = p.x in low.x..high.x && p.y in low.y..high.y && p.z in low.z..high.z

    /** The mean share of the vertices within the radius of each point, each weighed by how near it lies. */
    private fun pointMeans(shares: FloatArray, at: SeamVertices): FloatArray = FloatArray(points.size) { i ->
        var total = 0f
        var weight = 0f
        for (vertex in shares.indices) {
            val distance = (at.position(vertex) - points[i]).length
            if (distance >= radius) continue
            val w = 1f - smoothStep(distance / radius)
            total += shares[vertex] * w
            weight += w
        }
        if (weight > 0f) total / weight else 0f
    }

    /** How far [p] lies from the stroke, and the point of it nearest. Dabs stand alone; otherwise the points join in a line. */
    private fun distanceTo(p: Vec3): Pair<Float, Int> {
        var best = Float.MAX_VALUE
        var nearest = 0
        for (i in points.indices) {
            val d = (p - points[i]).length
            if (d < best) {
                best = d
                nearest = i
            }
        }
        if (stroke.dabs) return best to nearest
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val along = points[i + 1] - a
            val length2 = along dot along
            if (length2 <= 0f) continue
            val t = (((p - a) dot along) / length2).coerceIn(0f, 1f)
            val d = (p - (a + along * t)).length
            if (d < best) {
                best = d
                nearest = if (t < 0.5f) i else i + 1
            }
        }
        return best to nearest
    }
}

/** The place of [vertex] in body space. */
fun SeamVertices.position(vertex: Int) = Vec3(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2])

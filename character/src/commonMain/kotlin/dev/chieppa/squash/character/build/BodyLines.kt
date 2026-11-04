package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.LineGrid
import dev.chieppa.squash.geom.WeightStroke
import dev.chieppa.squash.geom.lineWeightAt

/** A run of points with the weight of the line at each. */
class WeightedRun(val points: List<ResolvedPoint>, val weights: List<Float>)

/** What [part] holds for lines: [grids], one per patch, each knowing its line weights, its [crease] with its parent and its marks' weights. */
class PartLines(val part: PlacedPart, val grids: List<LineGrid>, val crease: List<WeightedRun>, val markWeights: Map<MarkId, List<Float>>)

/**
 * The character as its lines read it, with nothing about a camera in it: what a view takes its contours from and a tool picks on.
 * See HLD: data-model, Surface work on the mesh.
 */
class BodyLines(val parts: Map<PartId, PartLines>)

/** Each placed part's patches in body space, with lines of plain weight. [checkCancelled] runs before each part. */
fun lineGrids(character: AssembledCharacter, checkCancelled: () -> Unit = {}): Map<PartId, List<LineGrid>> =
    character.parts.mapNotNull { placed ->
        val id = placed.id ?: return@mapNotNull null
        checkCancelled()
        id to placed.surface.patches.map { LineGrid.of(it, placed.origin, placed.surface::shown) }
    }.toMap()

/** [character] with [grids] and [creases] weighed by each part's line weights in [work]. A weight reaches only the parts its anchors name. */
fun bodyLines(
    character: AssembledCharacter,
    grids: Map<PartId, List<LineGrid>>,
    creases: List<SocketCrease>,
    work: BodySurfaceWork,
    checkCancelled: () -> Unit = {},
): BodyLines {
    val strokes = HashMap<PartId, MutableList<WeightStroke>>()
    for (held in work.parts.values) {
        for (weight in held.lineWeights) {
            weight.points.groupBy { it.anchor.part }.forEach { (part, points) ->
                val length = character.part(part)?.surface?.axisLength ?: return@forEach
                strokes.getOrPut(part) { ArrayList() } += WeightStroke(points.map { it.position }, weight.radius * length, weight.amount)
            }
        }
    }
    fun weigh(points: List<ResolvedPoint>): List<Float> = points.map { point ->
        strokes[point.anchor.part]?.let { lineWeightAt(point.position, it) } ?: 1f
    }
    val creaseOf = creases.associateBy { it.part }
    val parts = character.parts.mapNotNull { placed ->
        val id = placed.id ?: return@mapNotNull null
        checkCancelled()
        val own = strokes[id]
        val weighed = grids[id].orEmpty().map { grid -> if (own == null) grid else grid.weighted { lineWeightAt(it, own) } }
        val crease = creaseOf[id]?.runs.orEmpty().map { WeightedRun(it, weigh(it)) }
        val marks = work.parts[id]?.marks.orEmpty().associate { it.id to weigh(it.points) }
        id to PartLines(placed, weighed, crease, marks)
    }.toMap()
    return BodyLines(parts)
}

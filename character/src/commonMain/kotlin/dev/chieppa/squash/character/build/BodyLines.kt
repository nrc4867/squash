package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.LineGrid
import dev.chieppa.squash.geom.Vec3
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
        id to partGrids(placed)
    }.toMap()

/** [placed]'s patches in body space, with lines of plain weight. */
fun partGrids(placed: PlacedPart): List<LineGrid> = placed.surface.patches.map { LineGrid.of(it, placed.origin, placed.surface::shown) }

/** [character] with [grids] and [creases] weighed by each part's line weights in [work]. A weight reaches only the parts its anchors name. */
fun bodyLines(
    character: AssembledCharacter,
    grids: Map<PartId, List<LineGrid>>,
    creases: List<SocketCrease>,
    work: BodySurfaceWork,
    checkCancelled: () -> Unit = {},
): BodyLines {
    val weighing = LineWeighing(character, work)
    val creaseOf = creases.associateBy { it.part }
    val parts = character.parts.mapNotNull { placed ->
        val id = placed.id ?: return@mapNotNull null
        checkCancelled()
        id to weighing.partLines(placed, grids[id].orEmpty(), creaseOf[id])
    }.toMap()
    return BodyLines(parts)
}

/**
 * [body], a pose of the character [rest] holds the lines of, as its lines read it: a part the pose moved, or whose parent it moved
 * within reach of it, finds its grids and crease on [body] again; a part whose surface work [workMoved] says moved weighs its marks again; every other part
 * keeps its lines from [rest].
 */
fun posedLines(
    rest: BodyLines,
    body: AssembledCharacter,
    moved: Set<PartId>,
    work: BodySurfaceWork,
    workMoved: (PartId) -> Boolean,
    checkCancelled: () -> Unit = {},
): BodyLines {
    val weighing = LineWeighing(body, work)
    val parents = CreaseParents()
    val parts = body.parts.mapNotNull { placed ->
        val id = placed.id ?: return@mapNotNull null
        val kept = rest.parts[id]?.takeIf { id !in moved && !parentMovedNear(placed, body, rest, moved) }
        checkCancelled()
        id to when {
            kept == null -> weighing.partLines(placed, partGrids(placed), socketCrease(body, placed, parents))
            workMoved(id) -> PartLines(placed, kept.grids, kept.crease, weighing.markWeights(id))
            else -> kept
        }
    }.toMap()
    return BodyLines(parts)
}

/**
 * Whether the pose has moved [placed]'s parent where its crease is found: any of the parent's points within [placed]'s own reach of its
 * box, which holds every point that could be nearest a place on [placed] where it meets the parent.
 */
private fun parentMovedNear(placed: PlacedPart, body: AssembledCharacter, rest: BodyLines, moved: Set<PartId>): Boolean {
    val parent = placed.socket?.parent?.takeIf { it in moved } ?: return false
    val posed = body.part(parent) ?: return true
    val before = rest.parts[parent]?.part ?: return true
    val box = placed.box ?: return true
    val reach = box.radius
    val low = box.low - Vec3(reach, reach, reach)
    val high = box.high + Vec3(reach, reach, reach)
    for ((shown, held) in posed.surface.patches.zip(before.surface.patches)) {
        for (i in 0 until shown.nu * shown.nv) {
            val point = before.origin + before.surface.shown(held.point(i))
            val inside = point.x in low.x..high.x && point.y in low.y..high.y && point.z in low.z..high.z
            if (inside && shown.point(i) != held.point(i)) return true
        }
    }
    return false
}

/** The line weights [work] holds on [character], by the part each reaches. */
private class LineWeighing(character: AssembledCharacter, private val work: BodySurfaceWork) {
    private val strokes = HashMap<PartId, MutableList<WeightStroke>>()

    init {
        for (held in work.parts.values) {
            for (weight in held.lineWeights) {
                weight.points.groupBy { it.anchor.part }.forEach { (part, points) ->
                    val length = character.part(part)?.surface?.axisLength ?: return@forEach
                    strokes.getOrPut(part) { ArrayList() } += WeightStroke(points.map { it.position }, weight.radius * length, weight.amount)
                }
            }
        }
    }

    private fun weigh(points: List<ResolvedPoint>): List<Float> = points.map { point ->
        strokes[point.anchor.part]?.let { lineWeightAt(point.position, it) } ?: 1f
    }

    fun partLines(placed: PlacedPart, grids: List<LineGrid>, crease: SocketCrease?): PartLines {
        val id = requireNotNull(placed.id)
        val own = strokes[id]
        val weighed = grids.map { grid -> if (own == null) grid else grid.weighted { lineWeightAt(it, own) } }
        val runs = crease?.runs.orEmpty().map { WeightedRun(it, weigh(it)) }
        return PartLines(placed, weighed, runs, markWeights(id))
    }

    fun markWeights(part: PartId): Map<MarkId, List<Float>> = work.parts[part]?.marks.orEmpty().associate { it.id to weigh(it.points) }
}

package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.MIN_BORDER_POINTS
import dev.chieppa.squash.document.MIN_MARK_POINTS
import dev.chieppa.squash.document.MIN_WEIGHT_POINTS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/**
 * [parts]' surface work in body space: each point moves by the origin of the part its anchor names in [character], since
 * placing is translation and a surface has already mirrored what it shows. A point on a part not placed drops as a dangling
 * anchor does. [checkCancelled] runs before each part and throws to stop a stale run.
 */
fun placeSurfaceWork(
    parts: Map<PartId, ResolvedSurfaceWork>,
    character: AssembledCharacter,
    checkCancelled: () -> Unit = {},
): BodySurfaceWork {
    val origins = HashMap<PartId, Vec3>()
    character.parts.forEach { placed -> placed.id?.let { origins[it] = placed.origin } }
    val moved = { point: ResolvedPoint -> origins[point.anchor.part]?.let { point.copy(position = point.position + it) } }
    return BodySurfaceWork(parts.mapValues { (_, work) -> checkCancelled(); work.placedBy(moved) })
}

private fun ResolvedSurfaceWork.placedBy(point: (ResolvedPoint) -> ResolvedPoint?): ResolvedSurfaceWork {
    val placedMarks = marks.mapNotNull { it.placedBy(point) }
    val kept = placedMarks.mapTo(hashSetOf()) { it.id }
    return ResolvedSurfaceWork(
        baseSlot = baseSlot,
        areaFills = areaFills,
        marks = placedMarks,
        groups = groups.map { it.namingOnly(kept) },
        regions = regions.mapNotNull { region -> keptPoints(region.border, MIN_BORDER_POINTS, point)?.let { region.copy(border = it) } },
        strokes = strokes.mapNotNull { stroke -> keptPoints(stroke.points, MIN_STROKE_POINTS, point)?.let { stroke.copy(points = it) } },
        expressions = expressions.map { set ->
            set.copy(marks = set.marks.mapValues { (_, roles) -> roles.mapValues { (_, held) -> held.mapNotNull { it.placedBy(point) } } })
        },
        lineWeights = lineWeights.mapNotNull { weight -> keptPoints(weight.points, MIN_WEIGHT_POINTS, point)?.let { weight.copy(points = it) } },
    )
}

private fun ResolvedMark.placedBy(point: (ResolvedPoint) -> ResolvedPoint?): ResolvedMark? {
    val placed = keptPoints(points, kind.minPoints, point) ?: return null
    val placedVersions = versions.mapNotNull { (bin, version) ->
        keptPoints(version.points, MIN_MARK_POINTS, point)?.let { bin to version.copy(points = it) }
    }.toMap()
    return copy(points = placed, versions = placedVersions)
}

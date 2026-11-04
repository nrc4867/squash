package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.MIN_BORDER_POINTS
import dev.chieppa.squash.document.MIN_MARK_POINTS
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.expressionMarks
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt

/** The fewest anchors a brush stroke holds: one is a dab. */
internal const val MIN_STROKE_POINTS = 1

/** What an expression gives the mark groups a part holds, as that part reads them. */
data class ExpressionWork(val id: ExpressionId, val name: String, val marks: Map<MarkGroupId, Map<MarkRole, List<Mark>>>)

/** What a part's resolved surface work reads from the document, and the parts it names that exist, the part itself among them. */
data class SurfaceWorkInput(
    val surface: SurfaceWork = SurfaceWork(),
    val expressions: List<ExpressionWork> = emptyList(),
    val parts: Set<PartId> = emptySet(),
)

/** What [part] holds in [character]; empty when no such part is there. An expression counts for the groups the part holds. */
fun surfaceWorkInput(character: Character, part: PartId): SurfaceWorkInput {
    val instance = character.part(part) ?: return SurfaceWorkInput()
    val surface = character.resolved(instance).surface
    val expressions = character.expressions.mapNotNull { set ->
        val marks = surface.groups.associate { it.id to character.expressionMarks(set, instance, it.id) }.filterValues { it.isNotEmpty() }
        if (marks.isEmpty()) null else ExpressionWork(set.id, set.name, marks)
    }
    val expressionMarks = expressions.flatMap { set -> set.marks.values.flatMap { roles -> roles.values.flatten() } }
    val named = (surface.anchors() + expressionMarks.flatMap { it.anchors() }).mapTo(linkedSetOf()) { it.part }
    return SurfaceWorkInput(surface, expressions, setOf(part) + named.filter { character.part(it) != null })
}

private fun SurfaceWork.anchors(): List<Anchor> =
    marks.flatMap { it.anchors() } + regions.flatMap { it.border } + paint.strokes.flatMap { it.points }

private fun Mark.anchors(): List<Anchor> = points + versions.values.flatMap { it.points }

/**
 * Where [anchor] lands on this surface, or null when it holds no such patch. A u past the patch's rings holds at its end ring.
 */
fun PartSurface.pointAt(anchor: Anchor): ResolvedPoint? {
    val patch = patches.firstOrNull { it.name == anchor.patch } ?: return null
    val normal = patch.surfaceAt(anchor.u, anchor.v).normal
    return ResolvedPoint(anchor, shown(patch.positionAt(anchor.u, anchor.v) + normal * anchor.offset), shown(normal))
}

/** [points] through [transform], without those it drops, or null when fewer than [minimum] are left. */
internal fun <P> keptPoints(points: List<P>, minimum: Int, transform: (P) -> ResolvedPoint?): List<ResolvedPoint>? =
    points.mapNotNull(transform).takeIf { it.size >= minimum }

/** This group, naming only the marks in [kept]. */
internal fun MarkGroup.namingOnly(kept: Set<MarkId>): MarkGroup =
    copy(roles = roles.map { role -> role.copy(marks = role.marks.filter { it in kept }) })

/**
 * Turns [input]'s anchors into points on [surfaces]. An anchor naming a part or patch not there is dropped, and an item left
 * too short to be one is dropped with it: a mark or a mark version under two points, a region under three, a brush stroke
 * with none. [checkCancelled] runs before each item and throws to stop a stale run.
 */
fun resolveSurfaceWork(input: SurfaceWorkInput, surfaces: Map<PartId, PartSurface>, checkCancelled: () -> Unit = {}): ResolvedSurfaceWork {
    val point = { anchor: Anchor -> surfaces[anchor.part]?.pointAt(anchor) }
    val surface = input.surface
    val marks = surface.marks.mapNotNull { checkCancelled(); resolveMark(it, point) }
    val kept = marks.mapTo(hashSetOf()) { it.id }
    return ResolvedSurfaceWork(
        baseSlot = surface.baseSlot,
        areaFills = surface.areaFills,
        marks = marks,
        groups = surface.groups.map { it.namingOnly(kept) },
        regions = surface.regions.mapNotNull { checkCancelled(); resolveRegion(it, point) },
        strokes = surface.paint.strokes.mapNotNull { checkCancelled(); resolveStroke(it, point) },
        expressions = input.expressions.map { set ->
            val groups = set.marks.mapValues { (_, roles) ->
                roles.mapValues { (_, held) -> held.mapNotNull { checkCancelled(); resolveMark(it, point) } }
            }
            ResolvedExpression(set.id, set.name, groups)
        },
    )
}

private fun resolveMark(mark: Mark, point: (Anchor) -> ResolvedPoint?): ResolvedMark? {
    val points = keptPoints(mark.points, MIN_MARK_POINTS, point) ?: return null
    val versions = mark.versions.mapNotNull { (bin, version) ->
        keptPoints(version.points, MIN_MARK_POINTS, point)?.let { bin to ResolvedMarkVersion(it, version.width) }
    }.toMap()
    return ResolvedMark(mark.id, points, mark.width, mark.ink, mark.cameraLocked, versions)
}

private fun resolveRegion(region: Region, point: (Anchor) -> ResolvedPoint?): ResolvedRegion? =
    keptPoints(region.border, MIN_BORDER_POINTS, point)?.let { ResolvedRegion(region.id, it, region.slot, region.ink) }

private fun resolveStroke(stroke: BrushStroke, point: (Anchor) -> ResolvedPoint?): ResolvedBrushStroke? =
    keptPoints(stroke.points, MIN_STROKE_POINTS, point)?.let { ResolvedBrushStroke(stroke.id, it, stroke.slot, stroke.radius) }

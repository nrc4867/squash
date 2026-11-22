package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.MIN_BORDER_POINTS
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.MIN_MARK_POINTS
import dev.chieppa.squash.document.MIN_WEIGHT_POINTS
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SurfaceWork
import dev.chieppa.squash.document.expressionMarks
import dev.chieppa.squash.document.mirrorOf
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.surfaceAt

/** The fewest anchors a brush stroke holds: one is a dab. */
internal const val MIN_STROKE_POINTS = 1

/** What an expression gives the mark groups a part holds, as that part reads them. */
data class ExpressionWork(val id: ExpressionId, val name: String, val marks: Map<MarkGroupId, Map<MarkRole, List<Mark>>>)

/**
 * What a part's resolved surface work reads from the document, and the parts it names that exist, the part itself among them. [mirrors] are
 * the other sides of its mirrored marks, by the mark each mirrors, and [regionMirrors] of its mirrored regions; [mouthStyles] the style each
 * of its mouths talks in and [dentitions] the teeth each draws, where a template library was there to read them.
 */
data class SurfaceWorkInput(
    val surface: SurfaceWork = SurfaceWork(),
    val expressions: List<ExpressionWork> = emptyList(),
    val parts: Set<PartId> = emptySet(),
    val mirrors: Map<MarkId, Mark> = emptyMap(),
    val regionMirrors: Map<RegionId, Region> = emptyMap(),
    val mouthStyles: Map<MarkGroupId, MouthStyle> = emptyMap(),
    val dentitions: Map<MarkGroupId, MouthDentition> = emptyMap(),
    val jaws: Set<MarkGroupId> = emptySet(),
)

/** What [part] holds in [character]; empty when no such part is there. An expression counts for the groups the part holds. */
fun surfaceWorkInput(character: Character, part: PartId): SurfaceWorkInput {
    val instance = character.part(part) ?: return SurfaceWorkInput()
    val worn = character.wornPart(instance).data.surface.shown
    val surface = worn.copy(groups = worn.groups.map { group -> character.shownOpen(group).let { if (it == group.controls.open) group else group.copy(controls = group.controls.copy(open = it)) } })
    val expressions = character.expressions.mapNotNull { set ->
        val marks = surface.groups.associate { it.id to character.expressionMarks(set, instance, it.id) }.filterValues { it.isNotEmpty() }
        if (marks.isEmpty()) null else ExpressionWork(set.id, set.name, marks)
    }
    val expressionMarks = expressions.flatMap { set -> set.marks.values.flatMap { roles -> roles.values.flatten() } }
    val middle = instance.pair == null && instance.side == null
    val mirrors = if (middle) surface.marks.filter { it.mirrored }.associate { it.id to character.mirrorOf(it) } else emptyMap()
    val regionMirrors = if (middle) surface.regions.filter { it.mirrored }.associate { it.id to character.mirrorOf(it) } else emptyMap()
    val named = (surface.anchors() + expressionMarks.flatMap { it.anchors() } + mirrors.values.flatMap { it.anchors() } + regionMirrors.values.flatMap { it.border })
        .mapTo(linkedSetOf()) { it.part }
    return SurfaceWorkInput(surface, expressions, setOf(part) + named.filter { character.part(it) != null }, mirrors, regionMirrors)
}

private fun SurfaceWork.anchors(): List<Anchor> =
    marks.flatMap { it.anchors() } + regions.flatMap { it.border } + paint.strokes.flatMap { it.points } + lineWeights.flatMap { it.points }

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
    val place = FlightPlaces(point) { anchor -> surfaces[anchor.part]?.frameAt(anchor) }
    val marks = surface.marks.flatMap { mark ->
        checkCancelled()
        listOfNotNull(resolveMark(mark, place), input.mirrors[mark.id]?.let { resolveMark(it, place, mark.id) })
    }
    val kept = marks.mapTo(hashSetOf()) { it.id }
    return ResolvedSurfaceWork(
        baseSlot = surface.baseSlot,
        areaFills = surface.areaFills,
        marks = marks,
        groups = surface.groups.map { it.namingOnly(kept) },
        regions = surface.regions.flatMap { region ->
            checkCancelled()
            listOfNotNull(resolveRegion(region, point), input.regionMirrors[region.id]?.let { resolveRegion(it, point) })
        },
        strokes = surface.paint.strokes.mapNotNull { checkCancelled(); resolveStroke(it, point) },
        expressions = input.expressions.map { set ->
            val groups = set.marks.mapValues { (_, roles) ->
                roles.mapValues { (_, held) -> held.mapNotNull { checkCancelled(); resolveMark(it, place) } }
            }
            ResolvedExpression(set.id, set.name, groups)
        },
        lineWeights = surface.lineWeights.mapNotNull { checkCancelled(); resolveWeight(it, point) },
        mouthStyles = input.mouthStyles,
        dentitions = input.dentitions,
        jaws = input.jaws,
    )
}

/** Where anchors land, and the directions at them that a floating mark is held in. */
private class FlightPlaces(val point: (Anchor) -> ResolvedPoint?, val frame: (Anchor) -> SurfaceFrame?)

/** A floating mark's places in the air, from its root on. Having no surface under them, they carry no normal, so they hold the line's interior weight from every side. */
private fun floatingPoints(mark: Mark, place: FlightPlaces): List<ResolvedPoint>? {
    val root = place.point(mark.points.first()) ?: return null
    val frame = place.frame(root.anchor) ?: return null
    return (listOf(root.position) + mark.flight.map { frame.placed(root.position, it) }).map { ResolvedPoint(root.anchor, it, Vec3.ZERO) }
}

private fun resolveMark(mark: Mark, place: FlightPlaces, source: MarkId = mark.id): ResolvedMark? {
    val point = place.point
    val points = (if (mark.kind == MarkKind.FLOATING) floatingPoints(mark, place) else keptPoints(mark.points, mark.kind.minPoints, point)) ?: return null
    val versions = mark.versions.mapNotNull { (bin, version) ->
        keptPoints(version.points, MIN_MARK_POINTS, point)?.let { bin to ResolvedMarkVersion(it, version.width) }
    }.toMap()
    return ResolvedMark(mark.id, points, mark.width, mark.ink, mark.cameraLocked, versions, mark.kind, source, mark.slot)
}

private fun resolveWeight(weight: LineWeight, point: (Anchor) -> ResolvedPoint?): ResolvedLineWeight? =
    keptPoints(weight.points, MIN_WEIGHT_POINTS, point)?.let { ResolvedLineWeight(weight.id, it, weight.radius, weight.amount) }

private fun resolveRegion(region: Region, point: (Anchor) -> ResolvedPoint?): ResolvedRegion? {
    val landed = region.border.map(point)
    val kept = landed.filterNotNull().takeIf { it.size >= MIN_BORDER_POINTS } ?: return null
    val newIndex = landed.runningFold(0) { at, p -> if (p == null) at else at + 1 }
    val corners = region.corners.filter { landed[it] != null }.mapTo(HashSet()) { newIndex[it] }
    return ResolvedRegion(region.id, kept, region.slot, region.ink, corners, region.curved)
}

private fun resolveStroke(stroke: BrushStroke, point: (Anchor) -> ResolvedPoint?): ResolvedBrushStroke? =
    keptPoints(stroke.points, MIN_STROKE_POINTS, point)?.let { ResolvedBrushStroke(stroke.id, it, stroke.slot, stroke.radius, stroke.softness) }

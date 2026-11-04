package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3

/**
 * Where [anchor] lands on the current mesh, its offset included, with the outward [normal] there. In a part's
 * [ResolvedSurfaceWork] these are in the surface space of the part the anchor names, and in [BodySurfaceWork] in body space.
 */
data class ResolvedPoint(val anchor: Anchor, val position: Vec3, val normal: Vec3)

data class ResolvedMarkVersion(val points: List<ResolvedPoint>, val width: MarkWidth)

data class ResolvedMark(
    val id: MarkId,
    val points: List<ResolvedPoint>,
    val width: MarkWidth,
    val ink: Boolean,
    val cameraLocked: Boolean,
    val versions: Map<CameraBin, ResolvedMarkVersion>,
    val kind: MarkKind = MarkKind.LINE,
)

data class ResolvedRegion(val id: RegionId, val border: List<ResolvedPoint>, val slot: SlotName, val ink: Boolean)

data class ResolvedBrushStroke(val id: BrushStrokeId, val points: List<ResolvedPoint>, val slot: SlotName, val radius: Float, val softness: Float = 0f)

/** A line weight's anchors as points; [radius] is a fraction of the axis length of the part it lies on. */
data class ResolvedLineWeight(val id: LineWeightId, val points: List<ResolvedPoint>, val radius: Float, val amount: Float)

/** What an expression gives the mark groups a part holds, by group and role. */
data class ResolvedExpression(val id: ExpressionId, val name: String, val marks: Map<MarkGroupId, Map<MarkRole, List<ResolvedMark>>>)

/**
 * A part's surface work as positions, keeping its ids, names and draw order. [groups] are the document's, naming only the
 * marks that resolved. See HLD: data-model, Surface work on the mesh.
 */
data class ResolvedSurfaceWork(
    val baseSlot: SlotName? = null,
    val areaFills: Map<String, SlotName> = emptyMap(),
    val marks: List<ResolvedMark> = emptyList(),
    val groups: List<MarkGroup> = emptyList(),
    val regions: List<ResolvedRegion> = emptyList(),
    val strokes: List<ResolvedBrushStroke> = emptyList(),
    val expressions: List<ResolvedExpression> = emptyList(),
    val lineWeights: List<ResolvedLineWeight> = emptyList(),
)

/** Every part's surface work in body space, in the document's order of parts. */
data class BodySurfaceWork(val parts: Map<PartId, ResolvedSurfaceWork>)

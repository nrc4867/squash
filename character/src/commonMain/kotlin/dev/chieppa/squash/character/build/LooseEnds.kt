package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.looseEndPartId
import dev.chieppa.squash.geom.Frame
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surfaceAt
import kotlin.math.roundToInt

/** Each bone of a loose end runs this share of its covered part's length. */
const val LOOSE_BONE_SHARE = 0.06f

/** A loose end is this share of its hem's way round wide, and this share of its width thick. */
private const val LOOSE_WIDTH_SHARE = 0.16f
private const val LOOSE_THICKNESS = 0.25f

/** How far a loose end leans out from the body as it hangs, against straight down. */
private const val LOOSE_OUTWARD = 0.35f

private const val RINGS_PER_BONE = 3
private const val LOOSE_SEGMENTS = 12
private const val SECTION_POINTS = 8

/** Where a closed end's pole sits past its last full ring, as a share of a bone's length. */
private const val POLE_STEP = 0.02f

private val DOWN = Vec3(0f, -1f, 0f)

/**
 * Where a garment's loose [end] hangs from: the [branch] part it is cut from and the [vertex] of that branch's mesh at its root, at
 * [root] in body space, hanging along [direction] for [length], [width] wide [across]; its [bones] split that length evenly.
 */
class Hanging(
    val garment: GarmentId,
    val end: LooseEnd,
    val branch: PartId,
    val vertex: Int,
    val root: Vec3,
    val direction: Vec3,
    val across: Vec3,
    val length: Float,
    val width: Float,
) {
    val bones: Int get() = end.bones

    /** The length of each of its bones. */
    val boneLength: Float get() = length / end.bones
}

/**
 * The loose ends of [garment] hung from its built [pieces]: a flat strip from each end's place on its branch's hem (its far ring when it
 * hangs from the hem, else its middle ring), at its v round, hanging down and a little out from the body, a bone's length per bone, its
 * ends closed. See HLD: data-model, Garments.
 */
internal fun looseEnds(garment: Garment, pieces: List<PlacedPart>, body: AssembledCharacter): List<PlacedPart> = garment.looseEnds.mapNotNull { end ->
    val branch = pieces.firstOrNull { it.id == branchPartId(garment.id, end.branch) } ?: return@mapNotNull null
    val covered = body.part(end.branch) ?: return@mapNotNull null
    hangingFrom(garment.id, end, branch, covered.surface.axisLength)?.let { strip(it, covered) }
}

private fun hangingFrom(garment: GarmentId, end: LooseEnd, branch: PlacedPart, partLength: Float): Hanging? {
    val covering = branch.covering ?: return null
    val patch = branch.surface.patches.singleOrNull() ?: return null
    val ring = if (end.fromHem) patch.nu - 1 else patch.nu / 2
    val column = if (patch.closedV) {
        (end.v * patch.nv).roundToInt().mod(patch.nv)
    } else {
        ((end.v - covering.fromV) / (covering.toV - covering.fromV) * (patch.nv - 1)).roundToInt().coerceIn(0, patch.nv - 1)
    }
    val vertex = patch.index(ring, column)
    fun at(c: Int) = branch.origin + branch.surface.shown(patch.point(patch.index(ring, c)))
    val root = at(column)
    val before = at(if (column > 0) column - 1 else if (patch.closedV) patch.nv - 1 else column)
    val after = at(if (column < patch.nv - 1) column + 1 else if (patch.closedV) 0 else column)
    val u = patch.ringUs[ring]
    val normal = branch.surface.shown(patch.surfaceAt(u, column.toFloat() / patch.segments).normal)
    val direction = (DOWN + normal * LOOSE_OUTWARD).normalized()
    val tangent = after - before
    val across = (tangent - direction * (tangent dot direction)).takeIf { it.length > 0f }?.normalized() ?: (normal cross direction).normalized()
    val around = (0 until patch.segments).sumOf { (at((it + 1) % patch.nv) - at(it)).length.toDouble() }.toFloat()
    val width = around * LOOSE_WIDTH_SHARE
    if (width <= 0f) return null
    return Hanging(garment, end, branchPartId(garment, covering.part), vertex, root, direction, across, end.bones * LOOSE_BONE_SHARE * partLength, width)
}

private fun strip(hanging: Hanging, covered: PlacedPart): PlacedPart {
    val width = hanging.width
    val rings = hanging.bones * RINGS_PER_BONE + 1
    val section = SectionProfile.CIRCLE.spline(SECTION_POINTS)
    val up = (hanging.across cross hanging.direction).normalized()
    fun slice(u: Float, center: Vec3, scale: Float): Slice {
        val frame = Frame(center, hanging.direction, up)
        return Slice(u, frame, width * LOOSE_THICKNESS * scale, width * scale, section, frame.rotation)
    }
    val step = hanging.boneLength * POLE_STEP
    val full = List(rings) { k ->
        val t = k.toFloat() / (rings - 1)
        slice(t, hanging.root + hanging.direction * (hanging.length * t), 1f)
    }
    val slices = listOf(slice(0f, hanging.root - hanging.direction * step, 0f)) + full + slice(1f, full.last().frame.center + hanging.direction * step, 0f)
    val id = looseEndPartId(hanging.garment, hanging.end.id)
    val patch = SliceBuilder.loft(id.value, slices, LOOSE_SEGMENTS)
    val mesh = PartMesh(id.value, listOf(MeshBuilder.build(patch) { 0 }))
    return PlacedPart(
        id, covered.template, covered.side, PartSurface.of(mesh, PartBuilder.axes(covered.template), mirrored = false), Vec3.ZERO,
        hanging = hanging,
    )
}

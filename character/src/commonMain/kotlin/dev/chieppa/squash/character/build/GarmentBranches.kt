package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.cuffPartId
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Shell
import dev.chieppa.squash.geom.ShellBuilder
import dev.chieppa.squash.geom.ShellReach
import dev.chieppa.squash.geom.Slice

/** How many rings every branch of a garment has, whatever its length or drawing. */
const val BRANCH_RINGS = 32

/** How many rings every cuff has. */
const val CUFF_RINGS = 6

/**
 * For a garment's branch or cuff, the [garment] it belongs to and the body [part] it was built over: that part's patch [bodyPatch],
 * the ring of it under each of its rings and the column under each of its columns, and the region of the part it covers, [from] to
 * [to] in u and [fromV] to [toV] round it.
 */
class Covering(
    val garment: GarmentId,
    val part: PartId,
    val bodyPatch: Int,
    val bodyRings: IntArray,
    val bodyColumns: IntArray,
    val from: Float = 0f,
    val to: Float = 1f,
    val fromV: Float = 0f,
    val toV: Float = 1f,
) {
    /** Whether it goes the whole way round its part. */
    val allRound: Boolean get() = toV - fromV >= 1f
}

/** [garment]'s [branch] built over [covered], or null when the part has no swept patch to build it on. */
internal fun branchOver(garment: Garment, branch: GarmentBranch, covered: PlacedPart): PlacedPart? {
    val id = covered.id ?: return null
    val bodyPatch = covered.surface.patches.indexOfFirst { it.slices != null }
    if (bodyPatch < 0) return null
    val axes = PartBuilder.axes(covered.template)
    val shell = ShellBuilder.shell(
        branch.part.value, covered.surface.patches[bodyPatch], branch.from, branch.to, BRANCH_RINGS, branch.ease, drawnReach(branch, axes),
        branch.fromV, branch.toV,
    )
    val covering = Covering(garment.id, id, bodyPatch, shell.bodyRings, shell.bodyColumns, branch.from, branch.to, branch.fromV, branch.toV)
    return shellPart(branchPartId(garment.id, id), shell, covered, axes, covering)
}

/**
 * The cuff of [garment] round [through], where it comes out of a branch: a ring of the garment over [through]'s own surface from its
 * root to [reach] in u, grown by [ease]; null when [through] has no swept patch.
 */
internal fun cuffOver(garment: GarmentId, through: PlacedPart, reach: Float, ease: Float): PlacedPart? {
    val id = through.id ?: return null
    val bodyPatch = through.surface.patches.indexOfFirst { it.slices != null }
    if (bodyPatch < 0 || reach <= 0f) return null
    val axes = PartBuilder.axes(through.template)
    val shell = ShellBuilder.shell("cuff", through.surface.patches[bodyPatch], 0f, reach, CUFF_RINGS, ease)
    return shellPart(cuffPartId(garment, id), shell, through, axes, Covering(garment, id, bodyPatch, shell.bodyRings, shell.bodyColumns, 0f, reach))
}

/** The swept patch of [part] a garment is built over, or null when it has none. */
internal fun PlacedPart.bodyPatch(): Patch? = surface.patches.firstOrNull { it.slices != null }

private fun shellPart(id: PartId, shell: Shell, covered: PlacedPart, axes: PartAxes, covering: Covering): PlacedPart {
    val mesh = PartMesh(id.value, listOf(MeshBuilder.build(shell.patch) { 0 }))
    return PlacedPart(
        id, covered.template, covered.side, PartSurface.of(mesh, axes, covered.mirrored), covered.origin,
        lengthFactor = covered.lengthFactor, turn = covered.turn, covering = covering,
    )
}

/**
 * How [branch]'s drawn outlines reach out past its ease at a ring: a Side outline sets the height and the middle of a ring it stands
 * off, a Top outline the width, both read at the ring's place along the part, in the part's own axes as the part's outlines are.
 */
private fun drawnReach(branch: GarmentBranch, axes: PartAxes): (Slice) -> ShellReach {
    val side = branch.outlines[View.SIDE]?.let { OutlineProfile.of(it.toPath().flatten()) }
    val top = branch.outlines[View.TOP]?.let { OutlineProfile.of(it.toPath().flatten()) }
    if (side == null && top == null) return { ShellReach() }
    return { slice ->
        val center = slice.frame.center
        val x = center dot axes.along
        val height = side?.takeIf { x in it.start..it.end }
        val width = top?.takeIf { x in it.start..it.end }
        ShellReach(
            height = height?.extentAt(x),
            width = width?.extentAt(x),
            up = height?.let { it.midAt(x) - (center dot axes.up) } ?: 0f,
            side = width?.let { -it.midAt(x) - (center dot axes.side) } ?: 0f,
        )
    }
}

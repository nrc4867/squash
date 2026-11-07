package dev.chieppa.squash.screens.surface.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SeamSide
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.RigSeam

/** The seam a stroke lands on, by the part whose seam it is, and the side Paint weight paints for there. */
data class SeamTarget(val child: PartId, val toward: SeamSide)

/**
 * The seam a stroke starting on [part] at [position] paints: of the seams [part] is a side of, the one whose socket lies nearest,
 * keeping to the seams [bone] is a side of when it is one of any. Paint weight paints for [bone] when the seam is its, and
 * otherwise for the side [part] is on. Null when [part] is a side of no seam and [bone] of none.
 */
fun seamFor(rig: Rig, part: PartId, position: Vec3, bone: BoneName?): SeamTarget? {
    val index = bone?.let(rig::indexOf)
    val ofBone = if (index == null) emptyList() else rig.seams.filter { it.carrier == index || it.first == index }
    val ofPart = rig.seams.filter { it.child == part || it.parent == part }
    val candidates = ofBone.filter { it in ofPart }.ifEmpty { ofBone }.ifEmpty { ofPart }
    val seam = candidates.minByOrNull { (it.anchor - position).length } ?: return null
    return SeamTarget(seam.child, sideOf(seam, part, index))
}

private fun sideOf(seam: RigSeam, part: PartId, bone: Int?): SeamSide = when (bone) {
    seam.carrier -> SeamSide.PARENT
    seam.first -> SeamSide.CHILD
    else -> if (part == seam.child) SeamSide.CHILD else SeamSide.PARENT
}

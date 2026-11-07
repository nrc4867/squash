package dev.chieppa.squash.screens.surface.rig

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.boneTransforms
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** How near a joint's dot a press or the pointer must come to take it, in pixels. */
const val JOINT_REACH_PIXELS = 8f

/** Where each of [rig]'s joints stands in [pose], in body space, in the order of its bones. */
fun jointPoints(rig: Rig, pose: Pose): List<Vec3> {
    val transforms = boneTransforms(rig, pose)
    return rig.bones.mapIndexed { i, bone -> transforms[i](bone.pivot) }
}

/** The joint whose dot lies under [pixel] through [sight], the nearest to it, or null when none lies within [JOINT_REACH_PIXELS]. */
fun jointAt(rig: Rig, points: List<Vec3>, pixel: Offset, sight: SurfaceView): BoneName? {
    var best: Int? = null
    var bestDistance = JOINT_REACH_PIXELS
    points.forEachIndexed { i, point ->
        val at = sight.toPixel(point) ?: return@forEachIndexed
        val distance = (at - pixel).getDistance()
        if (distance <= bestDistance) {
            best = i
            bestDistance = distance
        }
    }
    return best?.let { rig.bones[it].name }
}

/** How the joints' dots look: their [fill] and [edge], their radius and edge in pixels, and the [lit] joint's larger dot. */
class JointLook(val fill: Color, val edge: Color, val lit: Color, val radius: Float, val edgeWidth: Float, val litRadius: Float)

/** A dot at every joint in [points] through [sight], with [lit]'s larger and in the accent. */
fun jointDots(rig: Rig, points: List<Vec3>, sight: SurfaceView, look: JointLook, lit: BoneName?): ViewportOverlay = buildOverlay {
    points.forEachIndexed { i, point ->
        val at = sight.toPixel(point) ?: return@forEachIndexed
        val radius = if (rig.bones[i].name == lit) look.litRadius else look.radius
        dot(at, radius, look.edge)
        dot(at, radius - look.edgeWidth, if (rig.bones[i].name == lit) look.lit else look.fill)
    }
}

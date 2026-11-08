package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.EyeFrame
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.isEye
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.surfaceAt

/** How far the pupil travels with the eye looking square along the surface, as a share of the lid's reach. */
private const val GAZE_TRAVEL = 1.1f

/** Past this many degrees between an eye's facing and its head's, the eye is set to the side. */
private const val SIDE_SET = 60f

/** An eye set to the side of its head, a bird's: its [group], and the way it faces, [facing], its surface's normal at its middle. */
data class SideEye(val group: MarkGroupId, val facing: Vec3)

/**
 * [group]'s eye on [placed] as its gaze reads it, from its iris, or its pupil where it has no iris, and its lid in [bin]: its middle,
 * the surface's normal and u and v directions there, and how far its pupil travels; null without an iris or a pupil, or a lid.
 */
internal fun eyeFrame(group: MarkGroup, marks: Map<MarkId, ResolvedMark>, placed: PlacedPart, bin: CameraBin): EyeFrame? {
    val iris = (roleShapes(group, MarkRole.IRIS, marks, null, bin) + roleShapes(group, MarkRole.PUPIL, marks, null, bin)).firstOrNull() ?: return null
    val lid = roleShapes(group, MarkRole.LID, marks, null, bin).firstOrNull() ?: return null
    val patch = placed.surface.patches.firstOrNull { it.name == iris.patch } ?: return null
    val middle = iris.places.middle()
    val at = patch.surfaceAt(middle.x, middle.y)
    val surface = placed.surface
    val travel = Vec2(
        (lid.places.maxOf { it.x } - lid.places.minOf { it.x }) / 2f * GAZE_TRAVEL,
        (lid.places.maxOf { it.y } - lid.places.minOf { it.y }) / 2f * GAZE_TRAVEL,
    )
    return EyeFrame(placed.origin + surface.shown(at.position), surface.shown(at.normal), surface.shown(at.alongU).normalized(), surface.shown(at.aroundV).normalized(), travel)
}

/**
 * The eyes of [work] on [head] set to its side, each facing more than [SIDE_SET] degrees from the way the head faces, read from
 * their shapes in [bin]. An eye set to the front, as a fennec's or a person's, is not among them. See HLD: parts, Side-set eyes.
 */
fun sideEyes(head: PlacedPart, work: ResolvedSurfaceWork, bin: CameraBin): List<SideEye> {
    val marks = work.marks.associateBy { it.id }
    return work.groups.filter { it.isEye }.mapNotNull { group ->
        val normal = eyeFrame(group, marks, head, bin)?.normal ?: return@mapNotNull null
        SideEye(group.id, normal).takeIf { degreesBetween(head.facing, normal) > SIDE_SET }
    }
}

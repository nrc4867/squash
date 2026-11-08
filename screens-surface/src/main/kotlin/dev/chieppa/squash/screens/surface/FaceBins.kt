package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.SurfaceView
import kotlin.math.abs

/** The degrees of [FACE_BINS]: the head side on is 0 and straight ahead is 90. */
private const val RIGHT_ANGLE = 90f

/** A head's three angle versions. See HLD: look, Eyes and gaze. */
val FACE_SIDE = CameraBin("side")
val FACE_THREE_QUARTER = CameraBin("three-quarter")
val FACE_FRONT = CameraBin("front")

/** Which version shows at a head angle, the same for every head: side up to 35 degrees, three-quarter up to 70, front past that, each holding 6 degrees past its edge. */
val FACE_BINS = AngleBins(listOf(FACE_SIDE, FACE_THREE_QUARTER, FACE_FRONT), listOf(35f, 70f), 6f)

/** The way [PlacedPart] faces: its template's axis, turned as a pose turns the part. */
val PlacedPart.facing: Vec3 get() = turn * PartBuilder.axes(template).along

/** How far [part], a head, has turned to a viewer in the direction [toViewer]: 0 side on, 90 facing them, from behind as from ahead. */
fun headAngle(part: PlacedPart, toViewer: Vec3): Float = abs(RIGHT_ANGLE - degreesBetween(part.facing, toViewer))

/** How far [eye], set to the side, has turned to a viewer in the direction [toViewer], as a head's angle reads: 0 edge on, 90 square on. */
fun eyeAngle(eye: SideEye, toViewer: Vec3): Float = abs(RIGHT_ANGLE - degreesBetween(eye.facing, toViewer))

/** The angle a face's versions read on [part] toward [toViewer]: a side-set [eye]'s own, else the head's. */
fun faceAngle(part: PlacedPart, toViewer: Vec3, eye: SideEye? = null): Float = eye?.let { eyeAngle(it, toViewer) } ?: headAngle(part, toViewer)

/**
 * The version each view last showed of each face and each side-set eye, so a free view turning past an edge holds it through
 * [FACE_BINS]'s hysteresis. Locked views never turn, so all share one reading.
 */
class FaceBinsHeld {
    private val held = HashMap<Triple<Boolean, Any, MarkGroupId?>, CameraBin>()

    /** The version [part], a head, shows through [view], or with [eye] the version that side-set eye of it shows. */
    fun binOf(part: PlacedPart, view: SurfaceView, eye: SideEye? = null): CameraBin {
        val free = view is FullView
        val key = Triple(free, part.id ?: part, eye?.group)
        val center = part.box?.center ?: part.origin
        val bin = FACE_BINS.binFor(faceAngle(part, view.toViewer(center), eye), if (free) held[key] else null)
        if (free) held[key] = bin
        return bin
    }
}

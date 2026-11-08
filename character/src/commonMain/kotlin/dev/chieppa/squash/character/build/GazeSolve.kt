package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.min

/** How far an eye's direction may turn in from the two eyes' shared one, in radians, which holds a near target from crossing them. */
private const val CONVERGENCE_LIMIT = (20.0 * PI / 180.0).toFloat()

/** The largest a far pupil grows to stay as large to the eye as the near one. */
private const val MOST_COMPENSATION = 1.5f

/** The least share of a face turned from the viewer that size compensation reads, past which it stops growing. */
private const val LEAST_FACING = 0.2f

private const val SMALL = 1e-5f

/** How far a side-set eye's look leans out along its own facing, as a share. */
private const val SIDE_BIAS = 0.4f

/**
 * An eye as its gaze reads it: its [center] in body space, its surface [normal] there, the directions [alongU] and [aroundV] its region's u and v grow in, unit length,
 * the [travel] in u and v that reaches its edge looking square along the surface, and whether it is set to the side of its head, [sideSet], a bird's.
 */
data class EyeFrame(val center: Vec3, val normal: Vec3, val alongU: Vec3, val aroundV: Vec3, val travel: Vec2, val sideSet: Boolean = false)

/** Where a surface eye looks: its pupil's [offset] from where it was drawn, in the patch's u and v, and the [scale] it draws at. */
data class SurfaceGaze(val offset: Vec2, val scale: Float)

/**
 * Where the eyes in [frames] look at [target] from [toCamera], in the order given. Each turns toward [target] from its own center, so a near target converges them, up to
 * [CONVERGENCE_LIMIT] from their shared direction. That direction leans by [GazeCheats.cameraBias] toward the camera, and a side-set eye's by [SIDE_BIAS] out along its own
 * facing, as an eye on the side of the head rests looking sideways, so a target ahead moves its pupil forward without pinning it to the lid. It then becomes an offset in the
 * eye's u and v through its travel. See HLD: look, The pupil system.
 */
fun solveSurfaceGaze(frames: List<EyeFrame>, target: Vec3, toCamera: Vec3, cheats: GazeCheats): List<SurfaceGaze> {
    if (frames.isEmpty()) return emptyList()
    val middle = frames.fold(Vec3.ZERO) { sum, eye -> sum + eye.center } * (1f / frames.size)
    val shared = direction(target - middle)
    val camera = direction(toCamera)
    val facings = frames.map { maxOf(it.normal dot camera, LEAST_FACING) }
    val nearest = facings.max()
    return frames.indices.map { i ->
        val eye = frames[i]
        val own = limitedFrom(shared, direction(target - eye.center))
        val biased = direction(own * (1f - cheats.cameraBias) + camera * cheats.cameraBias)
        val looked = if (eye.sideSet) direction(biased * (1f - SIDE_BIAS) + direction(eye.normal) * SIDE_BIAS) else biased
        val offset = Vec2((looked dot eye.alongU) * eye.travel.x, (looked dot eye.aroundV) * eye.travel.y)
        val scale = if (cheats.sizeCompensation) min(nearest / facings[i], MOST_COMPENSATION) else 1f
        SurfaceGaze(offset, scale)
    }
}

/** [own] held within [CONVERGENCE_LIMIT] of [shared]. */
private fun limitedFrom(shared: Vec3, own: Vec3): Vec3 {
    val angle = acos((shared dot own).coerceIn(-1f, 1f))
    if (angle <= CONVERGENCE_LIMIT) return own
    return direction(shared + (own - shared) * (CONVERGENCE_LIMIT / angle))
}

/**
 * A bulging eye's turn to look at [target]: the direction its sphere's front takes from its [center], its [rest] direction turned toward the target by no more than
 * [limitRadians].
 */
fun solveBulgingGaze(center: Vec3, rest: Vec3, target: Vec3, limitRadians: Float): Vec3 {
    val front = direction(rest)
    val own = direction(target - center)
    val angle = acos((front dot own).coerceIn(-1f, 1f))
    if (angle <= limitRadians) return own
    return direction(front + (own - front) * (limitRadians / angle))
}

private fun direction(of: Vec3): Vec3 = if (of.length < SMALL) Vec3.ZERO else of.normalized()

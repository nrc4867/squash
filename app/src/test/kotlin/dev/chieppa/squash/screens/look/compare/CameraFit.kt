package dev.chieppa.squash.screens.look.compare

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.look.shots.ReferenceFrame
import dev.chieppa.squash.screens.look.shots.ShotCamera
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** The longest side, in pixels, of the silhouettes the coarse search and the refinement compare. */
private const val COARSE_SIDE = 160
private const val FINE_SIDE = 480

/** The coarse search's azimuths, every so many degrees round, and its elevations. */
private const val AZIMUTH_STEP = 15
private val ELEVATIONS = listOf(-10f, 0f, 10f, 25f, 40f)

/** How many of the coarse search's best cameras are refined. */
private const val REFINED = 3

/** How many times a camera's distance and target are set to match the reference's box. */
private const val ALIGN_PASSES = 3

/** The refinement's first steps: degrees round and up, degrees of field, shares of the model's height across and up, share of distance. */
private val FIRST_STEPS = floatArrayOf(6f, 6f, 6f, 0.03f, 0.03f, 0.04f)

/** The refinement stops when every step has halved this many times. */
private const val HALVINGS = 7

/**
 * How much IoU a degree away from the camera the search started from costs, round and in field of view: enough to choose between a
 * view and its mirror from behind, or between near and far lenses, where the silhouettes alone score alike.
 */
private const val AZIMUTH_PRIOR = 0.0001f
private const val FOV_PRIOR = 0.0005f

/** How close in IoU a camera from the other side of the character must come to be reported beside the fit. */
private const val MIRROR_NEAR = 0.02f

/** The field of view the search keeps within, in degrees. */
private const val FOV_LOW = 8f
private const val FOV_HIGH = 70f

/**
 * A camera in the reference frame as the fit searches it: [azimuth] degrees round from the front toward the character's left, [elevation]
 * degrees up, [distance] metres from [target], a vertical field of [fov] degrees, looking at [target].
 */
data class FitPose(val azimuth: Float, val elevation: Float, val distance: Float, val target: Vec3, val fov: Float) {
    val toViewer: Vec3
        get() {
            val a = Math.toRadians(azimuth.toDouble())
            val e = Math.toRadians(elevation.toDouble())
            return Vec3((sin(a) * cos(e)).toFloat(), sin(e).toFloat(), (cos(a) * cos(e)).toFloat())
        }
    val eye: Vec3 get() = target + toViewer * distance

    /** The camera's right and up in the reference frame. */
    val right: Vec3 get() = (Vec3(0f, 1f, 0f) cross toViewer).normalized()
    val up: Vec3 get() = toViewer cross right

    fun shot(width: Int, height: Int) = ShotCamera(eye, target, fov, width, height)

    companion object {
        /** The pose of [camera]. */
        fun of(camera: ShotCamera): FitPose {
            val d = camera.eye - camera.target
            val length = d.length
            val azimuth = Math.toDegrees(atan2(d.x.toDouble(), d.z.toDouble())).toFloat()
            val elevation = Math.toDegrees(asin((d.y / length).toDouble())).toFloat()
            return FitPose(azimuth, elevation, length, camera.target, camera.fovDegrees)
        }
    }
}

/**
 * A fitted camera and the IoU of the silhouettes it was fitted on, and [mirror], the best camera from the far side of the character when
 * it scores within [MIRROR_NEAR] of the fit, as a symmetric character's back view can.
 */
data class CameraFitResult(val camera: ShotCamera, val iou: Float, val tried: Int, val mirror: Pair<ShotCamera, Float>? = null)

/**
 * Searches the camera through which [model], set in the reference [frame], best matches the [reference] silhouette: the view round and up,
 * the distance and target, and the field of view, by silhouette IoU on silhouettes rastered on the CPU. A coarse sweep round the character
 * sets each camera's distance and target to match the reference's box, and the best few are refined a parameter at a time with halving
 * steps. [start], a camera the author gave, is tried beside the sweep and the search leans toward its azimuth; [fov] is the field the sweep
 * starts from, which the search leans toward. [outline] is how far, in the reference's pixels, the render's outline reaches past the
 * surface, which the rastered silhouettes are grown by.
 */
class CameraFit(private val model: ModelTriangles, private val frame: ReferenceFrame, private val reference: Silhouette, private val outline: Float = 0f) {
    private var tried = 0
    private val modelHeight: Float = run {
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (i in 0 until model.pointCount) {
            val y = model.positions[i * 3 + 1]
            low = minOf(low, y)
            high = maxOf(high, y)
        }
        (high - low) / frame.unitsPerMetre
    }
    private val modelMiddle: Vec3 = run {
        val low = FloatArray(3) { Float.MAX_VALUE }
        val high = FloatArray(3) { -Float.MAX_VALUE }
        for (i in 0 until model.pointCount) for (k in 0 until 3) {
            low[k] = minOf(low[k], model.positions[i * 3 + k])
            high[k] = maxOf(high[k], model.positions[i * 3 + k])
        }
        val body = Vec3((low[0] + high[0]) / 2f, (low[1] + high[1]) / 2f, (low[2] + high[2]) / 2f)
        Vec3(-body.z / frame.unitsPerMetre, (body.y - frame.ground) / frame.unitsPerMetre, body.x / frame.unitsPerMetre)
    }

    private inner class Scale(side: Int) {
        val factor = max(1, (max(reference.width, reference.height) + side - 1) / side)
        val target = reference.shrunk(factor)
        val box = target.box()
        private val grow = Math.round(outline / factor)

        fun raster(pose: FitPose): Silhouette =
            rasterSilhouette(model, BodyCamera(frame.toBody(pose.eye), frame.toBody(pose.target), Math.toRadians(pose.fov.toDouble()).toFloat()), target.width, target.height).grown(grow)

        fun score(pose: FitPose): Float {
            tried++
            return intersectionOverUnion(target, raster(pose)) ?: 0f
        }

        /** [pose] with its distance and target moved so its silhouette's box matches the reference's in height and middle. */
        fun aligned(pose: FitPose): FitPose {
            val want = box ?: return pose
            var p = pose
            repeat(ALIGN_PASSES) {
                tried++
                val have = raster(p).box() ?: return p.copy(distance = p.distance * 2f)
                val distance = p.distance * have.height / want.height
                val metresPerPixel = 2f * distance * tan(Math.toRadians(p.fov / 2.0).toFloat()) / target.height
                val dx = (have.centerX - want.centerX) * metresPerPixel
                val dy = (have.centerY - want.centerY) * metresPerPixel
                p = p.copy(distance = distance, target = p.target + p.right * dx - p.up * dy)
            }
            return p
        }
    }

    fun fit(start: ShotCamera?, fov: Float): CameraFitResult {
        val coarse = Scale(COARSE_SIDE)
        val distance = modelHeight / (2f * tan(Math.toRadians(fov / 2.0).toFloat())) * 1.3f
        val from = start?.let(FitPose::of)
        val prior = Prior(from?.azimuth, from?.fov ?: fov)
        val sweep = buildList {
            for (azimuth in 0 until 360 step AZIMUTH_STEP) for (elevation in ELEVATIONS) add(FitPose(azimuth.toFloat(), elevation, distance, modelMiddle, fov))
            from?.let(::add)
        }
        val ranked = sweep.map { coarse.aligned(it) }.map { it to coarse.score(it) - prior.cost(it) }.sortedByDescending { it.second }
        val fine = Scale(FINE_SIDE)
        val refined = ranked.take(REFINED).map { refine(fine, fine.aligned(it.first), prior) }
        val (pose, iou) = refined.maxBy { it.second - prior.cost(it.first) }
        val far = (refined.filter { turn(it.first.azimuth, pose.azimuth) > 90f } +
            listOfNotNull(ranked.firstOrNull { turn(it.first.azimuth, pose.azimuth) > 90f }?.let { refine(fine, fine.aligned(it.first), Prior(null, prior.fov)) }))
            .maxByOrNull { it.second }
        val mirror = far?.takeIf { it.second >= iou - MIRROR_NEAR }?.let { it.first.shot(reference.width, reference.height) to it.second }
        return CameraFitResult(pose.shot(reference.width, reference.height), iou, tried, mirror)
    }

    /** What the search leans toward: an [azimuth] when a camera was given, and a [fov]. */
    private class Prior(val azimuth: Float?, val fov: Float) {
        fun cost(pose: FitPose): Float = (azimuth?.let { turn(pose.azimuth, it) * AZIMUTH_PRIOR } ?: 0f) + abs(pose.fov - fov) * FOV_PRIOR
    }

    /** [pose] improved a parameter at a time, each step tried both ways and halved when neither way helps; its IoU without the [prior]. */
    private fun refine(scale: Scale, pose: FitPose, prior: Prior): Pair<FitPose, Float> {
        var best = pose
        var iou = scale.score(pose)
        var score = iou - prior.cost(pose)
        val steps = FIRST_STEPS.copyOf()
        val halvings = IntArray(steps.size)
        while (halvings.any { it < HALVINGS }) {
            for (k in steps.indices) {
                if (halvings[k] >= HALVINGS) continue
                var moved = false
                for (sign in listOf(1f, -1f)) {
                    val next = moved(best, k, sign * steps[k])
                    val nextIou = scale.score(next)
                    val s = nextIou - prior.cost(next)
                    if (s > score) {
                        best = next
                        iou = nextIou
                        score = s
                        moved = true
                        break
                    }
                }
                if (!moved) {
                    steps[k] /= 2f
                    halvings[k]++
                }
            }
        }
        return best to iou
    }

    /** [pose] with parameter [k] moved by [step]; a change of field keeps the picture's size at the target by moving the eye with it. */
    private fun moved(pose: FitPose, k: Int, step: Float): FitPose = when (k) {
        0 -> pose.copy(azimuth = pose.azimuth + step)
        1 -> pose.copy(elevation = (pose.elevation + step).coerceIn(-80f, 80f))
        2 -> {
            val fov = (pose.fov + step).coerceIn(FOV_LOW, FOV_HIGH)
            val keep = tan(Math.toRadians(pose.fov / 2.0)) / tan(Math.toRadians(fov / 2.0))
            pose.copy(fov = fov, distance = pose.distance * keep.toFloat())
        }
        3 -> pose.copy(target = pose.target + pose.right * (step * modelHeight))
        4 -> pose.copy(target = pose.target + pose.up * (step * modelHeight))
        else -> pose.copy(distance = pose.distance * (1f + step))
    }
}

/** How far apart two azimuths are, in degrees, the short way round. */
private fun turn(a: Float, b: Float): Float {
    val d = ((a - b) % 360f + 360f) % 360f
    return if (d > 180f) 360f - d else d
}

/** This camera as the recipe command takes it: `eye=x,y,z target=x,y,z fov=<degrees>`. */
fun ShotCamera.describe(): String {
    fun v(p: Vec3) = "%.3f,%.3f,%.3f".format(p.x, p.y, p.z)
    return "eye=${v(eye)} target=${v(target)} fov=%.1f".format(fovDegrees)
}


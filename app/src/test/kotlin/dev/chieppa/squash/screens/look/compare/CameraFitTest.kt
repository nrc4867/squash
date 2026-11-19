package dev.chieppa.squash.screens.look.compare

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.look.shots.ReferenceFrame
import dev.chieppa.squash.screens.look.shots.ShotCamera
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CameraFitTest {
    /** Boxes from low to high corners in body space, as triangles. */
    private fun boxes(vararg corners: Pair<Vec3, Vec3>): ModelTriangles {
        val positions = mutableListOf<Float>()
        val indices = mutableListOf<Int>()
        val faces = listOf(intArrayOf(0, 1, 3, 2), intArrayOf(4, 6, 7, 5), intArrayOf(0, 4, 5, 1), intArrayOf(2, 3, 7, 6), intArrayOf(0, 2, 6, 4), intArrayOf(1, 5, 7, 3))
        for ((low, high) in corners) {
            val base = positions.size / 3
            for (i in 0 until 8) {
                positions += if (i and 4 == 0) low.x else high.x
                positions += if (i and 2 == 0) low.y else high.y
                positions += if (i and 1 == 0) low.z else high.z
            }
            for (f in faces) indices += listOf(base + f[0], base + f[1], base + f[2], base + f[0], base + f[2], base + f[3])
        }
        return ModelTriangles(positions.toFloatArray(), indices.toIntArray())
    }

    /** A figure that looks different from every side: a body, a head forward, a tail back and one arm out to the right. */
    private val figure = boxes(
        Vec3(-0.15f, 0.5f, -0.12f) to Vec3(0.15f, 1.2f, 0.12f),
        Vec3(0.0f, 1.2f, -0.1f) to Vec3(0.3f, 1.45f, 0.1f),
        Vec3(-0.6f, 0.9f, -0.03f) to Vec3(-0.15f, 0.98f, 0.03f),
        Vec3(-0.05f, 0.9f, 0.12f) to Vec3(0.05f, 1.0f, 0.5f),
        Vec3(-0.12f, 0f, -0.1f) to Vec3(-0.02f, 0.5f, -0.02f),
        Vec3(0.02f, 0f, 0.02f) to Vec3(0.12f, 0.5f, 0.1f),
    )

    private val frame = ReferenceFrame(ground = 0f)

    private fun silhouette(camera: ShotCamera) = rasterSilhouette(
        figure,
        BodyCamera(frame.toBody(camera.eye), frame.toBody(camera.target), Math.toRadians(camera.fovDegrees.toDouble()).toFloat()),
        camera.width,
        camera.height,
    )

    @Test
    fun `the fit finds a three-quarter camera from a camera well off it`() {
        val truth = ShotCamera(Vec3(1.6f, 1.2f, 2.4f), Vec3(0f, 0.75f, 0f), 30f, 300, 400)
        val reference = silhouette(truth)
        val start = ShotCamera(Vec3(-0.5f, 2.0f, 3.5f), Vec3(0.2f, 0.5f, 0f), 30f, 300, 400)
        val fit = CameraFit(figure, frame, reference).fit(start, 30f)
        val iou = intersectionOverUnion(reference, silhouette(fit.camera)) ?: 0f
        val want = FitPose.of(truth)
        val got = FitPose.of(fit.camera)
        assertTrue(iou > 0.95f, "IoU $iou of ${fit.camera.describe()}")
        assertTrue(abs(got.azimuth - want.azimuth) < 4f && abs(got.elevation - want.elevation) < 4f, "found ${fit.camera.describe()}, want ${truth.describe()}")
    }

    @Test
    fun `a pose turns back into the camera it was made from`() {
        val camera = ShotCamera(Vec3(1f, 1.5f, -2f), Vec3(0.1f, 0.8f, 0.2f), 25f, 10, 10)
        val back = FitPose.of(camera).shot(10, 10)
        assertTrue((back.eye - camera.eye).length < 1e-4f && (back.target - camera.target).length < 1e-4f)
    }
}

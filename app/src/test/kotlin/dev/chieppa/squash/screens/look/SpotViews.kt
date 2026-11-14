package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BIN_FRONT
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.smoke.PROBE_GROUND
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.assertNotBlank
import dev.chieppa.squash.screens.smoke.keepImage
import dev.chieppa.squash.screens.smoke.renderSnapshot
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.GpuOptions
import java.io.File
import kotlin.math.atan
import kotlin.test.assertNotNull

/** How much of a spot tile the spot's ball fills. */
private const val SPOT_FILL = 0.9f

/** How far a spot's camera stands from it at most, outside the whole character. */
private const val SPOT_DISTANCE = 4f

/**
 * In spot radii: how far past where the line from a spot toward its camera leaves the body the look for a part in the way starts, the
 * step it takes, how far short of a part it stops, and how near past leaving the body the camera may stand.
 */
private const val SKIP_RADII = 0.5f
private const val STEP_RADII = 0.1f
private const val SHORT_RADII = 0.3f
private const val NEAREST_RADII = 1.5f

/** One camera on a spot: from [toViewer], a ball of [radius] round [center] filling the tile. */
class Spot(val center: Vec3, val radius: Float, val toViewer: Vec3)

/** Close views of places on a character rather than of parts, through the real graph and scene, one tile each in rows. */
class SpotViews(private val work: TestWork, private val errors: MutableList<String>, private val folder: File) {
    /** [rows] of spots, [tile] pixels a side each, kept as the image [name]. */
    fun keep(session: CharacterSession, rows: List<List<Spot>>, name: String, tile: Int = TILE) {
        val state = state(session)
        val field = session.characterSurface.output.value?.surface?.field?.field
        val columns = rows.maxOf { it.size }
        val views = rows.flatMapIndexed { r, row ->
            row.mapIndexed { c, spot ->
                val rect = PixelRect(c * tile, r * tile, tile, tile)
                characterView(rect, cameraOn(spot, field), state, PROBE_LOOK, ObjectShading(Shading.TWO_TONE), 0f) { BIN_FRONT }
            }
        }
        val image = renderSnapshot(SceneFrame(PROBE_GROUND, views), tile * columns, tile * rows.size, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name, folder)
        assertNotBlank(image.pixels, name)
    }

    private fun state(session: CharacterSession): CharacterState {
        work.settle(session)
        return session.shownState()
    }

    companion object {
        const val TILE = 480

        /** Where a spot close-up looks from, toward the viewer from the spot. */
        val AROUND = listOf(
            Vec3(1f, 0.15f, 0f),
            Vec3(0f, 0.15f, 1f),
            Vec3(-1f, 0.15f, 0f),
            Vec3(0f, 0.15f, -1f),
            Vec3(0.5f, 1f, 0.3f),
            Vec3(0.5f, -1f, -0.3f),
        )

        /**
         * The camera on [spot]: [SPOT_DISTANCE] out, or, where the one surface's [field] shows another part of the character on the way
         * out past the body round the spot, just short of that part, so nothing nearer hides the spot.
         */
        fun cameraOn(spot: Spot, field: JoinedField? = null): CameraView {
            val back = spot.toViewer.normalized()
            var distance = SPOT_DISTANCE
            if (field != null) {
                var t = 0f
                while (t < SPOT_DISTANCE && field.value(spot.center + back * t) < 0f) t += spot.radius * STEP_RADII
                val out = t
                t += spot.radius * SKIP_RADII
                while (t < SPOT_DISTANCE) {
                    if (field.value(spot.center + back * t) < 0f) {
                        distance = maxOf(t - spot.radius * SHORT_RADII, out + spot.radius * NEAREST_RADII)
                        break
                    }
                    t += spot.radius * STEP_RADII
                }
            }
            val fov = 2f * atan(spot.radius / (distance * SPOT_FILL))
            return CameraView(eye = (spot.center + back * distance).toGpu(), target = spot.center.toGpu(), fovY = fov)
        }
    }
}

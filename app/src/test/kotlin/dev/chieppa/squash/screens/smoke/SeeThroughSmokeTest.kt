package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.sheet.placedDisc
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.Mat4
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SIZE = 64
private const val LENS_OPACITY = 0.4f
private const val CHANNEL_TOLERANCE = 3

/** A see-through draw listed before what lies behind it still shows it through, since a view draws its whole draws first. */
@Tag("slow")
class SeeThroughSmokeTest {
    private val behind = Color(0.8f, 0.1f, 0.1f)
    private val lens = Color(0.1f, 0.2f, 0.9f)
    private val background = Color(1f, 1f, 1f)

    @Test
    fun whatLiesBehindALensShowsThroughIt() {
        val back = MeshDraw(RenderMesh.UnitDisc, PartMaterial(behind), placedDisc(Vec3(0f, 0f, 0.6f), Vec3.X * 0.8f, Vec3.Y * 0.8f, Vec3.Z))
        val front = MeshDraw(RenderMesh.UnitDisc, PartMaterial(lens), placedDisc(Vec3(0f, 0f, 0.3f), Vec3.X * 0.5f, Vec3.Y * 0.5f, Vec3.Z), opacity = LENS_OPACITY)
        val view = SceneView(PixelRect(0, 0, SIZE, SIZE), Mat4.Identity, listOf(front, back))
        val errors = mutableListOf<String>()
        val image = renderSnapshot(SceneFrame(background, listOf(view)), SIZE, SIZE, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        assertEquals(emptyList(), errors)
        val centre = image[SIZE / 2, SIZE / 2]
        val expected = lens.over(behind, LENS_OPACITY).toArgb()
        assertTrue((0..2).all { abs((centre shr (it * 8) and 0xFF) - (expected shr (it * 8) and 0xFF)) <= CHANNEL_TOLERANCE }, "the lens over what is behind it, not ${Integer.toHexString(centre)}")
    }
}

package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.graphics.toArgb
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.sheet.ribbonDraw
import dev.chieppa.squash.screens.sheet.translation
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val ORBIT_TILE = 300
private const val ORBIT_TILES = 16
private const val ORBIT_COLUMNS = 4
private const val CLOSE_NEAR = 0.002f

private val LEG_ID = PartId("leg")

/** Color and two-tone drawn on a real leg through commands, the real graph and the real scene, left for a person to read. */
@Tag("slow")
class RenderSeamsSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()

    private fun CharacterSession.paintedLeg(work: TestWork): PlacedPart {
        (seamsPalette() + seamsPaint(LEG_ID)).forEach(::send)
        work.settle(this)
        return assertNotNull(assembly.output.value?.part(LEG_ID))
    }

    @Test
    fun aPaintedLegReadsFlatAndTwoToneFromTwoAngles() {
        val work = TestWork()
        val session = seamsSession(work, listOf(PartInstance(LEG_ID, seamsRef("leg"))))
        val leg = session.paintedLeg(work)
        val box = assertNotNull(leg.box)
        val distance = box.radius * 2.6f
        session.showColorAt(pixelsPerUnit(distance))
        work.settle(session)
        val angles = listOf("seam" to 0f, "side" to 2.1f)
        val modes = listOf("flat" to Shading.FLAT, "two-tone" to Shading.TWO_TONE)
        for ((angleName, turn) in angles) {
            for ((modeName, mode) in modes) {
                val camera = cameraOn(leg, 0.5f, 0f, distance, turn)
                val toCamera = camera.toCameraFrom(box.center)
                val shading = ObjectShading(mode)
                val frame = SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, listOf(seamsDraw(session, leg, shading, toCamera)))))
                val pixels = seamsRender("seams-leg-$modeName-$angleName", frame, errors)
                if (turn == 0f) assertEquals(0, isolated(pixels, SEAMS_SIZE), "no pixel of $modeName shows alone through a layer")
            }
        }
        work.close()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aTaperedRibbonRunsOverTheSurface() {
        val work = TestWork()
        val session = seamsSession(work, listOf(PartInstance(LEG_ID, seamsRef("leg"))))
        val line = List(14) { i -> Anchor(LEG_ID, u = 0.12f + i * 0.058f, v = (0.96f + 0.03f * sin(i * 0.9).toFloat()) % 1f) }
        session.send(SetMark(LEG_ID, MarkId("line"), Mark(MarkId("line"), line)))
        val leg = session.paintedLeg(work)
        val box = assertNotNull(leg.box)
        val distance = box.radius * 2.6f
        val camera = cameraOn(leg, 0.5f, 0f, distance, 0.35f)
        val mark = assertNotNull(session.surfaceWork(LEG_ID).output.value).marks.single()
        val ribbon = assertNotNull(mark.ribbonDraw(LineRule(7f, 14f), SEAMS_INK, translation(leg.origin)))
        val toCamera = camera.toCameraFrom(box.center)
        val rect = seamsWhole
        val scene = SceneView(rect, camera.viewProjection(SEAMS_SIZE, SEAMS_SIZE), listOf(seamsDraw(session, leg, ObjectShading(), toCamera)), listOf(ribbon))
        val pixels = seamsRender("seams-ribbon", SceneFrame(SEAMS_WELL, listOf(scene)), errors)
        assertTrue(pixels.count { it == SEAMS_INK.toArgb() } > 500, "the ribbon shows")
        work.close()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun aCloseZoomShowsACrispCurvedEdge() {
        val work = TestWork()
        val session = seamsSession(work, listOf(PartInstance(LEG_ID, seamsRef("leg"))))
        val leg = session.paintedLeg(work)
        val distance = 0.09f
        val camera = cameraOn(leg, 0.195f, 0.25f, distance, near = CLOSE_NEAR, lift = 0f)
        val before = assertNotNull(session.bodyColor.output.value).parts.getValue(LEG_ID).layers.sumOf { it.cut.mesh.vertexCount }
        session.showColorAt(pixelsPerUnit(distance))
        work.settle(session)
        val after = assertNotNull(session.bodyColor.output.value).parts.getValue(LEG_ID).layers.sumOf { it.cut.mesh.vertexCount }
        assertTrue(after > before, "zooming in flattens finer: $before then $after")
        val frame = SceneFrame(SEAMS_WELL, listOf(seamsView(seamsWhole, camera, listOf(seamsDraw(session, leg, ObjectShading(), Vec3.Z)))))
        val pixels = seamsRender("seams-close-edge", frame, errors)
        assertEquals(0, isolated(pixels, SEAMS_SIZE))
        work.close()
        assertEquals(emptyList(), errors)
    }

    @Test
    fun noLayerFlickersAtAnyOrbit() {
        val work = TestWork()
        val session = seamsSession(work, listOf(PartInstance(LEG_ID, seamsRef("leg"))))
        val leg = session.paintedLeg(work)
        val box = assertNotNull(leg.box)
        val distance = box.radius * 2.6f
        session.showColorAt(pixelsPerUnit(distance))
        work.settle(session)
        val views = List(ORBIT_TILES) { i ->
            val camera = cameraOn(leg, 0.5f, 0f, distance, turn = i * 2f * PI.toFloat() / ORBIT_TILES, lift = 0.15f + 0.1f * (i % 3))
            val toCamera = camera.toCameraFrom(box.center)
            val rect = PixelRect((i % ORBIT_COLUMNS) * ORBIT_TILE, (i / ORBIT_COLUMNS) * ORBIT_TILE, ORBIT_TILE, ORBIT_TILE)
            seamsView(rect, camera, listOf(seamsDraw(session, leg, ObjectShading(), toCamera)))
        }
        val width = ORBIT_COLUMNS * ORBIT_TILE
        val pixels = seamsRender("seams-orbit", SceneFrame(SEAMS_WELL, views), errors, width, width)
        assertEquals(0, isolated(pixels, width), "no pixel shows alone through a layer at any orbit")
        work.close()
        assertEquals(emptyList(), errors)
    }

    /** How many pixels hold a color none of their eight neighbours hold, in an area of those neighbours all of one flat color: a speck. */
    private fun isolated(pixels: IntArray, width: Int): Int {
        val height = pixels.size / width
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val here = pixels[y * width + x]
                val first = pixels[(y - 1) * width + x - 1]
                if (here == first) continue
                var same = true
                for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && pixels[(y + dy) * width + x + dx] != first) same = false
                if (same) count++
            }
        }
        return count
    }
}

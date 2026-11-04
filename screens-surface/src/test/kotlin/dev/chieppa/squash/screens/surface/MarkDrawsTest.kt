package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.ResolvedSurfaceWork
import dev.chieppa.squash.character.build.resolveSurfaceWork
import dev.chieppa.squash.character.build.surfaceOf
import dev.chieppa.squash.character.build.surfaceWorkInput
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.fennec.BIN_FRONT
import dev.chieppa.squash.screens.fennec.BIN_SIDE
import dev.chieppa.squash.screens.fennec.BIN_THREE_QUARTER
import dev.chieppa.squash.document.FUR_MARK_PREFIX
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val RULE = LineRule(2f)
private val INK = Color.Black
private val LIGHT = Vec3(0.4f, 0.8f, -0.4f)
private val SIDE_VIEW = Vec3(0f, 0f, -1f)
private val AHEAD_VIEW = Vec3(1f, 0f, -0.1f)
private const val TOLERANCE = 0.001f
private const val PIXELS = 600

/** The white is drawn a fifth wider than the lid, so a clipped one stays well under this. */
private const val LID_SLACK = 1.05f

class MarkDrawsTest {
    private val document = fennecDocument()
    private val folder = TemplateFolder.shipped()

    private fun surface(part: PartId): PartSurface {
        val instance = assertNotNull(document.character.part(part))
        val data = assertNotNull(document.character.resolved(part))
        return surfaceOf(assertNotNull(folder.template(instance.template)), data, mirrored = false)
    }

    private fun work(part: PartId, surface: PartSurface): ResolvedSurfaceWork =
        resolveSurfaceWork(surfaceWorkInput(document.character, part), mapOf(part to surface))

    private fun eye(bin: CameraBin, toCamera: Vec3 = SIDE_VIEW): EyeDraws {
        val surface = surface(HEAD)
        val work = work(HEAD, surface)
        val group = work.groups.first { it.id == MarkGroupId("eye-l") }
        return eyeDraws(group, work.marks.associateBy { it.id }, surface, bin, document.character.palette, LIGHT, toCamera, TOLERANCE, RULE, INK)
    }

    @Test
    fun anEyeFillsFourRolesAndInksThree() {
        val eye = eye(BIN_SIDE)
        assertEquals(4, eye.layers.size, "white, iris, pupil and highlight")
        assertEquals(3, eye.ribbons.size, "lid, lashes and brow")
    }

    @Test
    fun eachBinDrawsItsOwnShape() {
        val shapes = listOf(BIN_SIDE, BIN_THREE_QUARTER, BIN_FRONT).map { eye(it).layers.first().mesh.vertices.toList() }
        assertEquals(3, shapes.toSet().size)
    }

    @Test
    fun onlyTheHighlightMovesWithTheCamera() {
        val side = eye(BIN_SIDE, SIDE_VIEW)
        val ahead = eye(BIN_SIDE, AHEAD_VIEW)
        (0..2).forEach { assertContentEquals(side.layers[it].mesh.vertices, ahead.layers[it].mesh.vertices, "role $it stays where it was drawn") }
        assertFalse(side.layers[3].mesh.vertices.contentEquals(ahead.layers[3].mesh.vertices), "the highlight moves")
    }

    @Test
    fun theLidClipsTheWhite() {
        val surface = surface(HEAD)
        val lid = work(HEAD, surface).marks.first { it.id.value == "eye-l-lid" }.pointsIn(BIN_SIDE).dropLast(1).map { it.position }
        val center = lid.reduce { a, b -> a + b } * (1f / lid.size)
        val reach = lid.maxOf { (it - center).length }
        val white = eye(BIN_SIDE).layers[0].mesh
        assertTrue(white.vertexCount > 0)
        repeat(white.vertexCount) { assertTrue((white.position(it) - center).length <= reach * LID_SLACK, "vertex $it of the white lies outside the lid") }
    }

    @Test
    fun furShowsOnlyNearTheSilhouetteAndSpacedOnScreen() {
        val surface = surface(TORSO)
        val fur = work(TORSO, surface).marks.filter { it.id.value.startsWith(FUR_MARK_PREFIX) }
        assertTrue(fur.size > 100)
        val eye = Vec3(0.5f, 0f, -4f)
        val camera = CameraView(eye = eye.toGpu(), target = Vec3(0.5f, 0f, 0f).toGpu())
        val rect = PixelRect(0, 0, PIXELS, PIXELS)
        val tight = furRibbons(fur, FurRule(showNear = 0.5f, spacingPixels = 1f, line = RULE), camera, rect, INK)
        val wide = furRibbons(fur, FurRule(showNear = 1.1f, spacingPixels = 1f, line = RULE), camera, rect, INK)
        val spaced = furRibbons(fur, FurRule(showNear = 1.1f, spacingPixels = 40f, line = RULE), camera, rect, INK)
        assertTrue(tight.isNotEmpty(), "strokes near the silhouette show")
        assertTrue(tight.size < wide.size, "near the silhouette is fewer than every stroke facing the camera: ${tight.size} of ${wide.size}")
        assertTrue(spaced.size < wide.size, "wider spacing shows fewer: ${spaced.size} of ${wide.size}")
    }
}

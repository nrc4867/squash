package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.RenderMesh
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.stretch.frame.OverlayLine
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.Vec3 as GpuVec3
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SheetFrameTest {
    private val templates = TemplateFolder.load(Path.of(System.getProperty("squash.templates"))).parts
    private val look = SheetLook(
        Color.White, Color.Black, Color.Gray, Color.DarkGray, Color.Yellow, Color.Red, Color.LightGray,
        PartMaterial(Color.Green), 1f, 2f, 1f, 2f, 2f, 2f, 1f, 2f, 2f, 3f, 3f,
    )
    private val rects = sheetPaneRects(1000, 800, 2)
    private val outline = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0f, 1f), CurvePoint(1f, 1f), CurvePoint(1f, 0f)))

    private fun template(id: String) = templates.first { it.id.value == id }

    private fun part(template: PartTemplate) = assertNotNull(sheetPart(PartId("p"), template, PartBuilder.mesh("p", template, PartBuilder.build(template))))

    private fun input(part: SheetPart, outlines: Map<View, OutlineCurve> = emptyMap(), active: View? = View.SIDE): SheetInput {
        val fit = fitSheet(part.axes, part.extents, rects)
        val cameras = SheetPane.entries.mapNotNull { pane ->
            pane.view?.let { it to LockedCamera(it, part.axes, fit, rects[pane.ordinal].width, rects[pane.ordinal].height) }
        }.toMap()
        val full = CameraView(eye = part.center.toGpu() + GpuVec3(3f, 2f, -4f), target = part.center.toGpu())
        return SheetInput(rects, 1000, 800, look, SheetSelection(part, outlines), cameras, full, active)
    }

    private fun meshesIn(frame: SceneFrame, pane: SheetPane, part: SheetPart) =
        frame.views.single { it.viewport == rects[pane.ordinal] }.draws.count { it.mesh === part.mesh }

    @Test
    fun axesAgreeWithWhatEveryShippedTemplateBuilds() {
        for (template in templates) {
            val axes = PartAxes.of(template.axis.rest.toVec3())
            val frame = PartBuilder.build(template).firstNotNullOf { it.slices?.first()?.frame }
            assertTrue((axes.along - frame.tangent).length < 1e-3f, "${template.id}: along ${axes.along}, tangent ${frame.tangent}")
            assertTrue((axes.up - frame.up).length < 1e-3f, "${template.id}: up ${axes.up}, ${frame.up}")
            assertTrue((axes.side - frame.side).length < 1e-3f, "${template.id}: side ${axes.side}, ${frame.side}")
        }
    }

    @Test
    fun everyShippedTemplateMakesAPartWithAnOutlineInEachView() {
        for (template in templates) {
            val part = part(template)
            assertEquals(View.entries.toSet(), part.silhouettes.keys)
            assertTrue(part.silhouettes.values.all { it.isNotEmpty() }, "${template.id}")
        }
    }

    @Test
    fun theFrontSilhouetteOfALegIsItsSectionAndItsLengthShowsInSide() {
        val part = part(template("leg"))
        val side = part.silhouettes.getValue(View.SIDE).flatten()
        assertTrue(abs(side.maxOf { it dot part.axes.along } - part.extents.along.max) < 1e-3f)
        val front = part.silhouettes.getValue(View.FRONT).flatten()
        assertTrue(abs(front.maxOf { it dot part.axes.up } - part.extents.up.max) < 1e-3f)
    }

    @Test
    fun withNothingSelectedThePanesStayEmpty() {
        val frame = sheetFrame(SheetInput(rects, 1000, 800, look))
        assertTrue(frame.views.isEmpty())
        assertEquals(2, frame.overlay.rects.size, "only the dividers")
        assertTrue(frame.overlay.lines.isEmpty() && frame.overlay.dots.isEmpty())
        assertEquals(Color.White, frame.background)
    }

    @Test
    fun aPartWithNoOutlinesShowsFilledInFullAndDashedInTheLockedPanes() {
        val part = part(template("paw"))
        val frame = sheetFrame(input(part))
        assertEquals(1, meshesIn(frame, SheetPane.FULL, part))
        for (pane in listOf(SheetPane.TOP, SheetPane.SIDE, SheetPane.FRONT)) assertEquals(0, meshesIn(frame, pane, part), "$pane")
        val inTop = rects[SheetPane.TOP.ordinal].toRect()
        val insideTop = frame.overlay.lines.filter { inTop.contains(it.from) && inTop.contains(it.to) }
        assertTrue(insideTop.any { it.color == look.template }, "the mesh's outline dashed")
        assertTrue(insideTop.any { it.color == look.guide }, "Side's guides")
    }

    @Test
    fun aDrawnViewShowsTheMeshFilled() {
        val part = part(template("paw"))
        val frame = sheetFrame(input(part, mapOf(View.SIDE to outline)))
        assertEquals(1, meshesIn(frame, SheetPane.SIDE, part))
        assertEquals(0, meshesIn(frame, SheetPane.TOP, part))
        val inSide = rects[SheetPane.SIDE.ordinal].toRect()
        assertTrue(frame.overlay.lines.none { it.color == look.template && inSide.contains(it.from) }, "no dashes over it")
    }

    @Test
    fun theGroundLiesOnlyWhereThePaneLooksAlongIt() {
        val paw = part(template("paw"))
        val frame = sheetFrame(input(paw))
        fun discs(pane: SheetPane) = frame.views.single { it.viewport == rects[pane.ordinal] }.draws.count { it.mesh === RenderMesh.UnitDisc }
        assertEquals(1, discs(SheetPane.SIDE))
        assertEquals(1, discs(SheetPane.FRONT))
        assertEquals(0, discs(SheetPane.TOP))
        assertEquals(1, discs(SheetPane.FULL))

        val leg = part(template("leg"))
        val standing = sheetFrame(input(leg))
        assertEquals(0, standing.views.single { it.viewport == rects[SheetPane.SIDE.ordinal] }.draws.size, "a leg's up is not the world's")
    }

    @Test
    fun onlyTheActivePaneGetsTheRing() {
        val part = part(template("paw"))
        val frame = sheetFrame(input(part, active = View.TOP))
        val inTop = rects[SheetPane.TOP.ordinal].toRect()
        assertEquals(4, frame.overlay.lines.count { it.color == look.active })
        assertTrue(frame.overlay.lines.filter { it.color == look.active }.all { inTop.contains(it.from) })
    }

    @Test
    fun aToolsOverlayKeepsToItsPaneOnTheSurface() {
        val part = part(template("paw"))
        val base = input(part)
        val line = OverlayLine(Offset(10f, 10f), Offset(5000f, 10f), Color.Magenta)
        val tool = SheetInput(rects, 1000, 800, look, base.selection, base.cameras, base.full, null, mapOf(View.FRONT to ViewportOverlay(lines = listOf(line))))
        val drawn = sheetFrame(tool).overlay.lines.single { it.color == Color.Magenta }
        val front = rects[SheetPane.FRONT.ordinal]
        assertEquals(Offset(front.x + 10f, front.y + 10f), drawn.from)
        assertEquals(front.right.toFloat(), drawn.to.x)
    }

    @Test
    fun aMeshIsTheSameObjectInEveryPaneSoItUploadsOnce() {
        val part = part(template("paw"))
        val frame = sheetFrame(input(part, View.entries.associateWith { outline }))
        val meshes = frame.views.flatMap { view -> view.draws.map { it.mesh } }.filter { it !== RenderMesh.UnitDisc }
        assertEquals(4, meshes.size)
        meshes.forEach { assertSame(part.mesh, it) }
    }
}

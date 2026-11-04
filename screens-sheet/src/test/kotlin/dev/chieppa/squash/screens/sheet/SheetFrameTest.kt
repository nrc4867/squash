package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyView
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
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
        PartMaterial(Color.Green, Color(0xFF008000), Color.Black), 1f, 2f, 1f, 2f, 2f, 2f, 1f, 2f, 2f, 3f, 3f,
    )
    private val rects = sheetPaneRects(1000, 800, 2)
    private val outline = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0f, 1f), CurvePoint(1f, 1f), CurvePoint(1f, 0f)))

    private fun template(id: String) = templates.first { it.id.value == id }

    private fun part(template: PartTemplate) = sheetPartOf(template)

    private fun input(
        part: SheetPart,
        outlines: Map<View, OutlineCurve> = emptyMap(),
        active: View? = View.SIDE,
        others: List<SheetPart> = emptyList(),
        hints: SheetHints = SheetHints(),
    ): SheetInput {
        val cameras = camerasOn(part, fitSheet(part.box, part.axes, rects), rects)
        val full = CameraView(eye = part.center.toGpu() + GpuVec3(3f, 2f, -4f), target = part.center.toGpu())
        val box = others.fold(part.box) { box, other -> box.union(other.box) }
        val selection = SheetSelection(part, outlines, others, box, box.low.y)
        return SheetInput(rects, 1000, 800, look, selection, cameras, full, active, hints = hints)
    }

    private fun viewIn(frame: SceneFrame, pane: SheetPane): SceneView = frame.views.single { it.viewport == rects[pane.ordinal] }

    private fun meshesIn(frame: SceneFrame, pane: SheetPane, part: SheetPart) = viewIn(frame, pane).draws.count { it.mesh === part.mesh }

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
            assertTrue(View.entries.all { part.silhouette(it).isNotEmpty() }, "${template.id}")
        }
    }

    @Test
    fun theFrontSilhouetteOfALegIsItsSectionAndItsLengthShowsInSide() {
        val part = part(template("leg"))
        val side = part.silhouette(View.SIDE).flatten()
        assertTrue(abs(side.maxOf { it dot part.axes.along } - part.extents.along.max) < 1e-3f)
        val front = part.silhouette(View.FRONT).flatten()
        assertTrue(abs(front.maxOf { it dot part.axes.up } - part.extents.up.max) < 1e-3f)
    }

    @Test
    fun aSilhouetteLandsWhereThePartSits() {
        val at = Vec3(1f, 2f, 3f)
        val here = part(template("leg")).silhouette(View.SIDE).flatten().first()
        val moved = sheetPartOf(template("leg"), at).silhouette(View.SIDE).flatten().first()
        assertTrue((moved - here - at).length < 1e-3f, "$here to $moved")
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
    fun theTemplateHintHidesTheDashes() {
        val frame = sheetFrame(input(part(template("paw")), hints = SheetHints(template = false)))
        assertTrue(frame.overlay.lines.none { it.color == look.template })
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
    fun aDrawnTopOfALegFillsThePaneHoldingIt() {
        val part = part(template("leg"))
        val frame = sheetFrame(input(part, mapOf(View.TOP to outline)))
        assertEquals(1, meshesIn(frame, SheetPane.FRONT, part), "a leg's Top sits beside Side")
        assertEquals(0, meshesIn(frame, SheetPane.TOP, part))
    }

    @Test
    fun aGhostShowsBehindTheSelectionFurthestFirstAtTheHintsStrength() {
        val selected = sheetPartOf(template("leg"), Vec3(0f, 0f, 0.5f), Side.RIGHT, "selected")
        val near = sheetPartOf(template("leg"), Vec3(0f, 0f, -0.5f), Side.LEFT, "near")
        val far = sheetPartOf(template("leg"), Vec3(0.5f, 0f, 0.9f), Side.RIGHT, "far")
        val frame = sheetFrame(input(selected, mapOf(View.SIDE to outline), others = listOf(near, far)))
        val draws = viewIn(frame, SheetPane.SIDE).draws

        assertSame(RenderMesh.UnitDisc, draws.first().mesh, "the ground goes down first")
        assertEquals(DepthLayer.BACK, draws.first().layer)
        val ghosts = draws.drop(1).dropLast(1)
        assertEquals(listOf(far.mesh, near.mesh), ghosts.map { it.mesh }, "a nearer ghost blends over a further one")
        assertTrue(ghosts.all { it.opacity == DEFAULT_GHOST && it.layer == DepthLayer.SCENE })
        val top = draws.last()
        assertSame(selected.mesh, top.mesh, "the selection comes last")
        assertEquals(1f, top.opacity)
        assertEquals(DepthLayer.FRONT, top.layer, "so a ghost on the near side never covers it")
    }

    @Test
    fun theGhostOrderFollowsThePanesView() {
        val selected = sheetPartOf(template("paw"), id = "selected")
        val forward = sheetPartOf(template("paw"), Vec3(1f, 0f, 0f), id = "forward")
        val rear = sheetPartOf(template("paw"), Vec3(-1f, 0f, 0f), id = "rear")
        val draws = viewIn(sheetFrame(input(selected, others = listOf(forward, rear))), SheetPane.FRONT).draws.filter { it.opacity < 1f }
        assertEquals(listOf(rear.mesh, forward.mesh), draws.map { it.mesh }, "Front looks from body forward")
    }

    @Test
    fun aGhostAtZeroIsNotDrawnAndTheSlidersStrengthReachesTheDraws() {
        val selected = sheetPartOf(template("paw"), id = "selected")
        val other = sheetPartOf(template("leg"), id = "other")
        assertEquals(0, meshesIn(sheetFrame(input(selected, others = listOf(other), hints = SheetHints(ghost = 0f))), SheetPane.SIDE, other))
        val faint = viewIn(sheetFrame(input(selected, others = listOf(other), hints = SheetHints(ghost = 0.2f))), SheetPane.SIDE)
        assertEquals(0.2f, faint.draws.single { it.mesh === other.mesh }.opacity)
    }

    @Test
    fun fullShowsTheWholeCharacterOpaqueThePlannedPartsLast() {
        val selected = sheetPartOf(template("leg"), id = "selected")
        val other = sheetPartOf(template("torso"), id = "other")
        val planned = SheetPart(
            dev.chieppa.squash.character.build.PlacedPart(
                null,
                template("head"),
                null,
                dev.chieppa.squash.character.build.surfaceOf(template("head"), dev.chieppa.squash.document.PartData(), false),
                Vec3.ZERO,
            ),
        )
        val full = viewIn(sheetFrame(input(selected, others = listOf(planned, other))), SheetPane.FULL).draws
        assertEquals(1f, full.single { it.mesh === selected.mesh }.opacity)
        assertEquals(1f, full.single { it.mesh === other.mesh }.opacity)
        assertTrue(full.single { it.mesh === planned.mesh }.opacity < 1f)
        assertSame(planned.mesh, full.last().mesh, "blended over what is solid")
    }

    @Test
    fun aMirroredPartTakesTheBoardsFarColorAndSitsByItsOrigin() {
        val right = sheetPartOf(template("leg"), Vec3(1f, 2f, 3f), Side.RIGHT, "right")
        val draw = viewIn(sheetFrame(input(right, mapOf(View.SIDE to outline))), SheetPane.SIDE).draws.single { it.mesh === right.mesh }
        assertEquals(look.part.shade, draw.material.base)
        val at = draw.model.project(GpuVec3(0f, 0f, 0f))!!
        assertTrue(abs(at.x - 1f) + abs(at.y - 2f) + abs(at.z - 3f) < 1e-4f, "$at")
    }

    @Test
    fun theGroundLiesOnlyWhereThePaneLooksAlongItUnderTheWholeCharacter() {
        val leg = part(template("leg"))
        val frame = sheetFrame(input(leg))
        fun discs(pane: SheetPane) = viewIn(frame, pane).draws.count { it.mesh === RenderMesh.UnitDisc }
        assertEquals(1, discs(SheetPane.SIDE), "a leg stands on it too")
        assertEquals(1, discs(SheetPane.FRONT))
        assertEquals(0, discs(SheetPane.TOP))
        assertEquals(1, discs(SheetPane.FULL))
        val ground = viewIn(frame, SheetPane.SIDE).draws.first()
        val at = ground.model.project(GpuVec3(0f, 0f, 0f))!!
        assertTrue(abs(at.y - leg.box.low.y) < 1e-4f, "at the lowest vertex: ${at.y}, ${leg.box.low.y}")
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
    fun theRingFollowsTheViewToThePaneHoldingIt() {
        val leg = part(template("leg"))
        val frame = sheetFrame(input(leg, active = View.TOP))
        val beside = rects[SheetPane.FRONT.ordinal].toRect()
        assertTrue(frame.overlay.lines.filter { it.color == look.active }.all { beside.contains(it.from) })
        assertEquals(View.TOP, leg.axes.framing[BodyView.FRONT])
    }

    @Test
    fun aTiltedPartsGhostsAndGroundGoThroughTheCameraThatLooksAlongItsNormal() {
        val tail = part(template("tail"))
        val torso = sheetPartOf(template("torso"), Vec3(0f, -0.2f, 0f), id = "torso")
        val paw = sheetPartOf(template("paw"), Vec3(0.2f, -0.8f, 0.3f), id = "paw")
        val base = input(tail, others = listOf(paw, torso))
        val frame = sheetFrame(base)
        for (pane in listOf(SheetPane.TOP, SheetPane.SIDE, SheetPane.FRONT)) {
            val view = tail.axes.framing.getValue(assertNotNull(pane.body))
            val camera = base.cameras.getValue(view)
            val matrix = viewIn(frame, pane).viewProjection
            assertEquals(camera.viewProjection(), matrix, "$pane")
            val order = viewIn(frame, pane).draws.filter { it.mesh === torso.mesh || it.mesh === paw.mesh }
            val nearestLast = listOf(torso, paw).sortedBy { it.center dot camera.basis.back }
            assertEquals(nearestLast.map { it.mesh }, order.map { it.mesh }, "$pane: furthest from the tilted viewer first")
        }
        val front = base.cameras.getValue(View.FRONT)
        val ground = viewIn(frame, SheetPane.FRONT).draws.first()
        val selection = assertNotNull(base.selection)
        val middle = ground.model.project(GpuVec3(0f, 0f, 0f))!!
        assertTrue((Vec3(middle.x, middle.y, middle.z) - Vec3(selection.box.center.x, selection.floor, selection.box.center.z)).length < 1e-3f)
        val edge = ground.model.project(GpuVec3(0f, 1f, 0f))!!
        val reach = Vec3(edge.x, edge.y, edge.z) - Vec3(middle.x, middle.y, middle.z)
        assertTrue(abs(reach dot front.basis.back) < 1e-3f, "the disc stays square to the tilted viewer")
        val tilt = abs(front.basis.back.y)
        assertTrue(reach.length >= selection.box.spanAlong(front.basis.screenRight cross Vec3.Y) * 0.56f * tilt - 1e-3f, "the ground opens by the camera's tilt: $reach")
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

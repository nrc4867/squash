package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BrushStroke
import dev.chieppa.squash.document.BrushStrokeId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetBrushStroke
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.golden.GoldenFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The color node over a real session: what it reacts to, and what it does not. */
@OptIn(ExperimentalCoroutinesApi::class)
class SurfaceColorTest {
    private val fur = SlotName("fur")
    private val cream = SlotName("cream")
    private val sock = outline(0f to 0.2f, 0.6f to 0.08f, 0.6f to -0.12f, 0f to -0.2f)

    private fun stroke(part: PartInstance, id: String, slot: SlotName = fur, u: Float = 0.5f) =
        BrushStroke(BrushStrokeId(id), listOf(on(part, u, 0.92f), on(part, u, 0.0f), on(part, u, 0.08f)), slot, 0.05f)

    private fun TestScope.painted(): CharacterSession {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.send(SetPaletteSlot(fur, SlotColor(0.8f, 0.5f, 0.2f)))
        session.send(SetPaletteSlot(cream, SlotColor(0.9f, 0.9f, 0.8f)))
        session.send(SetBaseSlot(leftLeg.id, fur))
        session.send(SetBrushStroke(leftLeg.id, BrushStrokeId("dab"), stroke(leftLeg, "dab", cream)))
        return session
    }

    private fun CharacterSession.color(): BodyColor = assertNotNull(bodyColor.output.value)

    @Test
    fun aPaletteColorChangeCutsNothing() = runTest {
        val session = painted()
        session.settle()
        val color = session.color()
        assertFalse(color.parts.getValue(leftLeg.id).layers.isEmpty())

        session.send(SetPaletteSlot(cream, SlotColor(0.1f, 0.2f, 0.3f)))
        session.settle()

        assertSame(color, session.color())
    }

    @Test
    fun aPaintEditCutsOnlyItsShapeAndRebuildsNoMesh() = runTest {
        val session = painted()
        session.settle()
        val mesh = session.mesh(leftLeg.id).output.value
        val surface = session.surface(leftLeg.id).output.value
        val assembled = session.assembly.output.value
        val before = session.heldColor(leftLeg.id).output.value?.cuts?.single()?.cut

        session.send(SetBrushStroke(leftLeg.id, BrushStrokeId("more"), stroke(leftLeg, "more", cream, 0.2f)))
        assertFalse(session.graph.settled.value, "settled waits on the cut")
        session.settle()

        assertSame(mesh, session.mesh(leftLeg.id).output.value)
        assertSame(surface, session.surface(leftLeg.id).output.value)
        assertSame(assembled, session.assembly.output.value)
        val cuts = assertNotNull(session.heldColor(leftLeg.id).output.value).cuts
        assertEquals(2, cuts.size)
        assertSame(before, cuts.first().cut, "a shape that did not change keeps its cut")
    }

    @Test
    fun changingASlotKeepsTheCutAndMovesTheName() = runTest {
        val session = painted()
        session.settle()
        val before = assertNotNull(session.heldColor(leftLeg.id).output.value).cuts.single()

        val changed = stroke(leftLeg, "dab", fur)
        session.send(SetBrushStroke(leftLeg.id, changed.id, changed))
        session.settle()

        val after = assertNotNull(session.heldColor(leftLeg.id).output.value).cuts.single()
        assertSame(before.cut, after.cut)
        assertEquals(fur, after.slot)
    }

    @Test
    fun softnessReachesTheCutAndSoftStrokesHoldTheirPlaceBetweenHardOnes() = runTest {
        val session = surfaceSession(leftLeg)
        val order = listOf("hard-a" to 0f, "hard-b" to 0f, "soft-a" to 0.5f, "soft-b" to 1f, "hard-c" to 0f)
        order.forEachIndexed { i, (id, softness) ->
            val brush = stroke(leftLeg, id, cream, 0.2f + i * 0.1f).copy(softness = softness)
            session.send(SetBrushStroke(leftLeg.id, brush.id, brush))
        }
        session.settle()
        val layers = session.color().parts.getValue(leftLeg.id).layers
        assertEquals(listOf(false, true, true, false), layers.map { it.cut.alphas != null }, "two hard as one, each soft alone, a hard after them")
    }

    @Test
    fun aPaletteChangeCutsNoSoftStrokeAndSoftnessCutsAgain() = runTest {
        val session = surfaceSession(leftLeg)
        val soft = stroke(leftLeg, "soft", cream).copy(softness = 0.5f)
        session.send(SetPaletteSlot(cream, SlotColor(0.9f, 0.9f, 0.8f)))
        session.send(SetBrushStroke(leftLeg.id, soft.id, soft))
        session.settle()
        val color = session.color()
        session.send(SetPaletteSlot(cream, SlotColor(0.1f, 0.2f, 0.3f)))
        session.settle()
        assertSame(color, session.color())
        val before = assertNotNull(session.heldColor(leftLeg.id).output.value).cuts.single().cut
        session.send(SetBrushStroke(leftLeg.id, soft.id, soft.copy(softness = 0.9f)))
        session.settle()
        assertNotSame(before, assertNotNull(session.heldColor(leftLeg.id).output.value).cuts.single().cut)
    }

    @Test
    fun layersRunInTheDocumentedOrderAndNeighboursMerge() = runTest {
        val session = surfaceSession(leftLeg)
        val knee = "knee"
        val border = listOf(on(leftLeg, 0.2f, 0.1f), on(leftLeg, 0.2f, 0.3f), on(leftLeg, 0.4f, 0.2f))
        session.send(SetBrushStroke(leftLeg.id, BrushStrokeId("one"), stroke(leftLeg, "one", cream, 0.3f)))
        session.send(SetBrushStroke(leftLeg.id, BrushStrokeId("two"), stroke(leftLeg, "two", cream, 0.7f)))
        session.send(SetRegion(leftLeg.id, RegionId("patch"), Region(RegionId("patch"), border, fur)))
        session.send(SetAreaFill(leftLeg.id, knee, cream))
        session.settle()
        val layers = session.color().parts.getValue(leftLeg.id).layers
        assertEquals(listOf(cream, fur, cream), layers.map { it.slot }, "area, then region, then the two strokes as one")
    }

    @Test
    fun aRegionCrossingOntoAParentLandsOnBothAndAMirroredSideMirrors() = runTest {
        val session = surfaceSession(torsoPart, leftLeg, rightLeg)
        session.settle()
        val across = listOf(on(leftLeg, 0.1f, 0.1f), on(leftLeg, 0.1f, 0.4f), on(torsoPart, 0.5f, 0.4f), on(torsoPart, 0.5f, 0.3f), on(torsoPart, 0.45f, 0.2f))
        session.send(SetRegion(leftLeg.id, RegionId("sock"), Region(RegionId("sock"), across, fur)))
        session.send(SetBrushStroke(leftLeg.id, BrushStrokeId("dab"), stroke(leftLeg, "dab")))
        session.settle()
        val color = session.color()
        assertTrue(color.parts.getValue(torsoPart.id).layers.isNotEmpty(), "the shape arrives on the parent")
        val left = color.parts.getValue(leftLeg.id).layers.last().cut
        val right = color.parts.getValue(rightLeg.id).layers.last().cut
        assertEquals(left.mesh.vertexCount, right.mesh.vertexCount)
        for (v in 0 until left.mesh.vertexCount) assertClose(left.mesh.position(v).mirrored, right.mesh.position(v), 1e-5f)
    }

    @Test
    fun zoomReflattensOnlyWhenItHasChangedEnough() = runTest {
        val session = painted()
        session.settle()
        val first = session.color()
        session.showColorAt(1000f)
        session.settle()
        assertSame(first, session.color(), "the same step")

        session.showColorAt(20000f)
        session.settle()
        val close = session.color()
        assertNotSame(first, close)
        val before = first.parts.getValue(leftLeg.id).layers.single().cut.mesh.vertexCount
        val after = close.parts.getValue(leftLeg.id).layers.single().cut.mesh.vertexCount
        assertTrue(after > before, "a closer look flattens finer: $before then $after")
    }

    @Test
    fun aStrokesCutFollowsAnOversketchThroughItsAnchors() = runTest {
        val session = painted()
        session.settle()
        val stages = mutableListOf("drawn\n${fixtureOf(session)}")
        val before = session.color().parts.getValue(leftLeg.id).layers.single().cut

        session.send(SetOutline(leftLeg.id, View.SIDE, sock))
        session.settle()
        stages += "oversketched\n${fixtureOf(session)}"
        val after = session.color().parts.getValue(leftLeg.id).layers.single().cut

        assertNotEquals(before.mesh.position(0), after.mesh.position(0))
        val patch = session.builtSurface(leftLeg).body()
        val grid = assertNotNull(session.builtSurface(leftLeg).grid(patch.name))
        for (v in 0 until after.mesh.vertexCount) {
            val on = grid.positionOn(grid.gridU(after.us[v].toDouble()), grid.gridV(after.vs[v].toDouble()))
            assertClose(on, after.mesh.position(v), 1e-3f)
        }
        GoldenFixture.check(Path.of("src/desktopTest/golden/color-cut.txt"), stages.joinToString(""), "character")
    }

    private fun fixtureOf(session: CharacterSession): String = session.color().parts.getValue(leftLeg.id).layers.joinToString("") { line(it.cut) }

    private fun line(cut: PatchCut) = buildString {
        append("${cut.patch} ${cut.mesh.vertexCount} ${cut.mesh.indices.size}\n")
        for (v in 0 until cut.mesh.vertexCount) append("${text(cut.mesh.position(v))}\n")
    }

    private fun text(v: Vec3) = "${(v.x / FIXTURE_STEP).roundToInt()},${(v.y / FIXTURE_STEP).roundToInt()},${(v.z / FIXTURE_STEP).roundToInt()}"

    @Test
    fun aMissingPartDrawsNothingAndAnAreaNotOnTheTemplateDrops() = runTest {
        val session = surfaceSession(leftLeg)
        session.send(SetAreaFill(leftLeg.id, "nowhere", fur))
        session.settle()
        assertTrue(session.color().parts.getValue(leftLeg.id).layers.isEmpty())
    }

    @Test
    fun aPadsColoredPatchShowsItsSlotAsAWholePatch() = runTest {
        val paw = PartInstance(PartId("paw-1"), templateRef("paw"), SocketRef(torsoPart.id, "neck"))
        val session = surfaceSession(torsoPart, paw)
        session.settle()
        val layers = session.color().parts.getValue(paw.id).layers
        assertTrue(layers.any { it.slot == SlotName("pad") && it.cut.patch == "pad:palm" }, "${layers.map { it.slot to it.cut.patch }}")
    }
}

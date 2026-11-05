package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.MarkFlight
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecDocument
import dev.chieppa.squash.screens.sheet.FullView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FLOAT_VIEW_PIXELS = 800

/** How far a mirrored place may lie from the reflection of its other side, in body units: the surface is straight lines between its grid. */
private const val MIRROR_HELD = 0.02f

private const val PIXEL_HELD = 0.05f

class FloatingMarksTest {
    private val session = openSession(fennecDocument())
    private val root = Anchor(HEAD, u = 0.5f, v = 0.2f)
    private val flight = listOf(MarkFlight(0.02f, 0.03f, 0.1f), MarkFlight(0.04f, 0.09f, 0.2f))

    private fun marks(part: PartId): List<ResolvedMark> {
        session.settle()
        return assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(part)).marks
    }

    private fun send(look: StrokeLook = StrokeLook(), on: Anchor = root): MarkId {
        val command = assertNotNull(floatingCommand(session.document.value.character, on, flight, thisSide = false, look))
        session.send(command)
        return command.id
    }

    /** Whether [one] and [other] are reflections of each other at every point, across the plane their roots lie either side of. */
    private fun assertReflected(one: ResolvedMark, other: ResolvedMark) {
        val middle = (one.points.first().position.z + other.points.first().position.z) / 2f
        one.points.zip(other.points).forEach { (a, b) ->
            val expected = Vec3(a.position.x, a.position.y, 2f * middle - a.position.z)
            assertTrue((expected - b.position).length < MIRROR_HELD, "${b.position} is not by $expected")
        }
    }

    @Test
    fun aPixelLandsOnThePlaneFacingTheViewerThroughTheRoot() {
        val camera = sideCamera(Vec3(0f, 0.5f, 0f), Vec3(1f, 2f, -4f))
        val sight = FullView({ camera }, FLOAT_VIEW_PIXELS, FLOAT_VIEW_PIXELS)
        val start = Vec3(0.2f, 0.7f, 0.1f)
        val pixel = assertNotNull(sight.toPixel(start)) + Offset(60f, -35f)
        val point = assertNotNull(pointFacingViewer(start, pixel, sight))
        val shown = assertNotNull(sight.toPixel(point))
        assertTrue((shown - pixel).getDistance() < PIXEL_HELD, "it shows at $shown")
        assertEquals(0f, (point - start) dot sight.toViewer(start), 1e-3f)
    }

    @Test
    fun aFloatingMarkLeavesTheSurfaceFromItsRootAndHoldsNoNormal() {
        val id = send()
        val mark = marks(HEAD).first { it.id == id }
        assertEquals(flight.size + 1, mark.points.size)
        assertTrue(mark.points.all { it.normal == Vec3.ZERO }, "it weighs the same from every side")
        assertTrue((mark.points.last().position - mark.points.first().position).length > 0.1f, "it goes somewhere")
        session.history.undo()
        assertTrue(marks(HEAD).none { it.id == id })
    }

    @Test
    fun aFlightWithNothingInItIsNoStroke() {
        assertEquals(null, floatingCommand(session.document.value.character, root, emptyList(), thisSide = false))
    }

    @Test
    fun aMirroredMarkOnAMiddlePartShowsAcrossTheMiddleAtEveryPoint() {
        val id = send(StrokeLook(mirrored = true))
        val both = marks(HEAD).filter { it.source == id }
        assertEquals(2, both.size)
        val (mark, twin) = both
        assertEquals(id, mark.id)
        assertNotEquals(id, twin.id)
        assertReflected(mark, twin)
    }

    @Test
    fun aMarkOnAPairedPartNeedsNoMirrorAndComesOutReflected() {
        val id = send(on = Anchor(EAR_L, u = 0.5f, v = 0.25f))
        val left = marks(EAR_L).first { it.id == id }
        val right = marks(EAR_R).first { it.id == id }
        assertReflected(left, right)
    }

    @Test
    fun erasingEitherSideOfAMirroredMarkTakesTheMarkItself() {
        val id = send(StrokeLook(mirrored = true))
        val twin = marks(HEAD).first { it.source == id && it.id != id }
        val camera = sideCamera(twin.points.last().position, Vec3(0f, 0f, 5f))
        val sight = FullView({ camera }, FLOAT_VIEW_PIXELS, FLOAT_VIEW_PIXELS)
        val pixel = assertNotNull(sight.toPixel(twin.points.last().position))
        val work = assertNotNull(session.bodySurfaceWork.output.value)
        val commands = erasedAt(work, sight, pixel, 4f, { false })
        assertTrue(commands.isNotEmpty(), "it touches the floating end")
        commands.forEach(session::send)
        assertTrue(marks(HEAD).none { it.source == id }, "both sides go")
    }

    @Test
    fun aStrokeDrawsInItsColorAndWithoutOneInInk() {
        val inked = send()
        val colored = send(StrokeLook(slot = SlotName("cream")))
        session.settle()
        val ink = Color.Black
        val palette = session.document.value.character.palette
        val lines = SurfaceLines(assertNotNull(session.bodyLines.output.value), assertNotNull(session.bodySurfaceWork.output.value), characterLook(LineRules(), 1f), LineRules().taper, ink, 1f, palette)
        val camera = sideCamera(Vec3(0f, 0.5f, 0f))
        val colors = lines.figure(FullView({ camera }, FLOAT_VIEW_PIXELS, FLOAT_VIEW_PIXELS)).map { it.color }
        assertTrue(colors.count { it != ink } == 1, "only $colored is not in ink, $inked is")
    }

    @Test
    fun aPensPressureSetsWidthAndAMouseLeavesTheRulesWeight() {
        assertEquals(MarkWidth(), pressureWidth(listOf(null, null, null)))
        assertEquals(MarkWidth(), pressureWidth(listOf(0.5f, null, 0.5f)), "a point with no reading says nothing of the rest")
        val stops = pressureWidth(listOf(0.5f, 1f, 0.25f)).stops
        assertEquals(listOf(0f, 0.5f, 1f), stops.map { it.at })
        assertEquals(listOf(1f, 2f, 0.5f), stops.map { it.scale }, "half pressure is the rule's weight")
        assertEquals(16, pressureWidth(List(200) { 0.5f }).stops.size, "a long stroke keeps few stops")
    }
}

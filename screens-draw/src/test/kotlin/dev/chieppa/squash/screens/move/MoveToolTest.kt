package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.mouseDrag
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/** How many steps across a pane the search for a bare spot on the torso takes. */
private const val GRID = 24

@OptIn(ExperimentalTestApi::class)
class MoveToolTest {
    private val work = TestWork()

    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))

    /** A dog with its front legs placed, settled, and a Side pane on the left leg. */
    private inner class Bench {
        val state = AppState(folder, work)
        val leg: PartId
        val torso: PartId

        init {
            val plan = folder.plans.first()
            val new = newCharacter(plan, folder, "Fennec")
            state.open(new.document).also { open -> new.commands.forEach(open::send) }
            torso = new.root
            val session = assertNotNull(state.session.value)
            val socket = openSockets(session.document.value, plan, folder).first { it.name == "front-leg" }
            val parts = placeOnSocket(socket, assertNotNull(folder.template(socket.takes.first())), session.document.value)
            session.send(AddParts(parts))
            leg = parts.first { it.side == Side.LEFT }.id
            state.selectedPart.value = leg
            state.screen.value = Screen.WORKSPACE
        }

        val session get() = assertNotNull(state.session.value)
        val character: AssembledCharacter get() = assertNotNull(session.assembly.output.value)

        fun params() = session.document.value.character.resolved(torso) ?: PartData()

        fun camera(view: View = View.SIDE): LockedCamera {
            val part = assertNotNull(sheetSelection(character, leg, emptyMap())).part
            return LockedCamera(view, part.axes, SheetFit(part.origin, 200f, 4f), 600, 400, part.origin)
        }

        fun steps() = session.history.steps.value.done.size
    }

    private fun tool(bench: Bench) = MoveTool(bench.state, SMOKE_LOOK, TEST_CURVE_LOOK)

    private fun androidx.compose.ui.test.ComposeUiTest.settled(bench: Bench) {
        settle(work) {
            bench.session.graph.settled.value && bench.session.assembly.output.value?.part(bench.leg) != null
        }
    }

    private class Locked(private val camera: LockedCamera) : HandleView {
        override fun toPixel(point: Vec3): Offset = camera.toPixel(point)
        override fun toViewer(point: Vec3): Vec3 = camera.basis.back
    }

    @Test
    fun everySocketHasAHandlePlacedOrNotAndTheSelectedPartsKneeHasOne() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val handles = moveHandles(bench.character, bench.leg)
        val sockets = handles.filterIsInstance<SocketHandle>()
        assertEquals(2, sockets.count { it.socket == "front-leg" }, "one for each side of a placed pair")
        assertTrue(sockets.any { it.socket == "tail" && it.child == null }, "an empty socket")
        assertEquals(listOf("knee"), handles.filterIsInstance<JointHandle>().map { it.name }, "hip and ankle stay on the ends")
    }

    @Test
    fun aKneeStopsShortOfTheJointsAroundIt() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val knee = moveHandles(bench.character, bench.leg).filterIsInstance<JointHandle>().single()
        assertTrue(knee.range.start > 0f && knee.range.endInclusive < 1f, "${knee.range}")
    }

    @Test
    fun theSurfaceUnderAPlacesPixelIsThatPlace() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val torso = assertNotNull(bench.character.part(bench.torso))
        val view = Locked(bench.camera())
        val wanted = assertNotNull(torso.pointOn(0.6f, 0.3f))
        val place = assertNotNull(surfaceUnder(torso, view.toPixel(wanted), view))
        val found = assertNotNull(torso.pointOn(place.u, place.v))
        assertTrue((view.toPixel(found) - view.toPixel(wanted)).getDistance() < 1f, "$place")
    }

    @Test
    fun ofTwoHandlesOnOneSpotThePressTakesTheOneNearerTheViewer() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val view = Locked(bench.camera())
        val handles = moveHandles(bench.character, null).filterIsInstance<SocketHandle>().filter { it.child != null }
        val shown = assertNotNull(view.toPixel(handles.first().position))
        val taken = assertIs<SocketHandle>(handleAt(handles, shown, 12f, view))
        val nearest = handles.maxBy { it.position dot view.toViewer(it.position) }
        assertEquals(nearest.side, taken.side)
    }

    @Test
    fun draggingASocketsDotMovesItAsOneUndoStep() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val before = bench.character.part(bench.leg)!!.origin
        val camera = bench.camera()
        val view = Locked(camera)
        val handle = moveHandles(bench.character, bench.leg).filterIsInstance<SocketHandle>().first { it.child == bench.leg }
        val start = view.toPixel(handle.position)
        val steps = bench.steps()
        val tool = tool(bench)
        showPane(tool, camera)
        mouseDrag(List(8) { Offset(start.x + it * 6f, start.y - it * 2f) })
        assertEquals(steps + 1, bench.steps())
        assertTrue(bench.params().params.keys.any { it == PlacementParams.socketU("front-leg") })
        settled(bench)
        settle(work) { bench.character.part(bench.leg)!!.origin != before }
        bench.session.history.undo()
        settle(work) { bench.character.part(bench.leg)!!.origin == before }
        assertTrue(bench.params().params.isEmpty())
    }

    @Test
    fun draggingTheKneeWritesItsPlaceAlongTheLeg() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val camera = bench.camera()
        val view = Locked(camera)
        val knee = moveHandles(bench.character, bench.leg).filterIsInstance<JointHandle>().single()
        val start = view.toPixel(knee.position)
        val along = view.toPixel(assertNotNull(bench.character.part(bench.leg)!!.pointOn(0.9f, 0f))) - start
        showPane(tool(bench), camera)
        mouseDrag(List(6) { start + along * (it * 0.1f) })
        val written = assertNotNull(bench.session.document.value.character.resolved(bench.leg)?.params?.get(PlacementParams.joint("knee")))
        assertNotEquals(knee.u, written)
        assertTrue(abs(written - knee.u) < 0.45f && written in knee.range)
    }

    @Test
    fun pressingAPlacedPartsDotSelectsItAndAnEmptyOneOpensAddPart() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val camera = bench.camera()
        val view = Locked(camera)
        val handles = moveHandles(bench.character, bench.leg).filterIsInstance<SocketHandle>()
        val tail = handles.first { it.socket == "tail" }
        showPane(tool(bench), camera)
        mouseDrag(listOf(view.toPixel(tail.position)))
        assertEquals(Screen.WORKSPACE, bench.state.screen.value)
        assertEquals("tail", bench.state.addingSocket.value?.name)
    }

    @Test
    fun addPartPutsADotOnEachOpenSocketAloneAndAPressOpensItsPopover() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val camera = bench.camera()
        val view = Locked(camera)
        val open = moveHandles(bench.character, bench.leg).filterIsInstance<SocketHandle>()
        val leg = open.first { it.socket == "front-leg" }
        val tail = open.first { it.socket == "tail" }
        val add = AddPartTool(bench.state, SMOKE_LOOK, TEST_CURVE_LOOK)
        showPane(add, camera)
        mouseDrag(listOf(view.toPixel(leg.position)))
        assertNotEquals("front-leg", bench.state.addingSocket.value?.name, "a placed part's socket has no dot to press")
        mouseDrag(listOf(view.toPixel(tail.position)))
        assertEquals("tail", bench.state.addingSocket.value?.name)
        assertEquals(bench.leg, bench.state.selectedPart.value, "and the selection stays")
    }

    @Test
    fun addPartPlacesASocketWhereAPartIsPressedAwayFromTheDotsAndOpensItsPopover() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val camera = bench.camera()
        val view = Locked(camera)
        val dots = moveHandles(bench.character, null).filterIsInstance<SocketHandle>().map { view.toPixel(it.position) }
        val pixels = (1 until GRID).flatMap { row -> (1 until GRID).map { column -> Offset(600f * column / GRID, 400f * row / GRID) } }
        val press = pixels.first { pixel ->
            dots.all { (it - pixel).getDistance() > TEST_CURVE_LOOK.grabReach * 2 } &&
                socketPlaceAt(bench.character, pixel, camera, LockedHandleView(camera))?.part?.id == bench.torso
        }
        showPane(AddPartTool(bench.state, SMOKE_LOOK, TEST_CURVE_LOOK), camera)
        val steps = bench.steps()
        mouseDrag(listOf(press))
        waitForIdle()
        assertEquals(steps + 1, bench.steps(), "placing the socket is one step")
        val socket = assertNotNull(bench.state.addingSocket.value, "the popover opens on the new socket")
        assertEquals(bench.torso, socket.parent)
        assertNotNull(HandSockets.idOf(socket.name), "a hand-made socket")
    }
}

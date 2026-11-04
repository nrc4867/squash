package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.edit.mouseDrag
import dev.chieppa.squash.screens.edit.showPane
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.squash.document.AddParts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SETTLE_MILLIS = 20_000L

@OptIn(ExperimentalTestApi::class)
class MoveToolTest {
    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))

    /** A dog with its front legs placed, settled, and a Side pane on the left leg. */
    private inner class Bench {
        val state = AppState(folder, CoroutineScope(SupervisorJob() + Dispatchers.Default))
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
        waitUntil(timeoutMillis = SETTLE_MILLIS) {
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
        waitUntil(timeoutMillis = SETTLE_MILLIS) { bench.character.part(bench.leg)!!.origin != before }
        bench.session.history.undo()
        waitUntil(timeoutMillis = SETTLE_MILLIS) { bench.character.part(bench.leg)!!.origin == before }
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
    fun pressingAPlacedPartsDotSelectsItAndAnEmptyOnePicksAType() = runComposeUiTest {
        val bench = Bench()
        settled(bench)
        val camera = bench.camera()
        val view = Locked(camera)
        val handles = moveHandles(bench.character, bench.leg).filterIsInstance<SocketHandle>()
        val tail = handles.first { it.socket == "tail" }
        showPane(tool(bench), camera)
        mouseDrag(listOf(view.toPixel(tail.position)))
        assertEquals(Screen.PICK_PART, bench.state.screen.value)
        assertEquals("tail", bench.state.pickedSocket.value?.name)
    }
}

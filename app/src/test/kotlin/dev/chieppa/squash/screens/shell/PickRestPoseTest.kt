package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.plan
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.sheet.LocalSceneSurface
import dev.chieppa.squash.screens.sheet.SceneSurfaceContent
import dev.chieppa.squash.screens.sheet.restPoseCamera
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val SETTLE_MILLIS = 20_000L

/** A dot is its edge and its fill. */
private const val OVERLAY_DOTS_PER_SOCKET = 2
private const val PIXEL_TOLERANCE = 0.5f
private const val TORSO_SOCKETS = 4
private const val REVISITS = 3

private object PickNoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The frame Pick a part type hands the surface, over the same flows a person takes through the app. */
@OptIn(ExperimentalTestApi::class)
class PickRestPoseTest {
    private var shown: SceneFrame? = null

    private val recorder: SceneSurfaceContent = { frame, _ -> SideEffect { if (frame != null) shown = frame } }

    private fun ComposeUiTest.openOnPick(state: AppState): CharacterSession {
        setStretchContent {
            Box(Modifier.fillMaxSize()) {
                SquashApp(state, PickNoFiles)
                val screen by state.screen.collectAsState()
                if (screen == Screen.PICK_PART) {
                    CompositionLocalProvider(LocalSceneSurface provides recorder) { RestPoseSurface(state) }
                }
            }
        }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.partRow("front-leg")).performClick()
        return settled(state)
    }

    private fun ComposeUiTest.settled(state: AppState): CharacterSession {
        val session = assertNotNull(state.session.value)
        waitUntil(timeoutMillis = SETTLE_MILLIS) { session.graph.settled.value }
        waitForIdle()
        return session
    }

    /** Every socket still open, each with a dot exactly where the camera puts it. */
    private fun assertDotsOnEachOpenSocket(state: AppState, session: CharacterSession) {
        val frame = assertNotNull(shown)
        val document = session.document.value
        val plan = assertNotNull(state.templates.plan(document.character.bodyPlan))
        val open = openSockets(document, plan, state.templates)
        val character = assertNotNull(session.assembly.output.value)
        val dots = frame.overlay.dots
        assertEquals(open.size * OVERLAY_DOTS_PER_SOCKET, dots.size, "open: ${open.map { it.name }}")
        val view = frame.views.single().viewport
        val camera = restPoseCamera(assertNotNull(character.box), view.width, view.height)
        for (socket in open) {
            val at = camera.toPixel(character.sockets.first { it.ref == socket.ref }.position)
            val nearest = dots.minOf { (it.center - at).getDistance() }
            assertEquals(0f, nearest, PIXEL_TOLERANCE, socket.name)
        }
    }

    @Test
    fun aFreshTorsoHasADotOnEachOfItsSockets() = runComposeUiTest {
        val state = AppState(shipped)
        val session = openOnPick(state)
        assertDotsOnEachOpenSocket(state, session)
        assertEquals(TORSO_SOCKETS * OVERLAY_DOTS_PER_SOCKET, assertNotNull(shown).overlay.dots.size)
    }

    @Test
    fun theDotsComeBackEachTimeTheScreenDoes() = runComposeUiTest {
        val state = AppState(shipped)
        val session = openOnPick(state)
        repeat(REVISITS) {
            runOnIdle { state.screen.value = Screen.WORKSPACE }
            waitForIdle()
            shown = null
            runOnIdle { state.openPartTypes() }
            settled(state)
            assertDotsOnEachOpenSocket(state, session)
            assertEquals(TORSO_SOCKETS * OVERLAY_DOTS_PER_SOCKET, assertNotNull(shown).overlay.dots.size)
        }
    }

    @Test
    fun placingAPartTakesOnlyItsSocketsDotAndUndoBringsItBack() = runComposeUiTest {
        val state = AppState(shipped)
        val session = openOnPick(state)
        onNodeWithTag(ShellTags.PICK_DRAW).performClick()
        settled(state)
        runOnIdle { state.openPartTypes() }
        settled(state)
        assertDotsOnEachOpenSocket(state, session)
        val character = assertNotNull(session.assembly.output.value)
        val torso = session.document.value.character.parts.first { it.socket == null }.id
        assertEquals(setOf("neck", "back-leg", "tail"), character.sockets.filter { it.ref.parent == torso }.map { it.ref.socket }.toSet())

        runOnIdle { session.history.undo() }
        settled(state)
        assertDotsOnEachOpenSocket(state, session)
        assertEquals(TORSO_SOCKETS * OVERLAY_DOTS_PER_SOCKET, assertNotNull(shown).overlay.dots.size)
    }
}

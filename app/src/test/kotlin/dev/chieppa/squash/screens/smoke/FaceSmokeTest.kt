package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.SetMarkControls
import dev.chieppa.squash.document.ShowExpression
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.EARS_BACK
import dev.chieppa.squash.screens.fennec.EARS_FORWARD
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.EAR_R
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.HAPPY
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.headAngle
import dev.chieppa.stretch.gpu.GpuOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val TILE = 320
private const val COLUMNS = 3
private const val FACE_MARGIN = 0.05f

/** The three camera directions of the face: side on, three-quarter and ahead, from the head's left. */
private val FACE_VIEWS = listOf(Vec3(0f, 0.1f, -1f), Vec3(0.7f, 0.1f, -0.7f), Vec3(1f, 0.1f, -0.1f))

private val MOUTH = MarkGroupId("mouth")

/** The fennec's face through its controls and expressions, three views at a time, as images under smoke-images. */
@Tag("slow")
class FaceSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()

    private fun session(): CharacterSession {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(EMPTY_FENNEC, TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
        fennecCommands().forEach(session::send)
        work.settle(session)
        return session
    }

    private fun row(session: CharacterSession, name: String) {
        work.settle(session)
        val state = session.probeState(work)
        val head = state.character.parts.filter { it.id == HEAD || it.id == EAR_L || it.id == EAR_R }
        val views = FACE_VIEWS.mapIndexed { i, toViewer ->
            val rect = PixelRect(i * TILE, 0, TILE, TILE)
            val framed = framedCamera(head, toViewer, TILE, TILE, FACE_MARGIN)
            val shown = session.probeState(work, framed.camera, TILE, TILE, session.document.value.character.shownExpression)
            val bin = FACE_BINS.binFor(headAngle(assertNotNull(shown.character.part(HEAD)), toViewer))
            characterView(rect, framed.camera, shown, PROBE_LOOK, ObjectShading(Shading.FLAT), framed.pixelsPerUnit) { bin }
        }
        keep(name, views)
    }

    private fun keep(name: String, views: List<SceneView>) {
        val image = renderSnapshot(SceneFrame(PROBE_GROUND, views), TILE * COLUMNS, TILE, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name)
        assertNotBlank(image.pixels, name)
    }

    @Test
    fun theFaceActsThroughItsControls() {
        val session = session()
        row(session, "face-closed")
        session.send(SetMarkControls(HEAD, MOUTH, MarkControls(open = 0.5f)))
        row(session, "face-mouth-half")
        session.send(SetMarkControls(HEAD, MOUTH, MarkControls(open = 1f)))
        row(session, "face-mouth-open")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun expressionsCarryTheirPoses() {
        val session = session()
        for ((name, id) in listOf<Pair<String, ExpressionId?>>("happy" to HAPPY, "ears-back" to EARS_BACK, "ears-forward" to EARS_FORWARD)) {
            session.send(ShowExpression(id))
            row(session, "face-$name")
        }
        assertEquals(emptyList(), errors)
    }
}

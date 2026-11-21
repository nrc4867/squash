package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BIN_FRONT
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import java.io.File
import kotlin.test.assertNotNull

private const val TILE = 480
private const val COLUMNS = 3
private const val ROWS = 2
private const val CLOSE_MARGIN = 0.1f

/** Where each close-up looks from, toward the viewer from the part. */
private val CLOSE_VIEWS = listOf(
    Vec3(1f, 0.15f, 0f),
    Vec3(0f, 0.15f, 1f),
    Vec3(-1f, 0.15f, 0f),
    Vec3(0f, 0.15f, -1f),
    Vec3(0.5f, 1f, 0.3f),
    Vec3(0.5f, -1f, -0.3f),
)

/** Close views of one part and the part it hangs from, six sides in one image, two-tone with lines, through the real graph and scene. */
class CloseUps(private val work: TestWork, private val errors: MutableList<String>, private val images: File = SMOKE_IMAGES) {
    /** A session over [library], the shipped templates unless given, with [commands] sent and settled. */
    fun session(empty: SquashDocument, commands: List<DocumentCommand>, library: TemplateLibrary = shippedLibrary()): CharacterSession {
        val session = CharacterSession(empty, library, work.scope, work.dispatcher)
        commands.forEach(session::send)
        work.settle(session)
        return session
    }

    private fun CharacterSession.posedState(): CharacterState {
        work.settle(this)
        return shownState()
    }

    /** [part] alone and the part it hangs from, from six sides, one tile each, the camera clear of both, kept as the image [name]. */
    fun keep(session: CharacterSession, part: PartId, name: String) {
        val whole = session.posedState()
        val parent = session.document.value.character.part(part)?.socket?.parent
        val state = whole.keptTo(setOfNotNull(part, parent))
        val framed = listOf(assertNotNull(state.character.part(part)))
        val shown = state.character.parts
        val views = CLOSE_VIEWS.mapIndexed { i, toViewer ->
            val rect = PixelRect(i % COLUMNS * TILE, i / COLUMNS * TILE, TILE, TILE)
            val close = framedCamera(framed, toViewer, TILE, TILE, CLOSE_MARGIN, clearOf = shown)
            characterView(rect, close.camera, state, PROBE_LOOK, probeShading(session), close.pixelsPerUnit) { BIN_FRONT }
        }
        val image = FrameSnapshots.render(SceneFrame(PROBE_GROUND, views), TILE * COLUMNS, TILE * ROWS, errors)
        keepImage(image.pixels, image.width, name, images)
        assertNotBlank(image.pixels, name)
    }

    /** The whole character framed on [part], from six sides, one tile each, kept as the image [name]. */
    fun keepAround(session: CharacterSession, part: PartId, name: String) {
        val state = session.posedState()
        val framed = listOf(assertNotNull(state.character.part(part)))
        val views = CLOSE_VIEWS.mapIndexed { i, toViewer ->
            val rect = PixelRect(i % COLUMNS * TILE, i / COLUMNS * TILE, TILE, TILE)
            val close = framedCamera(framed, toViewer, TILE, TILE, CLOSE_MARGIN)
            characterView(rect, close.camera, state, PROBE_LOOK, probeShading(session), close.pixelsPerUnit) { BIN_FRONT }
        }
        val image = FrameSnapshots.render(SceneFrame(PROBE_GROUND, views), TILE * COLUMNS, TILE * ROWS, errors)
        keepImage(image.pixels, image.width, name, images)
        assertNotBlank(image.pixels, name)
    }

    companion object {
        fun shippedLibrary(): TemplateLibrary = TemplateFolder.shipped().library()
    }
}

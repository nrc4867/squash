package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.curled
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BIN_FRONT
import dev.chieppa.squash.screens.fennec.EMPTY_FENNEC
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.fennecCommands
import dev.chieppa.squash.screens.person.EMPTY_PERSON
import dev.chieppa.squash.screens.person.HAND_L
import dev.chieppa.squash.screens.person.personCommands
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.toucan.EMPTY_TOUCAN
import dev.chieppa.squash.screens.toucan.FOOT_L as TOUCAN_FOOT_L
import dev.chieppa.squash.screens.toucan.toucanCommands
import dev.chieppa.stretch.gpu.GpuOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val TILE = 480
private const val COLUMNS = 3
private const val ROWS = 2
private const val CLOSE_MARGIN = 0.1f

/** How far the curled renders close the digits. */
private const val CURL = 0.7f

/** Where each close-up looks from, toward the viewer from the part. */
private val CLOSE_VIEWS = listOf(
    Vec3(1f, 0.15f, 0f),
    Vec3(0f, 0.15f, 1f),
    Vec3(-1f, 0.15f, 0f),
    Vec3(0f, 0.15f, -1f),
    Vec3(0.5f, 1f, 0.3f),
    Vec3(0.5f, -1f, -0.3f),
)

/**
 * Close views of the person's hand, the fennec's front paw and the toucan's foot, at rest and curled, two-tone with lines, through
 * the real graph, rig and scene, left for a person to judge whether they read as hands, paws and feet.
 */
@Tag("slow")
class DigitCloseUpSmokeTest {
    private val errors = CopyOnWriteArrayList<String>()
    private val work = TestWork()

    private fun session(empty: SquashDocument, commands: List<DocumentCommand>): CharacterSession {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(empty, TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
        commands.forEach(session::send)
        work.settle(session)
        return session
    }

    private fun CharacterSession.posedState(): CharacterState {
        work.settle(this)
        return CharacterState(
            assertNotNull(posedBody.output.value),
            assertNotNull(bodyColor.output.value),
            assertNotNull(posedSurfaceWork.output.value),
            document.value.character.palette,
            assertNotNull(posedLines.output.value),
            fur = FurView.EMPTY,
        )
    }

    /** The part alone and the part it hangs from, from six sides, one tile each. */
    private fun keepViews(session: CharacterSession, part: PartId, name: String) {
        val whole = session.posedState()
        val parent = session.document.value.character.part(part)?.socket?.parent
        val kept = setOfNotNull(part, parent)
        val shown = AssembledCharacter(whole.character.parts.filter { it.id in kept }, emptyList())
        val lines = BodyLines(whole.lines.parts.filterKeys { it in kept })
        val state = CharacterState(shown, whole.color, whole.work, whole.palette, lines, whole.fur)
        val framed = listOf(assertNotNull(shown.part(part)))
        val views = CLOSE_VIEWS.mapIndexed { i, toViewer ->
            val rect = PixelRect(i % COLUMNS * TILE, i / COLUMNS * TILE, TILE, TILE)
            val close = framedCamera(framed, toViewer, TILE, TILE, CLOSE_MARGIN)
            characterView(rect, close.camera, state, PROBE_LOOK, ObjectShading(Shading.TWO_TONE), close.pixelsPerUnit) { BIN_FRONT }
        }
        val image = renderSnapshot(SceneFrame(PROBE_GROUND, views), TILE * COLUMNS, TILE * ROWS, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, "digits-$name")
        assertNotBlank(image.pixels, "digits-$name")
    }

    private fun closeUps(empty: SquashDocument, commands: List<DocumentCommand>, part: PartId, name: String) {
        val session = session(empty, commands)
        val pose = SessionPose(session)
        work.settle(session)
        keepViews(session, part, "$name-rest")
        val rig = assertNotNull(pose.rig.rig.output.value)
        session.send(SetPose(rig.curled(Pose(), listOf(part), CURL)))
        keepViews(session, part, "$name-curled")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun thePersonsHand() = closeUps(EMPTY_PERSON, personCommands(), HAND_L, "hand")

    @Test
    fun theFennecsPaw() = closeUps(EMPTY_FENNEC, fennecCommands(), FRONT_PAW_L, "paw")

    @Test
    fun theToucansFoot() = closeUps(EMPTY_TOUCAN, toucanCommands(), TOUCAN_FOOT_L, "foot-bird")
}

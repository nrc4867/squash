package dev.chieppa.squash.screens.look.shots

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.assertNotBlank
import dev.chieppa.squash.screens.smoke.keepImage
import dev.chieppa.squash.screens.smoke.renderSnapshot
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.headAngle
import dev.chieppa.stretch.gpu.GpuOptions
import java.io.File
import kotlin.test.assertNotNull

/** The reference renders' flat background, so a shot and its reference pair up on the same ground. */
val REFERENCE_GROUND = Color(0xFFF1ECE3)

/** The body's height under its lowest vertex as posed, where the reference frame's ground lands. */
fun AssembledCharacter.groundHeight(): Float =
    parts.filter { it.id != null }.minOf { part -> (0 until part.surface.mesh.vertexCount).minOf { part.origin.y + part.surface.mesh.position(it).y } }

/** Renders the character as posed through an explicit [ShotCamera] in the reference frame, at [unitsPerMetre] body units to a metre. */
class ShotViews(private val work: TestWork, private val errors: MutableList<String>, private val images: File, private val unitsPerMetre: Float = 1f) {
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

    /** The character through [shot], its soles on the reference's ground, kept as the image [name]. */
    fun keep(session: CharacterSession, shot: ShotCamera, name: String) {
        val state = session.posedState()
        val frame = ReferenceFrame(state.character.groundHeight(), unitsPerMetre)
        val camera = shot.inBody(frame)
        val toViewer = frame.directionToBody(shot.toViewer)
        val view = characterView(PixelRect(0, 0, shot.width, shot.height), camera, state, PROBE_LOOK, ObjectShading(Shading.TWO_TONE), 0f) { id ->
            val head = state.character.part(id)
            if (head == null) FACE_BINS.binFor(90f) else FACE_BINS.binFor(headAngle(head, toViewer))
        }
        val image = renderSnapshot(SceneFrame(REFERENCE_GROUND, listOf(view)), shot.width, shot.height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        keepImage(image.pixels, image.width, name, images)
        runCatching { assertNotBlank(image.pixels, name) }.onFailure { errors += "$name: ${it.message}" }
    }
}

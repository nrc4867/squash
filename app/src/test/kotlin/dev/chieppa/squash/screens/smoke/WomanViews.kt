package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.headAngle
import java.io.File
import kotlin.test.assertNotNull

private const val WHOLE_MARGIN = 0.05f
private const val CLOSE_MARGIN = 0.08f

/** Front, three-quarter and side, as the look probe turns round a character from its front toward its left. */
private val AROUND = listOf(0f, 40f, 90f)

/** The whole character, or the parts nearest a few, from a row of sides through the real graph and scene, as the app's free view draws them. */
class WomanViews(private val work: TestWork, private val errors: MutableList<String>, private val images: File = SMOKE_IMAGES) {
    private fun CharacterSession.posedState(): CharacterState {
        work.settle(this)
        return shownState()
    }

    /**
     * The character from each of [azimuths] in degrees round from its front, or from each direction of [toViewers], framed on [framed] parts
     * or on all of it, [tile] by [height] pixels a view, two-tone with lines and each head's face in the version its angle shows, kept as the
     * image [name]. With [only], just those parts are drawn and framed.
     */
    fun keep(
        session: CharacterSession,
        name: String,
        framed: Set<PartId>? = null,
        azimuths: List<Float> = AROUND,
        tile: Int = 600,
        height: Int = 900,
        toViewers: List<Vec3> = azimuths.map(::probeAzimuth),
        only: Set<PartId>? = null,
    ) {
        val state = session.posedState().let { if (only == null) it else it.keptTo(only) }
        val parts = state.character.parts.filter { it.id != null }
        val framing = parts.filter { framed == null || it.id in framed }
        val margin = if (framed == null) WHOLE_MARGIN else CLOSE_MARGIN
        val views = toViewers.mapIndexed { i, toViewer ->
            val rect = PixelRect(i * tile, 0, tile, height)
            val camera = framedCamera(framing, toViewer, tile, height, margin)
            characterView(rect, camera.camera, state, PROBE_LOOK, probeShading(session), camera.pixelsPerUnit) { id ->
                val head = state.character.part(id)
                if (head == null) FACE_BINS.binFor(90f) else FACE_BINS.binFor(headAngle(head, toViewer))
            }
        }
        val image = FrameSnapshots.render(SceneFrame(PROBE_GROUND, views), tile * toViewers.size, height, errors)
        keepImage(image.pixels, image.width, name, images)
        assertNotBlank(image.pixels, name)
    }
}

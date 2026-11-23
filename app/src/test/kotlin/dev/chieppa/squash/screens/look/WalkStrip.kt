package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.walkPoseOf
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.FrameSnapshots
import dev.chieppa.squash.screens.smoke.PROBE_GROUND
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.probeAzimuth
import dev.chieppa.squash.screens.smoke.probeShading
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.headAngle
import java.io.File

private const val WALK_MARGIN = 0.05f

/** How many frames of one stride the walk shows. */
private const val WALK_FRAMES = 6

/** The rows of the walk, each a way round the character: from its left side, then three-quarter. */
private val WALK_AZIMUTHS = listOf(90f, 45f)

/**
 * One image of the character walking: [WALK_FRAMES] frames of one stride ([walkPoseOf]), thighs and knees swinging and the arms
 * swinging against them, over its display pose, side by side from its side and, below, three-quarter, each row framed alike.
 */
class WalkStrip(private val work: TestWork, private val errors: MutableList<String>, private val folder: File) {
    /** Keeps the walk as the image [name], each panel [tile] by [height] pixels. */
    fun keep(session: CharacterSession, sessionPose: SessionPose, parts: LookParts, name: String, tile: Int, height: Int) {
        val rig = requireNotNull(sessionPose.rig.rig.output.value) { "the rig is not built" }
        if (parts.legs.isEmpty() && parts.arms.isEmpty()) {
            System.err.println("walk $name: no leg or arm to swing, so no walk")
            return
        }
        val character = session.document.value.character
        val sided = { ids: List<PartId> -> ids.map { it to character.part(it)?.side } }
        val states = (0 until WALK_FRAMES).map { frame ->
            session.send(SetPose(walkPoseOf(rig, sided(parts.legs), sided(parts.arms), frame.toFloat() / WALK_FRAMES)))
            work.settle(session)
            session.shownState()
        }
        session.send(SetPose(Pose()))
        work.settle(session)
        val views = WALK_AZIMUTHS.flatMapIndexed { row, azimuth ->
            val toViewer = probeAzimuth(azimuth)
            val camera = framedCamera(states.flatMap { state -> state.character.parts.filter { it.id != null } }, toViewer, tile, height, WALK_MARGIN)
            states.mapIndexed { i, state ->
                characterView(PixelRect(i * tile, row * height, tile, height), camera.camera, state, PROBE_LOOK, probeShading(session), camera.pixelsPerUnit) { id ->
                    FACE_BINS.binFor(state.character.part(id)?.let { headAngle(it, toViewer) } ?: 90f)
                }
            }
        }
        val image = FrameSnapshots.render(SceneFrame(PROBE_GROUND, views), tile * WALK_FRAMES, height * WALK_AZIMUTHS.size, errors)
        keepLabelledStrip(folder, image.pixels, image.width, (1..WALK_FRAMES).map { "walk $it/$WALK_FRAMES" }, tile, name)
    }
}

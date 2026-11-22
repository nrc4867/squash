package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetDisplayPose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.rig.tPoseOf
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.FrameSnapshots
import dev.chieppa.squash.screens.smoke.PROBE_GROUND
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.probeAzimuth
import dev.chieppa.squash.screens.smoke.probeShading
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.headAngle
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val STRIP_MARGIN = 0.05f
private const val LABEL_SIZE_PIXELS = 16
private const val LABEL_INSET_PIXELS = 10
private val LABEL_COLOR = Color(0x80, 0x78, 0x70)

/** The panel labels, left to right. */
private const val RELAXED_LABEL = "relaxed"
private const val REST_LABEL = "45 degrees"
private const val T_LABEL = "T-pose"

/**
 * One image of the character from the front in three poses side by side, each panel the same size and framed alike: its display pose,
 * its rest, which is the A-pose where the arms rest leaning out, and a T-pose with its arms straight out level (see [tPoseOf]). With no
 * arm the T panel is left out and a warning printed.
 */
class PoseStrip(private val work: TestWork, private val errors: MutableList<String>, private val folder: File) {
    /** Keeps the strip as the image [name], each panel [tile] by [height] pixels. */
    fun keep(session: CharacterSession, sessionPose: SessionPose, armIds: List<PartId>, name: String, tile: Int, height: Int) {
        val display = session.document.value.character.displayPose
        val tPose = tPoseOf(requireNotNull(sessionPose.rig.rig.output.value) { "the rig is not built" }, armIds)
        if (tPose.bones.isEmpty()) System.err.println("pose strip $name: no arm to hold out, so no T-pose panel")
        val panels = buildList {
            add(RELAXED_LABEL to { session.send(SetPose(Pose())) })
            add(REST_LABEL to { session.send(SetDisplayPose(Pose())) })
            if (tPose.bones.isNotEmpty()) add(T_LABEL to { session.send(SetPose(tPose)) })
        }
        val states = panels.map { (_, apply) ->
            apply()
            work.settle(session)
            session.shownState().also {
                session.send(SetDisplayPose(display))
                session.send(SetPose(Pose()))
            }
        }
        val toViewer = probeAzimuth(0f)
        val camera = framedCamera(states.flatMap { state -> state.character.parts.filter { it.id != null } }, toViewer, tile, height, STRIP_MARGIN)
        val views = states.mapIndexed { i, state -> characterView(PixelRect(i * tile, 0, tile, height), camera.camera, state, PROBE_LOOK, probeShading(session), camera.pixelsPerUnit) { id -> FACE_BINS.binFor(state.character.part(id)?.let { headAngle(it, toViewer) } ?: 90f) } }
        val image = FrameSnapshots.render(SceneFrame(PROBE_GROUND, views), tile * states.size, height, errors)
        keepLabelled(image.pixels, image.width, panels.map { it.first }, tile, name)
    }

    private fun keepLabelled(pixels: IntArray, width: Int, labels: List<String>, tile: Int, name: String) {
        val out = BufferedImage(width, pixels.size / width, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, width, out.height, pixels, 0, width)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, LABEL_SIZE_PIXELS)
        g.color = LABEL_COLOR
        labels.forEachIndexed { i, label -> g.drawString(label, i * tile + LABEL_INSET_PIXELS, LABEL_INSET_PIXELS + LABEL_SIZE_PIXELS) }
        g.dispose()
        folder.mkdirs()
        ImageIO.write(out, "png", File(folder, "$name.png"))
    }
}

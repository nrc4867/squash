package dev.chieppa.squash.screens.surface.eyes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.surface.EyeChartFrame
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.showsPose
import dev.chieppa.stretch.frame.OverlayBuilder

/** Pixel sizes of the box and its handles, in density independent pixels. */
private const val REACH = 9f
private const val TURN_REACH = 18f
private const val BOX_WIDTH = 1.5f
private const val DASH = 4f
private const val GAP = 3f
private const val CORNER_RADIUS = 4.5f
private const val RING = 2f
private const val TURN_RADIUS = 5f

/**
 * The picked painted eye's box in every pane, on each side that faces the viewer: corners that resize it, a round handle above that turns
 * it, and its inside, which moves it over the head. The other side follows, mirrored, and each drag is one undo step.
 */
class PaintedEyeHandles(private val state: AppState) {
    /** Each side of [eye] that faces the viewer through [sight], with its box there. */
    private fun boxes(eye: PaintedEye, sight: SurfaceView): List<Pair<EyeChartFrame, EyeBox>> {
        val open = state.session.value ?: return emptyList()
        val rest = open.characterSurface.output.value?.surface ?: return emptyList()
        val carry = if (state.showsPose(sight)) open.sessionSurface.posed.value?.output?.value?.posed?.carry else null
        val look = eye.look
        return SurfaceEyes.framesOf(rest, eye).mapNotNull { frame ->
            val shown = carry?.let { carried(frame, eye.anchor, look.width, it) } ?: frame
            if ((shown.faces dot sight.toViewer(shown.middle)) <= 0f) return@mapNotNull null
            val middle = sight.toPixel(shown.middle) ?: return@mapNotNull null
            val u = sight.toPixel(shown.middle + shown.perU * look.width) ?: return@mapNotNull null
            val v = sight.toPixel(shown.middle + shown.perV * look.height) ?: return@mapNotNull null
            shown to EyeBox(middle, (u - middle) / look.width, (v - middle) / look.height, look.width, look.height, look.tilt)
        }
    }

    /** [frame] carried into the pose by [carry], its chart directions read [reach] out from its middle. */
    private fun carried(frame: EyeChartFrame, anchor: Anchor, reach: Float, carry: (ResolvedPoint) -> ResolvedPoint): EyeChartFrame {
        fun moved(at: Vec3) = carry(ResolvedPoint(anchor, at, frame.faces))
        val middle = moved(frame.middle)
        val u = moved(frame.middle + frame.perU * reach).position
        val v = moved(frame.middle + frame.perV * reach).position
        return EyeChartFrame(middle.position, (u - middle.position) * (1f / reach), (v - middle.position) * (1f / reach), middle.normal, frame.mirrored)
    }

    /** Draws the picked eye's box and handles through [sight], in [ink] over [paper], at [density] pixels a unit. */
    fun OverlayBuilder.drawn(sight: SurfaceView, ink: Color, paper: Color, density: Float) {
        val open = state.session.value ?: return
        val eye = state.pickedPaintedEye(open) ?: return
        boxes(eye, sight).forEach { (_, box) ->
            val corners = box.corners
            dashed(corners + corners.first(), ink, BOX_WIDTH * density, DASH * density, GAP * density)
            val top = box.at(0f, box.height)
            val turn = box.turnHandle(TURN_REACH * density)
            line(top, turn, ink, BOX_WIDTH * density)
            dot(turn, TURN_RADIUS * density, ink)
            corners.forEach { corner ->
                dot(corner, CORNER_RADIUS * density, ink)
                dot(corner, (CORNER_RADIUS - RING) * density, paper)
            }
        }
    }

    /** Takes a press on the picked eye's box through [sight], following it to its release as one undo step; true when it took it. */
    suspend fun AwaitPointerEventScope.pressed(down: PointerInputChange, sight: SurfaceView, density: Float, hitAt: (Offset) -> Anchor?): Boolean {
        val session = state.session.value ?: return false
        val eye = state.pickedPaintedEye(session) ?: return false
        val (frame, box, grab) = boxes(eye, sight).firstNotNullOfOrNull { (frame, box) ->
            grabAt(box, down.position, REACH * density, TURN_REACH * density)?.let { Triple(frame, box, it) }
        } ?: return false
        down.consume()
        val drag = EyeDrag(eye, box, grab, frame.mirrored, down.position, if (grab == EyeGrab.Move) hitAt(down.position) else null)
        val gesture = session.history.begin()
        val released = followToRelease(down.id, {}) { pixel ->
            drag.commandAt(pixel, if (grab == EyeGrab.Move) hitAt(pixel) else null)?.let(gesture::update)
        }
        if (released) gesture.commit() else gesture.cancel()
        return true
    }
}

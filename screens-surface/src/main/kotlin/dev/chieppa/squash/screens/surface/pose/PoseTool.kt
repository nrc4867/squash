package dev.chieppa.squash.screens.surface.pose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.View
import dev.chieppa.squash.rig.PoseHandles
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.handles
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** A press taken by a pose tool, followed until it lets go: [move] at each pointer place with the keys held, [finish] once. */
interface PoseDrag {
    /** The pointer at [pixel], with Alt held as [alt] and Shift as [shift]. */
    fun move(pixel: Offset, alt: Boolean, shift: Boolean)

    /** Ends the drag, keeping what it did when [keep]. */
    fun finish(keep: Boolean)
}

/**
 * A tool of the Pose stage, in every pane alike: it shows the pose's handles over the character, follows the handle under the pointer,
 * and hands a press to [grab] with the handle it took, if any. What the pose is and the rig it moves come from the panes' own
 * reading, [shownPose] and [rig], so the handles redraw as the pose changes.
 */
@Stable
abstract class PoseTool(protected val state: AppState, var look: PoseHandleLook) : PaneTool {
    var shownPose by mutableStateOf(Pose())
    var rig by mutableStateOf<Rig?>(null)

    override val takesFreeCamera: Boolean get() = true
    override val showsWhole: Boolean get() = true

    protected val solver: PoseSolver? get() = rig?.let(state.pose::solverFor)

    /** The pose's handles as they stand, or null before the rig has run. */
    protected val handles: PoseHandles? get() = solver?.handles(shownPose)

    /** Whether this tool takes a press on [grip]. */
    protected abstract fun takes(grip: PoseGrip): Boolean

    /** Whether this tool shows [grip]; the ones it takes, and every other the board draws. */
    protected open fun shows(grip: PoseGrip): Boolean = true

    /** What a press at [pixel] through [sight] does, on [grip] if it took one; null when it does nothing. */
    protected abstract fun grab(grip: PoseGrip?, pixel: Offset, sight: SurfaceView, shift: Boolean): PoseDrag?

    /** What the tool draws over the handles through [sight]. */
    protected open fun OverlayBuilder.drawn(sight: SurfaceView) = Unit

    /** The handle this tool would take at [pixel] through [sight]. */
    protected open fun gripAt(pixel: Offset, sight: SurfaceView): PoseGrip? = handles?.gripAt(pixel, sight, look.reach, ::takes)

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = gestures(camera)

    override fun fullInput(view: FullView): Modifier = gestures(view)

    private fun gestures(sight: SurfaceView): Modifier = Modifier.pointerInput(this, sight) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            val grip = gripAt(down.position, sight)
            val drag = grab(grip, down.position, sight, currentEvent.keyboardModifiers.isShiftPressed) ?: return@awaitEachGesture
            down.consume()
            state.pose.held = grip
            var released = false
            try {
                released = followToRelease(down.id, {}) { pixel ->
                    val keys = currentEvent.keyboardModifiers
                    drag.move(pixel, keys.isAltPressed, keys.isShiftPressed)
                }
            } finally {
                drag.finish(released)
                state.pose.held = null
            }
        }
    }.then(hovering(sight))

    /** Follows the pointer over a pane without taking it, lighting the handle under it. */
    private fun hovering(sight: SurfaceView): Modifier = Modifier.pointerInput(this, sight, "hover") {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pixel = event.changes.firstOrNull()?.position ?: continue
                when (event.type) {
                    PointerEventType.Exit -> state.pose.hovered = null
                    PointerEventType.Enter, PointerEventType.Move -> if (event.changes.none { it.pressed }) state.pose.hovered = gripAt(pixel, sight)
                }
            }
        }
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay {
        val now = handles ?: return ViewportOverlay.Empty
        val lit = state.pose.lit
        return buildOverlay {
            poseHandles(now, sight, look, lit, state.pose.limb?.let(PoseGrip::End), ::shows)
            drawn(sight)
        }
    }
}

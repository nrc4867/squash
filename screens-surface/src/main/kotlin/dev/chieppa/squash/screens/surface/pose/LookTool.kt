package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.rig.SolveOptions
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.SurfaceView

/**
 * Look: a drag anywhere moves the look-at point, which the head turns to face with the neck taking a share, from the dot or, from
 * anywhere else, from under the pointer in the plane through the dot facing the viewer. One undo step a drag.
 */
class LookTool(state: AppState, look: PoseHandleLook) : PoseTool(state, look) {
    override fun takes(grip: PoseGrip): Boolean = grip == PoseGrip.Look

    override fun shows(grip: PoseGrip): Boolean = grip == PoseGrip.Look

    override fun grab(grip: PoseGrip?, pixel: Offset, sight: SurfaceView, shift: Boolean): PoseDrag? {
        val session = state.session.value ?: return null
        val solver = solver ?: return null
        val point = handles?.look ?: return null
        val shown = sight.toPixel(point) ?: return null
        val pose = session.document.value.character.pose
        val options = { _: Boolean, _: Boolean -> SolveOptions() }
        val from = if (grip == PoseGrip.Look) pixel else shown
        return SolvingDrag(session, pose, point, from, sight, options) { start, target, o -> solver.look(start, target, o) }.also {
            if (grip != PoseGrip.Look) it.move(pixel, alt = false, shift = false)
        }
    }
}

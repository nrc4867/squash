package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.SolveOptions
import dev.chieppa.squash.rig.Solved
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.core.History

/**
 * A drag that poses through [solve] from the pose it started on, [start], as the pointer moves [point] from [from]: each step a preview
 * on one open transaction of the session's history, so the drag is one undo step, written on release.
 */
internal class SolvingDrag(
    session: CharacterSession,
    private val start: Pose,
    private val point: Vec3,
    private val from: Offset,
    private val sight: SurfaceView,
    private val options: (alt: Boolean, shift: Boolean) -> SolveOptions,
    private val solve: (start: Pose, target: Vec3, options: SolveOptions) -> Solved,
) : PoseDrag {
    private val gesture: History<SquashDocument>.Transaction = session.history.begin()
    private var moved = false

    override fun move(pixel: Offset, alt: Boolean, shift: Boolean) {
        val target = sight.dragged(point, from, pixel) ?: return
        gesture.update(SetPose(solve(start, target, options(alt, shift)).pose))
        moved = true
    }

    override fun finish(keep: Boolean) {
        if (keep && moved) gesture.commit() else gesture.cancel()
    }
}

/**
 * Drag: a handle press moves what it holds. The body's ring moves the whole body over its planted paws; a paw's dot reaches with its
 * leg, lifting a planted paw; a pole swings its limb's knee; a tail's arc curls it; the look-at dot turns the head. Firm and Stretch,
 * or Alt and Shift held during the drag, stop the body where planted paws reach and let a limb stretch past its reach.
 */
class DragTool(state: AppState, look: PoseHandleLook) : PoseTool(state, look) {
    override fun takes(grip: PoseGrip): Boolean = grip !is PoseGrip.Ring

    override fun grab(grip: PoseGrip?, pixel: Offset, sight: SurfaceView, shift: Boolean): PoseDrag? {
        val session = state.session.value ?: return null
        val solver = solver ?: return null
        val handles = handles ?: return null
        val pose = session.document.value.character.pose
        val options = { alt: Boolean, held: Boolean -> SolveOptions(firm = state.pose.firm || alt, stretch = state.pose.stretch || held) }
        fun drag(point: Vec3?, start: Pose = pose, solve: (Pose, Vec3, SolveOptions) -> Solved): PoseDrag? =
            point?.let { SolvingDrag(session, start, it, pixel, sight, options, solve) }
        return when (grip) {
            PoseGrip.Body -> drag(handles.body) { start, target, o -> solver.moveBody(start, target - handles.body!!, o) }
            is PoseGrip.End -> {
                state.pose.limb = grip.limb
                val limb = solver.chains.limb(grip.limb) ?: return null
                drag(handles.ends[grip.limb], pose.withPlanted(limb.id, false)) { start, target, o -> solver.reach(start, limb, target, o) }
            }
            is PoseGrip.Pole -> {
                state.pose.limb = grip.limb
                val limb = solver.chains.limb(grip.limb) ?: return null
                drag(handles.poles[grip.limb]?.at) { start, target, _ -> solver.swing(start, limb, target) }
            }
            is PoseGrip.Curl -> {
                val curl = handles.curls.firstOrNull { it.tail.part == grip.tail } ?: return null
                drag(curl.tip) { start, target, _ -> solver.curl(start, curl.tail, target) }
            }
            PoseGrip.Look -> drag(handles.look) { start, target, o -> solver.look(start, target, o) }
            is PoseGrip.Ring, null -> null
        }
    }
}

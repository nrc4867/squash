package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetPlanted
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.SurfaceView

/** Takes a press and does nothing more with the drag that follows. */
internal object Pressed : PoseDrag {
    override fun move(pixel: Offset, alt: Boolean, shift: Boolean) = Unit

    override fun finish(keep: Boolean) = Unit
}

/** Pin: a click on a paw's dot, or on the paw or its leg, plants it where it stands or frees it, one step each. */
class PinTool(state: AppState, look: PoseHandleLook) : PoseTool(state, look) {
    override fun takes(grip: PoseGrip): Boolean = grip is PoseGrip.End

    override fun shows(grip: PoseGrip): Boolean = grip is PoseGrip.End || grip == PoseGrip.Body

    override fun grab(grip: PoseGrip?, pixel: Offset, sight: SurfaceView, shift: Boolean): PoseDrag? {
        val session = state.session.value ?: return null
        val limb = (grip as? PoseGrip.End)?.limb ?: limbUnder(pixel, sight) ?: return null
        state.pose.limb = limb
        session.send(SetPlanted(limb, limb !in session.document.value.character.pose.planted))
        return Pressed
    }

    private fun limbUnder(pixel: Offset, sight: SurfaceView): PartId? {
        val rig = rig ?: return null
        val posed = state.posing.value?.posed?.output?.value ?: return null
        val part = boneUnder(rig, posed, pixel, sight)?.part ?: return null
        return state.pose.solverFor(rig).chains.limbs.firstOrNull { it.paw == part || it.leg == part }?.id
    }
}

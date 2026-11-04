package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool

/** How many halvings find where the pointer crossed between two surfaces, which from a drag's longest step lands within a pixel. */
private const val CROSSING_STEPS = 8

/**
 * A tool working on the surface of the whole character, from a locked pane or a free one alike. A press on no surface is left to the
 * pane, so a free camera still orbits from the space around the character.
 */
abstract class SurfaceTool(protected val state: AppState) : PaneTool {
    override val takesFreeCamera: Boolean get() = true
    override val showsWhole: Boolean get() = true
    override val showsPreviews: Boolean get() = true

    /** Takes a press on [hit] at [pixel], following it until it lets go. Returns once the gesture is over. */
    protected abstract suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: PaneSight)

    /** Takes a press near what the tool shows, ahead of the surface under it; true when it took it. */
    protected open suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: PaneSight): Boolean = false

    /** The place under [pixel] on the open character, or null. */
    protected fun hitAt(pixel: Offset, sight: PaneSight): SurfaceHit? =
        state.session.value?.assembly?.output?.value?.let { surfaceHit(it, pixel, sight) }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = gestures(camera, LockedSight(camera))

    override fun fullInput(view: FullView): Modifier = gestures(view, FullSight(view))

    private fun gestures(key: Any, sight: PaneSight): Modifier = Modifier.pointerInput(this, key) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!isToolPress(down)) return@awaitEachGesture
            if (pressedOverlay(down, sight)) return@awaitEachGesture
            val hit = hitAt(down.position, sight) ?: return@awaitEachGesture
            down.consume()
            pressed(hit, down, sight)
        }
    }

    /**
     * The places either side of where the pointer crossed from [from], at [fromPixel], to [to], at [toPixel], on another part or patch, so a stroke
     * or border meets the crossing however fast the pointer went; nothing when both lie on one surface.
     */
    protected fun crossingBetween(from: SurfaceHit, fromPixel: Offset, to: SurfaceHit, toPixel: Offset, sight: PaneSight): List<SurfaceHit> {
        if (from.part == to.part && from.patch == to.patch) return emptyList()
        var nearHit = from
        var nearPixel = fromPixel
        var farHit = to
        var farPixel = toPixel
        repeat(CROSSING_STEPS) {
            val middle = (nearPixel + farPixel) / 2f
            val hit = hitAt(middle, sight) ?: return@repeat
            if (hit.part == from.part && hit.patch == from.patch) {
                nearHit = hit
                nearPixel = middle
            } else {
                farHit = hit
                farPixel = middle
            }
        }
        return listOf(nearHit, farHit).filter { it !== from && it !== to }
    }
}

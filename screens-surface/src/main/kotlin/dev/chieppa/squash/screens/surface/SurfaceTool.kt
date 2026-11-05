package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How many halvings find where the pointer crossed between two surfaces, which from a drag's longest step lands within a pixel. */
private const val CROSSING_STEPS = 8

/** How far the pointer moves on screen before a drag takes another place on the surface, in pixels. */
private const val ANCHOR_SPACING = 4f

/**
 * A tool working on the surface of the whole character, from a locked pane or a free one alike. A press on no surface is left to the
 * pane, so a free camera still orbits from the space around the character.
 */
abstract class SurfaceTool(protected val state: AppState) : PaneTool {
    override val takesFreeCamera: Boolean get() = true
    override val showsWhole: Boolean get() = true

    /** Whether a mark drawn on [part] shows on both sides of the body's middle, where Mirror is on and the part lies in the middle. */
    protected fun mirrorsOn(character: dev.chieppa.squash.document.Character, part: dev.chieppa.squash.document.PartId): Boolean =
        state.mirror.value && character.part(part)?.let { it.pair == null && it.side == null } == true

    /** Takes a press on [hit] at [pixel], following it until it lets go. Returns once the gesture is over. */
    protected abstract suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView)

    /** Takes a press near what the tool shows, ahead of the surface under it; true when it took it. */
    protected open suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean = false

    /** The place under [pixel] on the open character, or null. */
    protected open fun hitAt(pixel: Offset, sight: SurfaceView): SurfaceHit? =
        state.session.value?.assembly?.output?.value?.let { surfaceHit(it, pixel, sight) }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = gestures(camera, camera)

    override fun fullInput(view: FullView): Modifier = gestures(view, view)

    private fun gestures(key: Any, sight: SurfaceView): Modifier = Modifier.pointerInput(this, key) {
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
     * Follows [down], which pressed [hit], until it lets go, handing [taken] each place the drag reaches a spacing on, with those either side of a
     * crossing, so what it lays down meets the crossing. A place off the surface is passed over. True when the pointer let go in the pane.
     */
    protected suspend fun AwaitPointerEventScope.followSurface(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView, onMove: (PointerInputChange) -> Unit = {}, taken: (SurfaceHit) -> Unit): Boolean {
        var last = down.position
        var lastHit = hit
        return followToRelease(down.id, onMove) { pixel ->
            if ((pixel - last).getDistance() < ANCHOR_SPACING) return@followToRelease
            val next = hitAt(pixel, sight) ?: return@followToRelease
            crossingBetween(lastHit, last, next, pixel, sight).forEach(taken)
            last = pixel
            lastHit = next
            taken(next)
        }
    }

    /**
     * The places either side of where the pointer crossed from [from], at [fromPixel], to [to], at [toPixel], on another part or patch, so a stroke
     * or border meets the crossing however fast the pointer went; nothing when both lie on one surface.
     */
    protected fun crossingBetween(from: SurfaceHit, fromPixel: Offset, to: SurfaceHit, toPixel: Offset, sight: SurfaceView): List<SurfaceHit> {
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

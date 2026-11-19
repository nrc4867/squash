package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.penPressure
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceTool
import dev.chieppa.squash.screens.surface.keptAcross
import dev.chieppa.squash.screens.surface.pressureWidth
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** How far apart on screen a print's places are taken while drawing it, in pixels. */
private const val TAKE_SPACING = 3f

/** How far a kept place's curve may stray from what was drawn, in pixels. */
private const val KEEP_WITHIN = 2.5f

/**
 * A tool drawing on the picked garment's cloth, as the Surface stage's region and line tools draw on the skin: a press finds the cloth in
 * front of the body and anchors what it draws by the body's part, u and v under it, which the cloth's vertices carry, so a print rides
 * the cloth's fit and pose. With no garment picked, or off its cloth, a press is left to the pane.
 */
abstract class PrintTool(state: AppState, internal var look: SheetLook) : SurfaceTool(state) {
    protected val drawn = mutableStateListOf<SurfaceHit>()

    override fun hitAt(pixel: Offset, sight: SurfaceView): SurfaceHit? = state.dress.garment.value?.let { state.garmentHit(it, pixel, sight) }

    /** Follows [down], pressed on [hit], until it lets go, taking a place each [TAKE_SPACING] pixels; true when it let go in the pane. */
    protected suspend fun AwaitPointerEventScope.drawFrom(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView, onMove: (PointerInputChange) -> Unit = {}): Boolean {
        drawn.clear()
        drawn += hit
        var last = down.position
        return followToRelease(down.id, onMove) { pixel ->
            if ((pixel - last).getDistance() < TAKE_SPACING) return@followToRelease
            val next = hitAt(pixel, sight) ?: return@followToRelease
            drawn += crossingBetween(drawn.last(), last, next, pixel, sight)
            last = pixel
            drawn += next
        }
    }

    /** What was drawn, thinned to the places its curve needs. */
    protected fun kept(sight: SurfaceView) = keptAcross(drawn, sight, KEEP_WITHIN).map { it.anchor }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = shown(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = shown(view)

    private fun shown(sight: SurfaceView): ViewportOverlay = buildOverlay {
        drawn.mapNotNull { sight.toPixel(it.position) }.zipWithNext().forEach { (a, b) -> line(a, b, look.ring, look.ringWidth) }
    }
}

/**
 * Print region: a drag draws a closed border on the picked garment's cloth, however many parts it crosses, as a region printed in the
 * print color, its border inked where Ink border is on, on both sides of the body where Mirror is on.
 */
@Stable
class PrintRegionTool(state: AppState, look: SheetLook) : PrintTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val picked = state.pickedGarment() ?: return
        val session = state.session.value ?: return
        val slot = state.dress.printSlot.value ?: picked.garment.color ?: return
        try {
            if (!drawFrom(hit, down, sight)) return
            printRegionCommand(picked.garment, kept(sight), slot, state.dress.printInk.value, state.mirror.value)?.let(session::send)
        } finally {
            drawn.clear()
        }
    }
}

/**
 * Print line: a drag draws a line on the picked garment's cloth in the print color, the ink's where none is picked, at the print weight,
 * which a pen's pressure shapes along it, on both sides of the body where Mirror is on.
 */
@Stable
class PrintMarkTool(state: AppState, look: SheetLook) : PrintTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val picked = state.pickedGarment() ?: return
        val session = state.session.value ?: return
        var pressure = penPressure(down)
        val pressures = arrayListOf(pressure)
        try {
            val released = drawFrom(hit, down, sight) {
                pressure = penPressure(it)
                pressures += pressure
            }
            if (!released) return
            val width = pressureWidth(pressures)
            printMarkCommand(picked.garment, kept(sight), state.dress.printSlot.value, state.dress.printWeight.value, width, state.mirror.value)?.let(session::send)
        } finally {
            drawn.clear()
        }
    }
}

@Composable
fun rememberPrintRegionTool(state: AppState): PrintRegionTool {
    val look = rememberSheetLook()
    return remember(state) { PrintRegionTool(state, look) }.also { it.look = look }
}

@Composable
fun rememberPrintMarkTool(state: AppState): PrintMarkTool {
    val look = rememberSheetLook()
    return remember(state) { PrintMarkTool(state, look) }.also { it.look = look }
}

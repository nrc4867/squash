package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chieppa.squash.document.View
import dev.chieppa.stretch.frame.ViewportOverlay

/**
 * A tool working in the locked panes. Each pane hands it its [LockedCamera], which stays the same object until the sheet
 * fit or the panes' size changes, and takes its pointer input and overlay. Pointer positions reach the tool in pane
 * pixels, which [LockedCamera.toPlane] and [LockedCamera.toPart] turn into part space.
 */
@Stable
interface PaneTool {
    /** The pointer input for [view]'s pane. */
    fun pointerInput(view: View, camera: LockedCamera): Modifier

    /** What the tool draws over [view]'s pane, in pane pixels, clipped to the pane. Compose state read here redraws it. */
    fun overlay(view: View, camera: LockedCamera): ViewportOverlay = ViewportOverlay.Empty

    /** The view a stroke is in progress in, which shows as Drawing. */
    val drawingIn: View? get() = null
}

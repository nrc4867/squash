package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.stretch.frame.ViewportOverlay

/**
 * A stroke in progress in [view], as the points of that view's plane it has passed through. With [preview], [points] are the
 * outline release will write, around the line [centre] drawn to make it.
 */
class LiveStroke(val view: View, val points: List<CurvePoint>, val preview: Boolean = false, val centre: List<CurvePoint> = emptyList())

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

    /** The pointer input for the Full pane, under its orbit. */
    fun fullInput(view: FullView): Modifier = Modifier

    /** What the tool draws over the Full pane, in pane pixels. Compose state read here redraws it. */
    fun fullOverlay(view: FullView): ViewportOverlay = ViewportOverlay.Empty

    /** The stroke in progress, which the panes compare with the other views. Compose state read here redraws them. */
    val liveStroke: LiveStroke? get() = null

    /** The outlines of other views the stroke in progress replaces as it goes, which therefore hold it to nothing. Compose state read here redraws them. */
    val liveAlongside: List<LiveStroke> get() = emptyList()

    /** The view a stroke is in progress in, which shows as Drawing. */
    val drawingIn: View? get() = liveStroke?.view

    /** How far [view]'s outline strays from the stroke it was fitted to, in screen pixels, when it was fitted here. */
    fun fitErrorPixels(view: View): Float? = null

    /** Whether the panes hold their cameras still, because a drag is moving the part under them. */
    val holdsFit: Boolean get() = false
}

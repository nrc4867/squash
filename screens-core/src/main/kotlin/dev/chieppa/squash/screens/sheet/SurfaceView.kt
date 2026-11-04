package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.geom.Vec3

/** Any camera on body space, as a tool or a line reads it, in the pixels of the pane it fills. */
interface SurfaceView {
    /** The pane pixel showing [point], or null behind the camera. */
    fun toPixel(point: Vec3): Offset?

    /** From [point] towards the viewer. */
    fun toViewer(point: Vec3): Vec3

    /** How far from the viewer [point] lies; a nearer point is less. */
    fun depthOf(point: Vec3): Float

    /** How many pixels a body unit spans at [point]. */
    fun pixelsPerUnitAt(point: Vec3): Float
}

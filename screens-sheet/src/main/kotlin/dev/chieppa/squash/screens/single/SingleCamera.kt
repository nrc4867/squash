package dev.chieppa.squash.screens.single

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.SheetFit
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.fitSheet
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.OrbitCamera
import kotlin.math.tan
import dev.chieppa.stretch.gpu.Vec3 as GpuVec3

/** The view Single opens on. */
private val FIRST_VIEW = View.SIDE

/** How close and far the camera goes, wider than the default since a small part fills the view from close by. */
private val DISTANCE_RANGE = 0.05f..200f

/** The pixels per unit a perspective camera [distance] away shows at what it looks at, on a view [height] pixels tall. */
fun pixelsPerUnit(distance: Float, height: Int, fovY: Float): Float = height / (2f * distance * tan(fovY / 2f))

/**
 * Single's one camera. [orbit] moves freely, and near a part's view [snap] names that view, which shows through a
 * [LockedCamera] with the orbit's own target and scale, so zoom and pan carry across a snap. A snap changes the picture at
 * once: moving to it over time would need a timer.
 */
@Stable
class SingleCamera {
    var orbit by mutableStateOf(OrbitCamera(distanceRange = DISTANCE_RANGE))
        private set

    private var snapped: View? = null
    private var held: LockedCamera? = null
    private var opened = false

    /** Aims at [box], fitted to a [width] by [height] view as a Sheet pane fits it, keeping the direction; the first time, on [FIRST_VIEW] of [axes]. */
    fun frame(box: BodyBox, axes: PartAxes, width: Int, height: Int) {
        val fit = fitSheet(box, axes, List(SheetPane.entries.size) { PixelRect(0, 0, width, height) })
        orbit.lookAt(box.center.toGpu(), height / (2f * fit.pixelsPerUnit * tan(orbit.fovY / 2f)))
        if (!opened) snapTo(FIRST_VIEW, axes)
        opened = true
    }

    /** Turns the camera to look along [view] of [axes]. */
    fun snapTo(view: View, axes: PartAxes) {
        val angles = orbitAngles(axes.viewDirection(view), axes.basis(view).screenUp)
        orbit = OrbitCamera(orbit.target, angles.yaw, angles.pitch, orbit.distance, orbit.distanceRange, orbit.fovY)
    }

    /** The view the camera is on now, null when free. [hold] keeps it, while a stroke or a drag is under way. */
    fun snap(axes: PartAxes, hold: Boolean): View? {
        if (!hold) snapped = snappedView(snapped, toViewer(), axes)
        return snapped
    }

    private fun toViewer(): Vec3 = (orbit.eye - orbit.target).toBody().normalized()

    /** [view]'s camera for a [width] by [height] view, the same object until something it holds changes. [hold] keeps it still whatever changes. */
    fun lockedCamera(view: View, selection: SheetSelection, width: Int, height: Int, hold: Boolean): LockedCamera {
        val current = held
        if (hold && current?.view == view) return current
        val part = selection.part
        val center = orbit.target.toBody()
        val reach = (selection.box.center - center).length + selection.box.radius
        val fit = SheetFit(center, pixelsPerUnit(orbit.distance, height, orbit.fovY), reach)
        if (current != null && current.view == view && current.fit == fit && current.axes == part.axes &&
            current.origin == part.origin && current.drawingScale == selection.drawingScale && current.lengthFactor == part.lengthFactor &&
            current.width == width && current.height == height
        ) {
            return current
        }
        return LockedCamera(view, part.axes, fit, width, height, part.origin, selection.drawingScale, part.lengthFactor).also { held = it }
    }
}

private fun GpuVec3.toBody() = Vec3(x, y, z)

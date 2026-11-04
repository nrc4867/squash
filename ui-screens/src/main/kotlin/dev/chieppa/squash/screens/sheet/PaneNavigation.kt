package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.stretch.frame.twoFingerGestures
import kotlin.math.exp

private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 16f
private const val WHEEL_ZOOM = 0.15f

/** How far a locked pane is from showing the fit: [zoom] times the fit's scale, its middle moved by [offset] in body space. */
data class PaneNav(val zoom: Float = 1f, val offset: Vec3 = Vec3.ZERO) {
    /** [fit] as a pane with this navigation shows it. */
    fun applied(fit: SheetFit): SheetFit = fit.copy(center = fit.center + offset, pixelsPerUnit = fit.pixelsPerUnit * zoom)

    /** This navigation with the picture moved by [delta] pixels in [camera], the camera it shows through. */
    fun panned(delta: Offset, camera: LockedCamera): PaneNav {
        val scale = camera.fit.pixelsPerUnit
        return copy(offset = offset - camera.basis.screenRight * (delta.x / scale) + camera.basis.screenUp * (delta.y / scale))
    }

    /** This navigation zoomed by [factor], within limits, keeping what shows at [pixel] of [camera] where it is. */
    fun zoomed(factor: Float, pixel: Offset, camera: LockedCamera): PaneNav {
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val scale = camera.fit.pixelsPerUnit
        val fromMiddle = pixel - Offset(camera.width / 2f, camera.height / 2f)
        val toPointer = camera.basis.screenRight * (fromMiddle.x / scale) - camera.basis.screenUp * (fromMiddle.y / scale)
        return PaneNav(next, offset + toPointer * (1f - zoom / next))
    }
}

/** Each locked pane's navigation, by the view it holds. Compose state, so the panes follow it. */
@Stable
class PaneNavigation {
    private var navs by mutableStateOf(emptyMap<View, PaneNav>())

    operator fun get(view: View): PaneNav = navs[view] ?: PaneNav()

    fun update(view: View, change: (PaneNav) -> PaneNav) {
        navs = navs + (view to change(this[view]))
    }

    /** Shows [view]'s pane at the fit again. */
    fun fit(view: View) {
        navs = navs - view
    }

    fun fitAll() {
        navs = emptyMap()
    }
}

/**
 * A locked pane's own zoom and pan: the wheel or a pinch zooms about the pointer, a middle drag or two fingers pan. It sits
 * over the tool's input and leaves left presses to it, and does nothing while [held], since a stroke holds its camera still.
 */
internal fun Modifier.paneNavigation(navigation: PaneNavigation, camera: LockedCamera, held: Boolean): Modifier = composed {
    val current by rememberUpdatedState(camera)
    val hold by rememberUpdatedState(held)
    val view = camera.view
    Modifier
        .twoFingerGestures(
            onPan = { delta -> if (!hold) navigation.update(view) { it.panned(delta, current) } },
            onZoom = { factor, focus -> if (!hold) navigation.update(view) { it.zoomed(factor, focus, current) } },
            onTwist = { _, _ -> },
        )
        .pointerInput(navigation, view) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (hold) continue
                    when {
                        event.type == PointerEventType.Scroll -> {
                            val change = event.changes.first()
                            val factor = exp(-change.scrollDelta.y * WHEEL_ZOOM)
                            navigation.update(view) { it.zoomed(factor, change.position, current) }
                            change.consume()
                        }
                        event.buttons.isTertiaryPressed && event.changes.any { it.type == PointerType.Mouse } -> {
                            event.changes.forEach { change ->
                                if (change.previousPressed) navigation.update(view) { it.panned(change.position - change.previousPosition, current) }
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
}

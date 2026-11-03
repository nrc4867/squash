package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.polyline
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** The colors and pixel sizes of a stroke in progress. */
data class StrokeLook(val ink: Color, val width: Float, val tip: Color, val tipRadius: Float, val tipRing: Float)

/** The live stroke's drawing in the current theme and pixel density. */
@Composable
internal fun rememberStrokeLook(): StrokeLook {
    val c = Stretch.colors
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return StrokeLook(ink = c.inkStrong, width = px(7), tip = c.accent, tipRadius = px(8), tipRing = px(3))
}

/**
 * Draw outline: a stroke in a locked pane is previewed as it grows and, when it ends, replaces that view's outline on the
 * selected part with one [SetOutline], so one stroke is one undo step. A stroke the pane loses midway sends nothing.
 */
class DrawOutlineTool(private val state: AppState, var look: StrokeLook) : PaneTool {
    private var strokeView by mutableStateOf<View?>(null)
    private val strokePoints = mutableStateListOf<CurvePoint>()

    override val liveStroke: LiveStroke?
        get() = strokeView?.let { LiveStroke(it, strokePoints.toList()) }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!startsStroke(down)) return@awaitEachGesture
            down.consume()
            strokeView = view
            add(camera.toPlane(down.position))
            try {
                if (followToRelease(down.id, camera)) finish(view, camera)
            } finally {
                strokeView = null
                strokePoints.clear()
            }
        }
    }

    private fun AwaitPointerEventScope.startsStroke(down: PointerInputChange): Boolean = when {
        strokeView != null || state.selectedPart.value == null -> false
        down.type == PointerType.Eraser -> false
        down.type == PointerType.Mouse -> currentEvent.buttons.isPrimaryPressed
        else -> true
    }

    /** Collects the stroke until its pointer lets go, which returns true, or goes away, which returns false. */
    @OptIn(ExperimentalComposeUiApi::class)
    private suspend fun AwaitPointerEventScope.followToRelease(id: PointerId, camera: LockedCamera): Boolean {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == id } ?: return false
            change.historical.forEach { add(camera.toPlane(it.position)) }
            add(camera.toPlane(change.position))
            change.consume()
            val released = if (change.type == PointerType.Mouse) !event.buttons.isPrimaryPressed else !change.pressed
            if (released) return true
        }
    }

    private fun add(point: CurvePoint) {
        if (strokePoints.lastOrNull() != point) strokePoints += point
    }

    private fun finish(view: View, camera: LockedCamera) {
        val session = state.session.value ?: return
        val part = state.selectedPart.value ?: return
        val instance = session.document.value.character.part(part) ?: return
        val ends = strokeEnds(view, session.templates[instance.template].openEnd)
        val fitted = fittedOutline(strokePoints.toList(), ends, 1f / camera.fit.pixelsPerUnit) ?: return
        session.send(SetOutline(part, view, fitted.curve))
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val stroke = liveStroke ?: return ViewportOverlay.Empty
        if (stroke.view != view) return ViewportOverlay.Empty
        return buildOverlay {
            polyline(stroke.points, camera, look.ink, look.width)
            val tip: Offset = camera.toPixel(stroke.points.last())
            dot(tip, look.tipRadius + look.tipRing, look.ink)
            dot(tip, look.tipRadius, look.tip)
        }
    }
}

/** The tool for [state], kept across compositions and following the theme. */
@Composable
fun rememberDrawOutlineTool(state: AppState): DrawOutlineTool {
    val look = rememberStrokeLook()
    return remember(state) { DrawOutlineTool(state, look) }.also { it.look = look }
}

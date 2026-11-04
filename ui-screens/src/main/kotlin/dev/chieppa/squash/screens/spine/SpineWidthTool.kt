package dev.chieppa.squash.screens.spine

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.SetOutlines
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.SpineSample
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.draw.penPressure
import dev.chieppa.squash.screens.draw.strokeEnds
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

private fun List<Vec2>.inPlane() = map { CurvePoint(it.x, it.y) }

private fun CurvePoint.vec() = Vec2(x, y)

/**
 * Spine and width: a centre stroke in Side, its thickness set by a pen's pressure as it draws or by a drag across it after, and
 * on release the Side and Top outlines through one [SetOutlines], so one step to undo. A pen's release writes; a mouse's stroke
 * waits for its drag, whose release writes, and a press clear of the shape starts over. The spine is not kept: what remains
 * are ordinary curves. Only Side takes a stroke, since the width's reference is the spine drawn there.
 */
class SpineWidthTool(private val state: AppState, var look: SpineLook) : PaneTool {
    private val spine get() = state.spine

    override val holdsFit: Boolean get() = spine.active

    override val drawingIn: View? get() = if (spine.active) View.SIDE else null

    override val liveStroke: LiveStroke?
        get() = spine.preview?.let { LiveStroke(View.SIDE, it.shape.side.inPlane(), preview = true, centre = it.shape.line.points.inPlane()) }

    override val liveAlongside: List<LiveStroke>
        get() = spine.preview?.let { listOf(LiveStroke(View.TOP, it.shape.top.inPlane(), preview = true)) }.orEmpty()

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(state.editedOutline(view))

    /** Drops a stroke in hand, as when the tool or the part changes. */
    fun discard() {
        spine.clear()
        state.curves.drawing = null
    }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier {
        if (view != View.SIDE) return Modifier
        return Modifier.pointerInput(this, camera) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (state.selectedPart.value == null || !isToolPress(down)) return@awaitEachGesture
                down.consume()
                val at = camera.toPlane(down.position).vec()
                if (penPressure(down) == null && spine.pressWidth(at, look.curve.grabReach * spine.pixel)) dragWidth(down, camera) else stroke(down, camera)
            }
        }
    }

    private suspend fun AwaitPointerEventScope.dragWidth(down: PointerInputChange, camera: LockedCamera) {
        val released = followToRelease(down.id, {}) { position: Offset ->
            if (position != down.position) spine.dragWidth(camera.toPlane(position).vec())
        }
        if (released) finish()
    }

    private suspend fun AwaitPointerEventScope.stroke(down: PointerInputChange, camera: LockedCamera) {
        val part = state.selectedPart.value ?: return
        val session = state.session.value ?: return
        val instance = session.document.value.character.part(part) ?: return
        spine.begin(part, strokeEnds(View.SIDE, session.templates[instance.template].openEnd), camera.planePixel)
        state.curves.drawing = View.SIDE
        var pressure = penPressure(down)
        state.curves.penPressure = pressure
        spine.add(SpineSample(camera.toPlane(down.position).vec(), pressure))
        var kept = false
        try {
            val released = followToRelease(
                down.id,
                {
                    pressure = penPressure(it)
                    state.curves.penPressure = pressure
                },
            ) { spine.add(SpineSample(camera.toPlane(it).vec(), pressure)) }
            spine.endStroke()
            kept = released && spine.preview != null
            if (kept && spine.byPen) finish()
        } finally {
            state.curves.penPressure = null
            if (!kept) discard()
        }
    }

    /** Writes what the preview shows, as one step, and lets the stroke go. */
    private fun finish() {
        val session = state.session.value
        val part = spine.part
        val shape = spine.preview?.shape
        val pixel = spine.pixel
        if (session != null && part != null && shape != null && session.document.value.character.part(part) != null) {
            val fit = shape.fitted(pixel)
            val side = fit.side.toOutline()
            val top = fit.top.toOutline()
            state.curves.recordFit(side, fit.sideError / pixel)
            state.curves.recordFit(top, fit.topError / pixel)
            state.curves.selection = null
            state.curves.refit = null
            session.send(SetOutlines(part, mapOf(View.SIDE to side, View.TOP to top), state.thisSide))
        }
        discard()
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val preview = spine.preview ?: return ViewportOverlay.Empty
        return when (view) {
            View.SIDE -> buildOverlay {
                sidePreview(preview, camera, look, spine.ends as? StrokeEnds.CutAtPlane)
                if (!spine.stroking && !spine.byPen) widthHandle(preview.shape.across(spine.handleAt), spine.handleLeft, camera, look)
            }
            View.TOP -> buildOverlay { topPreview(preview, camera, look) }
            View.FRONT -> ViewportOverlay.Empty
        }
    }
}

/** The tool for [state], kept across compositions and following the theme. */
@Composable
fun rememberSpineWidthTool(state: AppState): SpineWidthTool {
    val look = rememberSpineLook()
    return remember(state) { SpineWidthTool(state, look) }.also { it.look = look }
}

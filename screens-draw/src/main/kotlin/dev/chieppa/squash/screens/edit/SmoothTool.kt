package dev.chieppa.squash.screens.edit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.toOutline
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.replacedSections
import dev.chieppa.squash.geom.BrushedPath
import dev.chieppa.squash.geom.SmoothBrush
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.stretchesUnderBrush
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.draw.isToolRelease
import dev.chieppa.squash.screens.draw.strokeEnds
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.shownCurve
import dev.chieppa.squash.screens.sheet.storedCurve
import dev.chieppa.squash.screens.sheet.storedPoint
import dev.chieppa.squash.screens.sheet.toShown
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** The limits of the Smooth brush's width slider, in screen pixels. */
const val MIN_BRUSH_SIZE = 10f
const val MAX_BRUSH_SIZE = 200f

private const val RADIUS_OF_SIZE = 0.5f

/** Where a pointer rests over the panes. */
private class BrushSpot(val view: View, val pixel: Offset)

/** Where in the document a drag is working. */
private class SmoothTarget(val session: CharacterSession, val part: PartId, val view: View, val curve: OutlineCurve, val thisSide: Boolean, val template: PartTemplate)

/**
 * Smooth: a ring follows the pointer over a pane with an outline, with the stretch under it dashed as affected. A drag calms
 * that stretch as the document follows through one gesture on the history, so the panes and the mesh show the live curve and
 * the drag is one undo step, on the pair or with Mirror off on that side. A drag the pane loses restores the curve.
 */
class SmoothTool(private val state: AppState, var look: CurveLook) : PaneTool {
    private var dragging by mutableStateOf(false)
    private var spot by mutableStateOf<BrushSpot?>(null)

    override val holdsFit: Boolean get() = dragging

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(state.editedOutline(view))

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier
        .pointerInput(this, view) { followSpot(view) }
        .pointerInput(this, view, camera) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (dragging || !isToolPress(down)) return@awaitEachGesture
                val target = target(view) ?: return@awaitEachGesture
                down.consume()
                drag(target, down, camera)
            }
        }

    /** Keeps [spot] under the pointer while it is in [view]'s pane, drag or no drag. */
    private suspend fun PointerInputScope.followSpot(view: View) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val at = event.changes.firstOrNull()?.position
                    spot = if (event.type == PointerEventType.Exit || at == null) null else BrushSpot(view, at)
                }
            }
        } finally {
            if (spot?.view == view) spot = null
        }
    }

    private fun target(view: View): SmoothTarget? {
        val session = state.session.value ?: return null
        val part = state.selectedPart.value ?: return null
        val instance = session.document.value.character.part(part) ?: return null
        val curve = state.editedOutline(view) ?: return null
        return SmoothTarget(session, part, view, curve, state.thisSide, session.templates[instance.template])
    }

    /** Follows [down] until it lets go, which commits what it smoothed, or goes away, which restores the start. */
    private suspend fun AwaitPointerEventScope.drag(target: SmoothTarget, down: PointerInputChange, camera: LockedCamera) {
        val pixel = camera.planePixel
        val brush = SmoothBrush(state.curves.brushSize * RADIUS_OF_SIZE * pixel, state.curves.brushStrength)
        val ends = strokeEnds(target.view, target.template.openEnd)
        var from = camera.toShown(down.position).vec()
        var brushed = BrushedPath(camera.shownCurve(target.curve).toPath(), ends, pixel).dab(from, brush)
        var gesture: History<SquashDocument>.Transaction? = null
        var last = target.curve
        var released = false
        fun show() {
            val curve = camera.storedCurve(brushed.path.toOutline(), target.curve)
            if (curve == last) return
            last = curve
            val open = gesture ?: target.session.history.begin().also { gesture = it }
            open.update(SetOutline(target.part, target.view, curve, target.thisSide))
        }
        dragging = true
        try {
            show()
            while (!released) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return
                change.consume()
                released = isToolRelease(event, change)
                (change.historical.map { it.position } + change.position).forEach { position ->
                    val to = camera.toShown(position).vec()
                    brushed = brushed.drag(from, to, brush)
                    from = to
                }
                show()
            }
        } finally {
            dragging = false
            val open = gesture
            when {
                open == null -> Unit
                released && last != target.curve -> {
                    open.commit()
                    record(target, last)
                }
                else -> open.cancel()
            }
        }
    }

    private fun record(target: SmoothTarget, curve: OutlineCurve) {
        val sections = target.curve.replacedSections(curve) ?: return
        state.curves.selection = null
        state.curves.refit = Refit(
            target.part,
            target.view,
            curve,
            sections.first,
            sections.last - sections.first + 1,
            refitReach(target.view, target.curve, curve, target.template),
        )
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val at = spot?.takeIf { it.view == view }?.pixel ?: return ViewportOverlay.Empty
        val outline = state.editedOutline(view) ?: return ViewportOverlay.Empty
        val radius = state.curves.brushSize * RADIUS_OF_SIZE
        val pixel = camera.planePixel
        return buildOverlay {
            stretchesUnderBrush(camera.shownCurve(outline).toPath(), camera.toShown(at).vec(), radius * pixel, pixel).forEach { stretch ->
                affected(stretch.map { camera.storedPoint(CurvePoint(it.x, it.y)) }, camera, look)
            }
            ring(at, radius, look.ring, look.handleWidth)
        }
    }
}

private fun CurvePoint.vec() = Vec2(x, y)

/** The tool for [state], kept across compositions and following the theme. */
@Composable
fun rememberSmoothTool(state: AppState): SmoothTool {
    val look = rememberCurveLook()
    return remember(state) { SmoothTool(state, look) }.also { it.look = look }
}

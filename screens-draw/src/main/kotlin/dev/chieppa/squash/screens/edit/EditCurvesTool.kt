package dev.chieppa.squash.screens.edit

import dev.chieppa.squash.document.DocumentCommand
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CurveLimit
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.changedSections
import dev.chieppa.squash.document.isAnchor
import dev.chieppa.squash.document.moved
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.draw.isToolRelease
import dev.chieppa.squash.screens.draw.strokeEnds
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay

/** Where in the document a drag is working. */
private class DragTarget(val session: CharacterSession, val part: PartId, val view: View, val curve: OutlineCurve, val index: Int, val limit: CurveLimit?, val write: (View, OutlineCurve?) -> DocumentCommand?, val template: PartTemplate)

/**
 * Edit curves: a locked pane shows the stored outline's anchors and handles, and a press on one selects it and drags it.
 * The document follows the drag through one gesture on the history, so the panes, the mesh and the disagree highlight show
 * the live curve, and the drag is one undo step. A press on nothing clears the selection. A drag the pane loses restores
 * the curve.
 */
class EditCurvesTool(private val state: AppState, var look: CurveLook) : PaneTool {
    override val holdsFit: Boolean get() = state.curves.dragging

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(outline(view))

    private fun outline(view: View): OutlineCurve? = state.editedOutline(view)

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (state.curves.dragging || !isToolPress(down)) return@awaitEachGesture
            val target = pressed(view, camera, down.position) ?: return@awaitEachGesture
            down.consume()
            state.curves.selection = CurveSelection(target.part, target.view, target.index)
            drag(target, down.id, camera, down.position)
        }
    }

    /** The point under [pixel], or null after clearing the selection when there is none. */
    private fun pressed(view: View, camera: LockedCamera, pixel: Offset): DragTarget? {
        val session = state.session.value ?: return null
        val part = state.selectedPart.value ?: return null
        val instance = session.document.value.character.part(part) ?: return null
        val curve = state.editedOutline(view) ?: return null
        val selected = state.curves.selectionIn(part, view, curve)?.index
        val index = curve.pointAt(pixel, look.grabReach, selected, camera::toPixel)
        if (index == null) {
            state.curves.selection = null
            return null
        }
        val limit = (strokeEnds(view, session.builtTemplate(instance).openEnd) as? StrokeEnds.CutAtPlane)
            ?.let { CurveLimit(it.plane, it.above) }
        return DragTarget(session, part, view, curve, index, limit, state.outlineWriter() ?: return null, session.builtTemplate(instance))
    }

    /** Follows [id] until it lets go, which commits what it moved, or goes away, which restores the start. */
    private suspend fun AwaitPointerEventScope.drag(target: DragTarget, id: PointerId, camera: LockedCamera, from: Offset) {
        val start = target.curve.points[target.index]
        val grabbed = camera.toPlane(from)
        val anchor = target.curve.owningAnchor(target.index)
        val corner = !isAnchor(target.index) && state.curves.isMarkedCorner(CurveSelection(target.part, target.view, anchor))
        var gesture: History<SquashDocument>.Transaction? = null
        var last = target.curve
        var released = false
        state.curves.dragging = true
        try {
            while (!released) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: return
                change.consume()
                released = isToolRelease(event, change)
                if (released || change.position == change.previousPosition) continue
                val plane = camera.toPlane(change.position)
                val to = CurvePoint(start.x + plane.x - grabbed.x, start.y + plane.y - grabbed.y)
                last = target.curve.moved(target.index, to, target.limit, corner)
                val open = gesture ?: target.session.history.begin().also { gesture = it }
                target.write(target.view, last)?.let(open::update)
            }
        } finally {
            state.curves.dragging = false
            val open = gesture
            when {
                open == null -> Unit
                released && last != target.curve -> {
                    open.commit()
                    recordDrag(target, last)
                }
                else -> open.cancel()
            }
        }
    }

    private fun recordDrag(target: DragTarget, curve: OutlineCurve) {
        val sections = target.curve.changedSections(curve) ?: return
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
        val curve = outline(view)?.takeIf { view == state.activeView.value } ?: return ViewportOverlay.Empty
        val part = state.selectedPart.value ?: return ViewportOverlay.Empty
        return curvePoints(curve, state.curves.selectionIn(part, view, curve)?.index, camera, look)
    }
}

/** The tool for [state], kept across compositions and following the theme. */
@Composable
fun rememberEditCurvesTool(state: AppState): EditCurvesTool {
    val look = rememberCurveLook()
    return remember(state) { EditCurvesTool(state, look) }.also { it.look = look }
}

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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.oversketchedOutline
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.sections
import dev.chieppa.squash.geom.OversketchPreview
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.CurveLook
import dev.chieppa.squash.screens.edit.Refit
import dev.chieppa.squash.screens.edit.affected
import dev.chieppa.squash.screens.edit.curvePoints
import dev.chieppa.squash.screens.edit.reachesWholePart
import dev.chieppa.squash.screens.edit.rememberCurveLook
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/**
 * Draw outline: in a pane with no outline yet, a stroke becomes one; over a drawn outline it is an oversketch, replacing
 * only the span it covers, and a stroke too far from the outline does nothing. Either way the stroke is previewed as it
 * grows, with what release would add and discard, and ends as one [SetOutline], so one stroke is one undo step. A stroke the
 * pane loses midway sends nothing.
 */
class DrawOutlineTool(private val state: AppState, var look: StrokeLook, var curveLook: CurveLook) : PaneTool {
    private var strokeView by mutableStateOf<View?>(null)
    private val strokePoints = mutableStateListOf<CurvePoint>()
    private var strokeCamera: LockedCamera? = null

    override val drawingIn: View? get() = strokeView

    /** The stroke, or over a drawn outline that outline as the stroke would leave it, which is what the other views are held to. */
    override val liveStroke: LiveStroke?
        get() {
            val view = strokeView ?: return null
            val points = strokePoints.toList()
            val outline = outline(view) ?: return LiveStroke(view, points)
            return LiveStroke(view, preview(view, outline, points)?.line?.map { CurvePoint(it.x, it.y) } ?: outlineSamples(outline))
        }

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(outline(view))

    private fun outline(view: View): OutlineCurve? {
        val part = state.selectedPart.value ?: return null
        return state.session.value?.document?.value?.character?.resolved(part)?.outlines?.get(view)
    }

    private fun preview(view: View, outline: OutlineCurve, points: List<CurvePoint>): OversketchPreview? {
        val camera = strokeCamera ?: return null
        val session = state.session.value ?: return null
        val part = state.selectedPart.value?.let(session.document.value.character::part) ?: return null
        val ends = strokeEnds(view, session.templates[part.template].openEnd)
        return strokeOverOutline(outline, points, ends, 1f / camera.fit.pixelsPerUnit, state.curves.options)
    }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (strokeView != null || state.selectedPart.value == null || !isToolPress(down)) return@awaitEachGesture
            down.consume()
            strokeCamera = camera
            strokeView = view
            add(camera.toPlane(down.position))
            try {
                if (followToRelease(down.id, camera)) finish(view, camera)
            } finally {
                strokeView = null
                strokeCamera = null
                strokePoints.clear()
            }
        }
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
            if (isToolRelease(event, change)) return true
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
        val pixel = 1f / camera.fit.pixelsPerUnit
        val curves = state.curves
        val existing = session.document.value.character.resolved(instance).outlines[view]
        if (existing == null) {
            val fitted = fittedOutline(strokePoints.toList(), ends, pixel, curves.strokeOptions) ?: return
            curves.recordFit(fitted.curve, fitted.errorPixels)
            session.send(SetOutline(part, view, fitted.curve))
            return
        }
        val done = oversketchedOutline(existing, strokePoints.toList(), ends, pixel, curves.options) ?: return
        curves.recordFit(done.curve, maxOf(done.errorPixels, curves.fitError(existing) ?: 0f))
        val wholePart = done.replaced == existing.sections || reachesWholePart(view, existing, done.curve)
        curves.refit = Refit(part, view, done.curve, done.firstSection, done.replaced, wholePart)
        curves.selection = null
        session.send(SetOutline(part, view, done.curve))
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val outline = outline(view)
        val points = outline?.let { curvePoints(it, null, camera, curveLook) } ?: ViewportOverlay.Empty
        if (strokeView != view || strokePoints.isEmpty()) return points
        val stroke = strokePoints.toList()
        val over = outline?.let { preview(view, it, stroke) }
        val guide = if (outline == null) firstStroke(view, stroke, camera) else oversketchGuide(over, stroke.map { Vec2(it.x, it.y) })
        return points + buildOverlay {
            over?.touched?.let { line -> affected(line.map { CurvePoint(it.x, it.y) }, camera, curveLook) }
            guide?.let { strokeGuide(it, stroke.last(), camera, look) }
        }
    }

    private fun firstStroke(view: View, stroke: List<CurvePoint>, camera: LockedCamera): StrokeGuide? {
        val session = state.session.value ?: return null
        val part = state.selectedPart.value?.let(session.document.value.character::part) ?: return null
        val ends = strokeEnds(view, session.templates[part.template].openEnd)
        return strokeCompletion(stroke, ends, 1f / camera.fit.pixelsPerUnit, state.curves.strokeOptions).guide()
    }
}

/** The tool for [state], kept across compositions and following the theme. */
@Composable
fun rememberDrawOutlineTool(state: AppState): DrawOutlineTool {
    val look = rememberStrokeLook()
    val curveLook = rememberCurveLook()
    return remember(state) { DrawOutlineTool(state, look, curveLook) }.also {
        it.look = look
        it.curveLook = curveLook
    }
}

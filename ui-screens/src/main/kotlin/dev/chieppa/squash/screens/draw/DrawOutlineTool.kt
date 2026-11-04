package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.oversketchedOutline
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.sections
import dev.chieppa.squash.geom.OversketchOptions
import dev.chieppa.squash.geom.OversketchPreview
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.classifyStroke
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.CurveLook
import dev.chieppa.squash.screens.edit.Refit
import dev.chieppa.squash.screens.edit.affected
import dev.chieppa.squash.screens.edit.curvePoints
import dev.chieppa.squash.screens.edit.RefitReach
import dev.chieppa.squash.screens.edit.refitReach
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
 * pane loses midway sends nothing. With Hold to snap on, a stroke whose pointer rests turns into the clean shape it best
 * reads as, which the pointer adjusts until release and which stands in for the stroke from then on.
 */
class DrawOutlineTool(private val state: AppState, var look: StrokeLook, var curveLook: CurveLook) : PaneTool {
    private var strokeView: View?
        get() = state.curves.drawing
        set(value) {
            state.curves.drawing = value
        }
    private val strokePoints = mutableStateListOf<CurvePoint>()
    private var strokeCamera: LockedCamera? = null
    private var held by mutableStateOf<HeldShape?>(null)

    override val drawingIn: View? get() = strokeView

    /** The stroke, or over a drawn outline that outline as the stroke would leave it, which is what the other views are held to. */
    override val liveStroke: LiveStroke?
        get() {
            val view = strokeView ?: return null
            val points = strokeNow()
            val outline = outline(view) ?: return LiveStroke(view, points)
            return LiveStroke(view, preview(view, outline, points)?.line?.map { CurvePoint(it.x, it.y) } ?: outlineSamples(outline))
        }

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(outline(view))

    private fun outline(view: View): OutlineCurve? = state.editedOutline(view)

    /** The points the stroke stands for now: what was drawn, or the shape it snapped to. */
    private fun strokeNow(): List<CurvePoint> {
        val shape = held
        val camera = strokeCamera
        return if (shape != null && camera != null) shape.points(camera.planePixel) else strokePoints.toList()
    }

    /** A snapped shape is already clean, so it is not smoothed again. */
    private val strokeOptions: StrokeOptions
        get() = state.curves.strokeOptions.let { if (held != null) it.copy(smoothing = 0f) else it }

    private val oversketchOptions: OversketchOptions get() = state.curves.options.copy(stroke = strokeOptions)

    private fun preview(view: View, outline: OutlineCurve, points: List<CurvePoint>): OversketchPreview? {
        val camera = strokeCamera ?: return null
        val session = state.session.value ?: return null
        val part = state.selectedPart.value?.let(session.document.value.character::part) ?: return null
        val ends = strokeEnds(view, session.templates[part.template].openEnd)
        return strokeOverOutline(outline, points, ends, camera.planePixel, oversketchOptions)
    }

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (strokeView != null || state.selectedPart.value == null || !isToolPress(down)) return@awaitEachGesture
            down.consume()
            strokeCamera = camera
            strokeView = view
            state.curves.penPressure = penPressure(down)
            add(camera.toPlane(down.position))
            try {
                val released = followHolding(down.id, down, { state.curves.penPressure = penPressure(it) }, { snap(camera) }) { add(camera.toPlane(it)) }
                if (released) finish(view, camera)
            } finally {
                strokeView = null
                strokeCamera = null
                strokePoints.clear()
                held = null
                state.curves.penPressure = null
            }
        }
    }

    private fun add(point: CurvePoint) {
        val shape = held
        if (shape != null) shape.pointer = point else if (strokePoints.lastOrNull() != point) strokePoints += point
    }

    private fun snap(camera: LockedCamera) {
        if (!state.curves.holdSnap || held != null) return
        val shape = classifyStroke(strokePoints.map { Vec2(it.x, it.y) }, camera.planePixel) ?: return
        held = HeldShape(shape, strokePoints.last())
    }

    private fun finish(view: View, camera: LockedCamera) {
        val session = state.session.value ?: return
        val part = state.selectedPart.value ?: return
        val instance = session.document.value.character.part(part) ?: return
        val ends = strokeEnds(view, session.templates[instance.template].openEnd)
        val pixel = camera.planePixel
        val curves = state.curves
        val existing = state.editedOutline(view)
        if (existing == null) {
            val fitted = fittedOutline(strokeNow(), ends, pixel, strokeOptions) ?: return
            curves.recordFit(fitted.curve, fitted.errorPixels)
            session.send(SetOutline(part, view, fitted.curve, state.thisSide))
            return
        }
        val done = oversketchedOutline(existing, strokeNow(), ends, pixel, oversketchOptions) ?: return
        curves.recordFit(done.curve, maxOf(done.errorPixels, curves.fitError(existing) ?: 0f))
        val reach = refitReach(view, existing, done.curve, session.templates[instance.template]).let {
            if (it == RefitReach.SECTIONS && done.replaced == existing.sections) RefitReach.WHOLE_PART else it
        }
        curves.refit = Refit(part, view, done.curve, done.firstSection, done.replaced, reach)
        curves.selection = null
        session.send(SetOutline(part, view, done.curve, state.thisSide))
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val outline = outline(view)
        val points = outline?.takeIf { view == state.activeView.value }?.let { curvePoints(it, null, camera, curveLook) } ?: ViewportOverlay.Empty
        if (strokeView != view || strokePoints.isEmpty()) return points
        val stroke = strokeNow()
        val tip = held?.pointer ?: stroke.last()
        val over = outline?.let { preview(view, it, stroke) }
        val guide = if (outline == null) firstStroke(view, stroke, camera) else oversketchGuide(over, stroke.map { Vec2(it.x, it.y) })
        return points + buildOverlay {
            over?.touched?.let { line -> affected(line.map { CurvePoint(it.x, it.y) }, camera, curveLook) }
            guide?.let { strokeGuide(it, tip, camera, look) }
        }
    }

    private fun firstStroke(view: View, stroke: List<CurvePoint>, camera: LockedCamera): StrokeGuide? {
        val session = state.session.value ?: return null
        val part = state.selectedPart.value?.let(session.document.value.character::part) ?: return null
        val ends = strokeEnds(view, session.templates[part.template].openEnd)
        return strokeCompletion(stroke, ends, camera.planePixel, strokeOptions).guide()
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

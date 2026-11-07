package dev.chieppa.squash.screens.draw

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.build.oversketchedOutline
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.sections
import dev.chieppa.squash.geom.OversketchOptions
import dev.chieppa.squash.geom.OversketchPreview
import dev.chieppa.squash.geom.SnapGuide
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.StrokeMarkKind
import dev.chieppa.squash.geom.StrokeOptions
import dev.chieppa.squash.geom.StrokePreview
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.classifyStroke
import dev.chieppa.squash.geom.stabilizerString
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
import dev.chieppa.squash.screens.sheet.PaneLine
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/**
 * Draw outline: in a pane with no outline yet, a stroke becomes one; over a drawn outline it is an oversketch, replacing
 * only the span it covers, and a stroke too far from the outline does nothing. Either way the stroke is previewed as it
 * grows, with what the build would read of it and what release would add and discard, and ends as one [SetOutline], so one
 * stroke is one undo step. A stroke the pane loses midway sends nothing. The Outline drawer's assists all act on the samples
 * or the options, ahead of the preview and the release alike, so the two cannot part. With Hold to snap on, a stroke whose
 * pointer rests turns into the clean shape it best reads as, which the pointer adjusts until release and which stands in
 * for the stroke from then on, whole, so guides still catch it and Half outline and forced points do not touch it.
 */
class DrawOutlineTool(private val state: AppState, var look: StrokeLook, var curveLook: CurveLook) : PaneTool {
    private var strokeView: View?
        get() = state.curves.drawing
        set(value) {
            state.curves.drawing = value
        }
    private val capture = StrokeCapture()
    private var strokeCamera: LockedCamera? = null
    private var held by mutableStateOf<HeldShape?>(null)
    private var snapLines: List<PaneLine> = emptyList()
    private var snapGuides: List<SnapGuide> = emptyList()

    override val drawingIn: View? get() = strokeView

    /** The stroke, or over a drawn outline that outline as the stroke would leave it, which is what the other views are held to. */
    override val liveStroke: LiveStroke?
        get() {
            val view = strokeView ?: return null
            val stroke = strokeNow()
            val outline = outline(view)
            if (outline == null) {
                if (!state.curves.halfOutline) return LiveStroke(view, stroke.samples)
                return LiveStroke(view, firstStroke(view, stroke)?.shape?.map { CurvePoint(it.x, it.y) } ?: stroke.samples)
            }
            return LiveStroke(view, preview(view, outline, stroke)?.line?.map { CurvePoint(it.x, it.y) } ?: outlineSamples(outline))
        }

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(outline(view))

    private fun outline(view: View): OutlineCurve? = state.editedOutline(view)

    /** What the stroke stands for now, what was drawn or the shape it snapped to, held to the guides. */
    private fun strokeNow(): SnappedStroke {
        val pixel = strokeCamera?.planePixel ?: 0f
        val guides = if (state.curves.snapGuides) snapGuides else emptyList()
        val reach = SNAP_GUIDE_PIXELS * pixel
        val shape = held ?: return SnappedStroke(capture.samples, capture.marks, capture.lineEnd, guides, reach)
        return SnappedStroke(shape.points(pixel).map { it.vec() }, emptyList(), null, guides, reach)
    }

    /** A snapped shape is already clean and already whole, so it is neither smoothed again nor mirrored. */
    private fun strokeOptions(view: View, stroke: SnappedStroke): StrokeOptions =
        if (held != null) state.curves.strokeOptions.copy(smoothing = 0f) else state.curves.strokeOptions(view, stroke.marks)

    private fun oversketchOptions(view: View, stroke: SnappedStroke): OversketchOptions =
        state.curves.options.copy(stroke = strokeOptions(view, stroke))

    private fun preview(view: View, outline: OutlineCurve, stroke: SnappedStroke): OversketchPreview? {
        val camera = strokeCamera ?: return null
        val template = selectedTemplate() ?: return null
        return strokeOverOutline(outline, stroke.samples, strokeEnds(view, template.openEnd), camera.planePixel, oversketchOptions(view, stroke))
    }

    private fun selectedTemplate() = state.session.value?.let { session ->
        state.selectedPart.value?.let(session.document.value.character::part)?.let(session::builtTemplate)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (strokeView != null || state.selectedPart.value == null || !isToolPress(down)) return@awaitEachGesture
            down.consume()
            strokeCamera = camera
            strokeView = view
            state.curves.penPressure = penPressure(down)
            snapLines = state.strokeSnapLines(camera)
            snapGuides = snapLines.map(camera::guideInPlane)
            val modifiers = currentEvent.keyboardModifiers
            capture.begin(camera.toPlane(down.position).vec(), stabilizerString(state.curves.stabilizer, camera.planePixel), modifiers.isAltPressed, modifiers.isShiftPressed)
            var forced: StrokeMarkKind? = null
            var forcedAt = Offset.Unspecified
            try {
                val released = followHolding(
                    down.id,
                    down,
                    {
                        state.curves.penPressure = penPressure(it)
                        val pressed = currentEvent.keyboardModifiers
                        forced = capture.modifiers(pressed.isAltPressed, pressed.isShiftPressed)
                        forcedAt = it.position
                    },
                    { snap(camera) },
                ) {
                    add(camera.toPlane(it))
                    if (it == forcedAt && held == null) forced?.let(capture::mark)
                }
                if (released) finish(view, camera)
            } finally {
                strokeView = null
                strokeCamera = null
                capture.clear()
                held = null
                state.curves.penPressure = null
            }
        }
    }

    private fun CurvePoint.vec() = Vec2(x, y)

    private fun add(point: CurvePoint) {
        val shape = held
        if (shape != null) shape.pointer = point else capture.moveTo(point.vec())
    }

    private fun snap(camera: LockedCamera) {
        if (!state.curves.holdSnap || held != null) return
        val samples = capture.samples
        val plane = selectedTemplate()?.takeIf { state.curves.closeEnd }?.let { strokeEnds(camera.view, it.openEnd) as? StrokeEnds.CutAtPlane }
        val shape = classifyStroke(samples, camera.planePixel, plane) ?: return
        held = HeldShape(shape, samples.last().let { CurvePoint(it.x, it.y) })
    }

    private fun finish(view: View, camera: LockedCamera) {
        val session = state.session.value ?: return
        val part = state.selectedPart.value ?: return
        val instance = session.document.value.character.part(part) ?: return
        val ends = strokeEnds(view, session.builtTemplate(instance).openEnd)
        val pixel = camera.planePixel
        val curves = state.curves
        val existing = state.editedOutline(view)
        val stroke = strokeNow()
        if (existing == null) {
            val fitted = fittedOutline(stroke.samples, ends, pixel, strokeOptions(view, stroke)) ?: return
            curves.recordFit(fitted.curve, fitted.errorPixels)
            session.send(SetOutline(part, view, fitted.curve, state.thisSide))
            return
        }
        val done = oversketchedOutline(existing, stroke.samples, ends, pixel, oversketchOptions(view, stroke)) ?: return
        curves.recordFit(done.curve, maxOf(done.errorPixels, curves.fitError(existing) ?: 0f))
        val reach = refitReach(view, existing, done.curve, session.builtTemplate(instance)).let {
            if (it == RefitReach.SECTIONS && done.replaced == existing.sections) RefitReach.WHOLE_PART else it
        }
        curves.refit = Refit(part, view, done.curve, done.firstSection, done.replaced, reach)
        curves.selection = null
        session.send(SetOutline(part, view, done.curve, state.thisSide))
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val outline = outline(view)
        val points = outline?.takeIf { view == state.activeView.value }?.let { curvePoints(it, null, camera, curveLook) } ?: ViewportOverlay.Empty
        if (strokeView != view || capture.isEmpty) return points
        val stroke = strokeNow()
        val over = outline?.let { preview(view, it, stroke) }
        val first = if (outline == null) firstStroke(view, stroke) else null
        val samples = stroke.samples.map { it.vec() }
        val guide = if (outline == null) first?.guide() else oversketchGuide(over, samples)
        val reading = selectedTemplate()?.let { template ->
            val shape = when {
                outline == null -> first?.shape.orEmpty()
                over?.joins == true -> over.line
                else -> outlineSamples(outline).map { it.vec() }
            }
            outlineReading(shape, view, template, camera.planePixel)
        }
        val tip = held?.pointer ?: stroke.samples.last()
        val trailed = stroke.lineEnd?.let { guide?.trailing(it, tip.vec(), camera.planePixel) } ?: guide
        val shown = trailed?.readBy(reading)
        return points + buildOverlay {
            reading?.let { readShade(it, camera, look) }
            listOfNotNull(over, over?.mirrored).forEach { part -> affected(part.touched.map { CurvePoint(it.x, it.y) }, camera, curveLook) }
            if (state.curves.snapGuides) caughtGuides(stroke.caught.map(snapLines::get), look)
            shown?.let { strokeGuide(it, tip, camera, look) }
        }
    }

    private fun firstStroke(view: View, stroke: SnappedStroke): StrokePreview? {
        val camera = strokeCamera ?: return null
        val template = selectedTemplate() ?: return null
        return strokeCompletion(stroke.samples, strokeEnds(view, template.openEnd), camera.planePixel, strokeOptions(view, stroke))
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

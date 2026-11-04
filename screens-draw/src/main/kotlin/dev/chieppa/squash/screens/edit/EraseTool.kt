package dev.chieppa.squash.screens.edit

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
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.withoutSections
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.geom.EraseReach
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.eraseReach
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.StrokeLook
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.draw.isToolPress
import dev.chieppa.squash.screens.draw.penPressure
import dev.chieppa.squash.screens.draw.rememberStrokeLook
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.shownCurve
import dev.chieppa.squash.screens.sheet.shownPoint
import dev.chieppa.squash.screens.sheet.PaneTool
import dev.chieppa.squash.screens.sheet.polyline
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay

/** What releasing an eraser stroke does to an outline: nothing, the [curve] it leaves, or with [curve] null clearing it. */
internal class Erasure(val sections: Set<Int>, val curve: OutlineCurve?)

/**
 * What a stroke through [samples] erases of [outline], within [reach]. Erasing a pair's outline from one side has nowhere to
 * write the absence, so whole erasure with [thisSide] only takes back an override, which [overridden] says this side has.
 */
internal fun erasure(outline: OutlineCurve, samples: List<CurvePoint>, reach: Float, thisSide: Boolean, overridden: Boolean, shownFrom: OutlineCurve = outline): Erasure? =
    when (val found = eraseReach(shownFrom.toPath(), samples.map { Vec2(it.x, it.y) }, reach)) {
        EraseReach.Nothing -> null
        EraseReach.Whole -> if (thisSide && !overridden) null else Erasure((0 until outline.toPath().segments.size).toSet(), null)
        is EraseReach.Sections -> outline.withoutSections(found.sections)?.let { Erasure(found.sections, it) }
            ?: if (thisSide && !overridden) null else Erasure(found.sections, null)
    }

/**
 * Erase: a stroke takes out the sections of an outline it passes over, each run becoming one straight section between the
 * anchors around it, since the document holds a curve as one chain; a stroke over most of the outline clears it, and the
 * view goes back to the template. Release does what the highlight shows, as one command and so one undo step. A stroke the
 * pane loses midway sends nothing.
 */
class EraseTool(private val state: AppState, var strokeLook: StrokeLook, var look: CurveLook) : PaneTool {
    private var strokeView by mutableStateOf<View?>(null)
    private val strokePoints = mutableStateListOf<CurvePoint>()

    override val holdsFit: Boolean get() = strokeView != null

    override fun fitErrorPixels(view: View): Float? = state.curves.fitError(state.editedOutline(view))

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = Modifier.pointerInput(this, view, camera) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (strokeView != null || state.selectedPart.value == null || !isToolPress(down)) return@awaitEachGesture
            down.consume()
            strokeView = view
            state.curves.penPressure = penPressure(down)
            add(camera.toPlane(down.position))
            try {
                val released = followToRelease(down.id, { state.curves.penPressure = penPressure(it) }) { add(camera.toPlane(it)) }
                if (released) finish(view, camera)
            } finally {
                strokeView = null
                strokePoints.clear()
                state.curves.penPressure = null
            }
        }
    }

    private fun add(point: CurvePoint) {
        if (strokePoints.lastOrNull() != point) strokePoints += point
    }

    private fun erased(view: View, camera: LockedCamera): Erasure? {
        val outline = state.editedOutline(view) ?: return null
        val part = selected() ?: return null
        val character = state.session.value?.document?.value?.character ?: return null
        val instance = character.part(part) ?: return null
        val thisSide = state.thisSide && instance.pair != null
        val overridden = view in character.overridesOf(instance).outlines
        return erasure(outline, strokePoints.map(camera::shownPoint), look.grabReach * camera.planePixel, thisSide, overridden, camera.shownCurve(outline))
    }

    private fun selected(): PartId? = state.selectedPart.value

    private fun finish(view: View, camera: LockedCamera) {
        val session = state.session.value ?: return
        val part = selected() ?: return
        val instance = session.document.value.character.part(part) ?: return
        val outline = state.editedOutline(view) ?: return
        val done = erased(view, camera) ?: return
        val curves = state.curves
        val after = done.curve
        curves.selection = null
        curves.refit = after?.let {
            val first = done.sections.min()
            val reach = refitReach(view, outline, it, session.templates[instance.template])
            Refit(part, view, it, first, done.sections.size, reach)
        }
        session.send(SetOutline(part, view, after, state.thisSide))
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay {
        val outline = state.editedOutline(view) ?: return ViewportOverlay.Empty
        val shown = if (view == state.activeView.value) curvePoints(outline, null, camera, look) else ViewportOverlay.Empty
        if (strokeView != view || strokePoints.isEmpty()) return shown
        val going = erased(view, camera)
        return shown + buildOverlay {
            if (going != null) goingSections(outline, going, camera)
            polyline(strokePoints.toList(), camera, strokeLook.dropped, strokeLook.droppedWidth)
        }
    }

    /** The sections about to go, dashed as affected, and the straight runs that will stand in for them. */
    private fun OverlayBuilder.goingSections(outline: OutlineCurve, going: Erasure, camera: LockedCamera) {
        val path = outline.toPath()
        going.sections.sorted().forEach { section ->
            val flat = BezierPath(listOf(path.segments[section])).flatten()
            affected(flat.xs.indices.map { CurvePoint(flat.xs[it], flat.ys[it]) }, camera, look)
        }
    }
}

/** The tool for [state], kept across compositions and following the theme. */
@Composable
fun rememberEraseTool(state: AppState): EraseTool {
    val strokeLook = rememberStrokeLook()
    val look = rememberCurveLook()
    return remember(state) { EraseTool(state, strokeLook, look) }.also {
        it.strokeLook = strokeLook
        it.look = look
    }
}

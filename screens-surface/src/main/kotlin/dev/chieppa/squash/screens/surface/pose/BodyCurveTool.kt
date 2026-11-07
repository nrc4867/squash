package dev.chieppa.squash.screens.surface.pose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.PoseSolver
import dev.chieppa.squash.rig.bendTo
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.pose.PoseGrip
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.pose_curve_length
import dev.chieppa.squash.uidesign.generated.pose_curve_square
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.ui.Chip
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The test tag of the chip a pane shows under the Body curve tool. */
const val BODY_CURVE_CHIP_TAG = "pose.curve.chip"

private const val PERCENT = 100f

/**
 * Body curve: a stroke drawn across a pane that looks squarely at Side or Top, a locked one or Full turned square, becomes that view's
 * stroke of the body curve, in place of the one before and keeping the other view's; the spine and tail bend onto it and the planted
 * paws stay where they stood. A press on a stroke's handle moves its end or bows its middle. Each drag previews as it goes and is one
 * undo step. Full turned any other way takes nothing and says why in its chip.
 */
class BodyCurveTool(state: AppState, look: PoseHandleLook) : PoseTool(state, look) {
    private val live = mutableStateListOf<Vec2>()
    private var liveView by mutableStateOf<View?>(null)

    override fun takes(grip: PoseGrip): Boolean = grip is PoseGrip.Curve

    override fun shows(grip: PoseGrip): Boolean = false

    /** The point the views' planes pass through: the middle of the root part. */
    private val middle: Vec3 get() = handles?.body ?: Vec3.ZERO

    override fun gripAt(pixel: Offset, sight: SurfaceView): PoseGrip? {
        val view = bodyCurveView(sight, middle) ?: return null
        val stroke = shownPose.curve?.stroke(view) ?: return null
        return stroke.spots().entries
            .mapNotNull { (spot, at) -> sight.toPixel(strokeWorld(at, view, middle))?.let { spot to (it - pixel).getDistance() } }
            .filter { it.second <= look.reach }
            .minByOrNull { it.second }
            ?.let { PoseGrip.Curve(view, it.first) }
    }

    override fun grab(grip: PoseGrip?, pixel: Offset, sight: SurfaceView, shift: Boolean): PoseDrag? {
        val session = state.session.value ?: return null
        val solver = solver ?: return null
        val at = middle
        val view = bodyCurveView(sight, at) ?: return null
        val start = session.document.value.character.pose
        val from = strokePoint(sight, pixel, view, at) ?: return null
        if (grip is PoseGrip.Curve) {
            val stroke = start.curve?.stroke(grip.view) ?: return null
            return CurveDrag(session, solver, start) { to ->
                strokePoint(sight, to, view, at)?.let { start.curve.withStroke(stroke.moved(grip.at, it - from)) }
            }
        }
        val pixelSize = 1f / sight.pixelsPerUnitAt(at).coerceAtLeast(1e-6f)
        live.clear()
        live += from
        liveView = view
        return CurveDrag(session, solver, start, onEnd = { live.clear(); liveView = null }) { to ->
            strokePoint(sight, to, view, at)?.let { live += it }
            fitBodyStroke(live.toList(), view, pixelSize)?.let { start.curve.withStroke(it) }
        }
    }

    override fun OverlayBuilder.drawn(sight: SurfaceView) {
        val at = middle
        val view = bodyCurveView(sight, at) ?: return
        val drawing = live.takeIf { liveView == view }?.toList().orEmpty()
        if (drawing.size >= 2) {
            curveLine(drawing.mapNotNull { sight.toPixel(strokeWorld(it, view, at)) }, look)
            return
        }
        val stroke = shownPose.curve?.stroke(view) ?: return
        val lit = (state.pose.lit as? PoseGrip.Curve)?.takeIf { it.view == view }?.at
        bodyStroke(stroke, sight, at, look, lit)
    }

    override val paneNote: @Composable (SurfaceView) -> Unit = { sight ->
        val view = bodyCurveView(sight, middle)
        val curve = shownPose.curve
        when {
            view == null && sight is FullView -> Chip(stringResource(Res.string.pose_curve_square), Modifier.testTag(BODY_CURVE_CHIP_TAG))
            view != null && curve?.stroke(view) != null ->
                Chip(stringResource(Res.string.pose_curve_length, (curve.length * PERCENT).roundToInt()), Modifier.testTag(BODY_CURVE_CHIP_TAG), mono = true)
        }
    }
}

/**
 * A drag that bends the body to the curve [curveAt] gives for each pointer place, from the pose it started on, [start], planted paws
 * held: each step a preview on one open transaction of the session's history, written on release as one step. [onEnd] runs once it
 * is over either way.
 */
private class CurveDrag(
    session: CharacterSession,
    private val solver: PoseSolver,
    private val start: Pose,
    private val onEnd: () -> Unit = {},
    private val curveAt: (Offset) -> BodyCurve?,
) : PoseDrag {
    private val gesture: History<SquashDocument>.Transaction = session.history.begin()
    private var moved = false

    override fun move(pixel: Offset, alt: Boolean, shift: Boolean) {
        val curve = curveAt(pixel) ?: return
        gesture.update(SetPose(solver.bendTo(start, curve).pose))
        moved = true
    }

    override fun finish(keep: Boolean) {
        if (keep && moved) gesture.commit() else gesture.cancel()
        onEnd()
    }
}

package dev.chieppa.squash.screens.surface.rig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.document.SeamBrush
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.seamStrokeScale
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.rig.rigNow
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceTool
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import kotlinx.coroutines.CoroutineScope
import kotlin.math.cos
import kotlin.math.sin

private const val RING_POINTS = 48
private const val TWO_PI = 6.2831855f

/**
 * Paint weight, Erase weight or Smooth weight, by [brush]: lays a seam stroke as one undo step on whatever surface the pointer passes
 * over, on the seam [seamFor] picks where it starts, with the brush's ring under the pointer. A press on a joint's dot picks that
 * bone and wiggles it instead, and the joint under the pointer shows its weight.
 */
@Stable
class SeamStrokeTool(state: AppState, val brush: SeamBrush, private val scope: CoroutineScope) : SurfaceTool(state) {
    internal var ringColor: Color = Color.Unspecified
    internal var ringWidth: Float = 0f
    private var ringAt by mutableStateOf<Vec3?>(null)

    override fun pointerInput(view: View, camera: LockedCamera): Modifier = super.pointerInput(view, camera).then(hovering(camera))

    override fun fullInput(view: FullView): Modifier = super.fullInput(view).then(hovering(view))

    /** Follows the pointer over a pane without taking it: the joint under it and where the ring goes. */
    private fun hovering(sight: SurfaceView): Modifier = Modifier.pointerInput(this, sight) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pixel = event.changes.firstOrNull()?.position ?: continue
                when (event.type) {
                    PointerEventType.Exit -> {
                        state.rig.hovered = null
                        ringAt = null
                    }
                    PointerEventType.Enter, PointerEventType.Move -> if (event.changes.none { it.pressed }) {
                        val joint = jointUnder(pixel, sight)
                        state.rig.hovered = joint
                        ringAt = if (joint == null) hitAt(pixel, sight)?.position else null
                    }
                }
            }
        }
    }

    private fun jointUnder(pixel: Offset, sight: SurfaceView): BoneName? {
        val rig = state.rigNow() ?: return null
        val pose = state.rig.held.value ?: state.session.value?.document?.value?.character?.pose ?: return null
        return jointAt(rig, jointPoints(rig, pose), pixel, sight)
    }

    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val joint = jointUnder(down.position, sight) ?: return false
        down.consume()
        state.rig.picked = joint
        state.rig.wiggle(joint)
        return true
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val rig = state.rigNow() ?: return
        val assembled = session.assembly.output.value ?: return
        val target = seamFor(rig, hit.part, hit.position, state.rig.picked) ?: return
        val pixels = seamStrokeScale(assembled, target.child) * sight.pixelsPerUnitAt(hit.position)
        if (pixels <= 0f) return
        val live = LiveSeamStroke(session, target, brush, state.rig.brushSize / pixels, state.rig.strength, state.thisSide, scope)
        live.add(hit.anchor)
        ringAt = hit.position
        var released = false
        try {
            released = followSurface(hit, down, sight) { next ->
                live.add(next.anchor)
                ringAt = next.position
            }
        } finally {
            live.finish(released)
        }
    }

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = ring(camera)

    override fun fullOverlay(view: FullView): ViewportOverlay = ring(view)

    private fun ring(sight: SurfaceView): ViewportOverlay {
        val at = ringAt ?: return ViewportOverlay.Empty
        val center = sight.toPixel(at) ?: return ViewportOverlay.Empty
        val radius = state.rig.brushSize
        return buildOverlay {
            val path = List(RING_POINTS + 1) { i ->
                val angle = TWO_PI * i / RING_POINTS
                center + Offset(cos(angle), sin(angle)) * radius
            }
            path.zipWithNext().forEach { (a, b) -> line(a, b, ringColor, ringWidth) }
            dot(center, ringWidth, ringColor)
        }
    }
}

@Composable
fun rememberSeamStrokeTool(state: AppState, brush: SeamBrush): SeamStrokeTool {
    val look = rememberSheetLook()
    val scope = rememberCoroutineScope()
    return remember(state, brush) { SeamStrokeTool(state, brush, scope) }.also {
        it.ringColor = look.ring
        it.ringWidth = look.guideWidth
    }
}

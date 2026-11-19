package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.shape.repainted
import dev.chieppa.squash.character.shape.withoutEdge
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.SurfaceCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.rememberSheetLook
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceTool
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import kotlin.math.floor

/** How near the pointer, in pixels, Erase takes an edge and Smooth eases its points. */
private const val ERASE_REACH = 12f
private const val SMOOTH_REACH = 28f

/** How far toward its neighbours' middle Smooth moves a point at each step of a drag. */
private const val SMOOTH_PULL = 0.5f

/**
 * A Dress tool working on the picked garment's edges on the body's surface, from a locked pane or a free one alike, with the edges
 * showing as Paint shows them.
 */
abstract class EdgeWorkTool(state: AppState, internal var look: SheetLook) : SurfaceTool(state) {
    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = buildOverlay { edges(state, look, camera) }

    override fun fullOverlay(view: FullView): ViewportOverlay = buildOverlay { edges(state, look, view) }

    /**
     * Follows the drag from [down] as one step, handing [step] each pointer place with the garment's cloth as the drag has left it so far,
     * which [step] answers with the cloth after it, or null for no change.
     */
    protected suspend fun AwaitPointerEventScope.gesture(down: PointerInputChange, sight: SurfaceView, step: (Offset, PaintedGarment) -> PaintedGarment?) {
        val picked = state.pickedGarment() ?: return
        val session = state.session.value ?: return
        val transaction: History<SquashDocument>.Transaction = session.history.begin()
        var now = picked.painted
        var changed = false
        var released = false
        val apply = { pixel: Offset ->
            step(pixel, now)?.let { next ->
                now = next
                changed = true
                transaction.update(shapeCommands(picked.character, picked.body, picked.garment, next))
            }
        }
        try {
            apply(down.position)
            released = followToRelease(down.id, {}) { pixel -> apply(pixel) }
        } finally {
            if (released && changed) transaction.commit() else transaction.cancel()
        }
    }

    /** The indices of [painted]'s edges showing within [reach] of [pixel]. */
    protected fun edgesNear(painted: PaintedGarment, pixel: Offset, sight: SurfaceView, reach: Float): List<Int> {
        val picked = state.pickedGarment() ?: return emptyList()
        return painted.edges.indices.filter { i ->
            shownCurves(picked.character, painted.edges[i].edge.curve).any { shown ->
                pixelsOf(picked.body, shown.curve, sight).any { it != null && (it - pixel).getDistance() <= reach }
            }
        }
    }
}

/**
 * Edit curves on edges: a press on a handle of the picked garment's hems drags it along the surface, bending the hem through it and
 * repainting the cells either side of where it moved, and picks that edge; the hem shows as dragged till it lets go.
 */
@Stable
class EdgeEditTool(state: AppState, look: SheetLook) : EdgeWorkTool(state, look) {
    private var dragged by mutableStateOf<SurfaceCurve?>(null)

    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = buildOverlay { edges(state, look, camera, dragged) }

    override fun fullOverlay(view: FullView): ViewportOverlay = buildOverlay { edges(state, look, view, dragged) }

    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean =
        dragEdgePoint(state, down, sight, pickedOnly = false) { dragged = it }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit
}

/** Erase on edges: a press or drag over an edge of the picked garment takes it away, its far side joining its area, one step per drag. */
@Stable
class EdgeEraseTool(state: AppState, look: SheetLook) : EdgeWorkTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val picked = state.pickedGarment() ?: return false
        if (edgesNear(picked.painted, down.position, sight, ERASE_REACH).isEmpty()) return false
        down.consume()
        state.dress.edge.value = null
        gesture(down, sight) { pixel, now -> erasedNear(now, pixel, sight) }
        return true
    }

    private fun erasedNear(now: PaintedGarment, pixel: Offset, sight: SurfaceView): PaintedGarment? {
        val gone = edgesNear(now, pixel, sight, ERASE_REACH).firstOrNull() ?: return null
        val on = now.edges[gone]
        val curves = now.edges.filter { it.area == on.area }.map { it.edge.curve }
        val mask = withoutEdge(now.areas[on.area].mask, on.edge.curve, curves)
        return now.with(areas = now.areas.mapIndexed { i, area -> if (i == on.area) area.with(mask) else area.with(area.mask - mask) })
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit
}

/**
 * Smooth on edges: a drag near the picked garment's edges eases each point within reach toward the middle of its neighbours on the
 * same part, the area drawn again along it, one step per drag.
 */
@Stable
class EdgeSmoothTool(state: AppState, look: SheetLook) : EdgeWorkTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val picked = state.pickedGarment() ?: return false
        if (edgesNear(picked.painted, down.position, sight, SMOOTH_REACH).isEmpty()) return false
        down.consume()
        gesture(down, sight) { pixel, now -> smoothedNear(now, pixel, sight) }
        return true
    }

    private fun smoothedNear(now: PaintedGarment, pixel: Offset, sight: SurfaceView): PaintedGarment? {
        val picked = state.pickedGarment() ?: return null
        var next = now
        var any = false
        for (area in now.areas.indices) {
            val curves = now.edges.filter { it.area == area }.map { on ->
                val curve = on.edge.curve
                val near = curve.points.indices.filter { j ->
                    shownCurves(picked.character, curve).any { shown -> pixelsOf(picked.body, shown.curve, sight)[j]?.let { (it - pixel).getDistance() <= SMOOTH_REACH } == true }
                }
                if (near.isEmpty()) curve else curve.copy(points = smoothed(curve.points, near.toSet(), curve.closed)).also { any = true }
            }
            if (curves.isEmpty()) continue
            val mask = repainted(next.areas[area].mask, now.edges.filter { it.area == area }.map { it.edge.curve }, curves)
            next = next.with(areas = next.areas.mapIndexed { i, each -> if (i == area) each.with(mask) else each.with(each.mask - mask) })
        }
        return next.takeIf { any }
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit
}

/** [points] with each of [which] moved [SMOOTH_PULL] of the way to the middle of its neighbours on its part and run, read round. */
internal fun smoothed(points: List<Anchor>, which: Set<Int>, closed: Boolean): List<Anchor> = points.mapIndexed { i, point ->
    if (i !in which) return@mapIndexed point
    val before = points.getOrNull(i - 1) ?: if (closed) points.last() else null
    val after = points.getOrNull(i + 1) ?: if (closed) points.first() else null
    if (before == null || after == null) return@mapIndexed point
    if (before.part != point.part || after.part != point.part || before.patch != point.patch || after.patch != point.patch) return@mapIndexed point
    val dvBefore = round(before.v - point.v)
    val dvAfter = round(after.v - point.v)
    val u = (before.u + after.u) / 2f
    val v = point.v + (dvBefore + dvAfter) / 2f
    point.copy(u = point.u + (u - point.u) * SMOOTH_PULL, v = point.v + (v - point.v) * SMOOTH_PULL)
}

private fun round(dv: Float): Float = dv - floor(dv + 0.5f)

@Composable
fun rememberEdgeEditTool(state: AppState): EdgeEditTool {
    val look = rememberSheetLook()
    return remember(state) { EdgeEditTool(state, look) }.also { it.look = look }
}

@Composable
fun rememberEdgeEraseTool(state: AppState): EdgeEraseTool {
    val look = rememberSheetLook()
    return remember(state) { EdgeEraseTool(state, look) }.also { it.look = look }
}

@Composable
fun rememberEdgeSmoothTool(state: AppState): EdgeSmoothTool {
    val look = rememberSheetLook()
    return remember(state) { EdgeSmoothTool(state, look) }.also { it.look = look }
}

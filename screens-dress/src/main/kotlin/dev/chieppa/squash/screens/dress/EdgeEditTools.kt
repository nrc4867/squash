package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentEdge
import dev.chieppa.squash.document.SquashDocument
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
 * showing as Edge shows them.
 */
abstract class EdgeWorkTool(state: AppState, internal var look: SheetLook) : SurfaceTool(state) {
    override fun overlay(view: View, camera: LockedCamera): ViewportOverlay = buildOverlay { edges(state, look, camera) }

    override fun fullOverlay(view: FullView): ViewportOverlay = buildOverlay { edges(state, look, view) }

    /** Follows the drag from [down] as one step, handing [step] each pointer place with the garment's edges as they stand. */
    protected suspend fun AwaitPointerEventScope.gesture(down: PointerInputChange, sight: SurfaceView, step: (Offset, Session) -> Unit) {
        val session = state.session.value ?: return
        val character = session.document.value.character
        val garment = state.dress.garment.value?.let(character.wardrobe::garment) ?: return
        val body = session.assembly.output.value ?: return
        val transaction: History<SquashDocument>.Transaction = session.history.begin()
        var edges = garment.edges
        var released = false
        val apply = { pixel: Offset ->
            step(pixel, Session(character, body, garment, edges, sight) { next ->
                edges = next
                transaction.update(shapeCommands(character, body, garment, next, garment.fill))
            })
        }
        try {
            apply(down.position)
            released = followToRelease(down.id, {}) { pixel -> apply(pixel) }
        } finally {
            if (released && edges != garment.edges) transaction.commit() else transaction.cancel()
        }
    }

    /** What a step of a gesture works on: the garment's [edges] so far, and [write], which gives it new ones. */
    protected class Session(
        val character: Character,
        val body: AssembledCharacter,
        val garment: Garment,
        val edges: List<GarmentEdge>,
        val sight: SurfaceView,
        val write: (List<GarmentEdge>) -> Unit,
    )
}

/** Edit curves on edges: a press on any point of the picked garment's edges drags it along the surface and picks that edge. */
@Stable
class EdgeEditTool(state: AppState, look: SheetLook) : EdgeWorkTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean =
        dragEdgePoint(state, down, sight, pickedOnly = false)

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit
}

/** Erase on edges: a press or drag over an edge of the picked garment takes the whole edge away, one step per drag. */
@Stable
class EdgeEraseTool(state: AppState, look: SheetLook) : EdgeWorkTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val session = state.session.value ?: return false
        val character = session.document.value.character
        val garment = state.dress.garment.value?.let(character.wardrobe::garment) ?: return false
        val body = session.assembly.output.value ?: return false
        nearestPoint(character, body, garment, down.position, sight, ERASE_REACH) ?: return false
        down.consume()
        state.dress.edge.value = null
        gesture(down, sight) { pixel, at ->
            val gone = at.edges.indices.filter { i ->
                shownCurves(at.character, at.edges[i].curve).any { shown ->
                    pixelsOf(at.body, shown.curve, at.sight).any { it != null && (it - pixel).getDistance() <= ERASE_REACH }
                }
            }
            if (gone.isNotEmpty()) at.write(at.edges.filterIndexed { i, _ -> i !in gone })
        }
        return true
    }

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) = Unit
}

/**
 * Smooth on edges: a drag near the picked garment's edges eases each point within reach toward the middle of its neighbours on the
 * same part, one step per drag.
 */
@Stable
class EdgeSmoothTool(state: AppState, look: SheetLook) : EdgeWorkTool(state, look) {
    override suspend fun AwaitPointerEventScope.pressedOverlay(down: PointerInputChange, sight: SurfaceView): Boolean {
        val session = state.session.value ?: return false
        val character = session.document.value.character
        val garment = state.dress.garment.value?.let(character.wardrobe::garment) ?: return false
        val body = session.assembly.output.value ?: return false
        nearestPoint(character, body, garment, down.position, sight, SMOOTH_REACH) ?: return false
        down.consume()
        gesture(down, sight) { pixel, at ->
            val next = at.edges.map { edge ->
                val shown = shownCurves(at.character, edge.curve)
                val near = edge.curve.points.indices.filter { j ->
                    shown.any { pixelsOf(at.body, it.curve, at.sight)[j]?.let { p -> (p - pixel).getDistance() <= SMOOTH_REACH } == true }
                }
                if (near.isEmpty()) edge else edge.copy(curve = edge.curve.copy(points = smoothed(edge.curve.points, near.toSet(), edge.curve.closed)))
            }
            if (next != at.edges) at.write(next)
        }
        return true
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

package dev.chieppa.squash.screens.surface

import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.draw.followToRelease
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How close to a mark's line a press takes it, in density independent pixels. */
private const val ERASE_REACH = 8f

/** Whether any of [points] that face the viewer shows within [reach] of [pixel]. */
private fun SurfaceView.touches(points: List<ResolvedPoint>, pixel: Offset, reach: Float): Boolean = points.any { point ->
    (point.normal dot toViewer(point.position)) > 0f && toPixel(point.position)?.let { (it - pixel).getDistance() <= reach } == true
}

/**
 * The commands taking away every loose mark and line weight of [work] that shows within [reach] of [pixel] through [view]. A pair's item
 * is taken once, through whichever side shows it first.
 */
fun erasedAt(work: BodySurfaceWork, view: SurfaceView, pixel: Offset, reach: Float, thisSide: (PartId) -> Boolean, seen: MutableSet<String> = HashSet()): List<DocumentCommand> =
    buildList {
        for ((part, held) in work.parts) {
            for (mark in held.looseMarks()) {
                if (view.touches(mark.points, pixel, reach) && seen.add(mark.id.value)) add(SetMark(part, mark.id, null, thisSide(part)))
            }
            for (weight in held.lineWeights) {
                if (view.touches(weight.points, pixel, reach) && seen.add(weight.id.value)) add(SetLineWeight(part, weight.id, null, thisSide(part)))
            }
        }
    }

/** Erase on the surface: a press or a drag takes away the strokes and line weights it crosses, each one undo step. */
class SurfaceEraseTool(state: AppState, var reach: Float) : SurfaceTool(state) {
    override val showsPreviews: Boolean get() = false

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val seen = HashSet<String>()
        val thisSide = { part: PartId -> state.thisSide && session.document.value.character.part(part)?.pair != null }
        fun eraseAt(pixel: Offset) {
            val work = session.bodySurfaceWork.output.value ?: return
            erasedAt(work, sight, pixel, reach, thisSide, seen).forEach(session::send)
        }
        eraseAt(down.position)
        followToRelease(down.id, {}, ::eraseAt)
    }
}

@Composable
fun rememberSurfaceEraseTool(state: AppState): SurfaceEraseTool {
    val reach = ERASE_REACH * LocalDensity.current.density
    return remember(state) { SurfaceEraseTool(state, reach) }.also { it.reach = reach }
}

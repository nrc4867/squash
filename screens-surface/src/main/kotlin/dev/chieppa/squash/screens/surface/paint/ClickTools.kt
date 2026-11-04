package dev.chieppa.squash.screens.surface.paint

import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.surface.SurfaceTool
import dev.chieppa.squash.screens.surface.SurfaceHit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.screens.AppState

/** Colors the section under a press with the current slot, in one step. */
@Stable
class FillTool(state: AppState) : SurfaceTool(state) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val slot = state.paint.slotIn(session.document.value.character.palette) ?: return
        session.fillCommand(session.sectionAt(hit, state.paint.fillReach), slot, state.thisSide)?.let(session::send)
    }
}

/** Makes the slot showing under a press the current one. */
@Stable
class PickColorTool(state: AppState) : SurfaceTool(state) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        session.slotAt(hit)?.let { state.paint.slot = it }
    }
}

@Composable
fun rememberFillTool(state: AppState): FillTool = remember(state) { FillTool(state) }

@Composable
fun rememberPickColorTool(state: AppState): PickColorTool = remember(state) { PickColorTool(state) }

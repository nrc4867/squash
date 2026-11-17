package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.choosePart
import dev.chieppa.squash.screens.sheet.SurfaceView

/**
 * Select, what a press does with no other tool armed: it chooses the part under it, and a press off the character lets the choice go. It never
 * paints or draws. The panes keep their look for the selected part.
 */
@Stable
class SelectPartTool(state: AppState) : SurfaceTool(state) {
    override val showsWhole: Boolean get() = false

    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        state.choosePart(hit.part)
    }

    override fun pressedOffSurface() {
        state.choosePart(null)
    }
}

@Composable
fun rememberSelectPartTool(state: AppState): SelectPartTool = remember(state) { SelectPartTool(state) }

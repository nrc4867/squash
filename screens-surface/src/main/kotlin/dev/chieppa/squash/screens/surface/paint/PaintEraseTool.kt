package dev.chieppa.squash.screens.surface.paint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import dev.chieppa.squash.character.build.ShapeSource
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.SurfaceTool

/**
 * Color's Erase: takes away each brush stroke and region the pointer presses or drags over, whichever shows on top there, as one undo step. A
 * drag the pane loses takes nothing away.
 */
@Stable
class PaintEraseTool(state: AppState) : SurfaceTool(state) {
    override suspend fun AwaitPointerEventScope.pressed(hit: SurfaceHit, down: PointerInputChange, sight: SurfaceView) {
        val session = state.session.value ?: return
        val gesture = session.history.begin()
        val erased = LinkedHashSet<ShapeSource>()
        val commands = ArrayList<DocumentCommand>()
        fun eraseAt(place: SurfaceHit) {
            val source = session.erasableAt(place) ?: return
            if (!erased.add(source)) return
            commands += session.eraseCommand(source, state.thisSide) ?: return
            gesture.update(Together(commands.toList()))
        }
        eraseAt(hit)
        var released = false
        try {
            released = followSurface(hit, down, sight) { eraseAt(it) }
        } finally {
            if (released && commands.isNotEmpty()) gesture.commit() else gesture.cancel()
        }
    }
}

@Composable
fun rememberPaintEraseTool(state: AppState): PaintEraseTool = remember(state) { PaintEraseTool(state) }

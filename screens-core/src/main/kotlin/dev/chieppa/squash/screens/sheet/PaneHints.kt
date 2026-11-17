package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.Stage

/**
 * The hints the panes draw with under [tool]: every part in full where the tool works across parts, in Rig with [rigging] and in Dress, and
 * the chosen part over the rest faded where the tool keeps to it.
 */
@Composable
fun paneHints(state: AppState, tool: PaneTool?, rigging: Boolean): SheetHints {
    val stage by state.stage.collectAsState()
    val chosen by state.partChosen.collectAsState()
    val whole = tool?.showsWhole == true || rigging || stage == Stage.DRESS
    val focus = whole && !rigging && chosen && tool?.keepsToChosenPart == true
    return SheetHints(state.curves.templateHint, state.curves.ghost, whole, focus)
}

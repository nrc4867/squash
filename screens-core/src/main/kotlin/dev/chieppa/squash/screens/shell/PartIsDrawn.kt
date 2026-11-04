package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.chieppa.squash.screens.AppState

/** Whether the selected part has an outline drawn, which the tools and the drawer read as a part being reshaped. */
@Composable
fun rememberPartIsDrawn(state: AppState): Boolean {
    val session by state.session.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val document = session?.document?.collectAsState()?.value
    return selected?.let { document?.character?.resolved(it) }?.outlines?.isNotEmpty() == true
}

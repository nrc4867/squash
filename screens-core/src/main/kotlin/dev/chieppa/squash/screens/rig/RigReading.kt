package dev.chieppa.squash.screens.rig

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.seamStrokes
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.screens.AppState

/** The open character's rig as the graph last derived it, or null before it has run or with nothing open. */
fun AppState.rigNow(): Rig? = posing.value?.rig?.rig?.output?.value

/** The open character's rig as the graph derives it, or null before it has run or with nothing open. */
@Composable
fun rememberRig(state: AppState): Rig? {
    val posing by state.posing.collectAsState()
    val node = posing?.rig?.rig ?: return null
    val rig by remember(node) { node.output }.collectAsState()
    return rig
}

/** How many of [rig]'s seams hold a stroke on [character]. */
fun paintedSeams(rig: Rig, character: Character): Int = rig.seams.count { character.seamStrokes(it.child).isNotEmpty() }

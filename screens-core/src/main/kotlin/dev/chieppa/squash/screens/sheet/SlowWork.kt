package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long work runs before the panes show its progress, so quick work never flashes a bar. */
const val SLOW_WORK_MILLIS = 1_000L

/**
 * Whether [working] has held for [after] milliseconds without a break: the moment work's progress is worth showing. False again as
 * soon as the work stops.
 */
@Composable
fun rememberSlowWork(working: Boolean, after: Long = SLOW_WORK_MILLIS): Boolean {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        slow = false
        if (!working) return@LaunchedEffect
        delay(after)
        slow = true
    }
    return working && slow
}

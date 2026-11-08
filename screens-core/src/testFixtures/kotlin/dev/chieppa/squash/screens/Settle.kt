package dev.chieppa.squash.screens

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.fail

/** How many rounds of running queued work and letting the UI take it in [settle] tries before it fails. */
const val SETTLE_ROUNDS = 50

/**
 * Runs [work]'s queued work and lets the UI take it in, round after round, until [done] holds and the open character's graph,
 * if [state] has one, is settled. Fails when they never do. Time never passes: every round runs only what is already queued.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.settle(work: TestWork, state: AppState? = null, done: () -> Boolean = { true }) {
    repeat(SETTLE_ROUNDS) {
        work.drain()
        waitForIdle()
        if (state?.session?.value?.settled != false && done()) {
            work.drain()
            waitForIdle()
            return
        }
    }
    fail("Not settled after $SETTLE_ROUNDS rounds of queued work")
}

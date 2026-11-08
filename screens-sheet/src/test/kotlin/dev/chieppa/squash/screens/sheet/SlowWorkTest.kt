package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Progress shows only for work that has run for a while. */
@OptIn(ExperimentalTestApi::class)
class SlowWorkTest {
    @Test
    fun workShowsOnlyAfterItHasRunForTheWholeGraceAndHidesTheMomentItStops() = runComposeUiTest {
        var working by mutableStateOf(false)
        var shown = false
        mainClock.autoAdvance = false
        setContent { shown = rememberSlowWork(working) }
        working = true
        mainClock.advanceTimeBy(SLOW_WORK_MILLIS / 2)
        assertFalse(shown, "quick work shows nothing")
        working = false
        mainClock.advanceTimeByFrame()
        working = true
        mainClock.advanceTimeBy(SLOW_WORK_MILLIS * 3 / 4)
        assertFalse(shown, "a break starts the grace again")
        mainClock.advanceTimeBy(SLOW_WORK_MILLIS / 2)
        assertTrue(shown, "slow work shows")
        working = false
        mainClock.advanceTimeByFrame()
        assertFalse(shown, "and hides as it stops")
    }
}

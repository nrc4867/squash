package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.screens.single.FitProgressBar
import dev.chieppa.squash.screens.single.SingleTags
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test

/** The bars that show the graph's count while a fit runs. */
@OptIn(ExperimentalTestApi::class)
class FitBarsTest {
    @Test
    fun fullShowsRefitsBarWhileTheFitRuns() = runComposeUiTest {
        setStretchContent { PaneViewport(null, PaneChip.REFIT, Modifier, progress = 0.5f) }
        onNodeWithTag(REFIT_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun singleShowsTheSurfacesStageBarAndPercent() = runComposeUiTest {
        setStretchContent { FitProgressBar(0.25f) }
        onNodeWithTag(SingleTags.FIT).assertIsDisplayed()
        onNodeWithText("Surface").assertIsDisplayed()
        onNodeWithText("25%").assertIsDisplayed()
    }
}

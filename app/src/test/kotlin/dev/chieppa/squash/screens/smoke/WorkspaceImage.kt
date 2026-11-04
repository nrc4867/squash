package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import dev.chieppa.squash.screens.sheet.PANE_CORNER_TAG
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.single.SingleTags
import dev.chieppa.stretch.gpu.GpuImage
import kotlin.math.roundToInt

/** The workspace as a person sees it in Single: its window with [image] under the viewport's floating corners. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.keepSingleWorkspace(image: GpuImage, name: String) {
    val workspace = onNodeWithTag(ShellTags.WORKSPACE)
    val window = workspace.captureToImage().argbPixels()
    val width = workspace.fetchSemanticsNode().size.width
    val bounds = onNodeWithTag(SingleTags.VIEWPORT).fetchSemanticsNode().boundsInWindow
    window.paste(width, image.pixels, image.width, bounds.left.roundToInt(), bounds.top.roundToInt())
    for (tag in listOf(SingleTags.TOOLBAR, SingleTags.HUD, SingleTags.THUMBNAILS)) {
        val corner = onNodeWithTag(tag)
        val node = corner.fetchSemanticsNode()
        window.paste(width, corner.captureToImage().argbPixels(), node.size.width, node.boundsInWindow.left.roundToInt(), node.boundsInWindow.top.roundToInt())
    }
    keepImage(window, width, name)
}

/** The workspace as a person sees it in the panes' layouts: its window with [image] at [left], [top], under every pane's label and chip. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.keepPanesWorkspace(image: GpuImage, left: Int, top: Int, name: String) {
    val workspace = onNodeWithTag(ShellTags.WORKSPACE)
    val window = workspace.captureToImage().argbPixels()
    val width = workspace.fetchSemanticsNode().size.width
    window.paste(width, image.pixels, image.width, left, top)
    val corners = onAllNodesWithTag(PANE_CORNER_TAG)
    for (i in corners.fetchSemanticsNodes().indices) {
        val corner = corners[i]
        val node = corner.fetchSemanticsNode()
        window.paste(width, corner.captureToImage().argbPixels(), node.size.width, node.boundsInWindow.left.roundToInt(), node.boundsInWindow.top.roundToInt())
    }
    keepImage(window, width, name)
}

package dev.chieppa.squash.screens.shell

import dev.chieppa.stretch.frame.DrawerPlacement
import dev.chieppa.stretch.frame.FrameLayout
import dev.chieppa.stretch.frame.Side

/** The Build frame's drawers. */
object DrawerIds {
    const val PARTS = "parts"
    const val TYPES = "types"
    const val REFERENCES = "references"
    const val HISTORY = "history"
    const val HELP = "help"
    const val INSPECTOR = "inspector"
}

private const val LEFT_WIDTH = FrameLayout.MIN_WIDTH
private const val RIGHT_WIDTH = 296f

/** The References drawer's width, which holds two images side by side on its board. */
private const val REFERENCES_WIDTH = 440f

val DEFAULT_LAYOUT = FrameLayout(
    listOf(
        DrawerPlacement(DrawerIds.PARTS, Side.Left, width = LEFT_WIDTH, open = true),
        DrawerPlacement(DrawerIds.TYPES, Side.Left, width = LEFT_WIDTH, bottom = true, railTop = true),
        DrawerPlacement(DrawerIds.REFERENCES, Side.Left, width = REFERENCES_WIDTH),
        DrawerPlacement(DrawerIds.HISTORY, Side.Left, width = LEFT_WIDTH),
        DrawerPlacement(DrawerIds.HELP, Side.Left, width = LEFT_WIDTH, bottom = true),
        DrawerPlacement(DrawerIds.INSPECTOR, Side.Right, width = RIGHT_WIDTH, open = true),
    ),
)

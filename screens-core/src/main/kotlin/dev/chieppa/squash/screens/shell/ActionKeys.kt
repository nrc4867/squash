package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.staticCompositionLocalOf

/** The key caps bound to an action, by its id, as a tooltip shows them. The app provides it from its keymap. */
val LocalActionKeys = staticCompositionLocalOf<(String) -> List<String>> { { emptyList() } }

/** The ids of the actions a control outside the shell names in its tooltip. */
object ActionKeys {
    const val MIRROR = "mirror"
    const val HALF_OUTLINE = "half-outline"
    const val FIT_PANE = "fit-pane"
    const val PARTS = "drawer-parts"
    const val ADD_PART = "tool-add-part"
    const val INSPECTOR = "drawer-inspector"
    const val NEW = "new"
    const val OPEN = "open"
}

/** The id of the action that arms a tool. */
val BuildTool.actionId: String get() = "tool-" + name.lowercase().replace('_', '-')

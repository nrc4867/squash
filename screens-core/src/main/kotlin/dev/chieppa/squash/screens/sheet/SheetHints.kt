package dev.chieppa.squash.screens.sheet

/**
 * What the Hint section shows around a part: the template's outline where a view is not drawn, and the rest of the character at [ghost]
 * strength. With [whole] a view shows every part in full and none of that.
 */
data class SheetHints(val template: Boolean = true, val ghost: Float = DEFAULT_GHOST, val whole: Boolean = false)

/** The board's ghost strength. */
const val DEFAULT_GHOST = 0.4f

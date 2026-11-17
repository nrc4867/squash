package dev.chieppa.squash.screens.sheet

/**
 * What the Hint section shows around a part: the template's outline where a view is not drawn, and the rest of the character at [ghost]
 * strength. With [whole] a view shows every part in full and none of that; with [focus] as well, every view shows the selection in full
 * over the rest of the character at [ghost] strength, with no outlines or guides.
 */
data class SheetHints(val template: Boolean = true, val ghost: Float = DEFAULT_GHOST, val whole: Boolean = false, val focus: Boolean = false) {
    /** Whether a view shows every part in full. */
    val allInFull: Boolean get() = whole && !focus
}

/** The board's ghost strength. */
const val DEFAULT_GHOST = 0.4f

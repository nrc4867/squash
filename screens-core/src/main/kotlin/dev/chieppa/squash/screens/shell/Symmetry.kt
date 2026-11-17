package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.build.centreLine
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.CentreLine
import dev.chieppa.squash.screens.AppState

/** The views a part down the body's middle is the same left and right in: those that look down on it or along it. */
val SYMMETRIC_VIEWS: Set<View> = setOf(View.TOP, View.FRONT)

/**
 * The character's line of symmetry in [view] of the selected part: its centre line where the part has no pair and no side,
 * so it runs down the body's middle, and [view] shows left and right; null elsewhere, where the outline has no mirror.
 */
fun AppState.symmetryIn(view: View): CentreLine? {
    val character = session.value?.document?.value?.character ?: return null
    val part = selectedPart.value?.let(character::part) ?: return null
    if (part.pair != null || part.side != null || view !in SYMMETRIC_VIEWS) return null
    return centreLine(view)
}

/** Whether Mirror on draws [view]'s outline as a half and its mirror image. */
fun AppState.mirrorsIn(view: View): Boolean = mirror.value && symmetryIn(view) != null

/** Whether the selected part is one down the body's middle, which mirrors only in [SYMMETRIC_VIEWS]. */
fun AppState.selectedIsMiddle(): Boolean {
    val character = session.value?.document?.value?.character ?: return false
    val part = selectedPart.value?.let(character::part) ?: return false
    return part.pair == null && part.side == null
}

package dev.chieppa.squash.screens.edit

import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.shell.Stage

/**
 * What the outline tools draw on now: in Dress, the picked garment's Redraw of the selected part while it is being redrawn, else the
 * garment's branch over it, or nothing where it covers none; elsewhere the selected part's own outlines.
 */
fun AppState.outlineTarget(): OutlineTarget? {
    val character = session.value?.document?.value?.character ?: return null
    val part = selectedPart.value?.takeIf { character.part(it) != null } ?: return null
    if (stage.value != Stage.DRESS) return OutlineTarget.Part(part, thisSide)
    val garment = dress.garment.value ?: return null
    return character.dressTarget(garment, part, dress.redrawing.value)
}

/** What an edit in [view] starts from, which is where the edit will land, on [outlineTarget]. */
fun AppState.editedOutline(view: View): OutlineCurve? {
    val character = session.value?.document?.value?.character ?: return null
    return outlineTarget()?.let(character::outlinesOf)?.get(view)
}

/** What makes a curve the outline in a view of what the tools draw on now, or clears it with null; bound to that target when called. */
fun AppState.outlineWriter(): ((View, OutlineCurve?) -> DocumentCommand?)? {
    val target = outlineTarget() ?: return null
    return { view, curve -> session.value?.document?.value?.character?.writeOutline(target, view, curve) }
}

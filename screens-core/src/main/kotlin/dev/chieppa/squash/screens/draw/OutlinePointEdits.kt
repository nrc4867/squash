package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.build.withPointDeleted
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.editedOutline
import dev.chieppa.squash.screens.edit.outlineWriter

/** The selected point's outline without that point, refitted where it was, or null if it cannot go: a handle, an open end or too few points. */
private fun AppState.withoutSelectedPoint(): Pair<View, OutlineCurve>? {
    if (curves.drawing != null || curves.dragging) return null
    val selection = curves.selection?.takeIf { it.part == selectedPart.value } ?: return null
    val curve = editedOutline(selection.view)?.takeIf { selection.index in it.points.indices } ?: return null
    return withPointDeleted(curve, selection.index)?.let { selection.view to it }
}

/** Whether Delete point can take the selected point out. */
fun AppState.canDeletePoint(): Boolean = withoutSelectedPoint() != null

/** Takes the selected anchor out of its outline, the two sections either side refitted as one, as one undo step. */
fun AppState.deleteSelectedPoint() {
    val (view, curve) = withoutSelectedPoint() ?: return
    val command = outlineWriter()?.invoke(view, curve) ?: return
    curves.selection = null
    session.value?.send(command)
}

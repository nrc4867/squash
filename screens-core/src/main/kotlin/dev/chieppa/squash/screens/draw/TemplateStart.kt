package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.build.templateOutline
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetLayout

/** Whether Start from template can act on [view]: the selected part has no outline there and no stroke is under way. */
fun AppState.canStartFromTemplate(view: View): Boolean {
    val character = session.value?.document?.value?.character ?: return false
    val part = selectedPart.value?.let(character::part) ?: return false
    return curves.drawing == null && view !in character.resolved(part).outlines
}

/** The view Start from template acts on from the keyboard or a menu: the one on show, when it can act there. */
fun AppState.templateStartView(): View? {
    if (layout.value == SheetLayout.SINGLE && curves.snapped == null) return null
    return activeView.value.takeIf { canStartFromTemplate(it) }
}

/** Turns the template hint in [view] into an ordinary outline, as one undo step. */
fun AppState.startFromTemplate(view: View) {
    if (!canStartFromTemplate(view)) return
    val opened = session.value ?: return
    val id = selectedPart.value ?: return
    val part = opened.document.value.character.part(id) ?: return
    val curve = templateOutline(opened.templates[part.template], opened.document.value.character.resolved(part), view) ?: return
    opened.send(SetOutline(id, view, curve, thisSide))
}

/** Start from template in [AppState.templateStartView]. */
fun AppState.startFromTemplate() {
    templateStartView()?.let { startFromTemplate(it) }
}

package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.build.templateOutline
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SheetLayout

/** The view Start from template would act on: the one on show, while the selected part has no outline there and no stroke is under way. */
fun AppState.templateStartView(): View? {
    val character = session.value?.document?.value?.character ?: return null
    val part = selectedPart.value?.let(character::part) ?: return null
    if (curves.drawing != null || (layout.value == SheetLayout.SINGLE && curves.snapped == null)) return null
    return activeView.value.takeIf { it !in character.resolved(part).outlines }
}

/** Turns the template hint of [AppState.templateStartView] into an ordinary outline, as one undo step. */
fun AppState.startFromTemplate() {
    val view = templateStartView() ?: return
    val opened = session.value ?: return
    val id = selectedPart.value ?: return
    val part = opened.document.value.character.part(id) ?: return
    val curve = templateOutline(opened.templates[part.template], opened.document.value.character.resolved(part), view) ?: return
    opened.send(SetOutline(id, view, curve, thisSide))
}

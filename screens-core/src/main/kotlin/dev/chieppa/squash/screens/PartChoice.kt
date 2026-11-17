package dev.chieppa.squash.screens

import dev.chieppa.squash.document.PartId

/** Selects [part] as a person's choice, or, with null, lets the choice go and keeps the part selected for the panes. */
fun AppState.choosePart(part: PartId?) {
    if (part != null) selectedPart.value = part
    partChosen.value = part != null
}

/** The part a person chose, which the tools that [dev.chieppa.squash.screens.sheet.PaneTool.keepsToChosenPart] keep to, or null. */
fun AppState.chosenPart(): PartId? = selectedPart.value.takeIf { partChosen.value }

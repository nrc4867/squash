package dev.chieppa.squash.screens

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import kotlinx.coroutines.flow.MutableStateFlow

/** Session state above the document: where the window is, what is open and what is selected. Never saved or undone. */
class AppState(val templates: TemplateFolder) {
    val screen = MutableStateFlow(Screen.HOME)
    val session = MutableStateFlow<CharacterSession?>(null)
    val selectedPart = MutableStateFlow<PartId?>(null)
}

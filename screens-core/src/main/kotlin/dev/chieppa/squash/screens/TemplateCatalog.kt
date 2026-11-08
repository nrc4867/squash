package dev.chieppa.squash.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.load.save
import dev.chieppa.squash.character.model.PartTemplate
import java.nio.file.Path

/**
 * The templates and body plans the app has loaded, which a template saved from a part joins at once. A view that reads [folder]
 * recomposes when a template joins it. See HLD: data-model, Save as template.
 */
class TemplateCatalog(loaded: TemplateFolder) {
    var folder: TemplateFolder by mutableStateOf(loaded)
        private set

    /** Writes [template] into the saved templates' folder and loads it, returning where, or null when there is no such folder. */
    fun save(template: PartTemplate): Path? {
        val file = folder.save(template) ?: return null
        folder = folder.copy(parts = folder.parts + template)
        return file
    }
}

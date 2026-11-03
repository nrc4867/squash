package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.TemplateRef

/** Every loaded template by its pinned id and version. */
class TemplateLibrary(templates: List<PartTemplate>) {
    private val byRef = templates.associateBy { it.ref }

    operator fun get(ref: TemplateRef): PartTemplate =
        requireNotNull(byRef[ref]) { "No template ${ref.id.value} version ${ref.version}" }
}

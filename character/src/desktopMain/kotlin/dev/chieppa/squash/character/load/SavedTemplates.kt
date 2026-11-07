package dev.chieppa.squash.character.load

import dev.chieppa.squash.character.build.savedTemplateText
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.TemplateId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * These templates with those saved from parts in [folder] after them, in name order, and [folder] as where the next is saved. A
 * saved file that does not load, or names a template already loaded, is left out. See HLD: parts, Blank mode.
 */
fun TemplateFolder.withSaved(folder: Path): TemplateFolder {
    val files = if (folder.exists()) folder.listDirectoryEntries().filter { it.name.endsWith(PART_SUFFIX) }.sortedBy { it.name } else emptyList()
    val loaded = files.fold(parts) { held, file ->
        val template = runCatching { TemplateLoader.loadPart(file.name, file.readText()) }.getOrNull()
        if (template == null || held.any { it.id == template.id }) held else held + template
    }
    return copy(parts = loaded, saved = folder)
}

/** An id for a template saved as [wanted] that no loaded template has: [wanted], or it with the first free number after it. */
fun TemplateFolder.freeId(wanted: TemplateId): TemplateId =
    generateSequence(1) { it + 1 }.map { if (it == 1) wanted else TemplateId("${wanted.value}-$it") }.first { id -> parts.none { it.id == id } }

/** Writes [template] into the saved templates' folder as its own file, returning where, or null when there is no such folder. */
fun TemplateFolder.save(template: PartTemplate): Path? {
    val folder = saved ?: return null
    Files.createDirectories(folder)
    val file = folder.resolve(template.id.value + PART_SUFFIX)
    file.writeText(savedTemplateText(template))
    return file
}

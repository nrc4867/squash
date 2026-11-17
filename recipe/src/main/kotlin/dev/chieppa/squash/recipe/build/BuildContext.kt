package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.recipe.RecipeException
import dev.chieppa.squash.recipe.model.Recipe
import dev.chieppa.squash.recipe.model.RecipePart

/** The ids a recipe part stands for: one part, or a pair's two sides. */
internal data class PartIds(val left: PartId, val right: PartId?) {
    val all: List<PartId> get() = listOfNotNull(left, right)
}

/**
 * A recipe's build as it runs: the document so far, the commands sent to make it, what the recipe names, and lookups that fail with
 * the recipe path of what they were asked for.
 */
internal class BuildContext(val recipe: Recipe, val templates: TemplateFolder, val plan: BodyPlan, start: SquashDocument) {
    var document: SquashDocument = start
        private set
    val commands = mutableListOf<DocumentCommand>()
    val warnings = mutableListOf<String>()

    /** Each head's face map, built once from the head as drawn when a place on its face is first asked for. */
    val faceMaps = mutableMapOf<PartId, FaceMap?>()

    /** The recipe's parts by every name they may be referred to by: a pair by its id and its sides' ids. */
    private val named = mutableMapOf<String, PartIds>()

    /** The slots the palette names. */
    private val slots: Set<String> = recipe.palette.map { it.slot }.toSet()

    /** Applies [command] to the document, failing at [path] with the command's own reason if it refuses. */
    fun send(path: String, command: DocumentCommand) {
        document = try {
            command.apply(document).document
        } catch (e: IllegalArgumentException) {
            throw RecipeException(path, e.message ?: e.toString())
        } catch (e: IllegalStateException) {
            throw RecipeException(path, e.message ?: e.toString())
        } catch (e: NoSuchElementException) {
            throw RecipeException(path, e.message ?: e.toString())
        }
        commands += command
    }

    fun fail(path: String, message: String): Nothing = throw RecipeException(path, message)

    /** What [make] makes, failing at [path] with the document type's own reason when it refuses the values. */
    fun <T> guard(path: String, make: () -> T): T = try {
        make()
    } catch (e: IllegalArgumentException) {
        fail(path, e.message ?: e.toString())
    }

    fun warn(path: String, message: String) {
        warnings += "$path: $message"
    }

    /** Records [part]'s ids, failing at [path] when one is taken. */
    fun name(path: String, part: RecipePart): PartIds {
        val ids = part.pair?.let { PartIds(PartId(it.left ?: "${part.id}-l"), PartId(it.right ?: "${part.id}-r")) } ?: PartIds(PartId(part.id), null)
        val names = (listOf(part.id) + ids.all.map { it.value }).distinct()
        names.firstOrNull { it in named }?.let { fail("$path.id", "a part is already called \"$it\"") }
        names.forEach { named[it] = ids }
        return ids
    }

    /** The parts [name] stands for: a pair's two sides by the pair's id, one side by its own id, or a single part. */
    fun idsOf(path: String, name: String): PartIds {
        val ids = named[name] ?: fail(path, "no part \"$name\" above this point; parts so far: ${named.keys.sorted().joinToString()}")
        if (ids.right != null && name == ids.left.value) return PartIds(ids.left, null)
        if (ids.right != null && name == ids.right.value) return PartIds(ids.right, null)
        return ids
    }

    /** The one part [name] refers to: a single part, a side by its id, or a pair's left side by the pair's id. */
    fun partOf(path: String, name: String): PartId = idsOf(path, name).left

    /** The template [id] at [version], or its newest, failing at [path] with the ids there are. */
    fun template(path: String, id: String, version: Int?): PartTemplate {
        val found = if (version == null) templates.template(TemplateId(id)) else templates.template(TemplateRef(TemplateId(id), version))
        if (found != null) return found
        val versions = templates.parts.filter { it.id.value == id }.map { it.version }.sorted()
        if (versions.isNotEmpty()) fail(path, "template $id has no version $version; it has ${versions.joinToString()}")
        fail(path, "no template \"$id\"; templates: ${templates.parts.map { it.id.value }.distinct().sorted().joinToString()}")
    }

    /** The template [part] is built from as the document holds it. */
    fun templateOf(part: PartId): PartTemplate {
        val instance = checkNotNull(document.character.part(part)) { "No part ${part.value}" }
        return checkNotNull(templates.template(instance.template)) { "No template ${instance.template}" }
    }

    /** [name] as a palette slot, failing at [path] when the palette has none. */
    fun slot(path: String, name: String): SlotName {
        if (name !in slots) fail(path, "no palette slot \"$name\"; the palette has ${slots.sorted().joinToString().ifEmpty { "none" }}")
        return SlotName(name)
    }
}

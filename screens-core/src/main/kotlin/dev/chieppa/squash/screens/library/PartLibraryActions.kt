package dev.chieppa.squash.screens.library

import dev.chieppa.squash.character.catalog.OpenSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.RemoveParts
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.changeTemplate
import dev.chieppa.squash.screens.templateChoices

/** What a pick in Library's Parts half does: add on the socket it targets, change the selected part's template, or nothing. */
sealed interface LibraryMode {
    /** Whether a pick of [template] does something here; the rest of the grid dims. */
    fun lit(template: TemplateId): Boolean

    data class Add(val socket: OpenSocket) : LibraryMode {
        override fun lit(template: TemplateId) = template in socket.takes
    }

    /** The selected [part], its [own] template, and the templates it can change to keeping its drawing. */
    data class Replace(val part: PartInstance, val own: TemplateId, val choices: Set<TemplateId>) : LibraryMode {
        override fun lit(template: TemplateId) = template == own || template in choices
    }

    data object Browse : LibraryMode {
        override fun lit(template: TemplateId) = true
    }
}

/** What Library's Parts half does now: Add while it targets a socket, else Replace for a selected part a garment does not own. */
fun AppState.libraryMode(): LibraryMode {
    addingSocket.value?.let { return LibraryMode.Add(it) }
    val document = session.value?.document?.value ?: return LibraryMode.Browse
    val part = selectedPart.value?.let(document.character::part)?.takeIf(::replaceable) ?: return LibraryMode.Browse
    return LibraryMode.Replace(part, part.template.id, templateChoices(part.id).map { it.id }.toSet())
}

/** Whether Library and the Parts menu offer Replace and Remove for [part]: one on a socket that no garment owns. */
fun replaceable(part: PartInstance): Boolean = part.socket != null && part.garment == null

/** Picks [type] in Library, as Enter or a double-click does: adds it on the target, or gives it to the selected part. */
fun AppState.pickFromLibrary(type: TemplateId) {
    when (val mode = libraryMode()) {
        is LibraryMode.Add -> addPart(type)
        is LibraryMode.Replace -> if (type in mode.choices) templates.template(type)?.let { replacePart(mode.part.id, it) }
        LibraryMode.Browse -> Unit
    }
}

/** Gives [part] and its pair's other side [template], keeping what they hold, one undo step. */
fun AppState.replacePart(part: PartId, template: PartTemplate) {
    changeTemplate(part, template)
    library.added(template.id)
}

/** [part], the other side of its pair and every part on any of them, outward from [part]. */
fun Character.removalOf(part: PartId): List<PartId> {
    val start = part(part) ?: return emptyList()
    val sides = listOfNotNull(start, twinOf(start)).map { it.id }
    val taken = sides.toMutableList()
    var reach = sides.toSet()
    while (reach.isNotEmpty()) {
        val on = parts.filter { it.socket?.parent in reach && it.id !in taken }.map { it.id }
        taken += on
        reach = on.toSet()
    }
    return taken
}

/** [wardrobe] with nothing left on [gone]: no branch, join, change or loose end there. */
fun wardrobeWithout(wardrobe: Wardrobe, gone: Set<PartId>): Wardrobe = wardrobe.copy(
    garments = wardrobe.garments.map { garment ->
        garment.copy(
            branches = garment.branches.filter { it.part !in gone },
            joins = garment.joins - gone,
            changes = garment.changes - gone,
            looseEnds = garment.looseEnds.filter { it.branch !in gone },
        )
    },
)

/**
 * The step that removes [part] with the other side of its pair and every part on them, with what garments held there, or null for a
 * part Remove does not take.
 */
fun Character.removePartsCommand(part: PartId): DocumentCommand? {
    val instance = part(part)?.takeIf(::replaceable) ?: return null
    val gone = removalOf(instance.id)
    val remove = RemoveParts(gone)
    val kept = wardrobeWithout(wardrobe, gone.toSet())
    return if (kept == wardrobe) remove else Together(listOf(remove, SetWardrobe(kept)))
}

/** Removes [part], the other side of its pair and every part on them, one undo step. */
fun AppState.removePart(part: PartId) {
    val open = session.value ?: return
    open.document.value.character.removePartsCommand(part)?.let(open::send)
}

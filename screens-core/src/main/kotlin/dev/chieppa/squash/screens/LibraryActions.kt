package dev.chieppa.squash.screens

import dev.chieppa.squash.character.catalog.newerThan
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.UpdateFromLibrary

/** The newer version of [part]'s template the library holds, shipped or saved, or null while it pins the newest. */
fun AppState.libraryUpdate(part: PartId): PartTemplate? {
    val instance = session.value?.document?.value?.character?.part(part) ?: return null
    return templates.newerThan(instance.template)
}

/** Moves [part] and its pair's other side to the library's newest version of their template, keeping all they hold, one undo step. See HLD: data-model, Update from library. */
fun AppState.updateFromLibrary(part: PartId) {
    val newer = libraryUpdate(part) ?: return
    session.value?.send(UpdateFromLibrary(part, newer.version))
}

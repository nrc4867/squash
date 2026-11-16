package dev.chieppa.squash.screens.toucan

import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.face.eyeLandmark
import dev.chieppa.squash.character.face.newEyesCommand
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId

/**
 * The toucan with drawn eyes: Add eyes' two starter eyes about the bird head's side eye landmark, one on each side, for what only
 * side-set drawn eyes show.
 */
fun toucanDrawnEyesDocument(): SquashDocument {
    val parts = toucanDocument()
    val head = checkNotNull(TemplateFolder.shipped().template(TemplateId("head-bird")))
    val eyes = checkNotNull(newEyesCommand(parts.character, HEAD, eyeLandmark(head)))
    return eyes.apply(parts).document
}

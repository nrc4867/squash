package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.screens.fennec.ref

/**
 * Her face drawn flat as seen from straight ahead, x to her left and y up in the head's drawn units from the neck's top, mapped onto the
 * head as drawn, so the eyes, brows, lips, cheeks and the shades' bridge are placed by where they show from the front.
 */
internal val FACE: FaceMap by lazy {
    val template = TemplateFolder.shipped().parts.single { it.ref == ref("head-round") }
    checkNotNull(FaceMap.of(HEAD, template, PartData(HEAD_OUTLINES, mapOf("size" to HEAD_SIZE))))
}

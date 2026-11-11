package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.FaceMap
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.screens.fennec.ref

/** Her face drawn flat as seen from straight ahead, x to her left and y up in the head's drawn units from the neck's top, mapped onto the head as drawn. */
internal val FACE: FaceMap by lazy {
    val template = TemplateFolder.shipped().parts.single { it.ref == ref("head-round") }
    checkNotNull(FaceMap.of(HEAD, template, PartData(HEAD_OUTLINES, mapOf("size" to HEAD_SIZE))))
}

/** How far above the neck's top the reference's head centre lies, in the head's drawn units, which the face's numbers are read from. */
internal const val HEAD_CENTRE = 0.09f

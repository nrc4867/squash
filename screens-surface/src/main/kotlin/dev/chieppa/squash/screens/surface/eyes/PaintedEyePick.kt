package dev.chieppa.squash.screens.surface.eyes

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.FaceFeature
import dev.chieppa.squash.screens.surface.faceHead

/** Whether Face works on the eyes as painted eyes: Eyes is picked. */
fun AppState.paintsEyes(): Boolean = face.feature == FaceFeature.EYES

/** The painted eye the Face stage works on in [session]'s character: its head's first, while Face works on painted eyes. */
fun AppState.pickedPaintedEye(session: CharacterSession): PaintedEye? {
    val character = session.document.value.character
    if (!paintsEyes()) return null
    val head = character.faceHead(session.templates) ?: return null
    return character.paintedEyes.firstOrNull { it.anchor.part == head }
}

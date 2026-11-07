package dev.chieppa.squash.screens.surface.pose

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.stretch.core.History

/** A slider's drag as one undo step: each change previews on one open transaction, which [done] writes. */
internal class SliderGesture(private val session: CharacterSession) {
    private var open: History<SquashDocument>.Transaction? = null

    fun change(command: DocumentCommand) {
        val gesture = open ?: session.history.takeUnless { it.inGesture }?.begin()?.also { open = it } ?: return
        gesture.update(command)
    }

    fun done() {
        open?.commit()
        open = null
    }
}

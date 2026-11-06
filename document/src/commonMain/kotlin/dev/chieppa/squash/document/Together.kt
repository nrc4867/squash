package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Applies [commands] in order as one step, as a stroke and its mirror on the other eye are. */
@Serializable
@SerialName("together")
data class Together(val commands: List<DocumentCommand>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        var held = document
        val undos = ArrayList<DocumentCommand>()
        for (command in commands) {
            val applied = command.apply(held)
            held = applied.document
            undos.add(0, applied.undo as DocumentCommand)
        }
        return Applied(held, Together(undos))
    }
}

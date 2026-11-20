package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets the membrane [id] to [membrane], adding it after the others when new; null removes it. Every part it is anchored to exists. */
@Serializable
@SerialName("setMembrane")
data class SetMembrane(val id: MembraneId, val membrane: Membrane?) : DocumentCommand {
    init {
        require(membrane == null || membrane.id == id) { "Membrane ${membrane?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val membranes = document.character.membranes
        val old = membranes.firstOrNull { it.id == id }
        membrane?.parts?.forEach { document.requirePart(it) }
        val now = when {
            membrane == null -> membranes.filterNot { it.id == id }
            old == null -> membranes + membrane
            else -> membranes.map { if (it.id == id) membrane else it }
        }
        return Applied(document.copy(character = document.character.copy(membranes = now)), SetMembrane(id, old))
    }
}

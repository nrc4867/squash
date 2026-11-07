package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Sets [role] on [part], and on the other side of its pair, which shares it. [guessed] marks it a guess not yet confirmed; a role the user picks is confirmed. */
@Serializable
@SerialName("setRole")
data class SetRole(val part: PartId, val role: Role?, val guessed: Boolean = false) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val sides = document.character.parts.filter { it.id == part || (instance.pair != null && it.pair == instance.pair) }.map { it.id }.toSet()
        val parts = document.character.parts.map { if (it.id in sides) it.copy(role = role, roleGuessed = guessed && role != null) else it }
        return Applied(document.copy(character = document.character.copy(parts = parts)), SetRole(part, instance.role, instance.roleGuessed))
    }
}

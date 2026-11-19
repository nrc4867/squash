package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.screens.surface.CharacterState

/** This state drawing only the parts [kept], their lines with them, the surface as those parts' own sections and without garments. */
fun CharacterState.keptTo(kept: Set<PartId>): CharacterState {
    val shown = AssembledCharacter(character.parts.filter { it.id in kept }, emptyList())
    val lines = BodyLines(lines.parts.filterKeys { it in kept })
    return CharacterState(shown, color, work, palette, lines, fur, surface, paint = paint, dress = dress, only = kept)
}

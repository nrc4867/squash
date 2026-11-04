package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a character's fills are lit: one color per part, or a lit color and a shadow color with a hard edge. */
@Serializable
enum class ShadingMode {
    @SerialName("flat")
    FLAT,

    @SerialName("twoTone")
    TWO_TONE,
}

/** A character's shading choice, which is the object's own within what a scene allows. [farSideDarker] darkens the side of a pair away from the camera. */
@Serializable
data class CharacterShading(val mode: ShadingMode = ShadingMode.FLAT, val farSideDarker: Boolean = true)

/** Chooses how the character is shaded. */
@Serializable
@SerialName("setCharacterShading")
data class SetCharacterShading(val shading: CharacterShading) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(shading = shading)), SetCharacterShading(document.character.shading))
}

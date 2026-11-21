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

/** Where a character's light comes from. */
@Serializable
enum class ShadingLight {
    /** From in front of the viewer, above and to their left, whichever way the character is seen: a back view is lit as a front one. */
    @SerialName("view")
    VIEW,

    /** From in front of the character, above and to its left, fixed to the body: a back view falls in shade. */
    @SerialName("body")
    BODY,
}

/** How much darker a far side is where nothing else sets it, as a share of its color. */
const val FAR_SIDE_DEPTH = 0.18f

/** The most a far side darkens. */
const val MOST_FAR_SIDE_DEPTH = 0.5f

/**
 * A character's shading choice, which is the object's own within what a scene allows. [farSideDarker] darkens the side of a pair away
 * from the camera by [farSide], a share of its color, 0 to [MOST_FAR_SIDE_DEPTH]; [light] is where the light comes from. [key] is the shot's
 * key light: with one, a surface is lit where either it or [light] reaches, and materials' shines come from it, so they stay on the side
 * toward it as the camera turns.
 */
@Serializable
data class CharacterShading(
    val mode: ShadingMode = ShadingMode.FLAT,
    val farSideDarker: Boolean = true,
    val farSide: Float = FAR_SIDE_DEPTH,
    val light: ShadingLight = ShadingLight.VIEW,
    val key: KeyLight? = null,
) {
    init {
        require(farSide in 0f..MOST_FAR_SIDE_DEPTH) { "A far side's depth lies from 0 to $MOST_FAR_SIDE_DEPTH" }
    }
}

/** The way to a key light in body space, x forward, y up and z to the character's right; any length but zero. */
@Serializable
data class KeyLight(val x: Float, val y: Float, val z: Float) {
    init {
        require(x != 0f || y != 0f || z != 0f) { "A key light needs a direction" }
    }
}

/** Chooses how the character is shaded. */
@Serializable
@SerialName("setCharacterShading")
data class SetCharacterShading(val shading: CharacterShading) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(shading = shading)), SetCharacterShading(document.character.shading))
}

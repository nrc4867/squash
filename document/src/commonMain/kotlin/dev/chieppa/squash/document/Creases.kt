package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The most any crease or wrinkle strength is: twice its default. */
const val MOST_CREASE_STRENGTH = 2f

/** A crease strength switched on with nothing chosen: a garment's own creases, wrinkles and drape, and a character's once turned on. */
const val DEFAULT_CREASE_STRENGTH = 1f

/** How much of a joint's crease shows with the joint at rest, as a share of a full bend's: a faint short line where a sheet draws one. */
const val REST_CREASE = 0.3f

/**
 * A character's creases and wrinkles: [skin], how strongly a bending joint creases the body's surface on its inner side, ink and shade;
 * [cloth], how strongly every garment creases at those joints, wrinkles where it bunches and hangs in folds, times each garment's own
 * strengths; and [rest], how much of a joint's crease shows at rest, as a share of a full bend's. 0 is off for either. See HLD: look,
 * Creases and wrinkles.
 */
@Serializable
data class CharacterCreases(val skin: Float = 0f, val cloth: Float = 0f, val rest: Float = REST_CREASE) {
    init {
        require(skin in 0f..MOST_CREASE_STRENGTH) { "A skin crease strength lies from 0 to $MOST_CREASE_STRENGTH" }
        require(cloth in 0f..MOST_CREASE_STRENGTH) { "A cloth crease strength lies from 0 to $MOST_CREASE_STRENGTH" }
        require(rest in 0f..1f) { "A rest crease share lies from 0 to 1" }
    }

    /** Whether any crease or wrinkle shows. */
    val on: Boolean get() = skin > 0f || cloth > 0f
}

/** Sets the character's creases and wrinkles. */
@Serializable
@SerialName("setCharacterCreases")
data class SetCharacterCreases(val creases: CharacterCreases) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(creases = creases)), SetCharacterCreases(document.character.creases))
}

/** Which of a garment's crease strengths a command sets. */
@Serializable
enum class ClothCrease {
    /** Ink, shade and a small fold on the inner side of each bending joint it covers. */
    @SerialName("creases")
    CREASES,

    /** Wrinkles where the posed cloth bunches against its rest. */
    @SerialName("wrinkles")
    WRINKLES,

    /** Soft folds hanging down a loose top and gathered above its hems and cuffs. */
    @SerialName("drape")
    DRAPE,
}

/** Sets [garment]'s [which] strength to [strength], 0 to [MOST_CREASE_STRENGTH]. */
@Serializable
@SerialName("setGarmentCrease")
data class SetGarmentCrease(val garment: GarmentId, val which: ClothCrease, val strength: Float) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> = document.garmentChanged(garment) { held ->
        val kept = strength.coerceIn(0f, MOST_CREASE_STRENGTH)
        when (which) {
            ClothCrease.CREASES -> held.copy(creases = kept)
            ClothCrease.WRINKLES -> held.copy(wrinkles = kept)
            ClothCrease.DRAPE -> held.copy(drape = kept)
        }
    }
}

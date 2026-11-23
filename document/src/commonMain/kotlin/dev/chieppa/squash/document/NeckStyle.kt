package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a character's head meets its neck, from no visible neck to a slim neck under the head's own outline. It sets the defaults of the
 * head's join: how wide it blends into the neck, whether that join is inked, and the shade the head casts under its chin. See HLD: look,
 * Necks.
 */
@Serializable
enum class NeckStyle {
    /** The head flows into the neck and shoulders in one smooth curve: no neck shows. */
    @SerialName("blended")
    BLENDED,

    /** A short neck under a soft jaw line: the head blends into the neck by less, and a light shade lies under the chin. */
    @SerialName("between")
    BETWEEN,

    /** A slim neck under the head's own closed outline: the head barely blends into it, its join is inked, and its chin casts a shade. */
    @SerialName("defined")
    DEFINED,
}

/** Sets the character's neck style. */
@Serializable
@SerialName("setNeckStyle")
data class SetNeckStyle(val style: NeckStyle) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(neck = style)), SetNeckStyle(document.character.neck))
}

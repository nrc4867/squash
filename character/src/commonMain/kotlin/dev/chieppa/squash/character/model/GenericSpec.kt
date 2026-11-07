package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** The fewest and most of something a part may choose. */
@Serializable
data class CountRange(
    @Range(min = 2.0)
    val min: Int,
    @Range(min = 2.0)
    val max: Int,
) {
    operator fun contains(count: Int): Boolean = count in min..max
}

/**
 * What makes a template generic: a part from it chooses its own section profile, which views set it, and its rings and segments,
 * as part parameters set in the Shape inspector. The template's `section`, `views` and `topology` are what a part starts with.
 * See HLD: parts, Blank mode.
 */
@Serializable
@Doc("A generic template: each part chooses its section profile, the views that set it, and its rings and segments; the template's section, views and topology are the defaults.")
data class GenericSpec(
    @Doc("The fewest and most rings a part may take.")
    val rings: CountRange,
    @Doc("The fewest and most control points a part's section may take.")
    val segments: CountRange,
)

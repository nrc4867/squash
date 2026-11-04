package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

private const val DEFAULT_LIFT = 0.42f
private const val DEFAULT_RUFF_LIFT = 1.1f
private const val FULL_WAY_ROUND = 1.0

/** A stretch of a part where fur stands outward. */
@Serializable
@Doc("A stretch of the body patch where fur stands outward from the surface instead of lying along it.")
data class RuffSpec(
    @Doc("Along the axis, from 0 to 1.")
    val u: Span,
    @Doc("Around the part, from 0 to 1.")
    val v: Span,
    @Doc("How far a stroke rises for each unit it runs along the surface.") @Range(min = 0.0)
    val lift: Float = DEFAULT_RUFF_LIFT,
)

/** How a part's fur lies until a comb changes it. See HLD: look, Surface work rules. */
@Serializable
@Doc("How the part's fur lies until a comb changes it.")
data class GrainSpec(
    @Doc("The end of the axis fur runs toward.")
    val toward: End = End.END,
    @Doc("How far a stroke rises for each unit it runs along the surface, where no ruff says otherwise.") @Range(min = 0.0, max = FULL_WAY_ROUND)
    val lift: Float = DEFAULT_LIFT,
    val ruffs: List<RuffSpec> = emptyList(),
)

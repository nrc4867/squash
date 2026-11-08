package dev.chieppa.squash.character.build

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.blend.GradientBlend
import dev.chieppa.squash.geom.blend.ImplicitBlend

private const val SEPARATOR = ":"

/**
 * The fillet a part's `blend` block asks for: its [radius] in the part's units, its share [apart], and the pairs of patches it joins
 * by name, [between], a repeating block's name covering each of its patches, or every pair when null, and the patches that keep their
 * place, [still].
 */
data class BlendSpec(val radius: Float, val apart: Float, val between: List<List<String>>?, val still: List<String> = emptyList()) {
    private fun joins(a: String, b: String): Boolean =
        between == null || between.any { pair -> pair.size == 2 && (named(a, pair[0]) && named(b, pair[1]) || named(a, pair[1]) && named(b, pair[0])) }

    private fun named(patch: String, name: String): Boolean = patch == name || patch.substringBefore(SEPARATOR) == name

    /** [built] with its crossings filleted. */
    fun applyTo(built: List<Patch>, checkCancelled: () -> Unit = {}): List<Patch> =
        ImplicitBlend(GradientBlend(radius, apart), joins = ::joins, moves = { patch -> still.none { named(patch, it) } }).apply(built, checkCancelled)
}

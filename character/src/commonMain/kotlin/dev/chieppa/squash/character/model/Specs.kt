package dev.chieppa.squash.character.model

import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.TemplateId

/** A bone boundary at [u]; bones are the spans between joints in u order. */
data class JointSpec(
    val name: String,
    val u: Float,
    val v: Float?,
    val hinge: Hinge,
    val limits: Limits?,
    val stretch: Boolean,
)

/**
 * A child attachment point. [pair] gives it a mirrored twin; [tilt] turns its outward normal toward the part's end, in degrees;
 * [sink] multiplies how deep a child sits inside the part here.
 */
data class SocketSpec(
    val name: String,
    val u: Float,
    val v: Float,
    val outward: Outward,
    val pair: Mirror?,
    val takes: List<TemplateId>,
    val tilt: Float = 0f,
    val sink: Float = 1f,
)

/** A named surface point on a part, at [u] and [v], that face marks and sets anchor to. */
data class LandmarkSpec(val name: String, val u: Float, val v: Float)

/** A patch a block builds: called [name], or `name:i` for each of a row when it [repeats]. */
data class PatchName(val name: String, val repeats: Boolean = false)

/** A patch, or every one of a row of patches, that a template shows in the palette slot named [slot]. */
data class PatchSlot(val patch: String, val slot: String)

/** A tagged surface region on [patch] that weights and shading can read and Fill colors; [color] names the slot it shows until filled. */
data class AreaSpec(val name: String, val u: Span, val v: Span, val tag: AreaTag, val patch: String = BODY_PATCH, val color: String? = null) {
    /** Where its v span ends, running on past 1 when it crosses the seam. */
    val vEnd: Float get() = if (v.end < v.start) v.end + 1f else v.end
}

/**
 * One digit's chain of joints: the digit's patch, its place in its row, the span of the part's u it runs along, whether it is
 * turned [back] past a right angle, and its [joints] in u order from its root, named `<patch>.<n>` from 1.
 */
data class DigitChain(val patch: String, val index: Int, val span: Span, val back: Boolean, val joints: List<JointSpec>)

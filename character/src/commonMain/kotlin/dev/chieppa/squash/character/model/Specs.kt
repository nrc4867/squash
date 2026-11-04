package dev.chieppa.squash.character.model

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

/** A child attachment point. [pair] gives it a mirrored twin. */
data class SocketSpec(
    val name: String,
    val u: Float,
    val v: Float,
    val outward: Outward,
    val pair: Mirror?,
    val takes: List<TemplateId>,
)

/** A patch a block builds: called [name], or `name:i` for each of a row when it [repeats]. */
data class PatchName(val name: String, val repeats: Boolean = false)

/** A patch, or every one of a row of patches, that a template shows in the palette slot named [slot]. */
data class PatchSlot(val patch: String, val slot: String)

/** A tagged surface region that weights and shading can read. */
data class AreaSpec(val name: String, val u: Span, val v: Span, val tag: AreaTag)

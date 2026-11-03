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

/** A tagged surface region that weights and shading can read. */
data class AreaSpec(val name: String, val u: Span, val v: Span, val tag: AreaTag)

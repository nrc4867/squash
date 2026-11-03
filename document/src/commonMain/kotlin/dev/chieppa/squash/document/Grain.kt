package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** A combed direction at a surface point, in u and v. */
@Serializable
data class GrainComb(val patch: String = BODY_PATCH, val u: Float, val v: Float, val du: Float, val dv: Float)

/** A part's direction field in anchor space: along the axis, except where [combs] override it. */
@Serializable
data class Grain(val combs: List<GrainComb> = emptyList())

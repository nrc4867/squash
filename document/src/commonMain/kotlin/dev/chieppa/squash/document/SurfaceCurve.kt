package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * A curve drawn on a character's surface, through [points] anchored in each part's (u, v) as a region's border is, back to its first
 * point when [closed]. With [mirrored] it holds on the other side of the body's middle too.
 */
@Serializable
data class SurfaceCurve(val points: List<Anchor>, val closed: Boolean = false, val mirrored: Boolean = false)

package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * Outlines by view, parameter values by name and the work on the surface: what a part resolves to, what a pair shares, or
 * what a side overrides.
 */
@Serializable
data class PartData(
    val outlines: Map<View, OutlineCurve> = emptyMap(),
    val params: Map<String, Float> = emptyMap(),
    val surface: SurfaceWork = SurfaceWork(),
) {
    /** Only what shapes the part's mesh, for a build that reruns when that changes and not when the surface does. */
    val shape: PartData get() = copy(surface = SurfaceWork())

    /** These values, with [overrides] replacing the ones it names. */
    internal fun overlaidWith(overrides: PartData): PartData =
        PartData(outlines + overrides.outlines, params + overrides.params, surface.overlaidWith(overrides.surface))

    /** These values with [view]'s outline set to [curve]; null removes it. */
    internal fun withOutline(view: View, curve: OutlineCurve?): PartData =
        copy(outlines = if (curve == null) outlines - view else outlines + (view to curve))

    /** These values with parameter [name] set to [value]; null removes it. */
    internal fun withParam(name: String, value: Float?): PartData =
        copy(params = if (value == null) params - name else params + (name to value))
}

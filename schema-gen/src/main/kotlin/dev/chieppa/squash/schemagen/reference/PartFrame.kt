package dev.chieppa.squash.schemagen.reference

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.placedBy
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.positionAt

/** Points round a ring its middle is read from. */
private const val MIDDLE_SAMPLES = 16

/** A template built with nothing drawn, read for which way places on its surface face, in body space at rest. */
internal class PartFrame(template: PartTemplate, params: Map<String, Float> = emptyMap()) {
    val placed: PartTemplate = template.placedBy(params)
    private val patches: List<Patch> = PartBuilder.build(placed, params)
    val axes = PartBuilder.axes(placed)

    private fun patchAt(u: Float, patch: String?): Patch? =
        patches.firstOrNull { it.name == (patch ?: BODY_PATCH) && it.covers(u) } ?: patches.firstOrNull { patch == null && it.covers(u) }

    private fun middle(patch: Patch, u: Float): Vec3 =
        (0 until MIDDLE_SAMPLES).fold(Vec3.ZERO) { sum, i -> sum + patch.positionAt(u, i.toFloat() / MIDDLE_SAMPLES) } * (1f / MIDDLE_SAMPLES)

    /**
     * Which way the surface at [u] and [v] on [patch] faces from its ring's middle, or null where nothing is built. Where the ring has
     * closed to an end cap, it faces along the axis out of that end.
     */
    fun facing(u: Float, v: Float, patch: String? = null): Vec3? {
        val found = patchAt(u, patch) ?: return null
        val out = found.positionAt(u, v) - middle(found, u)
        val waist = found.positionAt(MIDDLE_U, v) - middle(found, MIDDLE_U)
        if (out.length < waist.length * CAP_SHARE) return if (u < MIDDLE_U) axes.along * -1f else axes.along
        return out.normalized()
    }

    private companion object {
        const val MIDDLE_U = 0.5f

        /** A ring narrower than this share of the middle ring is read as an end cap. */
        const val CAP_SHARE = 0.25f
    }
}

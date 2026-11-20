package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.DigitOverrides
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.ViewSizing
import dev.chieppa.squash.geom.field.FieldFit
import dev.chieppa.squash.geom.meshProfile

/** How many parts' undrawn silhouettes are kept, so dragging an outline builds the part as it stands undrawn once. */
private const val KEPT = 32

/**
 * A part's silhouettes in Side (along, up) and Top (along, less side) as it builds with nothing drawn, in body units: what Start from
 * template draws, leaving out a patch that runs back against the axis, a hind toe, as it does, and any digit's own shape.
 */
internal class UndrawnSilhouettes(val height: OutlineProfile?, val width: OutlineProfile?) {
    /** The fit of the part's solid to [drawn]. */
    fun fitTo(drawn: ViewSizing): FieldFit? = FieldFit.of(drawn, height, width)

    /** Side as [fit] carries it onto the drawing, in drawing units of [scale]: the height a part takes where Side is not drawn. */
    fun carriedHeight(fit: FieldFit, scale: Float): OutlineProfile? =
        height?.let { profile -> carried(profile, scale) { p -> fit.toDrawn(Vec3(p.x, p.y, 0f)).let { Vec2(it.x, it.y) } } }

    /** Top as [fit] carries it onto the drawing, in drawing units of [scale]: the width a part takes where Top is not drawn. */
    fun carriedWidth(fit: FieldFit, scale: Float): OutlineProfile? =
        width?.let { profile -> carried(profile, scale) { p -> fit.toDrawn(Vec3(p.x, 0f, -p.y)).let { Vec2(it.x, -it.z) } } }

    private fun carried(profile: OutlineProfile, scale: Float, toDrawn: (Vec2) -> Vec2): OutlineProfile? =
        OutlineProfile.ofEdges(profile.ring().map { toDrawn(it) * (1f / scale) }.zipWithNext())

    companion object {
        private val kept = object : LinkedHashMap<Pair<PartTemplate, Map<String, Float>>, UndrawnSilhouettes>(KEPT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<PartTemplate, Map<String, Float>>, UndrawnSilhouettes>) = size > KEPT
        }

        /** The silhouettes of the part [ctx] builds, built once for its template and values. */
        fun of(ctx: BuildContext): UndrawnSilhouettes {
            val values = DigitOverrides.without(ctx.values)
            val key = ctx.template to values
            synchronized(kept) { kept[key] }?.let { return it }
            val made = measured(ctx.template, PartBuilder.build(ctx.template, values, checkCancelled = ctx.checkCancelled))
            synchronized(kept) { kept[key] = made }
            return made
        }

        private fun measured(template: PartTemplate, patches: List<Patch>): UndrawnSilhouettes {
            val axes = PartBuilder.axes(template)
            val along = patches.filter { patch -> patch.slices?.let { (it.last().frame.center - it.first().frame.center) dot axes.along >= 0f } ?: false }
            val meshes = along.map { patch -> MeshBuilder.build(patch) { 0 } }
            return UndrawnSilhouettes(
                meshProfile(meshes) { axes.viewPoint(View.SIDE, it) },
                meshProfile(meshes) { axes.viewPoint(View.TOP, it) },
            )
        }
    }
}

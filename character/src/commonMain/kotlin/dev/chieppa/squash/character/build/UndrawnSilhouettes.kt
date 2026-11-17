package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.ViewSizing
import dev.chieppa.squash.geom.field.FieldFit
import dev.chieppa.squash.geom.meshProfile

/** How many parts' undrawn silhouettes are kept, so dragging an outline builds the part as it stands undrawn once. */
private const val KEPT = 32

/**
 * A part's silhouettes in Side (along, up) and Top (along, less side) as it builds with nothing drawn, in body units: what Start from
 * template draws, leaving out a patch that runs back against the axis, a hind toe, as it does.
 */
internal class UndrawnSilhouettes(val height: OutlineProfile?, val width: OutlineProfile?) {
    /** The fit of the part's solid to [drawn]. */
    fun fitTo(drawn: ViewSizing): FieldFit? = FieldFit.of(drawn, height, width)

    companion object {
        private val kept = object : LinkedHashMap<Pair<PartTemplate, Map<String, Float>>, UndrawnSilhouettes>(KEPT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<PartTemplate, Map<String, Float>>, UndrawnSilhouettes>) = size > KEPT
        }

        /** The silhouettes of the part [ctx] builds, built once for its template and values. */
        fun of(ctx: BuildContext): UndrawnSilhouettes {
            val key = ctx.template to ctx.values
            synchronized(kept) { kept[key] }?.let { return it }
            val made = measured(ctx.template, PartBuilder.build(ctx.template, ctx.values, checkCancelled = ctx.checkCancelled))
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

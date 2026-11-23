package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.mouth.shaped.isTooth
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.MaterialShine
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.ShineShape
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.PatchCut

/** How large a shaped tooth's plastic spot is where its slot sets no material: smaller than plastic's own, as a tooth is small. */
private const val TOOTH_SPOT = 0.22f

/** The material a slot shaped teeth are painted in draws them in when it sets none of its own: plastic, its spot sized to a tooth. */
val TEETH_MATERIAL = SlotMaterial(MaterialPreset.PLASTIC, shine = MaterialShine(ShineShape.SPOT, TOOTH_SPOT))

/** This palette with [slot] taking [material] where it sets none of its own. */
fun Palette.withDefault(slot: SlotName, material: SlotMaterial): Palette =
    if (material(slot) != null) this else Palette(slots.map { if (it.name == slot) it.copy(material = material) else it })

/** The palette a layer on [patch] in [slot] draws from: a shaped tooth's slot takes [TEETH_MATERIAL] where it sets no material. */
fun Palette.forLayer(patch: String, slot: SlotName): Palette = if (isTooth(patch)) withDefault(slot, TEETH_MATERIAL) else this

/** Where each patch of [patches] runs along u, root to tip, by name. */
fun spansOf(patches: List<Patch>): Map<String, ClosedFloatingPointRange<Float>> = patches.associate { it.name to it.ringUs.first()..it.ringUs.last() }

/** How far along its part, 0 at the root and 1 at the tip, each vertex of [cut] lies: by its patch's span in [spans], else by the cut's own. */
fun alongOf(cut: PatchCut, spans: Map<String, ClosedFloatingPointRange<Float>>): FloatArray {
    val span = spans[cut.patch] ?: if (cut.us.isEmpty()) 0f..1f else cut.us.min()..cut.us.max()
    val length = span.endInclusive - span.start
    return FloatArray(cut.us.size) { i -> if (length > 0f) ((cut.us[i] - span.start) / length).coerceIn(0f, 1f) else 0f }
}

/** How far along its part each vertex of a separate part's mesh of [patches] lies, patch by patch, by each patch's rings. */
fun alongOf(patches: List<Patch>): FloatArray {
    val out = ArrayList<Float>()
    for (patch in patches) {
        val us = patch.ringUs
        val first = us.first()
        val length = us.last() - first
        for (ring in 0 until patch.nu) repeat(patch.nv) { out += if (length > 0f) (us[ring] - first) / length else 0f }
    }
    return out.toFloatArray()
}

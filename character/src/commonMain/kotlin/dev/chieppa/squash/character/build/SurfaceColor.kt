package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.PatchCut
import dev.chieppa.squash.geom.SlottedCut
import dev.chieppa.squash.geom.cutShape
import dev.chieppa.squash.geom.mergedCuts
import kotlinx.coroutines.flow.MutableStateFlow

/** A cut shape in the space of the part it lies on, with its layer's rank and what it comes from. */
class LandedCut(val rank: Int, val part: PartId, val slot: SlotName, val cut: PatchCut, val source: ShapeSource? = null)

/** What one part's surface work draws: its [baseSlot], and its shapes wherever they land, in layer order. */
class HeldColor(val baseSlot: SlotName?, val cuts: List<LandedCut>)

/** What is drawn on one part: its [baseSlot], and over it [layers] in the order they overdraw, each in the part's surface space. */
class PartColor(val baseSlot: SlotName?, val layers: List<SlottedCut<SlotName>>)

/** Every part's color. A palette slot stays a name here, so a palette color changes nothing in it. */
class BodyColor(val parts: Map<PartId, PartColor>)

/** Keeps one holder's latest cuts, so a run cuts only the shapes that changed. A run is the node's, and a stale one can only leave cuts that are right. */
class ShapeCuts {
    private data class Key(val surface: PartSurface, val shape: LandedShape, val tolerance: Float)

    private val held = MutableStateFlow<Map<Key, PatchCut>>(emptyMap())

    /** [shapes] on [surfaces] at detail [step], leaving out any on a patch not there. [checkCancelled] runs before each cut and throws to stop a stale run. */
    fun cuts(shapes: List<LandedShape>, surfaces: Map<PartId, PartSurface>, step: Int, checkCancelled: () -> Unit = {}): List<LandedCut> {
        val before = held.value
        val now = HashMap<Key, PatchCut>(shapes.size)
        val out = shapes.mapNotNull { landed ->
            checkCancelled()
            val surface = surfaces[landed.part] ?: return@mapNotNull null
            val grid = surface.grid(landed.patch) ?: return@mapNotNull null
            val tolerance = if (landed.shape.isCurved) ColorDetail.tolerance(step) else 0f
            val key = Key(surface, landed.copy(rank = 0, slot = UNSLOTTED, source = null), tolerance)
            val cut = now[key] ?: before[key] ?: cutShape(landed.shape, grid, tolerance, checkCancelled)
            now[key] = cut
            LandedCut(landed.rank, landed.part, landed.slot, cut, landed.source)
        }
        held.value = now
        return out
    }

    private companion object {
        val UNSLOTTED = SlotName("")
    }
}

/** Each part's color from what every holder in [held] draws: all that lands on a part, in layer order, then holders' order, with neighbours of a slot merged. */
fun bodyColorOf(held: Map<PartId, HeldColor>): BodyColor {
    val landing = held.values.flatMap { it.cuts }.groupBy { it.part }
    return BodyColor(
        (held.keys + landing.keys).associateWith { part ->
            val layers = landing[part].orEmpty().sortedBy { it.rank }.map { SlottedCut(it.slot, it.cut) }
            PartColor(held[part]?.baseSlot, mergedCuts(layers))
        },
    )
}

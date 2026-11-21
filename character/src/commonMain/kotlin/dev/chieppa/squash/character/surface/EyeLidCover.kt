package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName

/** How far round an eye's opening, in its heights, a layer of the head's paint must reach to be the paint the eye sits in. */
internal const val COVER_MARGIN = 0.25f

/**
 * What lies under a surface's eyes: the [layers] of its parts' paint, in draw order, each part's own [skin] slot, and the [baseSkin] the
 * surface shows under a part with none.
 */
class EyeGround(val layers: List<PaintLayer> = emptyList(), val skin: Map<PartId, SlotName?> = emptyMap(), val baseSkin: SlotName? = null) {
    /** The skin [part] shows where no layer of paint lies on it. */
    fun skinOf(part: PartId): SlotName? = skin[part] ?: baseSkin


    companion object {
        /** Nothing known under the eyes: a lid takes no slot of its own. */
        val NONE = EyeGround()
    }
}

/** A layer's colour as it shows: its slot, its tint, and whether it is flat. */
private data class LayerStyle(val slot: SlotName?, val tint: LayerTint?, val flat: Boolean)

/**
 * The layer of [under], the head's paint below the eyes, that an eye sits in: over [covered], the vertices of its opening and a
 * [COVER_MARGIN] round it, the colour that shows on most of them, each showing its topmost layer or the skin, so a face painted in two
 * halves still counts as one colour and paint hidden under part of the open eye or ringing it is passed over; null where the skin shows
 * most, and the lids take the skin.
 */
internal fun paintEyeSitsIn(covered: IntArray, under: List<PaintLayer>): PaintLayer? {
    val painted = under.filter { it.fill == null }
    val counts = HashMap<LayerStyle?, Int>()
    val first = HashMap<LayerStyle, PaintLayer>()
    for (v in covered) {
        val top = painted.lastOrNull { v < it.values.size && it.values[v] >= 0f }
        val style = top?.let { LayerStyle(it.slot, it.tint, it.flat) }
        if (top != null && style != null) first.getOrPut(style) { top }
        counts[style] = (counts[style] ?: 0) + 1
    }
    val most = counts.maxByOrNull { it.value }?.key ?: return null
    return first[most]
}

/**
 * [layers] with their lid cover, if any, in the colour of [sitsIn], the head's paint the eye sits in, or shaded in [skin], the head's own
 * slot, where it is null.
 */
internal fun coveredAs(layers: List<PaintLayer>, sitsIn: PaintLayer?, skin: SlotName?): List<PaintLayer> = layers.map { layer ->
    when {
        !layer.isLidCover -> layer
        sitsIn != null -> layer.coloredAs(sitsIn.slot, sitsIn.tint, sitsIn.flat)
        else -> layer.coloredAs(skin, null, flat = false)
    }
}

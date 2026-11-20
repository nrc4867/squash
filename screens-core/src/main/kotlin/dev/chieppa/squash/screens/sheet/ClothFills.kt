package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.surface.SeeThroughCloth
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.CLOTH_FLOATS
import dev.chieppa.squash.render.ClothDraw
import dev.chieppa.squash.render.DefaultToneRule
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.Tones

/**
 * [garment]'s see-through cloth over its drawn mesh as the renderer reads it ([ClothDraw]): each vertex in the tones of the area holding
 * it, a see-through area's own slot or else the garment's, darkened on a far side by its share of that side as the surface is, with its
 * opacity and its net from [cloth].
 */
fun clothDraw(garment: SurfaceGarment, cloth: SeeThroughCloth, palette: Palette, fallback: PartMaterial, shading: ObjectShading, toCamera: Vec3): ClothDraw {
    val count = cloth.area.size
    val garmentSlot = garment.cover.garment.color
    val areas = garment.cover.garment.areas
    val looks = garment.cover.cover.seeThrough
    val slots = areas.mapIndexed { i, area -> if (looks.getOrNull(i) != null) area.color ?: garmentSlot else garmentSlot }
    val sides = HashMap<SlotName?, SideTones>()
    fun sidesOf(slot: SlotName?) = sides.getOrPut(slot) { SideTones.of(palette.colorOf(slot) ?: fallback.base, palette.shadowOf(slot), shading, toCamera) }
    val positive = garment.surface.positiveZ
    val negative = garment.surface.negativeZ
    val channels = Array(CLOTH_FLOATS) { FloatArray(count) }
    for (v in 0 until count) {
        val tones = sidesOf(slots.getOrNull(cloth.area[v]) ?: garmentSlot).at(positive.getOrElse(v) { 0f }, negative.getOrElse(v) { 0f })
        channels.setColor(LIT, v, tones.lit)
        channels.setColor(SHADOW, v, tones.shadow)
    }
    val held = listOf(cloth.opacity, cloth.net, cloth.thread, cloth.part, cloth.along, cloth.roundCos, cloth.roundSin, cloth.trust, cloth.countA, cloth.countB, cloth.slopeA, cloth.slopeB)
    held.forEachIndexed { i, values -> values.copyInto(channels[LOOK + i]) }
    val drawn = channels.map(garment.cut::values)
    val drawnCount = drawn.first().size
    val attributes = FloatArray(drawnCount * CLOTH_FLOATS)
    for (v in 0 until drawnCount) for (c in 0 until CLOTH_FLOATS) attributes[v * CLOTH_FLOATS + c] = drawn[c][v]
    return ClothDraw(attributes)
}

private const val LIT = 0
private const val SHADOW = 3
private const val LOOK = 6

/** Writes [color] at vertex [v] into the three channels from [from]. */
private fun Array<FloatArray>.setColor(from: Int, v: Int, color: Color) {
    this[from][v] = color.red
    this[from + 1][v] = color.green
    this[from + 2][v] = color.blue
}

/** A slot's tones on the near side and on each far side, mixed at a vertex as the surface's far-side layer mixes them. */
private class SideTones(val near: Tones, val positive: Tones, val negative: Tones) {
    fun at(positiveShare: Float, negativeShare: Float): Tones = Tones(
        mix(mix(near.lit, positive.lit, positiveShare), negative.lit, negativeShare),
        mix(mix(near.shadow, positive.shadow, positiveShare), negative.shadow, negativeShare),
    )

    companion object {
        fun of(lit: Color, own: Color?, shading: ObjectShading, toCamera: Vec3) = SideTones(
            DefaultToneRule.tones(lit, own, shading, null, toCamera),
            DefaultToneRule.tones(lit, own, shading, BodySide.POSITIVE_Z, toCamera),
            DefaultToneRule.tones(lit, own, shading, BodySide.NEGATIVE_Z, toCamera),
        )
    }
}

private fun mix(a: Color, b: Color, t: Float): Color {
    val s = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * s, a.green + (b.green - a.green) * s, a.blue + (b.blue - a.blue) * s)
}

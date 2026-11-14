package dev.chieppa.squash.screens.surface.rig

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.rig.MAX_INFLUENCES
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.SurfaceSkin
import dev.chieppa.squash.rig.smoothStepOf
import dev.chieppa.squash.screens.sheet.RigLayer

/** The heat map's colors, coolest first, and where along a bone's weight each comes in and is full. */
private val HEAT = listOf(
    HeatStop(Color(0xFFF5BF3A), 0f, 0.35f),
    HeatStop(Color(0xFFEE7A24), 0.35f, 0.7f),
    HeatStop(Color(0xFFD63A22), 0.7f, 1f),
)

private class HeatStop(val color: Color, val from: Float, val to: Float)

/**
 * The layers blending each part's color into the colors of the parts whose bones share its vertices, so a seam's band shows as
 * the two colors mixing by weight. Laid in order, each layer at the share of its weight in all weight so far, they add up to
 * the mean of the colors weighed by the bones' weights.
 */
fun seamLayers(rig: Rig, colors: Map<PartId, Color>): Map<PartId, List<RigLayer>> = rig.parts.associate { part ->
    part.id to blended(part.id, partWeights(rig, part.vertexCount, part.weights.bones, part.weights.weights), part.vertexCount, colors)
}

/** The same blend over a one surface's vertices, weighed by its [skin], laid over [base]'s color. */
fun surfaceSeamLayers(rig: Rig, skin: SurfaceSkin, base: PartId, colors: Map<PartId, Color>): List<RigLayer> =
    blended(base, partWeights(rig, skin.vertexCount, skin.bones, skin.weights), skin.vertexCount, colors)

/** The heat map of [bone]'s weight over every part it moves, from warm where it barely holds a vertex to red where it holds all of it. */
fun heatLayers(rig: Rig, bone: Int): Map<PartId, List<RigLayer>> = rig.parts.mapNotNull { part ->
    heated(FloatArray(part.vertexCount) { part.weights.weightOf(it, bone) })?.let { part.id to it }
}.toMap()

/** The heat map of [bone]'s weight over a one surface, weighed by its [skin]; empty where the bone holds none of it. */
fun surfaceHeatLayers(skin: SurfaceSkin, bone: Int): List<RigLayer> = heated(FloatArray(skin.vertexCount) { v ->
    var w = 0f
    for (slot in 0 until MAX_INFLUENCES) if (skin.bones[v * MAX_INFLUENCES + slot] == bone) w += skin.weights[v * MAX_INFLUENCES + slot]
    w
}).orEmpty()

private fun heated(weight: FloatArray): List<RigLayer>? {
    if (weight.none { it > 0f }) return null
    return HEAT.mapIndexed { i, stop ->
        RigLayer(stop.color, FloatArray(weight.size) { v ->
            val w = weight[v]
            if (i == 0) (if (w > 0f) smoothStepOf(w / stop.to) else 0f) else smoothStepOf((w - stop.from) / (stop.to - stop.from))
        })
    }
}

/** [base]'s color blended into each other part's in [byPart] by weight, as layers over [count] vertices. */
private fun blended(base: PartId, byPart: Map<PartId, FloatArray>, count: Int, colors: Map<PartId, Color>): List<RigLayer> {
    val others = byPart.keys.filter { it != base && it in colors }
    var sofar = byPart[base] ?: FloatArray(count)
    return others.map { other ->
        val own = byPart.getValue(other)
        val total = FloatArray(count) { sofar[it] + own[it] }
        val alphas = FloatArray(count) { if (total[it] > 0f) own[it] / total[it] else 0f }
        sofar = total
        RigLayer(colors.getValue(other), alphas)
    }.filter { layer -> layer.alphas.any { it > 0f } }
}

/** Each part's share of each of [count] vertices whose [bones] and [weights] hold [MAX_INFLUENCES] each, by the parts its bones belong to. */
private fun partWeights(rig: Rig, count: Int, bones: IntArray, weights: FloatArray): Map<PartId, FloatArray> {
    val shares = LinkedHashMap<PartId, FloatArray>()
    for (v in 0 until count) for (slot in 0 until MAX_INFLUENCES) {
        val bone = bones[v * MAX_INFLUENCES + slot]
        if (bone < 0) continue
        val owner = rig.bones[bone].name.part
        shares.getOrPut(owner) { FloatArray(count) }[v] += weights[v * MAX_INFLUENCES + slot]
    }
    return shares
}

package dev.chieppa.squash.screens.surface.rig

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.rig.MAX_INFLUENCES
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.RigPart
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
    val byPart = partWeights(rig, part)
    val others = byPart.keys.filter { it != part.id && it in colors }
    var sofar = byPart[part.id] ?: FloatArray(part.vertexCount)
    part.id to others.map { other ->
        val own = byPart.getValue(other)
        val total = FloatArray(part.vertexCount) { sofar[it] + own[it] }
        val alphas = FloatArray(part.vertexCount) { if (total[it] > 0f) own[it] / total[it] else 0f }
        sofar = total
        RigLayer(colors.getValue(other), alphas)
    }.filter { layer -> layer.alphas.any { it > 0f } }
}

/** The heat map of [bone]'s weight over every part it moves, from warm where it barely holds a vertex to red where it holds all of it. */
fun heatLayers(rig: Rig, bone: Int): Map<PartId, List<RigLayer>> = rig.parts.mapNotNull { part ->
    val weight = FloatArray(part.vertexCount) { part.weights.weightOf(it, bone) }
    if (weight.none { it > 0f }) return@mapNotNull null
    part.id to HEAT.mapIndexed { i, stop ->
        RigLayer(stop.color, FloatArray(part.vertexCount) { v ->
            val w = weight[v]
            if (i == 0) (if (w > 0f) smoothStepOf(w / stop.to) else 0f) else smoothStepOf((w - stop.from) / (stop.to - stop.from))
        })
    }
}.toMap()

/** Each part's share of every vertex of [part], by the parts its bones belong to. */
private fun partWeights(rig: Rig, part: RigPart): Map<PartId, FloatArray> {
    val shares = LinkedHashMap<PartId, FloatArray>()
    for (v in 0 until part.vertexCount) for (slot in 0 until MAX_INFLUENCES) {
        val bone = part.weights.bones[v * MAX_INFLUENCES + slot]
        if (bone < 0) continue
        val owner = rig.bones[bone].name.part
        shares.getOrPut(owner) { FloatArray(part.vertexCount) }[v] += part.weights.weights[v * MAX_INFLUENCES + slot]
    }
    return shares
}

package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.ChartLidPaint
import dev.chieppa.squash.geom.Vec3
import kotlin.math.sqrt

/** How far toward the eye's facing a vertex's normal must turn, as a cosine, to take the lid's paint. */
private const val LEAST_LID_FACING = 0.2f

/**
 * The lid's layers of [shapes] in [tones], in order: the skin the lids cover as they close, shaded and in no slot until it is given the
 * head's (see [PaintLayer.coloredAs]), then as flat layers its skin's fade-into colour, its skin, its marks and its ink mass, those the
 * look leaves off left out. All are cut once over the vertices of the head's [members] facing the eye within the lid's reach of
 * [middle] at any pose, an oval in the eye's chart of [across] and [up], which runs [along] and [around] body units a unit, and each is
 * drawn to its own shape at each fragment, so the lids move by their shapes alone.
 */
internal fun eyeLidLayers(
    chart: SurfaceChart,
    members: List<Int>,
    middle: Vec3,
    facing: Vec3,
    across: Vec3,
    up: Vec3,
    along: Float,
    around: Float,
    shapes: EyeShapes,
    tones: EyeTones,
    grow: Float,
): List<PaintLayer> {
    val detail = shapes.detail
    val (reachU, reachV) = detail.lidReach
    if (reachU <= 0f || reachV <= 0f) return emptyList()
    val surface = chart.surface
    val mesh = surface.mesh
    val reach = sqrt((reachU * along).let { it * it } + (reachV * around).let { it * it }) + chart.reach
    val near = chart.held(members) { v -> (mesh.position(v) - middle).length <= reach && (mesh.normal(v) dot facing) >= LEAST_LID_FACING }
    val candidates = chart.trianglesNear(chart.trianglesOf(members), near)
    val scale = minOf(reachU * along, reachV * around)
    val values = chart.layOn(members, near, candidates) { v, _, _ ->
        val offset = mesh.position(v) - middle
        val u = (offset dot across) / along / reachU
        val w = (offset dot up) / around / reachV
        (1f - sqrt(u * u + w * w)) * scale
    }
    val (chartU, chartV) = eyeChartAt(surface, candidates, middle, across * (1f / along), up * (1f / around))
    fun layer(tone: EyeTones.Tone, paint: ChartLidPaint?, withMass: Boolean, flat: Boolean = true): PaintLayer? {
        val fill = LayerFill(chartU, chartV, shapes.almond, null, shapes.turn, shapes.fillTurn, grow, lid = if (withMass) detail.lid else null, paint = paint)
        return PaintLayer.of(tone.slot, surface, values, candidates, flat = flat, fill = fill, tint = tone.tint)
    }
    val cover = layer(EyeTones.Tone(null, null), detail.lidCover, withMass = false, flat = false)
    val paints = listOfNotNull(
        detail.lidSkinUnder?.let { tones.lidSkinUnder to it },
        detail.lidSkin?.let { tones.lidSkin to it },
    ) + detail.lidMarks.mapIndexed { i, mark -> tones.lidMarks[i] to mark }
    return listOfNotNull(cover) + paints.mapNotNull { (tone, paint) -> layer(tone, paint, withMass = false) } + listOfNotNull(detail.lid?.let { layer(tones.lid, null, withMass = true) })
}

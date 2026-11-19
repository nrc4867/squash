package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.shape.ChartPlaces
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/**
 * Which parts a Paint stroke lays cloth on, a dab at a time, the stroke started on [start], read on [places]. A dab lays the cells within
 * the brush's reach of its middle, on any part but those above [start] in the body, its parent and theirs: a round brush at a limb's
 * root would catch them round the socket. Such a part joins the stroke once a dab is centred on it with no cell of [start] within the
 * brush's reach, the stroke clearly gone onto it, and from then on takes what each dab reaches of it.
 */
class BrushReach(private val places: ChartPlaces, start: PartId) {
    private val chart = places.chart
    private val first = chart.leaderOf(start)
    private val above: Set<PartId> = generateSequence(chart.part(first)?.parent) { chart.part(it)?.parent }.mapTo(HashSet()) { chart.leaderOf(it) }
    private val joined = HashSet<PartId>()

    /** The parts above the start that the stroke has gone onto. */
    val crossed: Set<PartId> get() = joined

    /** The cells a dab of [radius] centred at [center], on [on], lays. */
    fun dab(center: Vec3, on: PartId?, radius: Float): SurfaceMask {
        val reached = places.within(center, radius)
        val part = on?.let(chart::leaderOf)
        if (part != null && part in above && part !in joined && reached.count(first) == 0) joined += part
        if (above.none { it !in joined && reached.count(it) > 0 }) return reached
        return SurfaceMask.build(chart) { p, cell -> reached.held(p, cell) && (chart.leaderOf(p) !in above || chart.leaderOf(p) in joined) }
    }

    /** The cells dabs of [radius] at [dabs], each a middle and the part under it, lay, in turn. */
    fun stroke(dabs: List<Pair<Vec3, PartId?>>, radius: Float): SurfaceMask =
        dabs.fold(SurfaceMask.empty(chart)) { held, (center, on) -> held + dab(center, on, radius) }
}

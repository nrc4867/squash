package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * What is drawn on a part: as a part resolves it, as a pair shares it, or as a side overrides it. Color layers in this order:
 * [baseSlot] colors the whole part, [areaFills] the areas its template names, by area name, then [regions], then [paint]. A
 * null [grain] runs along the axis. Lists run in draw order.
 */
@Serializable
data class SurfaceWork(
    val baseSlot: SlotName? = null,
    val areaFills: Map<String, SlotName> = emptyMap(),
    val grain: Grain? = null,
    val marks: List<Mark> = emptyList(),
    val groups: List<MarkGroup> = emptyList(),
    val regions: List<Region> = emptyList(),
    val paint: Paint = Paint(),
) {
    init {
        requireUniqueIds("mark", marks.map { it.id.value })
        requireUniqueIds("mark group", groups.map { it.id.value })
        requireUniqueIds("region", regions.map { it.id.value })
        requireUniqueIds("brush stroke", paint.strokes.map { it.id.value })
    }

    /** This work, with [overrides] replacing what it names. An item it does not hold goes after the ones this holds. */
    internal fun overlaidWith(overrides: SurfaceWork): SurfaceWork = SurfaceWork(
        baseSlot = overrides.baseSlot ?: baseSlot,
        areaFills = areaFills + overrides.areaFills,
        grain = overrides.grain ?: grain,
        marks = marks.overlaidWith(overrides.marks) { it.id },
        groups = groups.overlaidWith(overrides.groups) { it.id },
        regions = regions.overlaidWith(overrides.regions) { it.id },
        paint = Paint(paint.strokes.overlaidWith(overrides.paint.strokes) { it.id }),
    )

    internal fun mapAnchors(transform: (Anchor) -> Anchor): SurfaceWork = copy(
        marks = marks.map { it.mapAnchors(transform) },
        regions = regions.map { it.mapAnchors(transform) },
        paint = Paint(paint.strokes.map { it.mapAnchors(transform) }),
    )
}

private fun requireUniqueIds(kind: String, ids: List<String>) {
    require(ids.size == ids.toSet().size) { "Surface work holds a $kind id twice: $ids" }
}

private fun <T, K> List<T>.overlaidWith(overrides: List<T>, id: (T) -> K): List<T> {
    if (overrides.isEmpty()) return this
    val replacing = overrides.associateBy(id)
    val held = mapTo(mutableSetOf(), id)
    return map { replacing[id(it)] ?: it } + overrides.filter { id(it) !in held }
}

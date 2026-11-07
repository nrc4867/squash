package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * What is drawn on a part: as a part resolves it, as a pair shares it, or as a side overrides it. Color layers in this order:
 * [baseSlot] colors the whole part, [areaFills] the areas its template names, by area name, then [regions], then [paint]. A
 * null [grain] is what the template gives, and a null [fur] the default rule. Lists run in draw order. [seams] are the strokes painted
 * on the seam where this part meets its parent, in the order laid down, which the rig replays and nothing draws.
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
    val lineWeights: List<LineWeight> = emptyList(),
    val fur: FurRule? = null,
    val seams: List<SeamStroke> = emptyList(),
) {
    init {
        requireUniqueIds("mark", marks.map { it.id.value })
        requireUniqueIds("mark group", groups.map { it.id.value })
        requireUniqueIds("region", regions.map { it.id.value })
        requireUniqueIds("brush stroke", paint.strokes.map { it.id.value })
        requireUniqueIds("line weight", lineWeights.map { it.id.value })
        requireUniqueIds("seam stroke", seams.map { it.id.value })
    }

    /** This work without its seam strokes: everything a part shows on its surface. */
    val shown: SurfaceWork get() = if (seams.isEmpty()) this else copy(seams = emptyList())

    /** This work, with [overrides] replacing what it names. An item it does not hold goes after the ones this holds. */
    internal fun overlaidWith(overrides: SurfaceWork): SurfaceWork = SurfaceWork(
        baseSlot = overrides.baseSlot ?: baseSlot,
        areaFills = areaFills + overrides.areaFills,
        grain = overrides.grain ?: grain,
        marks = marks.overlaidWith(overrides.marks) { it.id },
        groups = groups.overlaidWith(overrides.groups) { it.id },
        regions = regions.overlaidWith(overrides.regions) { it.id },
        paint = Paint(paint.strokes.overlaidWith(overrides.paint.strokes) { it.id }),
        lineWeights = lineWeights.overlaidWith(overrides.lineWeights) { it.id },
        fur = overrides.fur ?: fur,
        seams = seams.overlaidWith(overrides.seams) { it.id },
    )

    internal fun mapAnchors(transform: (Anchor) -> Anchor): SurfaceWork = copy(
        marks = marks.map { it.mapAnchors(transform) },
        regions = regions.map { it.mapAnchors(transform) },
        paint = Paint(paint.strokes.map { it.mapAnchors(transform) }),
        lineWeights = lineWeights.map { it.mapAnchors(transform) },
        seams = seams.map { it.mapAnchors(transform) },
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

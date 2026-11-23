package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.isMouth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A color cut's inputs hold results this big, so a node keeps only a few of them. */
private const val COLOR_CACHE = 4

/** What a part's color reads: its work, how fine to flatten, and a surface it lands on. */
sealed interface ColorSource {
    data class Work(val work: ResolvedSurfaceWork) : ColorSource

    data class Detail(val step: Int) : ColorSource

    data class Surface(val surface: PartSurface) : ColorSource
}

private sealed interface ColorKey {
    data object Work : ColorKey

    data object Detail : ColorKey

    data class Surface(val part: PartId) : ColorKey
}

/**
 * What a part's color shapes are before they are cut: its [baseSlot], its [shapes] in layer order, the [surfaces] they land on and the
 * [step] they flatten at; and the ink [lines] it draws over them: its marks in no group, its lip lines and its open mouths' lower lips, fur aside;
 * and the [insideSlot] its mouth's inner role fills, which a jaw built open paints its inside in.
 */
data class HeldShapes(
    val baseSlot: SlotName?,
    val shapes: List<LandedShape>,
    val surfaces: Map<PartId, PartSurface>,
    val step: Int,
    val lines: List<ResolvedMark> = emptyList(),
    val insideSlot: SlotName? = null,
)

/** [work]'s ink lines: its marks in no group and those its groups hold as lip lines, fur aside. */
fun inkLines(work: ResolvedSurfaceWork): List<ResolvedMark> {
    val grouped = work.groups.flatMap { group -> group.roles.flatMap { it.marks } }.toSet()
    val lips = work.groups.flatMap { group -> group.roles.filter { it.role == MarkRole.LIP_LINE }.flatMap { it.marks } }.toSet()
    return work.marks.filter { it.kind != MarkKind.FUR && it.ink && (it.id !in grouped || it.id in lips) }
}

/**
 * A part's color shapes where they land, each in the space of the part and patch its anchors name, following its [work] and those
 * surfaces at the step of [detail].
 */
fun Graph.partShapesNode(
    input: Flow<PartColorInput>,
    part: PartId,
    work: Node<*, ResolvedSurfaceWork>,
    detail: Node<*, ColorSource>,
    surface: (PartId) -> Node<*, PartSurface>,
): Node<*, HeldShapes> {
    val own = follow<ResolvedSurfaceWork, ColorSource>(work, cacheSize = COLOR_CACHE, stage = BuildStages.SURFACE_WORK) { ColorSource.Work(it) }
    val wrapped = HashMap<PartId, Node<*, ColorSource>>()
    return gather(
        input.distinctUntilChanged(),
        sourcesOf = { shape ->
            val surfaces = (shape.parts + part).associateWith<PartId, Node<*, ColorSource>> { named ->
                wrapped.getOrPut(named) { follow(surface(named), cacheSize = COLOR_CACHE, stage = BuildStages.SURFACE_WORK) { ColorSource.Surface(it) } }
            }
            mapOf<ColorKey, Node<*, ColorSource>>(ColorKey.Work to own, ColorKey.Detail to detail) +
                surfaces.mapKeys { (named, _) -> ColorKey.Surface(named) }
        },
        cacheSize = COLOR_CACHE,
        stage = BuildStages.SURFACE_WORK,
    ) { (shape, sources) ->
        val resolved = (sources.getValue(ColorKey.Work) as ColorSource.Work).work
        val step = (sources.getValue(ColorKey.Detail) as ColorSource.Detail).step
        val surfaces = sources.entries.mapNotNull { (key, source) ->
            if (key is ColorKey.Surface) key.part to (source as ColorSource.Surface).surface else null
        }.toMap()
        val mouths = openMouths(part, resolved, surfaces)
        val jaw = shape.jawMouth?.let { mouth -> surfaces[part]?.let { jawMouthShapes(part, mouth, it) } }.orEmpty()
        val lines = inkLines(resolved).filter { it.id !in mouths.replaced } + mouths.lines
        val painted = if (mouths.regions.isEmpty()) resolved else resolved.copy(regions = resolved.regions.filterNot { it.id in mouths.regions })
        val inside = resolved.groups.firstOrNull { it.isMouth }?.roles?.firstOrNull { it.role == MarkRole.INNER }?.fill
        HeldShapes(resolved.baseSlot, colorShapes(part, painted, shape, surfaces) + mouths.shapes + jaw, surfaces, step, lines, inside)
    }
}

/**
 * A part's color shapes, cut to the surfaces they land on. Palette colors are not among its inputs, so they cost it nothing, and a shape
 * that did not change keeps its cut.
 */
fun Graph.partColorNode(shapes: Node<*, HeldShapes>): Node<*, HeldColor> {
    val cuts = ShapeCuts()
    return follow(shapes, cacheSize = COLOR_CACHE, stage = BuildStages.SURFACE_WORK) { held ->
        HeldColor(held.baseSlot, cuts.cuts(held.shapes, held.surfaces, held.step, cancellationCheck()))
    }
}

/** Every part's color, from each of [parts]' held color. */
fun Graph.bodyColorNode(parts: Flow<List<PartId>>, held: (PartId) -> Node<*, HeldColor>): Node<*, BodyColor> = gather(
    parts,
    sourcesOf = { ids -> ids.associateWith(held) },
    cacheSize = COLOR_CACHE,
    stage = BuildStages.SURFACE_WORK,
) { bodyColorOf(it.values) }

/** The flow of [part]'s color input from [document]. */
fun partColorInputs(document: Flow<SquashDocument>, part: PartId, templates: TemplateLibrary): Flow<PartColorInput> =
    document.map { partColorInput(it.character, part, templates) }

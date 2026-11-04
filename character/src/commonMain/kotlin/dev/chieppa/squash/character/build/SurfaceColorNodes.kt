package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
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
 * A part's color shapes, cut to the surfaces they land on, following its [work] and those surfaces at the step of [detail]. Palette
 * colors are not among its inputs, so they cost it nothing, and a shape that did not change keeps its cut.
 */
fun Graph.partColorNode(
    input: Flow<PartColorInput>,
    part: PartId,
    work: Node<*, ResolvedSurfaceWork>,
    detail: Node<*, ColorSource>,
    surface: (PartId) -> Node<*, PartSurface>,
): Node<*, HeldColor> {
    val own = follow<ResolvedSurfaceWork, ColorSource>(work, cacheSize = COLOR_CACHE, stage = BuildStages.SURFACE_WORK) { ColorSource.Work(it) }
    val wrapped = HashMap<PartId, Node<*, ColorSource>>()
    val cuts = ShapeCuts()
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
        HeldColor(resolved.baseSlot, cuts.cuts(colorShapes(part, resolved, shape, surfaces), surfaces, step, cancellationCheck()))
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

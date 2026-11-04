package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A view's camera changes every frame of an orbit, so its node remembers only the latest. */
private const val FUR_VIEW_CACHE = 1

private sealed interface FurSource {
    data class Placed(val character: AssembledCharacter) : FurSource

    data class Work(val work: BodySurfaceWork) : FurSource
}

private enum class FurKey { PLACED, WORK }

/** Every part's fur, following the grain and rule the document gives each part, [assembly]'s placement and [work]. */
fun Graph.bodyFurNode(
    document: Flow<SquashDocument>,
    templates: TemplateLibrary,
    assembly: Node<*, AssembledCharacter>,
    work: Node<*, BodySurfaceWork>,
): Node<*, BodyFur> {
    val placed = follow<AssembledCharacter, FurSource>(assembly, stage = BuildStages.SURFACE_WORK) { FurSource.Placed(it) }
    val resolved = follow<BodySurfaceWork, FurSource>(work, stage = BuildStages.SURFACE_WORK) { FurSource.Work(it) }
    return gather(
        document.map { furInputs(it.character, templates) }.distinctUntilChanged(),
        sourcesOf = { mapOf(FurKey.PLACED to placed, FurKey.WORK to resolved) },
        stage = BuildStages.SURFACE_WORK,
    ) { (inputs, sources) ->
        val character = (sources.getValue(FurKey.PLACED) as FurSource.Placed).character
        val held = (sources.getValue(FurKey.WORK) as FurSource.Work).work
        bodyFur(inputs, held, character, cancellationCheck())
    }
}

/** What [fur] shows through the camera [camera] last gave, nothing before it has given one. */
fun Graph.furViewNode(camera: Flow<FurCamera?>, fur: Node<*, BodyFur>): Node<*, FurView> = gather(
    camera,
    sourcesOf = { mapOf(Unit to fur) },
    cacheSize = FUR_VIEW_CACHE,
) { (held, sources) ->
    held?.let { furView(sources.getValue(Unit), it, cancellationCheck()) } ?: FurView.EMPTY
}

/** The cameras views have given, one flow per view, kept as they go. */
class FurCameras {
    private val cameras = HashMap<String, MutableStateFlow<FurCamera?>>()

    fun of(view: String): MutableStateFlow<FurCamera?> = cameras.getOrPut(view) { MutableStateFlow(null) }
}

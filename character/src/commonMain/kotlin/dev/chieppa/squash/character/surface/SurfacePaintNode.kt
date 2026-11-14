package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** What the paint node holds: the one surface's paint, or null for a character built as its parts' own meshes. */
class HeldPaint(val paint: SurfacePaint?)

/**
 * A one surface as a pose shows it, with its [paint] laid on it and its [dress] worn as posed, each null where it has none, and [carry],
 * which moves a point on the surface at rest to where the pose carries it, null while the pose leaves it at rest.
 */
class PosedSurface(val surface: CharacterSurface, val paint: SurfacePaint?, val dress: SurfaceDress? = null, val carry: ((ResolvedPoint) -> ResolvedPoint)? = null)

/** What a posed-surface node holds: the surface in its pose, or null for a character built as its parts' own meshes. */
class HeldPosedSurface(val posed: PosedSurface?)

/** What the paint reads: the surface, or one holder's shapes. */
private sealed interface PaintSource {
    class Surface(val held: HeldSurface) : PaintSource

    class Shapes(val held: HeldShapes) : PaintSource
}

/** Where a paint source comes from: the surface, or the holder [part]. */
private data class PaintKey(val part: PartId?)

/** What the one surface's paint reads besides the surface: the [parts] whose color shapes it lays, and the [eyes] painted on its heads. */
data class PaintInput(val parts: List<PartId>, val eyes: List<PaintedEye>)

/**
 * The paint of every holder among [input]'s parts, their color shapes from [shapes], on [surface]'s one surface, then its painted eyes.
 * See [SurfacePaint.of] and [SurfaceEyes]. Its shapes and its eyes are laid in parallel on the graph's dispatcher.
 */
fun Graph.surfacePaintNode(surface: Node<*, HeldSurface>, input: Flow<PaintInput>, shapes: (PartId) -> Node<*, HeldShapes>): Node<*, HeldPaint> {
    val surfaceSource = follow<HeldSurface, PaintSource>(surface, cacheSize = 1, stage = BuildStages.SURFACE_WORK) { PaintSource.Surface(it) }
    val wrapped = HashMap<PartId, Node<*, PaintSource>>()
    val memo = PaintMemo()
    return gather(
        input,
        sourcesOf = { held ->
            mapOf(PaintKey(null) to surfaceSource) + held.parts.associate { id ->
                PaintKey(id) to wrapped.getOrPut(id) { follow<HeldShapes, PaintSource>(shapes(id), cacheSize = 1, stage = BuildStages.SURFACE_WORK) { PaintSource.Shapes(it) } }
            }
        },
        cacheSize = 1,
        stage = BuildStages.SURFACE_WORK,
    ) { gathered ->
        val held = (gathered.values[PaintKey(null)] as? PaintSource.Surface)?.held?.surface ?: return@gather HeldPaint(null)
        val shapes = gathered.shape.parts.mapNotNull { id -> (gathered.values[PaintKey(id)] as? PaintSource.Shapes)?.let { id to it.held } }.toMap()
        coroutineScope {
            val eyes = async { SurfaceEyes.of(held, gathered.shape.eyes, IN_PARALLEL) }
            val paint = SurfacePaint.of(held, shapes, IN_PARALLEL, memo)
            HeldPaint(paint + eyes.await())
        }
    }
}

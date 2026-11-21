package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.build.ShineSource
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/** What the paint node holds: the one surface's paint, laid [on] that surface, or null for a character with no part in the field. */
class HeldPaint(val paint: SurfacePaint?, val on: CharacterSurface? = null) {
    /** Whether this paint is laid on [surface], or holds none. */
    fun isOn(surface: CharacterSurface): Boolean = paint == null || on === surface
}

/**
 * A one surface as a pose shows it, with its [paint] laid on it and its [dress] worn as posed, each null where it has none, and [carry],
 * which moves a point on the surface at rest to where the pose carries it, null while the pose leaves it at rest, and its [creases] and
 * wrinkles, null while the character has none on.
 */
class PosedSurface(
    val surface: CharacterSurface,
    val paint: SurfacePaint?,
    val dress: SurfaceDress? = null,
    val carry: ((ResolvedPoint) -> ResolvedPoint)? = null,
    val creases: PosedCreases? = null,
) {
    companion object {
        /**
         * [surface] at rest with [paint] and [dress], or null where either was laid on another surface: the graph's nodes settle one
         * after another, so the surface of one build can meet the paint or dress of the build before.
         */
        fun laid(surface: CharacterSurface, paint: HeldPaint?, dress: SurfaceDress?): PosedSurface? =
            if (paint?.isOn(surface) == false || dress?.isOn(surface) == false) null else PosedSurface(surface, paint?.paint, dress)
    }
}

/** What a posed-surface node holds: the surface in its pose, or null for a character with no part in the field; [coarse] while dragged. */
class HeldPosedSurface(val posed: PosedSurface?, val coarse: Boolean = false)

/** What the paint reads: the surface, or one holder's shapes. */
private sealed interface PaintSource {
    class Surface(val held: HeldSurface) : PaintSource

    class Shapes(val held: HeldShapes) : PaintSource
}

/** Where a paint source comes from: the surface, or the holder [part]. */
private data class PaintKey(val part: PartId?)

/**
 * What the one surface's paint reads besides the surface: the [parts] whose color shapes it lays, the [eyes] painted on its heads, and the
 * hair volumes whose band of [shines] runs over the hair in the field.
 */
data class PaintInput(val parts: List<PartId>, val eyes: List<PaintedEye>, val shines: List<ShineSource> = emptyList())

/**
 * The paint of every holder among [input]'s parts, their color shapes from [shapes], on [surface]'s one surface, then the hair's bands
 * of shine (see [hairShineLayer]), then its painted eyes.
 * See [SurfacePaint.of] and [SurfaceEyes]. Its shapes and its eyes are laid in parallel on the graph's dispatcher, and an eye whose lids
 * alone moved is not laid again (see [EyeMemo]).
 */
fun Graph.surfacePaintNode(surface: Node<*, HeldSurface>, input: Flow<PaintInput>, shapes: (PartId) -> Node<*, HeldShapes>): Node<*, HeldPaint> {
    val surfaceSource = follow<HeldSurface, PaintSource>(surface, cacheSize = KEPT_BUILDS, stage = BuildStages.SURFACE_WORK) { PaintSource.Surface(it) }
    val wrapped = HashMap<PartId, Node<*, PaintSource>>()
    val memo = PaintMemo()
    val eyeMemo = EyeMemo()
    return gather(
        input,
        sourcesOf = { held ->
            mapOf(PaintKey(null) to surfaceSource) + held.parts.associate { id ->
                PaintKey(id) to wrapped.getOrPut(id) { follow<HeldShapes, PaintSource>(shapes(id), cacheSize = KEPT_BUILDS, stage = BuildStages.SURFACE_WORK) { PaintSource.Shapes(it) } }
            }
        },
        cacheSize = KEPT_BUILDS,
        stage = BuildStages.SURFACE_WORK,
    ) { gathered ->
        val held = (gathered.values[PaintKey(null)] as? PaintSource.Surface)?.held?.surface ?: return@gather HeldPaint(null)
        val shapes = gathered.shape.parts.mapNotNull { id -> (gathered.values[PaintKey(id)] as? PaintSource.Shapes)?.let { id to it.held } }.toMap()
        coroutineScope {
            val eyes = async { SurfaceEyes.of(held, gathered.shape.eyes, IN_PARALLEL, eyeMemo) }
            val paint = SurfacePaint.of(held, shapes, IN_PARALLEL, memo)
            val shine = SurfacePaint(gathered.shape.shines.mapNotNull { hairShineLayer(held, it) })
            HeldPaint(paint + shine + eyes.await(), held)
        }
    }
}

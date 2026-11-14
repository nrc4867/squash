package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.BuildStages
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The garments a one surface wears, innermost first, each cut to what shows of it, and what of the body shows, [skin], cut where
 * [shown], one value a vertex of the body's drawn mesh, is at least zero; null where nothing is worn. [under] holds for the body, then
 * each garment, how far each of its vertices lies inside the coverage of the garments over it, the most of any.
 */
class SurfaceDress(val garments: List<SurfaceGarment>, val skin: LevelCut?, val shown: FloatArray? = null, val under: List<FloatArray> = emptyList()) {
    companion object {
        val NONE = SurfaceDress(emptyList(), null)
    }
}

/** What the garments node holds: the garments a one surface wears, innermost first, or null for a character built as its parts' own meshes. */
class HeldGarments(val garments: List<SurfaceGarment>?)

/** What the dress node holds: the one surface's dress, or null for a character built as its parts' own meshes. */
class HeldDress(val dress: SurfaceDress?)

/** What a one-surface [character] wears: its shown outfit's garments, innermost first, each with what it covers; none when it is not one surface. */
fun surfaceCovers(character: Character): List<GarmentCover> =
    if (character.oneSurface) character.wardrobe.worn.sortedBy { it.layer }.map { GarmentCover.of(it, character) } else emptyList()

/**
 * Each garment of [covers] grown from the body [meshed] meshes, built by a [GarmentBuilder] the node keeps, each batch of its work run in
 * parallel on the graph's dispatcher, a stale run stopping between batches. It needs the body's mesh only, so it runs beside the body's
 * finishing. See [SurfaceGarment].
 */
fun Graph.surfaceGarmentsNode(meshed: Node<*, HeldMesh>, covers: Flow<List<GarmentCover>>): Node<*, HeldGarments> {
    val builder = GarmentBuilder()
    val lock = Mutex()
    return gather(covers, { mapOf(Unit to meshed) }, cacheSize = 1, stage = BuildStages.ASSEMBLY) { gathered ->
        val body = gathered.values.getValue(Unit).meshed ?: return@gather HeldGarments(null)
        if (gathered.shape.isEmpty()) return@gather HeldGarments(emptyList())
        lock.withLock {
            val build = builder.start(body.field, body.mesh, body.character, gathered.shape)
            while (true) {
                currentCoroutineContext().ensureActive()
                IN_PARALLEL(build.next() ?: break)
            }
            HeldGarments(build.garments)
        }
    }
}

/** What the dress node reads: the body's surface, or its garments. */
private sealed interface DressSource {
    class Body(val held: HeldSurface) : DressSource

    class Worn(val held: HeldGarments) : DressSource
}

/** [garments] worn on [surface], with the skin they hide found in parallel on the graph's dispatcher. See [HiddenSkin]. */
fun Graph.surfaceDressNode(surface: Node<*, HeldSurface>, garments: Node<*, HeldGarments>): Node<*, HeldDress> {
    val body = follow<HeldSurface, DressSource>(surface, cacheSize = 1, stage = BuildStages.ASSEMBLY) { DressSource.Body(it) }
    val worn = follow<HeldGarments, DressSource>(garments, cacheSize = 1, stage = BuildStages.ASSEMBLY) { DressSource.Worn(it) }
    return gather(MutableStateFlow(Unit), { mapOf(BODY to body, WORN to worn) }, cacheSize = 1, stage = BuildStages.ASSEMBLY) { gathered ->
        val held = (gathered.values[BODY] as? DressSource.Body)?.held?.surface ?: return@gather HeldDress(null)
        val made = (gathered.values[WORN] as? DressSource.Worn)?.held?.garments ?: return@gather HeldDress(null)
        if (made.isEmpty()) return@gather HeldDress(SurfaceDress.NONE)
        val hiding = HiddenSkin(held, made)
        while (true) {
            currentCoroutineContext().ensureActive()
            IN_PARALLEL(hiding.next() ?: break)
        }
        HeldDress(SurfaceDress(hiding.worn, hiding.skin, hiding.shownSkin, hiding.under))
    }
}

private const val BODY = 0
private const val WORN = 1

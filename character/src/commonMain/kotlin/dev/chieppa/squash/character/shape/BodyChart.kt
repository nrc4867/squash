package dev.chieppa.squash.character.shape

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.surface.firstRunIndex
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/** How many rows of cells a part's chart holds along it, in u. */
const val CHART_ROWS = 64

/** How many columns of cells a part's chart holds round it, in v. */
const val CHART_COLUMNS = 32

/**
 * One part of a body as a grid of [CHART_ROWS] by [CHART_COLUMNS] cells over the (u, v) of its first run, named [patch]: the [parent]
 * it sits on, where on the parent's chart its root ring meets it ([socketU], [socketV]), and how much surface one cell holds,
 * [cellArea].
 */
class ChartPart(val id: PartId, val patch: String, val parent: PartId?, val socketU: Float, val socketV: Float, val cellArea: Float)

/**
 * A body's surface laid out as each part's grid of cells, joined where a part's root ring meets its parent at its socket. The other side
 * of a pair is read as its [leaders] side, since what is drawn on a pair holds on both; [parts] holds only the leading sides. A
 * general chart of a surface that knows nothing of what is drawn on it.
 */
class BodyChart(val parts: List<ChartPart>, private val leaders: Map<PartId, PartId> = emptyMap()) {
    private val byId = parts.associateBy { it.id }
    private val children = parts.filter { it.parent != null }.groupBy { it.parent!! }

    fun part(id: PartId): ChartPart? = byId[leaders[id] ?: id]

    /** The parts sitting on [id]. */
    fun childrenOf(id: PartId): List<ChartPart> = children[id].orEmpty()

    /** The chart part [anchor] lies on, read on the leading side of a pair, or null where it names no part's first run. */
    fun partOf(anchor: Anchor): ChartPart? = part(anchor.part)?.takeIf { it.patch == anchor.patch }

    companion object {
        /**
         * The chart of [body]'s own parts, those [includes] keeps, with the other sides of pairs read through [leaders]: each part's cells
         * sized from its first run's rings. Each part of [absent] the body leaves out is charted by the socket it would sit on, where the
         * body has that socket empty, its cells sized as its parent's and its first run named [absentPatch].
         */
        fun of(
            body: AssembledCharacter,
            leaders: Map<PartId, PartId>,
            absent: Map<PartId, SocketRef> = emptyMap(),
            absentPatch: String = BODY_PATCH,
            includes: (PlacedPart) -> Boolean = { true },
        ): BodyChart {
            val kept = body.parts.filter { it.id != null && it.covering == null && it.hanging == null && includes(it) && it.id !in leaders }
            val parts = kept.mapNotNull { placed ->
                val patch = placed.surface.patches.getOrNull(firstRunIndex(placed.surface.patches)) ?: return@mapNotNull null
                val spec = placed.socketSpec
                val parent = placed.socket?.parent?.let { leaders[it] ?: it }
                ChartPart(requireNotNull(placed.id), patch.name, parent, spec?.u ?: 0f, spec?.v ?: 0f, cellAreaOf(patch))
            }
            val left = absent.filterKeys { it !in leaders && body.part(it) == null }.mapNotNull { (id, socket) ->
                val parent = parts.firstOrNull { it.id == (leaders[socket.parent] ?: socket.parent) } ?: return@mapNotNull null
                val placed = body.part(parent.id) ?: return@mapNotNull null
                val at = body.sockets.firstOrNull { it.ref == socket }?.position ?: return@mapNotNull null
                val (u, v) = nearestOn(placed, at) ?: return@mapNotNull null
                ChartPart(id, absentPatch, parent.id, u, v, parent.cellArea)
            }
            val all = parts + left
            val ids = all.map { it.id }.toSet()
            return BodyChart(all.map { if (it.parent != null && it.parent !in ids) ChartPart(it.id, it.patch, null, 0f, 0f, it.cellArea) else it }, leaders)
        }

        /** The (u, v) of [placed]'s first run nearest [at], a point in body space, read at its grid's points. */
        private fun nearestOn(placed: PlacedPart, at: Vec3): Pair<Float, Float>? {
            val patch = placed.surface.patches.getOrNull(firstRunIndex(placed.surface.patches)) ?: return null
            if (patch.nu < 2 || patch.nv < 1) return null
            var best: Pair<Float, Float>? = null
            var nearest = Float.POSITIVE_INFINITY
            for (ring in 0 until patch.nu) for (c in 0 until patch.nv) {
                val distance = (placed.origin + placed.surface.shown(patch.point(patch.index(ring, c))) - at).length
                if (distance < nearest) {
                    nearest = distance
                    best = ring.toFloat() / (patch.nu - 1) to c.toFloat() / patch.nv
                }
            }
            return best
        }

        /** A cell's share of [patch]'s area: its length along its ring middles by its rings' mean girth, over the cells. */
        private fun cellAreaOf(patch: Patch): Float {
            if (patch.nu < 2 || patch.nv < 2) return 0f
            var length = 0f
            var girth = 0f
            var last: Vec3? = null
            for (ring in 0 until patch.nu) {
                var middle = Vec3.ZERO
                var around = 0f
                for (c in 0 until patch.nv) {
                    val point = patch.point(patch.index(ring, c))
                    middle += point
                    around += (patch.point(patch.index(ring, (c + 1) % patch.nv)) - point).length
                }
                middle *= 1f / patch.nv
                last?.let { length += (middle - it).length }
                last = middle
                girth += around
            }
            return length * (girth / patch.nu) / (CHART_ROWS * CHART_COLUMNS)
        }
    }
}

/** The row of the cell at [u]. */
internal fun rowAt(u: Float): Int = (u * CHART_ROWS).toInt().coerceIn(0, CHART_ROWS - 1)

/** The column of the cell at [v], read round. */
internal fun columnAt(v: Float): Int = ((v - floor(v)) * CHART_COLUMNS).toInt().coerceIn(0, CHART_COLUMNS - 1)

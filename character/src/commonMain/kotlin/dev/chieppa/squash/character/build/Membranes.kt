package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Membrane
import dev.chieppa.squash.document.MembraneEdge
import dev.chieppa.squash.document.MembraneId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.membranePartId
import dev.chieppa.squash.document.shownMembranes
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.Node
import kotlinx.coroutines.flow.Flow
import dev.chieppa.squash.geom.MembraneBuilder
import dev.chieppa.squash.geom.MembraneShape
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.RailRing
import dev.chieppa.squash.geom.ReadRail
import dev.chieppa.squash.geom.Vec3

/** How many places along a rail its ring is read at, the rest read between them. */
private const val RAIL_SAMPLES = 33

/** How many places along an edge its length is measured at. */
private const val EDGE_STEPS = 8

/** Rings across a membrane, from its first rail to its second, and points round each: along one face from the root to the free edge and back. */
private const val MEMBRANE_RINGS = 16
private const val MEMBRANE_SEGMENTS = 40

/** The step along a rail, a share of it, its direction there is read across. */
private const val TANGENT_STEP = 0.02f

/** The least half height a rail counts, so a rail sunk nowhere still has a thickness to swell to. */
private const val MIN_HALF = 1e-3f

private val DOWN = Vec3(0f, -1f, 0f)

/**
 * The most a rail's half height counts for, as a multiple of the thinner rail's middling one, so a membrane swells into a torso no more
 * than into the limb across from it.
 */
private const val FILLET_LIMIT = 1.5f

/** Where on a part a membrane's rail lies at one place: [part], its [patch] or none, and (u, v) there. */
data class RailPlace(val part: PartId, val patch: String?, val u: Float, val v: Float)

/**
 * A membrane's rail as a chain of edges on parts, from its root (0) to where its free edge meets it (1), each edge's share of the way its
 * length at rest.
 */
class RailChain(private val edges: List<MembraneEdge>, private val starts: FloatArray) {
    /** The parts the rail lies on. */
    val parts: Set<PartId> get() = edges.mapTo(LinkedHashSet()) { it.part }

    /** Where the rail lies at [t] of the way along it. */
    fun at(t: Float): RailPlace {
        val k = (starts.indexOfLast { it <= t }).coerceIn(0, edges.lastIndex)
        val end = if (k + 1 < starts.size) starts[k + 1] else 1f
        val f = if (end > starts[k]) ((t - starts[k]) / (end - starts[k])).coerceIn(0f, 1f) else 0f
        return edges[k].placeAt(f)
    }

    companion object {
        /** [edges] as a chain on [character]'s parts, or null when a part it names is not there. */
        fun of(edges: List<MembraneEdge>, character: AssembledCharacter): RailChain? {
            val lengths = edges.map { edge ->
                val part = character.part(edge.part) ?: return null
                (0 until EDGE_STEPS).sumOf { i ->
                    val a = part.placeOn(edge.placeAt(i.toFloat() / EDGE_STEPS)) ?: return null
                    val b = part.placeOn(edge.placeAt((i + 1).toFloat() / EDGE_STEPS)) ?: return null
                    (b - a).length.toDouble()
                }.toFloat()
            }
            val total = lengths.sum()
            val starts = FloatArray(edges.size)
            var run = 0f
            for (k in edges.indices) {
                starts[k] = if (total > 0f) run / total else k.toFloat() / edges.size
                run += lengths[k]
            }
            return RailChain(edges, starts)
        }
    }
}

/** A membrane as a part rides it: the [membrane] it is and its [first] and [second] rails, which each of its vertices' us read along. */
class Spanning(val membrane: MembraneId, val first: RailChain, val second: RailChain) {
    /** The parts it is spanned between. */
    val parts: Set<PartId> get() = first.parts + second.parts
}

private fun MembraneEdge.placeAt(f: Float): RailPlace {
    val dv = (toV - fromV).let { it - kotlin.math.round(it) }
    return RailPlace(part, patch, fromU + (toU - fromU) * f, (fromV + dv * f).mod(1f))
}

private fun PlacedPart.placeOn(place: RailPlace): Vec3? = pointOn(place.u, place.v, place.patch)

/**
 * The membranes [membranes] on this character's parts, each a part of its own, its mesh in body space: a sheet across from its first
 * rail to its second, each rail a chain of edges sunk into its parts, swelling into them and curving in along its free edge. A membrane
 * whose parts are not all there builds nothing. See HLD: surface, Membranes.
 */
fun AssembledCharacter.withMembranes(membranes: List<Membrane>): AssembledCharacter {
    if (membranes.isEmpty()) return this
    val built = membranes.mapNotNull { spanned(it, this) }
    return if (built.isEmpty()) this else AssembledCharacter(parts + built, sockets)
}

/** [membrane] built on [character], or null when a part it is anchored to is not there or builds nothing to read. */
internal fun spanned(membrane: Membrane, character: AssembledCharacter): PlacedPart? {
    val first = RailChain.of(membrane.first, character) ?: return null
    val second = RailChain.of(membrane.second, character) ?: return null
    val rawA = RailTable.of(first, character, membrane.sink) ?: return null
    val rawB = RailTable.of(second, character, membrane.sink) ?: return null
    val limit = minOf(rawA.median, rawB.median) * FILLET_LIMIT
    val a = rawA.heldTo(limit)
    val b = rawB.heldTo(limit)
    val ups = Array(RAIL_SAMPLES) { i ->
        val t = i.toFloat() / (RAIL_SAMPLES - 1)
        val along = (a.middle(t + TANGENT_STEP) - a.middle(t - TANGENT_STEP)) + (b.middle(t + TANGENT_STEP) - b.middle(t - TANGENT_STEP))
        val across = b.middle(t) - a.middle(t)
        val up = along cross across
        if (up.length > 0f) up.normalized() else DOWN
    }
    fun upAt(t: Float): Vec3 = ups[(t.coerceIn(0f, 1f) * (RAIL_SAMPLES - 1)).let { kotlin.math.round(it).toInt() }]
    val firstRail = ReadRail(FIRST_RAIL, a.widest, { t -> RailRing(a.middle(t), upAt(t), a.half(t)) }, { it })
    val secondRail = ReadRail(SECOND_RAIL, b.widest, { t -> RailRing(b.middle(t), upAt(t), b.half(t)) }, { it })
    val id = membranePartId(membrane.id)
    val across = (0 until RAIL_SAMPLES).sumOf { i -> (a.middle(i.toFloat() / (RAIL_SAMPLES - 1)).z + b.middle(i.toFloat() / (RAIL_SAMPLES - 1)).z).toDouble() }
    val away = Vec3(0f, 0f, if (across < 0.0) -1f else 1f)
    val shape = MembraneShape(membrane.thickness, membrane.edge, membrane.billow, DOWN, away)
    val patch = MembraneBuilder.membrane(id.value, firstRail, secondRail, MEMBRANE_RINGS, MEMBRANE_SEGMENTS, shape)
    val holder = requireNotNull(character.part(membrane.first.first().part))
    val mesh = PartMesh(id.value, listOf(MeshBuilder.build(patch) { 0 }))
    return PlacedPart(
        id, holder.template, holder.side, PartSurface.of(mesh, PartBuilder.axes(holder.template), mirrored = false), Vec3.ZERO,
        spanning = Spanning(membrane.id, first, second),
    )
}

/** The name a membrane's patch gives its first rail and its second, which its vertices' us read along. */
const val FIRST_RAIL = "first"
const val SECOND_RAIL = "second"

/** A rail's middle and half height read at [RAIL_SAMPLES] places along it at rest, in body space. */
private class RailTable(private val middles: Array<Vec3>, private val halves: FloatArray) {
    val widest: Float get() = halves.max()

    val median: Float get() = halves.sorted()[RAIL_SAMPLES / 2]

    /** This rail with no half height past [limit]. */
    fun heldTo(limit: Float) = RailTable(middles, FloatArray(halves.size) { minOf(halves[it], limit) })

    fun middle(t: Float): Vec3 = read(t) { i, j, f -> middles[i] + (middles[j] - middles[i]) * f }

    fun half(t: Float): Float = read(t) { i, j, f -> halves[i] + (halves[j] - halves[i]) * f }

    private fun <T> read(t: Float, between: (Int, Int, Float) -> T): T {
        val x = t.coerceIn(0f, 1f) * (RAIL_SAMPLES - 1)
        val i = x.toInt().coerceAtMost(RAIL_SAMPLES - 2)
        return between(i, i + 1, x - i)
    }

    companion object {
        /**
         * [chain] read on [character], each place [sink] of the way from the part's surface to the middle of its ring there, its half
         * height how deep that lies.
         */
        fun of(chain: RailChain, character: AssembledCharacter, sink: Float): RailTable? {
            val middles = arrayOfNulls<Vec3>(RAIL_SAMPLES)
            val halves = FloatArray(RAIL_SAMPLES)
            for (i in 0 until RAIL_SAMPLES) {
                val place = chain.at(i.toFloat() / (RAIL_SAMPLES - 1))
                val part = character.part(place.part) ?: return null
                val surface = part.pointOn(place.u, place.v, place.patch) ?: return null
                val middle = part.middleAt(place.u, place.patch) ?: return null
                middles[i] = surface + (middle - surface) * sink
                halves[i] = maxOf((surface - middle).length * sink, MIN_HALF)
            }
            return RailTable(Array(RAIL_SAMPLES) { requireNotNull(middles[it]) }, halves)
        }
    }
}

/** [assembled] with the membranes [membranes] holds spanned between its parts, following both. */
fun Graph.membranesNode(assembled: Node<*, AssembledCharacter>, membranes: Flow<List<Membrane>>): Node<*, AssembledCharacter> = gather(
    membranes,
    sourcesOf = { mapOf(Unit to assembled) },
    stage = BuildStages.ASSEMBLY,
) { (shown, built) ->
    built.getValue(Unit).withMembranes(shown)
}

/** The base colour of every membrane [character] shows: its own slot, else the base slot of the part its first rail starts on. */
fun membraneColors(character: Character): Map<PartId, PartColor> = character.shownMembranes.associate { membrane ->
    val holder = character.part(membrane.first.first().part)
    membranePartId(membrane.id) to PartColor(membrane.color ?: holder?.let { character.resolved(it).surface.baseSlot }, emptyList())
}

/** Where a new membrane's rails start along their parts, and where they end, in u. */
private const val NEW_RAIL_FROM = 0.1f
private const val NEW_RAIL_TO = 0.95f

/** How many places round a part are tried for the side that faces another. */
private const val FACING_STEPS = 32

/**
 * A new membrane [id] from part [from] to part [to] of [character]: each rail along its part from near its socket to near its end, on the
 * side facing the other part half way along them, built again on the other side when [mirrored]. Null when either part is not there.
 */
fun membraneBetween(id: MembraneId, from: PartId, to: PartId, character: AssembledCharacter, mirrored: Boolean): Membrane? {
    val a = character.part(from) ?: return null
    val b = character.part(to) ?: return null
    val towardB = b.middleAt(MIDDLE_U) ?: return null
    val towardA = a.middleAt(MIDDLE_U) ?: return null
    val vA = facingV(a, towardB) ?: return null
    val vB = facingV(b, towardA) ?: return null
    return Membrane(
        id,
        first = listOf(MembraneEdge(from, NEW_RAIL_FROM, vA, NEW_RAIL_TO, vA)),
        second = listOf(MembraneEdge(to, NEW_RAIL_FROM, vB, NEW_RAIL_TO, vB)),
        mirrored = mirrored,
    )
}

private const val MIDDLE_U = 0.5f

/** The v round [part] half way along it whose point lies nearest [target]. */
private fun facingV(part: PlacedPart, target: Vec3): Float? =
    (0 until FACING_STEPS).map { it.toFloat() / FACING_STEPS }.minByOrNull { v -> part.pointOn(MIDDLE_U, v)?.let { (it - target).length } ?: Float.MAX_VALUE }

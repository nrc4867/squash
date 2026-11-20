package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** A membrane's id. */
@Serializable
@JvmInline
value class MembraneId(val value: String)

/** How far a membrane's free edge curves in, as a share of how far its rails reach, unless it says. */
const val DEFAULT_MEMBRANE_EDGE = 0.2f

/** How far a membrane billows, as a share of the gap between its rails, unless it says. */
const val DEFAULT_MEMBRANE_BILLOW = 0.08f

/** How thick a membrane is half way between its rails, as a share of how deep its rails sit in their parts, unless it says. */
const val DEFAULT_MEMBRANE_THICKNESS = 0.2f

/** How deep into its parts a membrane's rails sit, from 0 on their surface to 1 on their middle, unless it says. */
const val DEFAULT_MEMBRANE_SINK = 0.5f

/** The most a membrane's free edge curves in, as a share of its rails' reach. */
const val MOST_MEMBRANE_EDGE = 0.6f

/** The most a membrane billows either way, as a share of the gap between its rails. */
const val MOST_MEMBRANE_BILLOW = 0.4f

/** The least and most a membrane's thickness is, as a share of its parts' thickness. */
const val LEAST_MEMBRANE_THICKNESS = 0.05f
const val MOST_MEMBRANE_THICKNESS = 0.8f

/**
 * One stretch of a membrane's rail on [part]: from ([fromU], [fromV]) to ([toU], [toV]) on its [patch], or on its first run reaching
 * that u without one. A socket point is an edge whose ends meet.
 */
@Serializable
@Doc("A stretch of a part's surface a membrane is anchored along, from one (u, v) to another; a point where the two meet.")
data class MembraneEdge(
    @Doc("The part, by id.")
    val part: PartId,
    @Doc("u along the part where the edge starts, 0 at its socket end.") @Range(min = -0.2, max = 1.2)
    val fromU: Float,
    @Doc("v round the part where the edge starts: 0 its top, 0.25 its left, 0.5 its underside, 0.75 its right.") @Range(min = 0.0, max = 1.0)
    val fromV: Float,
    @Doc("u where the edge ends.") @Range(min = -0.2, max = 1.2)
    val toU: Float,
    @Doc("v where the edge ends.") @Range(min = 0.0, max = 1.0)
    val toV: Float,
    @Doc("The run of the part it lies on, such as a digit (fingers:2); the part's first run reaching each u when left out.")
    val patch: String? = null,
)

/**
 * A thin sheet of skin spanned between parts: from the [first] rail, a chain of edges from the membrane's root to its free edge, across to
 * the [second], its free edge between the rails' ends curving in by [edge], its middle billowing by [billow], as
 * thick as [thickness] of how deep its rails sit half way between them and swelling into each, its rails sunk [sink] into their parts. It rides the
 * bones of the parts it is anchored to. With [mirrored] it is built again on the other side, on each paired part's twin.
 */
@Serializable
@Doc("A thin sheet of skin spanned between parts: a patagium from wrist to ankle, a wing from arm and finger to flank.")
data class Membrane(
    val id: MembraneId,
    @Doc("The first rail: edges in order from the membrane's root to where its free edge meets this side.")
    val first: List<MembraneEdge>,
    @Doc("The second rail, the same way.")
    val second: List<MembraneEdge>,
    @Doc("How far the free edge between the rails' ends curves in, a share of the rails' reach: 0 straight.") @Range(min = 0.0, max = 0.6)
    val edge: Float = DEFAULT_MEMBRANE_EDGE,
    @Doc("How far its middle billows off the plane between its rails, a share of the gap between them: toward the ground where it lies level, out from the body where it hangs beside it; negative the other way.") @Range(min = -0.4, max = 0.4)
    val billow: Float = DEFAULT_MEMBRANE_BILLOW,
    @Doc("How thick it is half way between its rails, a share of how deep its rails sit in their parts.") @Range(min = 0.05, max = 0.8)
    val thickness: Float = DEFAULT_MEMBRANE_THICKNESS,
    @Doc("How deep into its parts its rails sit: 0 on their surface, 1 on their middle.") @Range(min = 0.0, max = 1.0)
    val sink: Float = DEFAULT_MEMBRANE_SINK,
    @Doc("Its palette slot; the first rail's part's base colour when left out.")
    val color: SlotName? = null,
    @Doc("Whether it is built again on the other side, each paired part's twin in place of it and v mirrored on a part in the middle.")
    val mirrored: Boolean = false,
) {
    init {
        require(first.isNotEmpty() && second.isNotEmpty()) { "Membrane ${id.value} needs an edge on each rail" }
        require(edge in 0f..MOST_MEMBRANE_EDGE) { "A membrane's edge is 0 to $MOST_MEMBRANE_EDGE, not $edge" }
        require(billow in -MOST_MEMBRANE_BILLOW..MOST_MEMBRANE_BILLOW) { "A membrane's billow is within $MOST_MEMBRANE_BILLOW either way, not $billow" }
        require(thickness in LEAST_MEMBRANE_THICKNESS..MOST_MEMBRANE_THICKNESS) { "A membrane's thickness is $LEAST_MEMBRANE_THICKNESS to $MOST_MEMBRANE_THICKNESS, not $thickness" }
        require(sink in 0f..1f) { "A membrane's sink is 0 to 1, not $sink" }
    }

    /** The parts it is anchored to. */
    val parts: Set<PartId> get() = (first + second).mapTo(LinkedHashSet()) { it.part }
}

/** The id the copy of membrane [id] on the other side of the body has, where it is [Membrane.mirrored]. */
fun mirroredMembraneId(id: MembraneId): MembraneId = MembraneId(id.value + MIRRORED_MEMBRANE_SUFFIX)

private const val MIRRORED_MEMBRANE_SUFFIX = "~mirror"

/** Every membrane [this] character shows: each it holds, and each mirrored one's copy on the other side of the body. */
val Character.shownMembranes: List<Membrane>
    get() = membranes.flatMap { membrane -> if (membrane.mirrored) listOf(membrane, mirrorOf(membrane)) else listOf(membrane) }

/** [membrane] on the other side of the body: each edge on its part's twin, or across the middle of a part in the middle. */
fun Character.mirrorOf(membrane: Membrane): Membrane {
    fun mirrored(edge: MembraneEdge): MembraneEdge {
        val from = mirroredAnchor(Anchor(edge.part, edge.patch ?: BODY_PATCH, edge.fromU, edge.fromV))
        val to = mirroredAnchor(Anchor(edge.part, edge.patch ?: BODY_PATCH, edge.toU, edge.toV))
        return edge.copy(part = from.part, fromV = from.v, toV = to.v)
    }
    return membrane.copy(id = mirroredMembraneId(membrane.id), first = membrane.first.map(::mirrored), second = membrane.second.map(::mirrored), mirrored = false)
}

private const val MEMBRANE_PART_PREFIX = "membrane/"

/** The part id the membrane [id] is built, rigged, coloured and picked as. */
fun membranePartId(id: MembraneId): PartId = PartId(MEMBRANE_PART_PREFIX + id.value)

/** The membrane [part] is built as, or null when it names a part of the document. */
fun membraneOf(part: PartId): MembraneId? = part.value.takeIf { it.startsWith(MEMBRANE_PART_PREFIX) }?.let { MembraneId(it.removePrefix(MEMBRANE_PART_PREFIX)) }

/** The membrane the character holds that shows as [part], itself or the one mirrored to make it; null for any other part. */
fun Character.membraneShownAs(part: PartId): Membrane? {
    val id = membraneOf(part) ?: return null
    return membranes.firstOrNull { it.id == id || (it.mirrored && mirroredMembraneId(it.id) == id) }
}

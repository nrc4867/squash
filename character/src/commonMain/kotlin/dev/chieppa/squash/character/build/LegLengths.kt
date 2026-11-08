package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.Vec3
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The least a leg builds at, however much a lean asks of it. */
private const val LEAST_FACTOR = 0.05f

/** The point on a leg's far end its child sits on when it has no socket. */
private const val UNDERSIDE_V = 0.5f

/**
 * What each weight-bearing part of a character is built at along its axis, so all its paws stay on the ground. Anything else
 * builds at 1. See HLD: data-model, Legs stay level as they lean.
 */
data class LegLengths(private val placed: Map<PartId, Float> = emptyMap(), private val planned: Map<TemplateRef, Float> = emptyMap()) {
    fun ofPart(id: PartId): Float = placed[id] ?: 1f

    /** A part of [template] that the plan would take and nobody placed. */
    fun ofPlanned(template: TemplateRef): Float = planned[template] ?: 1f
}

/** How a part stands: [square] to the ground, and how much of its centre line's length its off-centre reach adds under a tilt. */
private data class Stance(val square: Float, val offCentre: Float = 0f) {
    val measure: Float get() = square + offCentre

    fun factor(least: Float): Float =
        if (measure == least) 1f else ((least - offCentre) / square).coerceIn(LEAST_FACTOR, 1f)
}

/** The lengths of [parts], each resolving to [data], and of what [plan] would add to them. */
fun legLengths(parts: List<AssemblyPart>, data: (PartId) -> PartData, plan: BodyPlan?, templates: TemplateLibrary): LegLengths {
    val measure = StanceMeasure(parts, data, plan, templates)
    val placed = parts.filter { templates.find(it.template)?.axis?.bearsWeight == true }.associate { it.id to measure.of(it) }
    val planned = reachableTemplates(parts, plan, templates).filter { it.axis.bearsWeight }.associate { it.ref to Stance(it.squareness()) }
    val least = (placed.values + planned.values).minOfOrNull { it.measure } ?: return LegLengths()
    return LegLengths(placed.mapValues { it.value.factor(least) }, planned.mapValues { it.value.factor(least) })
}

/** The lengths of [character]'s weight-bearing parts. */
fun legLengths(character: Character, templates: TemplateLibrary): LegLengths = legLengths(
    assemblyParts(character),
    { id -> character.part(id)?.let(character::resolvedShape) ?: PartData() },
    templates.plan(character.bodyPlan),
    templates,
)

/** [legLengths] over [templates], keeping the last character's, since every leg's build asks about the same one. */
class LegLengthsOf(private val templates: TemplateLibrary) {
    @Volatile
    private var last: Pair<Character, LegLengths>? = null

    fun of(character: Character): LegLengths {
        last?.let { (held, lengths) -> if (held === character) return lengths }
        return legLengths(character, templates).also { last = character to it }
    }
}

/** What a planned part of [template] is built at along its axis, following [document]. */
fun plannedLengthFactor(document: Flow<SquashDocument>, template: TemplateRef, templates: TemplateLibrary, lengths: LegLengthsOf): Flow<Float> {
    if (templates.find(template)?.axis?.bearsWeight != true) return MutableStateFlow(1f)
    return document.map { lengths.of(it.character).ofPlanned(template) }.distinctUntilChanged()
}

/** How square this template's axis stands to the ground: 1 straight down, less as it tilts. */
private fun PartTemplate.squareness(): Float = abs(PartBuilder.axes(this).along dot Vec3.Y)

/** Measures a part's [Stance] against what it sits on and what sits on it. */
private class StanceMeasure(
    private val parts: List<AssemblyPart>,
    private val data: (PartId) -> PartData,
    private val plan: BodyPlan?,
    private val templates: TemplateLibrary,
) {
    private fun socketsOf(template: PartTemplate): List<SocketSpec> = plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }

    fun of(part: AssemblyPart): Stance {
        val rest = templates[part.template]
        val placed = rest.placedBy(part.placement)
        val square = placed.squareness()
        if (placed.axis.rest == rest.axis.rest) return Stance(square)
        val far = socketsOf(rest).map { it.placedBy(part.placement, part.side) }.maxByOrNull { it.u }
        val shape = data(part.id)
        val length = centreLine(rest, shape, far) ?: return Stance(square)
        val leaned = reach(placed, shape, part, far) ?: return Stance(square)
        val upright = reach(rest, shape, part, far) ?: return Stance(square)
        return Stance(square, ((leaned - square * length) - (upright - length)) / length)
    }

    /** How far along its axis from the socket plane [template]'s far socket lies, at full length. */
    private fun centreLine(template: PartTemplate, shape: PartData, far: SocketSpec?): Float? {
        val ring = LegRings(template, shape).ringAt(far?.u ?: 1f) ?: return null
        return (ring.frame.center dot PartBuilder.axes(template).along).takeIf { it > 0f }
    }

    /** How far below its socket the origin of what sits on [part]'s far socket lies, with [template]'s tilt. */
    private fun reach(template: PartTemplate, shape: PartData, part: AssemblyPart, far: SocketSpec?): Float? {
        val rings = LegRings(template, shape)
        val u = far?.u ?: 1f
        val v = far?.v ?: UNDERSIDE_V
        val point = rings.positionAt(u, v) ?: return null
        val childSink = if (far?.outward == Outward.NORMAL) sinkOfChild(part, far) else 0f
        val child = point - (rings.normalAt(u, v) ?: return null) * childSink
        val plane = axisSinkOf(part)?.let { PartBuilder.axes(template).along * (templates.socketSink(part.template) * it) } ?: Vec3.ZERO
        return (plane - child).y
    }

    /** The sink multiplier of the socket [part] sits on when it sinks along its own axis, else null. */
    private fun axisSinkOf(part: AssemblyPart): Float? {
        val socket = part.socket ?: return null
        val parent = parts.firstOrNull { it.id == socket.parent } ?: return null
        val spec = templates.find(parent.template)?.let(::socketsOf)?.firstOrNull { it.name == socket.socket } ?: return null
        return spec.sink.takeIf { spec.outward == Outward.AXIS }
    }

    private fun sinkOfChild(part: AssemblyPart, far: SocketSpec): Float {
        val placed = parts.firstOrNull { it.socket?.parent == part.id && it.socket.socket == far.name }?.template
        val template = placed ?: far.takes.firstNotNullOfOrNull { templates.latest(it) }?.ref ?: return 0f
        return if (templates.find(template) != null) templates.socketSink(template) * far.sink else 0f
    }
}

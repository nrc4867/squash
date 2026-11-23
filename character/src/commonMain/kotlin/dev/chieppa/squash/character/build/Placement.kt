package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.Direction
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val PREFIX = "move."
private const val SOCKET_PREFIX = "${PREFIX}socket."
private const val JOINT_PREFIX = "${PREFIX}joint."
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
private const val SAME_U = 1e-3f

/** What the Move tool stores in a part's parameters, where pairs, overrides and undo already work. See HLD: data-model, Placing parts. */
object PlacementParams {
    /** Degrees the part leans away from the body's plane, on the left side; the right mirrors it. */
    const val OUTWARD = "${PREFIX}outward"

    /** Degrees the part leans toward its own up. */
    const val LEAN = "${PREFIX}lean"

    /**
     * How far either angle may go. Each turns the rest axis by its tangent, so the two add as a turn toward a corner and neither reaches
     * square across the rest; past this a degree moves the part too far, and a part pointing further round takes another rest.
     */
    const val MAX_ANGLE = 85f

    /** The place among [RestDirection]'s entries of the direction a part rests in, in place of its template's; see [restIn]. */
    const val REST = "${PREFIX}rest"

    /** Degrees the part rolls about its own axis, turning its up toward its v = 0.25 side; any angle. */
    const val ROLL = "${PREFIX}roll"

    /** Degrees the part turns about its own up, its tip swinging away from the body's plane on the left side; the right mirrors it. A foot's toe-out. */
    const val TOE_OUT = "${PREFIX}toeOut"

    /** Where joint [name] sits along the part. */
    fun joint(name: String) = "$JOINT_PREFIX$name"

    /** On a parent part: where socket [name] sits along it, for [side] alone, or for both with no side. */
    fun socketU(name: String, side: Side? = null) = "$SOCKET_PREFIX$name.${side.key()}u"

    /** On a parent part: where socket [name] sits around it, for [side] alone, or for both with no side. */
    fun socketV(name: String, side: Side? = null) = "$SOCKET_PREFIX$name.${side.key()}v"

    /** On a parent part: how deep, times the part's own sink, a part sits on socket [name], for [side] alone, or for both with no side. */
    fun socketSink(name: String, side: Side? = null) = "$SOCKET_PREFIX$name.${side.key()}sink"

    fun isPlacement(name: String) = name.startsWith(PREFIX)

    /** Whether [name] only says where a child sits, so the part's own build never reads it. */
    fun isSocket(name: String) = name.startsWith(SOCKET_PREFIX)

    private fun Side?.key() = when (this) {
        null -> ""
        Side.LEFT -> "left."
        Side.RIGHT -> "right."
    }
}

/**
 * A direction a part can rest in of its own, from its socket end toward its tip, in body space: [OUT] is away from the body's
 * plane on the left, which the right side mirrors.
 */
enum class RestDirection(val direction: Direction) {
    FORWARD(Direction(1f, 0f, 0f)),
    BACK(Direction(-1f, 0f, 0f)),
    UP(Direction(0f, 1f, 0f)),
    DOWN(Direction(0f, -1f, 0f)),
    OUT(Direction(0f, 0f, -1f)),
    ;

    companion object {
        /** The direction [params] give a part to rest in, or null where they leave its template's. */
        fun of(params: Map<String, Float>): RestDirection? = params[PlacementParams.REST]?.roundToInt()?.let(entries::getOrNull)

        /** The command that gives [part] [direction] to rest in, or its template's with null: one undo step. */
        fun restIn(part: PartId, direction: RestDirection?, thisSide: Boolean = false) =
            SetParameters(part, mapOf(PlacementParams.REST to direction?.ordinal?.toFloat()), thisSide)
    }
}

/** This socket where [placement], a parent part's placement values, puts it for [side]. */
internal fun SocketSpec.placedBy(placement: Map<String, Float>, side: Side?): SocketSpec {
    val u = placement[PlacementParams.socketU(name, side)] ?: placement[PlacementParams.socketU(name)] ?: u
    val v = placement[PlacementParams.socketV(name, side)] ?: placement[PlacementParams.socketV(name)] ?: v
    val sink = placement[PlacementParams.socketSink(name, side)] ?: placement[PlacementParams.socketSink(name)] ?: sink
    return copy(u = u, v = v, sink = sink)
}

/**
 * This template as a part with [params] builds it: its rest axis turned by the Move tool's lean and outward, keeping the up it had so
 * the part's drawings keep their meaning at any angle, then rolled about itself by its roll, and its joints where they were moved to. An undrawn part's bend
 * follows the joint that sat on it. Everything downstream reads the axes from here, so the panes tilt with the part.
 */
fun PartTemplate.placedBy(params: Map<String, Float>): PartTemplate {
    if (params.keys.none { PlacementParams.isPlacement(it) && !PlacementParams.isSocket(it) }) return this
    return restingBy(params).turnedBy(params).rolledBy(params).toedOutBy(params).jointsMovedBy(params)
}

/** This template rolled about its rest axis by the degrees [params] hold, its up turning toward its side. */
private fun PartTemplate.rolledBy(params: Map<String, Float>): PartTemplate {
    val roll = params[PlacementParams.ROLL] ?: 0f
    if (roll == 0f) return this
    val axes = PartAxes.of(axis.rest.toVec3(), axis.up?.toVec3())
    val angle = roll * DEGREES_TO_RADIANS
    val up = axes.up * cos(angle) + axes.side * sin(angle)
    return copy(axis = axis.copy(up = up.toDirection()))
}

/**
 * This template turned about its own up by the toe-out [params] hold, its axis swinging away from the body's plane and its up kept,
 * so a foot turns about the leg's line at the ankle with its sole flat, whatever lean or roll it has.
 */
private fun PartTemplate.toedOutBy(params: Map<String, Float>): PartTemplate {
    val toeOut = params[PlacementParams.TOE_OUT] ?: 0f
    if (toeOut == 0f) return this
    val axes = PartAxes.of(axis.rest.toVec3(), axis.up?.toVec3())
    val left = Vec3.Z * -1f
    val away = left - axes.up * (left dot axes.up)
    val outDirection = if (away.length < SAME_U) axes.side else away.normalized()
    val angle = toeOut * DEGREES_TO_RADIANS
    val along = axes.along * cos(angle) + outDirection * sin(angle)
    return copy(axis = axis.copy(rest = along.toDirection(), up = axes.up.toDirection()))
}

/** This template resting the way [params] say, its up following the new rest, or as it is when they say nothing. */
private fun PartTemplate.restingBy(params: Map<String, Float>): PartTemplate {
    val rest = RestDirection.of(params)?.direction ?: return this
    return if (rest == axis.rest) this else copy(axis = axis.copy(rest = rest, up = null))
}

private fun PartTemplate.turnedBy(params: Map<String, Float>): PartTemplate {
    val outward = params[PlacementParams.OUTWARD] ?: 0f
    val lean = params[PlacementParams.LEAN] ?: 0f
    if (outward == 0f && lean == 0f) return this
    val rest = PartAxes.of(axis.rest.toVec3(), axis.up?.toVec3())
    val left = Vec3.Z * -1f
    val away = left - rest.along * (left dot rest.along)
    val outDirection = if (away.length < SAME_U) rest.side else away.normalized()
    val along = (rest.along + outDirection * tan(outward * DEGREES_TO_RADIANS) + rest.up * tan(lean * DEGREES_TO_RADIANS)).normalized()
    return copy(axis = axis.copy(rest = along.toDirection(), up = rest.up.toDirection()))
}

private fun PartTemplate.jointsMovedBy(params: Map<String, Float>): PartTemplate {
    val moved = blocks.flatMap { it.joints() }.mapNotNull { joint -> params[PlacementParams.joint(joint.name)]?.let { joint to it } }
    if (moved.isEmpty()) return this
    val bendAt = moved.firstOrNull { (joint, _) -> abs(joint.u - axis.bendAt) < SAME_U }?.second ?: axis.bendAt
    return copy(
        axis = axis.copy(bendAt = bendAt),
        blocks = blocks.map { block -> moved.fold(block) { moving, (joint, u) -> moving.withJointAt(joint.name, u) } },
    )
}

private fun Vec3.toDirection() = Direction(x, y, z)

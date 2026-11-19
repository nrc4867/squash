package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.MAX_CURVE_PLACES
import dev.chieppa.squash.character.build.MIN_CURVE_PLACES
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.ViewParams
import dev.chieppa.squash.document.AddParameters
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.model.RecipeGeneric
import dev.chieppa.squash.recipe.model.RecipePart
import dev.chieppa.squash.recipe.model.RecipeSocket

private const val FLAG_ON = 1f
private const val FLAG_OFF = 0f

/** The command that places hand-made [socket] on [part]: one socket, or a row of them when it has a count above 1. */
internal fun BuildContext.socketCommand(path: String, part: PartId, socket: RecipeSocket): AddParameters {
    socket.sink?.takeIf { it < 0f }?.let { warn("$path.sink", "a sink below 0 is not a stand-off; give outset, in body units, with sink 0") }
    val one = HandSockets.add(part, socket.n, socket.u, socket.v, socket.outward, socket.mirrored, socket.sink, socket.outset)
    val count = socket.count
    if (count == null) {
        if (socket.toV != null) fail("$path.toV", "toV ends a row; give count too")
        return one
    }
    if (count !in 2..HandSockets.MAX_COUNT) fail("$path.count", "a row holds 2 to ${HandSockets.MAX_COUNT} sockets, not $count")
    val row = mapOf(
        HandSockets.count(socket.n) to count.toFloat(),
        HandSockets.to(socket.n) to (socket.to ?: socket.u),
        HandSockets.size(socket.n) to (socket.size ?: 1f),
        HandSockets.falloff(socket.n) to (socket.falloff ?: 1f),
    ) + listOfNotNull(socket.toV?.let { HandSockets.toV(socket.n) to it })
    return AddParameters(part, one.values + row)
}

/**
 * The part parameters [part]'s named anatomy fields stand for: its rest, generic choices, joints, landmarks, moved joints and sockets,
 * its join's ink and its curve. A key one of them sets that the part's `params` also set fails at [path].
 */
internal fun BuildContext.anatomyParams(path: String, part: RecipePart): Map<String, Float> {
    val values = linkedMapOf<String, Float>()
    part.rest?.let { values[PlacementParams.REST] = it.direction.ordinal.toFloat() }
    part.roll?.let { values[PlacementParams.ROLL] = it }
    part.topMiddle?.let { values[ViewParams.TOP_MIDDLE] = if (it) FLAG_ON else FLAG_OFF }
    part.generic?.let { values += genericParams(it) }
    part.joints.forEach { joint ->
        values[HandJoints.u(joint.n)] = joint.u
        values[HandJoints.hinge(joint.n)] = HandJoints.HINGES.indexOf(joint.hinge).toFloat()
        values[HandJoints.low(joint.n)] = minOf(joint.low, joint.high)
        values[HandJoints.high(joint.n)] = maxOf(joint.low, joint.high)
        if (joint.stretch) values[HandJoints.stretch(joint.n)] = FLAG_ON
    }
    part.landmarks.forEach { landmark ->
        values[HandLandmarks.u(landmark.n)] = landmark.u
        values[HandLandmarks.v(landmark.n)] = landmark.v
    }
    part.jointsAt.forEach { (name, u) -> values[PlacementParams.joint(name)] = u }
    part.socketsAt.forEach { (name, place) ->
        val (u, v) = pair(path, "socketsAt.$name", place, "[u, v]")
        values[PlacementParams.socketU(name)] = u
        values[PlacementParams.socketV(name)] = v
    }
    part.inkJoin?.let { values[JoinParams.INK] = if (it) FLAG_ON else FLAG_OFF }
    if (part.curve.isNotEmpty()) values += curveParams(path, part.curve)
    values.keys.firstOrNull { it in part.params }?.let { fail("$path.params.$it", "a named field of this part sets \"$it\" already") }
    return values
}

/** The curve places [curve] stands for, failing at [path] on a place that is not two or three numbers or a count out of range. */
private fun BuildContext.curveParams(path: String, curve: List<List<Float>>): Map<String, Float> {
    if (curve.size !in MIN_CURVE_PLACES..MAX_CURVE_PLACES) fail("$path.curve", "a curve holds $MIN_CURVE_PLACES to $MAX_CURVE_PLACES places, not ${curve.size}")
    val places = curve.mapIndexed { i, place ->
        if (place.size !in 2..3) fail("$path.curve[$i]", "a place is [along, up] or [along, up, side], not $place")
        Vec3(place[0], place[1], place.getOrElse(2) { 0f })
    }
    return AxisCurveParams.of(places)
}

private fun genericParams(generic: RecipeGeneric): Map<String, Float> = buildMap {
    generic.section?.let { put(GenericParams.SECTION, GenericParams.PROFILES.indexOf(it.profile).toFloat()) }
    generic.views?.let { shown -> View.entries.forEach { put(GenericParams.view(it), if (it in shown) FLAG_ON else FLAG_OFF) } }
    generic.rings?.let { put(GenericParams.RINGS, it.toFloat()) }
    generic.segments?.let { put(GenericParams.SEGMENTS, it.toFloat()) }
}

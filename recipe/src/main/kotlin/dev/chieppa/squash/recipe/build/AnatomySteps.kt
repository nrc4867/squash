package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.AxisSpiral
import dev.chieppa.squash.character.build.MAX_SPIRAL_TURNS
import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.MAX_CURVE_PLACES
import dev.chieppa.squash.character.build.MIN_CURVE_PLACES
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.DigitPatches
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.ViewParams
import dev.chieppa.squash.character.lint.MAX_SINK
import dev.chieppa.squash.character.model.CopySpec
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.AddParameters
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.model.RecipeGeneric
import dev.chieppa.squash.recipe.model.RecipePart
import dev.chieppa.squash.recipe.model.RecipeSocket

private const val FLAG_ON = 1f

/** The parameters [socket]'s spread or path stand for, its copies' layout and jitter, failing at [path] where they cannot hold. */
private fun BuildContext.copyParams(path: String, socket: RecipeSocket, siblings: List<RecipeSocket>, offered: (String) -> SocketSpec?): Map<String, Float?> {
    val n = socket.n
    if (socket.spread != null && socket.path != null) fail(path, "a socket spreads copies over an area or repeats them along a path, not both")
    if (socket.count != null) fail("$path.count", "count makes a row of sockets; a socket with spread or path gives its copies' count there")
    if (socket.mirrored) fail("$path.mirrored", "a socket showing copies is not mirrored; give each side's area or path")
    val shared = mapOf(HandSockets.size(n) to socket.size, HandSockets.falloff(n) to socket.falloff)
    socket.spread?.let { spread ->
        if (spread.count !in 1..CopySpec.MAX_COPIES) fail("$path.spread.count", "a spread shows 1 to ${CopySpec.MAX_COPIES} copies, not ${spread.count}")
        spread.rows?.let { rows ->
            if (rows < 1) fail("$path.spread.rows", "a spread in rows has at least one row, not $rows")
            if (rows * spread.count > CopySpec.MAX_COPIES) {
                fail("$path.spread.rows", "$rows rows of ${spread.count} make ${rows * spread.count} copies; a socket shows at most ${CopySpec.MAX_COPIES}")
            }
        }
        return shared + mapOf(
            HandSockets.spread(n) to spread.count.toFloat(), HandSockets.to(n) to (socket.to ?: socket.u), HandSockets.toV(n) to (socket.toV ?: socket.v),
            HandSockets.seed(n) to spread.seed.toFloat(), HandSockets.spacing(n) to spread.spacing, HandSockets.sizeJitter(n) to spread.sizeJitter,
            HandSockets.leanJitter(n) to spread.leanJitter, HandSockets.turnJitter(n) to spread.turnJitter, HandSockets.edge(n) to spread.edge, HandSockets.droop(n) to spread.droop,
            HandSockets.rows(n) to spread.rows?.toFloat(), HandSockets.stagger(n) to spread.stagger,
        )
    }
    val line = checkNotNull(socket.path)
    line.count?.let { if (it !in 1..CopySpec.MAX_COPIES) fail("$path.path.count", "a path shows 1 to ${CopySpec.MAX_COPIES} copies, not $it") }
    if (line.via.size > HandSockets.MAX_VIA) fail("$path.path.via", "a path runs through at most ${HandSockets.MAX_VIA} places, not ${line.via.size}")
    line.via.forEachIndexed { i, place -> if (place.size != 2) fail("$path.path.via[$i]", "a place is [u, v]") }
    val ends = line.between?.let { names ->
        if (names.size != 2) fail("$path.path.between", "a path runs between two sockets, not ${names.size}")
        names.mapIndexed { i, name ->
            val own = HandSockets.idOf(name)?.let { id -> siblings.firstOrNull { it.n == id } }
            own?.let { it.u to it.v } ?: offered(name)?.let { it.u to it.v }
                ?: fail("$path.path.between[$i]", "no socket \"$name\" on this part; name a socket.<n> it lists or one of its template's")
        }
    }
    val (from, to) = ends?.let { it[0] to it[1] } ?: ((socket.u to socket.v) to ((socket.to ?: socket.u) to (socket.toV ?: socket.v)))
    return shared + mapOf(
        HandSockets.u(n) to from.first, HandSockets.v(n) to from.second, HandSockets.to(n) to to.first, HandSockets.toV(n) to to.second,
        HandSockets.repeat(n) to (line.count ?: 0).toFloat(), HandSockets.pitch(n) to line.pitch, HandSockets.roll(n) to line.roll, HandSockets.sag(n) to line.sag,
    ) + line.via.flatMapIndexed { k, place -> listOf(HandSockets.viaU(n, k) to place[0], HandSockets.viaV(n, k) to place[1]) }
}
private const val FLAG_OFF = 0f

/**
 * The command that places hand-made [socket] on [part]: one socket, a row of them when it has a count above 1, or one showing copies of
 * the part on it over an area or along a path, on the digit its patch names. It warns when the part as [params] shape it builds no such
 * digit. A path [between] two sockets reads their places from [siblings], the sockets the part declares, or from [offered], the part's
 * template's own.
 */
internal fun BuildContext.socketCommand(
    path: String,
    part: PartId,
    socket: RecipeSocket,
    params: Map<String, Float> = emptyMap(),
    siblings: List<RecipeSocket> = emptyList(),
    offered: (String) -> SocketSpec? = { null },
): AddParameters {
    socket.sink?.takeIf { it < 0f }?.let { warn("$path.sink", "a sink below 0 is not a stand-off; give outset, in body units, with sink 0") }
    val digit = socket.patch?.let { patch -> digitOf("$path.patch", part, patch, params) }
    socket.reach?.takeIf { it < 0f }?.let { fail("$path.reach", "a root's reach from the axis is 0 or more: here $it") }
    val one = HandSockets.add(part, socket.n, socket.u, socket.v, socket.outward, socket.mirrored, socket.sink, socket.outset, digit, socket.reach)
    if (socket.spread != null || socket.path != null) return AddParameters(part, one.values + copyParams(path, socket, siblings, offered))
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

/** The number [patch], a digit patch of [part]'s template, goes by, failing at [path] when it is none; warns when [params] build no such digit. */
private fun BuildContext.digitOf(path: String, part: PartId, patch: String, params: Map<String, Float>): Int {
    val template = templateOf(part)
    val digit = DigitPatches.digitOf(template, patch)
    val built = DigitPatches.builtPatches(template, document.character.part(part)?.let { document.character.resolvedShape(it).params }.orEmpty() + params)
    val digits = built.filter { DigitPatches.digitOf(template, it) != null }.sorted()
    if (digit == null) fail(path, "${template.id.value} has no digit \"$patch\"; its digits are ${digits.joinToString().ifEmpty { "none" }}")
    if (patch !in built) warn(path, "${template.id.value} as shaped builds no \"$patch\"; it builds ${digits.joinToString().ifEmpty { "no digits" }}, so the socket shows nowhere")
    return digit
}

/**
 * The part parameters [part]'s named anatomy fields stand for: its rest, generic choices, joints, landmarks, moved joints and sockets,
 * its join's ink and its curve or spiral. A key one of them sets that the part's `params` also set fails at [path].
 */
internal fun BuildContext.anatomyParams(path: String, part: RecipePart): Map<String, Float> {
    val values = linkedMapOf<String, Float>()
    part.rest?.let { values[PlacementParams.REST] = it.direction.ordinal.toFloat() }
    part.roll?.let { values[PlacementParams.ROLL] = it }
    part.toeOut?.let { values[PlacementParams.TOE_OUT] = it }
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
        if (place.size !in 2..3) fail("$path.socketsAt.$name", "a socket's place is [u, v] or [u, v, sink], not $place")
        val (u, v) = pair(path, "socketsAt.$name", place.take(2), "[u, v]")
        values[PlacementParams.socketU(name)] = u
        values[PlacementParams.socketV(name)] = v
        place.getOrNull(2)?.let { sink ->
            if (sink !in 0f..MAX_SINK) fail("$path.socketsAt.$name", "a sink is 0 to $MAX_SINK, not $sink")
            values[PlacementParams.socketSink(name)] = sink
        }
    }
    part.inkJoin?.let { values[JoinParams.INK] = if (it) FLAG_ON else FLAG_OFF }
    if (part.curve.isNotEmpty()) values += curveParams(path, part.curve)
    part.spiral?.let { spiral ->
        if (part.curve.isNotEmpty()) fail("$path.spiral", "a part's axis follows its curve places or its spiral, not both")
        if (spiral.turns == 0f) fail("$path.spiral.turns", "a spiral makes some turns, up to $MAX_SPIRAL_TURNS either way")
        values += AxisCurveParams.of(AxisSpiral(spiral.turns.coerceIn(-MAX_SPIRAL_TURNS, MAX_SPIRAL_TURNS), spiral.pitch, spiral.shrink))
    }
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

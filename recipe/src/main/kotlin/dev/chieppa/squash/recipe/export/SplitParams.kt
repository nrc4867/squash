package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.character.build.AxisCurveParams
import dev.chieppa.squash.character.build.GenericParams
import dev.chieppa.squash.character.build.HandJoints
import dev.chieppa.squash.character.build.HandLandmarks
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.ViewParams
import dev.chieppa.squash.character.model.SIZE_PARAM
import dev.chieppa.squash.document.View
import dev.chieppa.squash.recipe.model.RecipeGeneric
import dev.chieppa.squash.recipe.model.RecipeJoint
import dev.chieppa.squash.recipe.model.RecipeLandmark
import dev.chieppa.squash.recipe.model.RecipeRest
import dev.chieppa.squash.recipe.model.RecipeSection
import dev.chieppa.squash.recipe.model.RecipeSocket
import kotlin.math.roundToInt

/** The keys a single hand-made socket's parameters use, which a recipe socket stands for. */
private val SOCKET_FIELDS = setOf("u", "v", "outward", "pair", "sink", "outset")

/** The keys a row of hand-made sockets adds, and the one it may add besides. */
private val ROW_FIELDS = setOf("count", "to", "size", "falloff")
private const val ROW_ROUND = "toV"

private val JOINT_FIELDS = setOf("u", "hinge", "low", "high", "stretch")
private val JOINT_NEEDS = setOf("u", "hinge", "low", "high")
private val LANDMARK_FIELDS = setOf("u", "v")
private const val SOCKET_MOVE = "move.socket."
private const val U_KEY = ".u"
private const val V_KEY = ".v"
private val SIDE_KEYS = listOf(".left", ".right")

/**
 * A part's parameters split into the recipe's named fields, each lifted only where the recipe field builds the same parameters again,
 * and the rest by name. With [anatomy] false only size, lean and outward are lifted, as a garment's shape holds the rest by name.
 */
internal class SplitParams(params: Map<String, Float>, anatomy: Boolean = true) {
    val size: Float? = params[SIZE_PARAM]
    val lean: Float? = params[PlacementParams.LEAN]
    val outward: Float? = params[PlacementParams.OUTWARD]
    private val lifted = mutableSetOf(SIZE_PARAM, PlacementParams.LEAN, PlacementParams.OUTWARD)

    val sockets: List<RecipeSocket> = if (anatomy) sockets(params) else emptyList()
    val joints: List<RecipeJoint> = if (anatomy) joints(params) else emptyList()
    val landmarks: List<RecipeLandmark> = if (anatomy) landmarks(params) else emptyList()
    val rest: RecipeRest? = if (anatomy) restOf(params) else null
    val roll: Float? = if (anatomy) params[PlacementParams.ROLL]?.also { lifted += PlacementParams.ROLL } else null
    val topMiddle: Boolean? = if (anatomy) flagOf(params, ViewParams.TOP_MIDDLE) else null
    val generic: RecipeGeneric? = if (anatomy) genericOf(params) else null
    val jointsAt: Map<String, Float> = if (anatomy) jointsAt(params) else emptyMap()
    val socketsAt: Map<String, List<Float>> = if (anatomy) socketsAt(params) else emptyMap()
    val inkJoin: Boolean? = if (anatomy) inkJoin(params) else null
    val curve: List<List<Float>> = if (anatomy) curveOf(params) else emptyList()

    /** The parameters no named field holds. */
    val others: Map<String, Float> = params.filterKeys { it !in lifted }

    private fun fields(params: Map<String, Float>, name: String): Set<String> =
        params.keys.filter { it.startsWith("$name.") }.map { it.removePrefix("$name.") }.toSet()

    private fun lift(name: String, keys: Set<String>) {
        lifted += keys.map { "$name.$it" }
    }

    private fun sockets(params: Map<String, Float>): List<RecipeSocket> = HandSockets.ids(params).mapNotNull { id ->
        val name = HandSockets.name(id)
        val keys = fields(params, name)
        val count = params[HandSockets.count(id)]
        val row = count != null && ROW_FIELDS.all { it in keys } && count == count.roundToInt().toFloat() && count.roundToInt() in 2..HandSockets.MAX_COUNT
        val allowed = if (row) SOCKET_FIELDS + ROW_FIELDS + ROW_ROUND else SOCKET_FIELDS
        if (!keys.all { it in allowed } || "u" !in keys || "v" !in keys) return@mapNotNull null
        if (params[HandSockets.pair(id)]?.let { it != 1f } == true) return@mapNotNull null
        lift(name, keys)
        RecipeSocket(
            id, params.getValue(HandSockets.u(id)), params.getValue(HandSockets.v(id)), params[HandSockets.outward(id)] ?: 0f,
            params[HandSockets.pair(id)] == 1f, params[HandSockets.sink(id)],
            outset = params[HandSockets.outset(id)],
            count = if (row) count.roundToInt() else null,
            to = if (row) params[HandSockets.to(id)] else null,
            toV = if (row) params[HandSockets.toV(id)] else null,
            size = if (row) params[HandSockets.size(id)] else null,
            falloff = if (row) params[HandSockets.falloff(id)] else null,
        )
    }

    private fun joints(params: Map<String, Float>): List<RecipeJoint> = HandJoints.ids(params).mapNotNull { id ->
        val name = HandJoints.name(id)
        val keys = fields(params, name)
        if (!keys.all { it in JOINT_FIELDS } || !keys.containsAll(JOINT_NEEDS)) return@mapNotNull null
        val hinge = params.getValue(HandJoints.hinge(id))
        val hingeAt = hinge.roundToInt()
        val low = params.getValue(HandJoints.low(id))
        val high = params.getValue(HandJoints.high(id))
        val stretch = params[HandJoints.stretch(id)]
        if (hinge != hingeAt.toFloat() || hingeAt !in HandJoints.HINGES.indices || low > high || (stretch != null && stretch != 1f)) return@mapNotNull null
        lift(name, keys)
        RecipeJoint(id, params.getValue(HandJoints.u(id)), HandJoints.HINGES[hingeAt], low, high, stretch == 1f)
    }

    private fun landmarks(params: Map<String, Float>): List<RecipeLandmark> = HandLandmarks.ids(params).mapNotNull { id ->
        val name = HandLandmarks.name(id)
        val keys = fields(params, name)
        if (keys != LANDMARK_FIELDS) return@mapNotNull null
        lift(name, keys)
        RecipeLandmark(id, params.getValue(HandLandmarks.u(id)), params.getValue(HandLandmarks.v(id)))
    }

    private fun restOf(params: Map<String, Float>): RecipeRest? {
        val value = params[PlacementParams.REST] ?: return null
        val rest = RecipeRest.entries.firstOrNull { it.direction.ordinal.toFloat() == value } ?: return null
        lifted += PlacementParams.REST
        return rest
    }

    private fun flagOf(params: Map<String, Float>, name: String): Boolean? {
        val value = params[name]?.takeIf { it == 0f || it == 1f } ?: return null
        lifted += name
        return value == 1f
    }

    private fun genericOf(params: Map<String, Float>): RecipeGeneric? {
        val section = params[GenericParams.SECTION]?.let { value -> RecipeSection.entries.firstOrNull { GenericParams.PROFILES.indexOf(it.profile).toFloat() == value } }
        val viewValues = View.entries.associateWith { params[GenericParams.view(it)] }
        val views = viewValues.takeIf { all -> all.values.all { it == 0f || it == 1f } }?.filterValues { it == 1f }?.keys?.toList()
        val rings = params[GenericParams.RINGS]?.takeIf { it == it.roundToInt().toFloat() }?.roundToInt()
        val segments = params[GenericParams.SEGMENTS]?.takeIf { it == it.roundToInt().toFloat() }?.roundToInt()
        if (section != null) lifted += GenericParams.SECTION
        if (views != null) lifted += View.entries.map(GenericParams::view)
        if (rings != null) lifted += GenericParams.RINGS
        if (segments != null) lifted += GenericParams.SEGMENTS
        return RecipeGeneric(section, views, rings, segments).takeIf { it != RecipeGeneric() }
    }

    private fun jointsAt(params: Map<String, Float>): Map<String, Float> {
        val prefix = PlacementParams.joint("")
        return params.filterKeys { it.startsWith(prefix) }.also { lifted += it.keys }.mapKeys { it.key.removePrefix(prefix) }
    }

    private fun socketsAt(params: Map<String, Float>): Map<String, List<Float>> {
        val names = params.keys.filter { it.startsWith(SOCKET_MOVE) && it.endsWith(U_KEY) }
            .map { it.removePrefix(SOCKET_MOVE).removeSuffix(U_KEY) }
            .filter { name -> SIDE_KEYS.none { name.endsWith(it) } && PlacementParams.socketV(name) in params }
        return names.associateWith { name ->
            lifted += setOf(PlacementParams.socketU(name), PlacementParams.socketV(name))
            listOf(params.getValue(PlacementParams.socketU(name)), params.getValue(PlacementParams.socketV(name)))
        }
    }

    private fun curveOf(params: Map<String, Float>): List<List<Float>> {
        val places = AxisCurveParams.places(params)
        val keys = AxisCurveParams.of(places).keys
        if (places.isEmpty() || params.keys.filter(AxisCurveParams::isCurve).any { it !in keys }) return emptyList()
        lifted += keys
        return places.map { if (it.z == 0f) listOf(it.x, it.y) else listOf(it.x, it.y, it.z) }
    }

    private fun inkJoin(params: Map<String, Float>): Boolean? {
        val value = params[JoinParams.INK]?.takeIf { it == 0f || it == 1f } ?: return null
        lifted += JoinParams.INK
        return value == 1f
    }
}

package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters

private const val PREFIX = "socket."
private const val FLAG_ON = 1f

/**
 * Sockets a person places on a part with the Move tool, held in that part's parameters as `socket.<n>.u`, `.v`, `.outward` and
 * `.pair`, so pairs, overrides, undo and saving treat them like any other. Socket `<n>` is named `socket.<n>`, takes every
 * template, and with `pair` set has a mirrored twin. See HLD: data-model, Hand-made sockets.
 */
object HandSockets {
    /** How far the outward angle turns from the surface's normal toward either end of the part, in degrees. */
    const val MAX_OUTWARD = 90f

    /** The name of hand-made socket [id], which a part on it names in its socket. */
    fun name(id: Int) = "$PREFIX$id"

    fun u(id: Int) = "${name(id)}.u"

    /** Where it sits around its part, as the left side reads it. */
    fun v(id: Int) = "${name(id)}.v"

    /** Degrees its outward direction turns from the surface's normal toward the part's end. */
    fun outward(id: Int) = "${name(id)}.outward"

    /** Set when it has a mirrored twin across the body's middle. */
    fun pair(id: Int) = "${name(id)}.pair"

    /** Whether parameter [name] holds a hand-made socket, which the part's own build never reads. */
    fun isHandSocket(name: String) = name.startsWith(PREFIX)

    /** The id of the hand-made socket called [socket], or null for any other socket. */
    fun idOf(socket: String): Int? = if (socket.startsWith(PREFIX)) socket.removePrefix(PREFIX).toIntOrNull() else null

    /** The ids of the hand-made sockets [params] hold, in order. */
    fun ids(params: Map<String, Float>): List<Int> =
        params.keys.filter(::isHandSocket).mapNotNull { it.removePrefix(PREFIX).substringBefore('.').toIntOrNull() }.distinct().sorted()

    /** The id the next socket placed on a part with [params] takes. */
    fun nextId(params: Map<String, Float>): Int = (ids(params).maxOrNull() ?: 0) + 1

    /** The hand-made sockets [params] hold. */
    fun specs(params: Map<String, Float>): List<SocketSpec> = ids(params).mapNotNull { id ->
        val u = params[u(id)] ?: return@mapNotNull null
        val v = params[v(id)] ?: return@mapNotNull null
        val pair = if ((params[pair(id)] ?: 0f) != 0f) Mirror.X else null
        SocketSpec(name(id), u, v, Outward.NORMAL, pair, emptyList(), params[outward(id)] ?: 0f)
    }

    /** The command that places socket [id] on [part] at [u] and [v], [outward] degrees from the normal, mirrored when [paired]: one undo step. */
    fun add(part: PartId, id: Int, u: Float, v: Float, outward: Float, paired: Boolean): SetParameters =
        SetParameters(part, mapOf(u(id) to u, v(id) to v, outward(id) to outward, pair(id) to if (paired) FLAG_ON else null))

    /** The command that moves socket [id] of [part] to [u] and [v]. */
    fun move(part: PartId, id: Int, u: Float, v: Float): SetParameters = SetParameters(part, mapOf(u(id) to u, v(id) to v))

    /** The command that clears socket [id] from [part]: one undo step. */
    fun delete(part: PartId, id: Int): SetParameters = SetParameters(part, listOf(u(id), v(id), outward(id), pair(id)).associateWith { null })
}

/** The sockets a part of [template] offers on [plan]: the plan's and the template's by name, then those its [params] hold by hand. */
fun socketsOn(plan: BodyPlan?, template: PartTemplate, params: Map<String, Float>): List<SocketSpec> =
    (plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }) + HandSockets.specs(params)

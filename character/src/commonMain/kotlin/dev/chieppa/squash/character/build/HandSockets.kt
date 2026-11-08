package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import kotlin.math.roundToInt

private const val PREFIX = "socket."
private const val FLAG_ON = 1f

/**
 * Sockets a person places on a part with the Move tool, held in that part's parameters as `socket.<n>.u`, `.v`, `.outward` and
 * `.pair`, so pairs, overrides, undo and saving treat them like any other. Socket `<n>` is named `socket.<n>`, takes every
 * template, and with `pair` set has a mirrored twin. With `.count` above 1 it is a row: that many sockets evenly spaced from `.u`
 * to `.to` at one v, named `socket.<n>.<i>` from 0, a part placed on the first starting at `.size` and on the last at `.falloff`
 * of that. See HLD: data-model, Hand-made sockets.
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

    /** How many sockets its row holds; one socket when unset. */
    fun count(id: Int) = "${name(id)}.count"

    /** Where along its part its row's last socket sits. */
    fun to(id: Int) = "${name(id)}.to"

    /** The size a part placed on its row's first socket starts at. */
    fun size(id: Int) = "${name(id)}.size"

    /** The size a part placed on its row's last socket starts at, as a share of the first's. */
    fun falloff(id: Int) = "${name(id)}.falloff"

    /** The name of socket [index] of row [id]. */
    fun member(id: Int, index: Int) = "${name(id)}.$index"

    /** The most sockets a row holds. */
    const val MAX_COUNT = 16

    /** The id of the row socket [socket] belongs to, or null for any other socket. */
    fun rowOf(socket: String): Int? =
        if (socket.startsWith(PREFIX)) socket.removePrefix(PREFIX).split('.').takeIf { it.size == 2 && it[1].toIntOrNull() != null }?.first()?.toIntOrNull() else null

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
    fun specs(params: Map<String, Float>): List<SocketSpec> = ids(params).flatMap { id ->
        val u = params[u(id)] ?: return@flatMap emptyList()
        val v = params[v(id)] ?: return@flatMap emptyList()
        val pair = if ((params[pair(id)] ?: 0f) != 0f) Mirror.X else null
        val tilt = params[outward(id)] ?: 0f
        val count = (params[count(id)] ?: 1f).roundToInt().coerceIn(1, MAX_COUNT)
        if (count == 1) return@flatMap listOf(SocketSpec(name(id), u, v, Outward.NORMAL, pair, emptyList(), tilt))
        val last = params[to(id)] ?: u
        val first = params[size(id)] ?: 1f
        val falloff = params[falloff(id)] ?: 1f
        List(count) { i ->
            val share = i.toFloat() / (count - 1)
            SocketSpec(member(id, i), u + (last - u) * share, v, Outward.NORMAL, pair, emptyList(), tilt, scale = first * (1f + (falloff - 1f) * share))
        }
    }

    /** The command that places socket [id] on [part] at [u] and [v], [outward] degrees from the normal, mirrored when [paired]: one undo step. */
    fun add(part: PartId, id: Int, u: Float, v: Float, outward: Float, paired: Boolean): SetParameters =
        SetParameters(part, mapOf(u(id) to u, v(id) to v, outward(id) to outward, pair(id) to if (paired) FLAG_ON else null))

    /**
     * The command that places a row of [count] sockets on [part] from [u] to [to] at [v], [outward] degrees from the normal, mirrored when
     * [paired], parts on it starting at [size] on the first and [falloff] of that on the last: one undo step.
     */
    fun addRow(part: PartId, id: Int, u: Float, to: Float, v: Float, outward: Float, paired: Boolean, count: Int, size: Float, falloff: Float): SetParameters =
        SetParameters(
            part,
            add(part, id, u, v, outward, paired).values + mapOf(count(id) to count.toFloat(), to(id) to to, size(id) to size, falloff(id) to falloff),
        )

    /** The command that moves socket [id] of [part] to [u] and [v]. */
    fun move(part: PartId, id: Int, u: Float, v: Float): SetParameters = SetParameters(part, mapOf(u(id) to u, v(id) to v))

    /** The command that clears socket [id] from [part]: one undo step. */
    fun delete(part: PartId, id: Int): SetParameters = SetParameters(part, listOf(u(id), v(id), outward(id), pair(id), count(id), to(id), size(id), falloff(id)).associateWith { null })
}

/**
 * The sockets a part of [template] offers on [plan]: the plan's and the template's by name, those its [params] hold by hand, then its
 * digits' tips, for the digits among [built], the patches its build made, or without them for the digits [params] would build.
 */
fun socketsOn(plan: BodyPlan?, template: PartTemplate, params: Map<String, Float>, built: Collection<String>? = null): List<SocketSpec> =
    (plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }) + HandSockets.specs(params) +
        (built?.let { PartBuilder.tipSockets(template, it) } ?: PartBuilder.tipSockets(template, params))

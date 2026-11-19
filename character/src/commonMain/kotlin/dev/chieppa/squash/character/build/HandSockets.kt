package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.Outward
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.AddParameters
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private const val PREFIX = "socket."
private const val FLAG_ON = 1f

/** How near a whole turn a row's run round its part counts as one, closing the row. */
private const val CLOSED_TURN = 1e-3f

/** [v] taken into 0 to 1, as round a part. */
private fun wrapped(v: Float): Float = if (v in 0f..1f) v else v - floor(v)

/**
 * Sockets a person places on a part with the Move tool, held in that part's parameters as `socket.<n>.u`, `.v`, `.outward`,
 * `.sink`, `.outset` and `.pair`, so pairs, overrides, undo and saving treat them like any other. Socket `<n>` is named `socket.<n>`, takes every
 * template, and with `pair` set has a mirrored twin. With `.count` above 1 it is a row: that many sockets evenly spaced from `.u`
 * to `.to` along it and from `.v` to `.toV` round it, named `socket.<n>.<i>` from 0, a part placed on the first starting at `.size` and on the
 * last at `.falloff` of that; a row whose v runs a whole turn closes round the part, its sockets a turn's share apart. See HLD:
 * data-model, Placing parts.
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

    /** How deep a part sits inside its parent here, as a multiple of that part's own sink, as a plan socket's `sink` is; 1 when unset. */
    fun sink(id: Int) = "${name(id)}.sink"

    /** How far a part on it stands off the surface along its normal, in body units, before it sinks; 0 when unset. */
    fun outset(id: Int) = "${name(id)}.outset"

    /** Set when it has a mirrored twin across the body's middle. */
    fun pair(id: Int) = "${name(id)}.pair"

    /** How many sockets its row holds; one socket when unset. */
    fun count(id: Int) = "${name(id)}.count"

    /** Where along its part its row's last socket sits. */
    fun to(id: Int) = "${name(id)}.to"

    /** Where round its part its row's last socket sits; the row's v when unset. A whole turn from its v closes the row round the part. */
    fun toV(id: Int) = "${name(id)}.toV"

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
        val sink = params[sink(id)] ?: 1f
        val outset = params[outset(id)] ?: 0f
        val count = (params[count(id)] ?: 1f).roundToInt().coerceIn(1, MAX_COUNT)
        if (count == 1) return@flatMap listOf(SocketSpec(name(id), u, v, Outward.NORMAL, pair, emptyList(), tilt, sink, outset = outset))
        val last = params[to(id)] ?: u
        val lastV = params[toV(id)] ?: v
        val closed = abs(lastV - v) >= 1f - CLOSED_TURN
        val first = params[size(id)] ?: 1f
        val falloff = params[falloff(id)] ?: 1f
        List(count) { i ->
            val share = i.toFloat() / (count - 1)
            val round = if (closed) sign(lastV - v) * i.toFloat() / count else (lastV - v) * share
            val at = wrapped(v + round)
            SocketSpec(member(id, i), u + (last - u) * share, at, Outward.NORMAL, pair, emptyList(), tilt, sink, scale = first * (1f + (falloff - 1f) * share), outset = outset)
        }
    }

    /**
     * The command that places socket [id] on [part] at [u] and [v], [outward] degrees from the normal, mirrored when [paired], a part on
     * it stood [outset] off the surface and sunk [sink] times its own depth, or once with null: one undo step. It fails on a part that already holds socket [id]; [move]
     * moves one.
     */
    fun add(part: PartId, id: Int, u: Float, v: Float, outward: Float, paired: Boolean, sink: Float? = null, outset: Float? = null): AddParameters =
        AddParameters(part, mapOf(u(id) to u, v(id) to v, outward(id) to outward, pair(id) to if (paired) FLAG_ON else null, sink(id) to sink, outset(id) to outset))

    /**
     * The command that places a row of [count] sockets on [part] from [u] to [to] at [v], [outward] degrees from the normal, mirrored when
     * [paired], parts on it starting at [size] on the first and [falloff] of that on the last: one undo step. It fails as [add] does.
     */
    fun addRow(part: PartId, id: Int, u: Float, to: Float, v: Float, outward: Float, paired: Boolean, count: Int, size: Float, falloff: Float): AddParameters =
        AddParameters(
            part,
            add(part, id, u, v, outward, paired).values + mapOf(count(id) to count.toFloat(), to(id) to to, size(id) to size, falloff(id) to falloff),
        )

    /** The command that moves socket [id] of [part] to [u] and [v]. */
    fun move(part: PartId, id: Int, u: Float, v: Float): SetParameters = SetParameters(part, mapOf(u(id) to u, v(id) to v))

    /** The command that clears socket [id] from [part]: one undo step. */
    fun delete(part: PartId, id: Int): SetParameters = SetParameters(part, listOf(u(id), v(id), outward(id), sink(id), outset(id), pair(id), count(id), to(id), toV(id), size(id), falloff(id)).associateWith { null })
}

/**
 * The sockets a part of [template] offers on [plan]: the plan's and the template's by name, those its [params] hold by hand, then its
 * digits' tips, for the digits among [built], the patches its build made, or without them for the digits [params] would build.
 */
fun socketsOn(plan: BodyPlan?, template: PartTemplate, params: Map<String, Float>, built: Collection<String>? = null): List<SocketSpec> =
    (plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }) + HandSockets.specs(params) +
        (built?.let { PartBuilder.tipSockets(template, it) } ?: PartBuilder.tipSockets(template, params))

package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.character.model.CopyArea
import dev.chieppa.squash.character.model.CopyLayout
import dev.chieppa.squash.character.model.CopySpec
import dev.chieppa.squash.character.model.SurfacePlace
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
 * last at `.falloff` of that; a row whose v runs a whole turn closes round the part, its sockets a turn's share apart. With `.spread` it
 * is one socket showing that many copies of the part on it over the area from `.u` to `.to` and `.v` to `.toV`, and with `.repeat` copies
 * along the path from (`.u`, `.v`) through its `.via.<k>` places to (`.to`, `.toV`); see [CopySpec]. See HLD: data-model, Placing parts
 * and Copies.
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

    /** The digit it sits on, as [DigitPatches] numbers them, so its u and v lie on that digit's patch; the part's first run reaching u when unset. */
    fun digit(id: Int) = "${name(id)}.digit"

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

    /** How many copies of its part it shows over an area of its part. */
    fun spread(id: Int) = "${name(id)}.spread"

    /** How many copies of its part it shows along a path on its part; with 0, as many as its pitch fits. */
    fun repeat(id: Int) = "${name(id)}.repeat"

    /** The seed its copies' places and jitter are picked from. */
    fun seed(id: Int) = "${name(id)}.seed"

    /** The least distance between two copies over an area, in body units. */
    fun spacing(id: Int) = "${name(id)}.spacing"

    /** The outer share of an area its copies' size falls off over toward its falloff. */
    fun edge(id: Int) = "${name(id)}.edge"

    /** How far each copy's size varies either way, as a share of it. */
    fun sizeJitter(id: Int) = "${name(id)}.sizeJitter"

    /** The most degrees a copy leans from the surface's normal. */
    fun leanJitter(id: Int) = "${name(id)}.leanJitter"

    /** The degrees each copy tilts from the surface's normal toward the body's down along the surface, so hair lies down over what it grows from. */
    fun droop(id: Int) = "${name(id)}.droop"

    /** The most degrees a copy turns about itself either way. */
    fun turnJitter(id: Int) = "${name(id)}.turnJitter"

    /** The distance between copies along a path, in body units; from the part's size when unset. */
    fun pitch(id: Int) = "${name(id)}.pitch"

    /** The rows a spread lays its copies in, evenly across its area, its count in each: plates, scales, shingles. */
    fun rows(id: Int) = "${name(id)}.rows"

    /** How far round, as a share of the gap between two copies in a row, every other row of a spread in rows is shifted. */
    fun stagger(id: Int) = "${name(id)}.stagger"

    /** The degrees each copy along a path rolls about the path more than the one before it, every other one turned. */
    fun roll(id: Int) = "${name(id)}.roll"

    /** How far a path droops at its middle, in body units, as a chain hangs between its two ends. */
    fun sag(id: Int) = "${name(id)}.sag"

    /** Where along its part place [k] a path runs through sits. */
    fun viaU(id: Int, k: Int) = "${name(id)}.via.$k.u"

    /** Where round its part place [k] a path runs through sits. */
    fun viaV(id: Int, k: Int) = "${name(id)}.via.$k.v"

    /** The most places a path runs through between its ends. */
    const val MAX_VIA = 24

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

    /** The hand-made sockets [params] hold, a socket on a digit on the patch [digitPatch] names for its number. */
    fun specs(params: Map<String, Float>, digitPatch: (Int) -> String? = { null }): List<SocketSpec> = ids(params).flatMap { id ->
        val u = params[u(id)] ?: return@flatMap emptyList()
        val v = params[v(id)] ?: return@flatMap emptyList()
        val pair = if ((params[pair(id)] ?: 0f) != 0f) Mirror.X else null
        val tilt = params[outward(id)] ?: 0f
        val sink = params[sink(id)] ?: 1f
        val outset = params[outset(id)] ?: 0f
        val patch = params[digit(id)]?.roundToInt()?.let(digitPatch)
        copiesOf(params, id)?.let { return@flatMap listOf(SocketSpec(name(id), u, v, Outward.NORMAL, pair, emptyList(), tilt, sink, patch = patch, outset = outset, copies = it.copy(patch = patch))) }
        val count = (params[count(id)] ?: 1f).roundToInt().coerceIn(1, MAX_COUNT)
        if (count == 1) return@flatMap listOf(SocketSpec(name(id), u, v, Outward.NORMAL, pair, emptyList(), tilt, sink, patch = patch, outset = outset))
        val last = params[to(id)] ?: u
        val lastV = params[toV(id)] ?: v
        val closed = abs(lastV - v) >= 1f - CLOSED_TURN
        val first = params[size(id)] ?: 1f
        val falloff = params[falloff(id)] ?: 1f
        List(count) { i ->
            val share = i.toFloat() / (count - 1)
            val round = if (closed) sign(lastV - v) * i.toFloat() / count else (lastV - v) * share
            val at = wrapped(v + round)
            SocketSpec(member(id, i), u + (last - u) * share, at, Outward.NORMAL, pair, emptyList(), tilt, sink, scale = first * (1f + (falloff - 1f) * share), patch = patch, outset = outset)
        }
    }

    /** The copies socket [id] of [params] shows, or null when it is a plain socket or a row. */
    fun copiesOf(params: Map<String, Float>, id: Int): CopySpec? {
        val spread = params[spread(id)]
        val repeat = params[repeat(id)]
        if (spread == null && repeat == null) return null
        val u = params[u(id)] ?: return null
        val v = params[v(id)] ?: return null
        val to = params[to(id)] ?: u
        val toV = params[toV(id)] ?: v
        val layout = if (spread != null) CopyLayout.AREA else CopyLayout.PATH
        val rows = if (spread != null) (params[rows(id)] ?: 0f).roundToInt().coerceAtLeast(0) else 0
        val count = ((spread ?: repeat) ?: 0f).roundToInt().coerceIn(0, if (rows > 0) CopySpec.MAX_COPIES / rows else CopySpec.MAX_COPIES)
        val via = (0 until MAX_VIA).mapNotNull { k -> params[viaU(id, k)]?.let { vu -> params[viaV(id, k)]?.let { vv -> SurfacePlace(vu, vv) } } }
        return CopySpec(
            layout = layout,
            count = count,
            area = CopyArea(minOf(u, to), maxOf(u, to), minOf(v, toV), maxOf(v, toV)),
            path = if (layout == CopyLayout.PATH) listOf(SurfacePlace(u, v)) + via + SurfacePlace(to, toV) else emptyList(),
            seed = (params[seed(id)] ?: 0f).roundToInt(),
            spacing = params[spacing(id)] ?: 0f,
            size = params[size(id)] ?: 1f,
            falloff = params[falloff(id)] ?: 1f,
            edge = params[edge(id)] ?: CopySpec.DEFAULT_EDGE,
            sizeJitter = params[sizeJitter(id)] ?: 0f,
            leanJitter = params[leanJitter(id)] ?: 0f,
            turnJitter = params[turnJitter(id)] ?: 0f,
            droop = params[droop(id)] ?: 0f,
            tilt = params[outward(id)] ?: 0f,
            sink = params[sink(id)] ?: 1f,
            outset = params[outset(id)] ?: 0f,
            pitch = params[pitch(id)] ?: 0f,
            roll = params[roll(id)] ?: 0f,
            sag = params[sag(id)] ?: 0f,
            rows = rows,
            stagger = params[stagger(id)] ?: CopySpec.DEFAULT_STAGGER,
        )
    }

    /**
     * The command that places a socket [id] on [part] showing [count] copies of the part on it scattered over the area from [u] to [to]
     * along it and [v] to [toV] round it, picked by [seed], none nearer another than [spacing], each varying in size by [sizeJitter] and
     * leaning and turning by up to [leanJitter] and [turnJitter] degrees: one undo step. It fails as [add] does.
     */
    fun addSpread(
        part: PartId, id: Int, u: Float, to: Float, v: Float, toV: Float, count: Int, seed: Int,
        spacing: Float = 0f, sizeJitter: Float = 0f, leanJitter: Float = 0f, turnJitter: Float = 0f, outward: Float = 0f, droop: Float = 0f,
    ): AddParameters = AddParameters(
        part,
        add(part, id, u, v, outward, false).values + mapOf(
            spread(id) to count.toFloat(), to(id) to to, toV(id) to toV, seed(id) to seed.toFloat(),
            spacing(id) to spacing.takeIf { it > 0f }, sizeJitter(id) to sizeJitter.takeIf { it > 0f },
            leanJitter(id) to leanJitter.takeIf { it > 0f }, turnJitter(id) to turnJitter.takeIf { it > 0f }, droop(id) to droop.takeIf { it != 0f },
        ),
    )

    /**
     * The command that places a socket [id] on [part] showing [count] copies of the part on it along the path from [u], [v] to [to],
     * [toV], each rolled [roll] degrees more than the one before it, drooping [sag] at its middle: one undo step. It fails as [add] does.
     */
    fun addPath(part: PartId, id: Int, u: Float, v: Float, to: Float, toV: Float, count: Int, roll: Float = 0f, sag: Float = 0f): AddParameters =
        AddParameters(
            part,
            add(part, id, u, v, 0f, false).values + mapOf(
                repeat(id) to count.toFloat(), to(id) to to, toV(id) to toV, roll(id) to roll.takeIf { it != 0f }, sag(id) to sag.takeIf { it > 0f },
            ),
        )

    /**
     * The command that places socket [id] on [part] at [u] and [v], [outward] degrees from the normal, mirrored when [paired], a part on
     * it stood [outset] off the surface and sunk [sink] times its own depth, or once with null, on [digit] when given: one undo step. It fails
     * on a part that already holds socket [id]; [move] moves one.
     */
    fun add(part: PartId, id: Int, u: Float, v: Float, outward: Float, paired: Boolean, sink: Float? = null, outset: Float? = null, digit: Int? = null): AddParameters =
        AddParameters(
            part,
            mapOf(u(id) to u, v(id) to v, outward(id) to outward, pair(id) to if (paired) FLAG_ON else null, sink(id) to sink, outset(id) to outset, digit(id) to digit?.toFloat()),
        )

    /**
     * The command that places a row of [count] sockets on [part] from [u] to [to] at [v], [outward] degrees from the normal, mirrored when
     * [paired], parts on it starting at [size] on the first and [falloff] of that on the last: one undo step. It fails as [add] does.
     */
    fun addRow(part: PartId, id: Int, u: Float, to: Float, v: Float, outward: Float, paired: Boolean, count: Int, size: Float, falloff: Float, digit: Int? = null): AddParameters =
        AddParameters(
            part,
            add(part, id, u, v, outward, paired, digit = digit).values + mapOf(count(id) to count.toFloat(), to(id) to to, size(id) to size, falloff(id) to falloff),
        )

    /** The command that moves socket [id] of [part] to [u] and [v]. */
    fun move(part: PartId, id: Int, u: Float, v: Float): SetParameters = SetParameters(part, mapOf(u(id) to u, v(id) to v))

    /** The command that clears socket [id] from [part]: one undo step. */
    fun delete(part: PartId, id: Int): SetParameters = SetParameters(part, keysOf(id).associateWith { null })

    /** Every parameter socket [id] may hold. */
    fun keysOf(id: Int): List<String> = listOf(
        u(id), v(id), outward(id), sink(id), outset(id), pair(id), count(id), to(id), toV(id), size(id), falloff(id), digit(id), spread(id), repeat(id),
        seed(id), spacing(id), edge(id), sizeJitter(id), leanJitter(id), turnJitter(id), droop(id), pitch(id), roll(id), sag(id),
    ) + (0 until MAX_VIA).flatMap { listOf(viaU(id, it), viaV(id, it)) }
}

/**
 * The sockets a part of [template] offers on [plan]: the plan's and the template's by name, those its [params] hold by hand, then its
 * digits' tips, for the digits among [built], the patches its build made, or without them for the digits [params] would build.
 */
fun socketsOn(plan: BodyPlan?, template: PartTemplate, params: Map<String, Float>, built: Collection<String>? = null): List<SocketSpec> =
    (plan?.socketsOn(template) ?: template.blocks.flatMap { it.sockets() }) + HandSockets.specs(params) { DigitPatches.patchOf(template, it) } +
        (built?.let { PartBuilder.tipSockets(template, it) } ?: PartBuilder.tipSockets(template, params))

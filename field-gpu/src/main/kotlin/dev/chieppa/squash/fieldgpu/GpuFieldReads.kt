package dev.chieppa.squash.fieldgpu

import dev.chieppa.squash.geom.field.ClippedSolid
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldBatch
import dev.chieppa.squash.geom.field.FieldReads
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.SweptSolid
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Field reads on a GPU of their own, [GpuFieldDevice] opened by [open] at the first batch. Batches asked for at once from several
 * threads are read together: whichever thread holds the device reads every batch waiting, in one compute dispatch, while the others wait
 * for it. Queries of swept solids, plain or clipped, are read on the GPU and any other solid's on the CPU. Should the device fail to open
 * or a dispatch fail, those batches and every one after them are read on the CPU instead, one point at a time where asked, and
 * [generation] changes so values read on the GPU are read again.
 */
class GpuFieldReads(private val open: () -> GpuFieldDevice = { GpuFieldDevice() }) : FieldReads, AutoCloseable {
    @Volatile
    private var failed = false

    @Volatile
    private var turn = 0

    private var device: GpuFieldDevice? = null
    private val waiting = ConcurrentLinkedQueue<Asked>()

    /** The queries and answers of the dispatch being made, kept from one to the next; the lane alone touches them. */
    private var queries = IntArray(0)
    private var answers = FloatArray(0)
    private val lock = ReentrantLock()

    /** The thread that drives the device, above the threads the reads come from so it wakes as soon as the GPU is done. */
    private val lane = Executors.newSingleThreadExecutor { work ->
        Thread(work, "gpu-field-reads").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
    }

    /** Why the GPU stopped being used, once it has. */
    @Volatile
    var failure: Throwable? = null
        private set

    /** Dispatches made so far, the batches and queries they read, and the time spent reading them. */
    val stats = ReadStats()

    override val gathers: Boolean get() = !failed

    override val generation: Int get() = turn

    override fun read(batch: FieldBatch) {
        if (failed) return CpuFieldReads.read(batch)
        val mine = Asked(batch)
        waiting += mine
        while (!mine.done) {
            lock.withLock {
                if (!mine.done) serve()
            }
        }
        mine.failure?.let { throw it }
    }

    /** Reads every batch waiting, together. */
    private fun serve() {
        val taken = ArrayList<Asked>()
        while (true) taken += waiting.poll() ?: break
        if (taken.isEmpty()) return
        val from = System.nanoTime()
        try {
            if (failed) {
                taken.forEach { CpuFieldReads.read(it.batch) }
            } else {
                try {
                    lane.submit {
                        val gpu = device ?: open().also { device = it }
                        readOn(gpu, taken.map { it.batch })
                    }.get()
                } catch (thrown: Throwable) {
                    val e = (thrown as? ExecutionException)?.cause ?: thrown
                    if (e is VirtualMachineError) throw e
                    failure = e
                    failed = true
                    turn++
                    runCatching { device?.close() }
                    device = null
                    taken.forEach { CpuFieldReads.read(it.batch) }
                }
            }
        } catch (e: Throwable) {
            taken.forEach { it.failure = e }
        } finally {
            stats.add(taken.size, taken.sumOf { it.batch.size }, System.nanoTime() - from)
            taken.forEach { it.done = true }
        }
    }

    private fun readOn(gpu: GpuFieldDevice, batches: List<FieldBatch>) {
        val offsets = IntArray(batches.size + 1)
        batches.forEachIndexed { b, batch -> offsets[b + 1] = offsets[b] + batch.members.size }
        val memberCount = offsets[batches.size]
        val onGpu = BooleanArray(memberCount)
        val solids = IdentityHashMap<SweptSolid, Unit>()
        batches.forEachIndexed { b, batch ->
            batch.members.forEachIndexed { m, member ->
                val swept = sweptOf(member) ?: return@forEachIndexed
                onGpu[offsets[b] + m] = true
                solids[swept] = Unit
            }
        }
        gpu.hold(solids.keys.toList())
        val packed = IntArray(memberCount * MEMBER_INTS)
        batches.forEachIndexed { b, batch ->
            batch.members.forEachIndexed { m, member -> if (onGpu[offsets[b] + m]) packMember(gpu, member, packed, (offsets[b] + m) * MEMBER_INTS) }
        }
        val total = batches.sumOf { it.size }
        if (queries.size < total * 4) queries = IntArray(total * 4 * 2)
        var count = 0
        for ((b, batch) in batches.withIndex()) for (i in 0 until batch.size) {
            val m = offsets[b] + batch.member[i]
            if (!onGpu[m]) continue
            queries[count * 4] = batch.x[i].toRawBits()
            queries[count * 4 + 1] = batch.y[i].toRawBits()
            queries[count * 4 + 2] = batch.z[i].toRawBits()
            queries[count * 4 + 3] = m or if (batch.place[i]) Int.MIN_VALUE else 0
            count++
        }
        val sent = System.nanoTime()
        if (answers.size < count * 3) answers = FloatArray(count * 3 * 2)
        gpu.dispatch(packed, queries, count, answers)
        stats.dispatched(System.nanoTime() - sent)
        var q = 0
        for ((b, batch) in batches.withIndex()) {
            var rest: FieldBatch? = null
            for (i in 0 until batch.size) {
                if (!onGpu[offsets[b] + batch.member[i]]) {
                    rest = rest ?: FieldBatch(batch.members)
                    rest.add(batch.member[i], batch.point(i), batch.place[i])
                    continue
                }
                batch.field[i] = answers[q * 3]
                if (batch.place[i]) {
                    batch.u[i] = answers[q * 3 + 1]
                    batch.v[i] = answers[q * 3 + 2]
                }
                q++
            }
            if (rest != null) {
                CpuFieldReads.read(rest)
                var r = 0
                for (i in 0 until batch.size) if (!onGpu[offsets[b] + batch.member[i]]) {
                    batch.field[i] = rest.field[r]
                    batch.u[i] = rest.u[r]
                    batch.v[i] = rest.v[r]
                    r++
                }
            }
        }
    }

    private fun packMember(gpu: GpuFieldDevice, member: JoinedMember, out: IntArray, o: Int) {
        val clipped = member.solid as? ClippedSolid
        val solid = gpu.indexOf(checkNotNull(sweptOf(member)))
        out[o] = member.origin.x.toRawBits()
        out[o + 1] = member.origin.y.toRawBits()
        out[o + 2] = member.origin.z.toRawBits()
        out[o + 3] = member.ease.toRawBits()
        out[o + 4] = (clipped?.low?.x ?: 0f).toRawBits()
        out[o + 5] = (clipped?.low?.y ?: 0f).toRawBits()
        out[o + 6] = (clipped?.low?.z ?: 0f).toRawBits()
        out[o + 7] = (if (member.mirrored) 1f else 0f).toRawBits()
        out[o + 8] = (clipped?.high?.x ?: 0f).toRawBits()
        out[o + 9] = (clipped?.high?.y ?: 0f).toRawBits()
        out[o + 10] = (clipped?.high?.z ?: 0f).toRawBits()
        out[o + 11] = solid or if (clipped != null) Int.MIN_VALUE else 0
    }

    private fun sweptOf(member: JoinedMember): SweptSolid? = when (val solid = member.solid) {
        is SweptSolid -> solid
        is ClippedSolid -> solid.solid as? SweptSolid
        else -> null
    }

    override fun close() = lock.withLock {
        lane.submit {
            device?.close()
            device = null
        }.get()
        lane.shutdown()
    }

    /** A batch waiting to be read, and whether it has been. */
    private class Asked(val batch: FieldBatch) {
        @Volatile
        var done = false

        @Volatile
        var failure: Throwable? = null
    }

    internal companion object {
        /** Values a member takes in the members buffer. */
        const val MEMBER_INTS = 12
    }
}

/** What a [GpuFieldReads] has read: dispatches, the batches and queries in them, and the time spent, all of it and on the device. */
class ReadStats {
    @Volatile
    var dispatches: Long = 0
        private set

    @Volatile
    var batches: Long = 0
        private set

    @Volatile
    var queries: Long = 0
        private set

    @Volatile
    var nanos: Long = 0
        private set

    @Volatile
    var deviceNanos: Long = 0
        private set

    internal fun add(batches: Int, queries: Int, nanos: Long) {
        dispatches++
        this.batches += batches
        this.queries += queries
        this.nanos += nanos
    }

    internal fun dispatched(nanos: Long) {
        deviceNanos += nanos
    }

    override fun toString(): String =
        "$dispatches dispatches of $batches batches, $queries queries, %.0f ms reading, %.0f ms of it on the device".format(nanos / 1e6, deviceNanos / 1e6)
}

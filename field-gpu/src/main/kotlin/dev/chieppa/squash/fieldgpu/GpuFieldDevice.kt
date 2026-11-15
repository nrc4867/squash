package dev.chieppa.squash.fieldgpu

import dev.chieppa.squash.geom.field.RunRings
import dev.chieppa.squash.geom.field.SweptSolid
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.HeadlessGpu
import dev.chieppa.stretch.gpu.webgpu.BufferBinding
import dev.chieppa.stretch.gpu.webgpu.BufferUsage
import dev.chieppa.stretch.gpu.webgpu.ComputePipelineSpec
import dev.chieppa.stretch.gpu.webgpu.GpuBindGroup
import dev.chieppa.stretch.gpu.webgpu.GpuBuffer
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

/** Rings and solids the device holds at first; it holds more when a batch needs them. */
private const val FIRST_RINGS = 16_384
private const val FIRST_SOLIDS = 512
private const val FIRST_QUERIES = 65_536
private const val FIRST_MEMBERS = 256

/** Bytes a ring's section values take, and a solid's and a query's entry. */
private const val SOLID_BYTES = 16L
private const val QUERY_BYTES = 16L
private const val ANSWER_BYTES = 12L

/**
 * A headless GPU device reading swept solids' fields with [FIELD_SHADER]: the solids a batch reads are held on the device until it runs
 * out of room, when it starts again from the batch's own; each batch's members and queries are written, dispatched once and read back.
 * Throws when the device cannot be opened or reports an error. One batch at a time.
 */
class GpuFieldDevice : AutoCloseable {
    private val errors = ArrayList<String>()
    private val gpu = runBlocking { HeadlessGpu.open(GpuOptions(onError = { synchronized(errors) { errors += it } })) }
    private val device = gpu.device
    private val shader = device.createShaderModule(FIELD_SHADER)
    private val pipeline = device.createComputePipeline(ComputePipelineSpec(shader, "main"))
    private val params = storage(16L)

    /** What the GPU is, for reports. */
    val description: String get() = gpu.description

    private var ringRoom = FIRST_RINGS
    private var solidRoom = FIRST_SOLIDS
    private var queryRoom = FIRST_QUERIES
    private var memberRoom = FIRST_MEMBERS
    private var rings = storage(ringRoom * RING_STRIDE * 4L)
    private var radii = storage(ringRoom * RunRings.ANGLES * 4L)
    private var vs = storage(ringRoom * RunRings.ANGLES * 4L)
    private var solids = storage(solidRoom * SOLID_BYTES)
    private var queries = storage(queryRoom * QUERY_BYTES)
    private var members = storage(memberRoom * MemberBytes)
    private var answers = device.createBuffer(queryRoom * ANSWER_BYTES, BufferUsage.Storage + BufferUsage.CopySrc)
    private var readback = device.createBuffer(queryRoom * ANSWER_BYTES, BufferUsage.CopyDst + BufferUsage.MapRead)
    private var group: GpuBindGroup? = null

    private val held = IdentityHashMap<SweptSolid, Int>()
    private var ringsHeld = 0

    private fun storage(bytes: Long): GpuBuffer = device.createBuffer(maxOf(bytes, 16L), BufferUsage.Storage + BufferUsage.CopyDst)

    /** Puts every one of [wanted] on the device that is not there yet. */
    fun hold(wanted: List<SweptSolid>) {
        val fresh = wanted.filter { it !in held }
        if (fresh.isEmpty()) return
        val tables = fresh.map { it.rings() }
        val needRings = tables.sumOf { it.count }
        if (ringsHeld + needRings > ringRoom || held.size + fresh.size > solidRoom) {
            held.clear()
            ringsHeld = 0
            val all = wanted.map { it.rings() }
            val total = all.sumOf { it.count }
            if (total > ringRoom || wanted.size > solidRoom) growSolids(maxOf(ringRoom, total * 2), maxOf(solidRoom, wanted.size * 2))
            wanted.forEachIndexed { i, solid -> put(solid, all[i]) }
            return
        }
        fresh.forEachIndexed { i, solid -> put(solid, tables[i]) }
    }

    /** Where [solid], held, lies among the device's solids. */
    fun indexOf(solid: SweptSolid): Int = checkNotNull(held[solid])

    private fun put(solid: SweptSolid, t: RunRings) {
        val index = held.size
        val first = ringsHeld
        val flat = FloatArray(t.count * RING_STRIDE)
        for (i in 0 until t.count) {
            val o = i * RING_STRIDE
            for (c in 0 until 3) {
                flat[o + c] = t.middles[i * 3 + c]
                flat[o + 3 + c] = t.tangents[i * 3 + c]
                flat[o + 6 + c] = t.ups[i * 3 + c]
            }
            flat[o + 9] = t.us[i]
            flat[o + 10] = t.gaps[i]
            flat[o + 11] = t.reaches[i]
            flat[o + 12] = if (t.hasV[i]) 1f else 0f
        }
        val q = device.queue
        q.writeBuffer(rings, flat, first * RING_STRIDE * 4L)
        q.writeBuffer(radii, t.radii, first * RunRings.ANGLES * 4L)
        q.writeBuffer(vs, t.vs, first * RunRings.ANGLES * 4L)
        q.writeBuffer(solids, intArrayOf(first, t.count, t.startPerU.toRawBits(), t.endPerU.toRawBits()), index * SOLID_BYTES)
        held[solid] = index
        ringsHeld += t.count
    }

    /**
     * Reads [count] queries of [queryInts], four values each (see [FIELD_SHADER]), of the members [memberInts] holds, into [into],
     * three a query.
     */
    fun dispatch(memberInts: IntArray, queryInts: IntArray, count: Int, into: FloatArray) {
        if (count == 0) return
        val memberCount = memberInts.size / GpuFieldReads.MEMBER_INTS
        if (count > queryRoom) growQueries(maxOf(count, queryRoom * 2))
        if (memberCount > memberRoom) growMembers(maxOf(memberCount, memberRoom * 2))
        val bind = group ?: bindGroup().also { group = it }
        val q = device.queue
        q.writeBuffer(members, memberInts)
        q.writeBuffer(queries, queryInts, 0, 0, count * 4)
        q.writeBuffer(params, intArrayOf(count, 0, 0, 0))
        device.createCommandEncoder().use { encoder ->
            val pass = encoder.beginComputePass()
            pass.setPipeline(pipeline)
            pass.setBindGroup(0, bind)
            pass.dispatchWorkgroups((count + WORKGROUP - 1) / WORKGROUP)
            pass.end()
            encoder.copyBufferToBuffer(answers, readback, size = count * ANSWER_BYTES)
            q.submit(encoder.finish())
        }
        val bytes = runBlocking { device.readBuffer(readback, count * ANSWER_BYTES) }
        synchronized(errors) { if (errors.isNotEmpty()) error("GPU field reads failed: ${errors.joinToString()}") }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(into, 0, count * 3)
    }

    private fun bindGroup(): GpuBindGroup = pipeline.bindGroupLayout(0).use { layout ->
        device.createBindGroup(layout, listOf(rings, radii, vs, solids, queries, answers, params, members).mapIndexed { i, b -> BufferBinding(i, b) })
    }

    private fun dropGroup() {
        group?.close()
        group = null
    }

    private fun growSolids(ringsTo: Int, solidsTo: Int) {
        dropGroup()
        listOf(rings, radii, vs, solids).forEach { it.close() }
        ringRoom = ringsTo
        solidRoom = solidsTo
        rings = storage(ringRoom * RING_STRIDE * 4L)
        radii = storage(ringRoom * RunRings.ANGLES * 4L)
        vs = storage(ringRoom * RunRings.ANGLES * 4L)
        solids = storage(solidRoom * SOLID_BYTES)
    }

    private fun growQueries(to: Int) {
        dropGroup()
        listOf(queries, answers, readback).forEach { it.close() }
        queryRoom = to
        queries = storage(queryRoom * QUERY_BYTES)
        answers = device.createBuffer(queryRoom * ANSWER_BYTES, BufferUsage.Storage + BufferUsage.CopySrc)
        readback = device.createBuffer(queryRoom * ANSWER_BYTES, BufferUsage.CopyDst + BufferUsage.MapRead)
    }

    private fun growMembers(to: Int) {
        dropGroup()
        members.close()
        memberRoom = to
        members = storage(memberRoom * MemberBytes)
    }

    override fun close() {
        dropGroup()
        listOf(readback, answers, members, queries, solids, vs, radii, rings, params).forEach { it.close() }
        pipeline.close()
        shader.close()
        gpu.close()
    }

    private companion object {
        val MemberBytes = GpuFieldReads.MEMBER_INTS * 4L
    }
}

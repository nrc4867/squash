package dev.chieppa.squash.render

import dev.chieppa.stretch.gpu.GpuContext
import dev.chieppa.stretch.gpu.webgpu.BufferBinding
import dev.chieppa.stretch.gpu.webgpu.BufferUsage
import dev.chieppa.stretch.gpu.webgpu.ColorTarget
import dev.chieppa.stretch.gpu.webgpu.CompareFunction
import dev.chieppa.stretch.gpu.webgpu.CullMode
import dev.chieppa.stretch.gpu.webgpu.DepthState
import dev.chieppa.stretch.gpu.webgpu.GpuBindGroup
import dev.chieppa.stretch.gpu.webgpu.GpuBuffer
import dev.chieppa.stretch.gpu.webgpu.GpuRenderPass
import dev.chieppa.stretch.gpu.webgpu.IndexFormat
import dev.chieppa.stretch.gpu.webgpu.RenderPipelineSpec
import dev.chieppa.stretch.gpu.webgpu.VertexAttribute
import dev.chieppa.stretch.gpu.webgpu.VertexBufferLayout
import dev.chieppa.stretch.gpu.webgpu.VertexFormat
import java.util.IdentityHashMap

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4
private const val VEC3_BYTES = 3L * Float.SIZE_BYTES

private val RIBBON_LAYOUT = VertexBufferLayout(
    StrokeRibbon.FLOATS_PER_VERTEX.toLong() * Float.SIZE_BYTES,
    listOf(
        VertexAttribute(VertexFormat.Float32x3, 0, 0),
        VertexAttribute(VertexFormat.Float32x3, VEC3_BYTES, 1),
        VertexAttribute(VertexFormat.Float32x3, 2 * VEC3_BYTES, 2),
        VertexAttribute(VertexFormat.Float32x3, 3 * VEC3_BYTES, 3),
        VertexAttribute(VertexFormat.Float32, 4 * VEC3_BYTES, 4),
        VertexAttribute(VertexFormat.Float32, 4 * VEC3_BYTES + Float.SIZE_BYTES, 5),
    ),
)

/** Draws stroke ribbons into the frame's pass, over what is already there, and keeps the buffers of those a frame shows. */
internal interface StrokeRibbons : AutoCloseable {
    fun draw(pass: GpuRenderPass, slot: Int, draw: RibbonDraw, view: SceneView)

    /** Lets go of the buffers of every ribbon not in [wanted]. */
    fun keepOnly(wanted: Collection<StrokeRibbon>)
}

/** [StrokeRibbons] that set their width in the vertex shader, so a ribbon costs nothing to orbit. Ribbons write no depth, and each draws over the last. */
internal class StrokeRibbonPass(context: GpuContext) : StrokeRibbons {
    private val device = context.device
    private val shader = device.createShaderModule(RIBBON_WGSL, "stroke ribbon")
    private val pipeline = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_ribbon",
            buffers = listOf(RIBBON_LAYOUT),
            fragmentEntry = "fs_ribbon",
            targets = listOf(ColorTarget(context.format)),
            cullMode = CullMode.None,
            depth = DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual),
            sampleCount = context.sampleCount,
            label = "stroke ribbon",
        ),
    )
    private val uploaded = IdentityHashMap<StrokeRibbon, Pair<GpuBuffer, GpuBuffer>>()
    private val slots = ArrayList<Pair<GpuBuffer, GpuBindGroup>>()
    private val data = FloatArray(RIBBON_FLOATS)

    override fun draw(pass: GpuRenderPass, slot: Int, draw: RibbonDraw, view: SceneView) {
        val (uniforms, group) = slotAt(slot)
        write(uniforms, draw, view)
        val (vertices, indices) = uploaded.getOrPut(draw.ribbon) { upload(draw.ribbon) }
        pass.setPipeline(pipeline)
        pass.setBindGroup(0, group)
        pass.setVertexBuffer(0, vertices)
        pass.setIndexBuffer(indices, IndexFormat.Uint32)
        pass.drawIndexed(draw.ribbon.indices.size)
    }

    private fun upload(ribbon: StrokeRibbon): Pair<GpuBuffer, GpuBuffer> {
        val vertices = device.createBuffer(ribbon.vertices.size.toLong() * Float.SIZE_BYTES, BufferUsage.Vertex + BufferUsage.CopyDst, "ribbon vertices")
        val indices = device.createBuffer(ribbon.indices.size.toLong() * Int.SIZE_BYTES, BufferUsage.Index + BufferUsage.CopyDst, "ribbon indices")
        device.queue.writeBuffer(vertices, ribbon.vertices)
        device.queue.writeBuffer(indices, ribbon.indices)
        return vertices to indices
    }

    private fun slotAt(index: Int): Pair<GpuBuffer, GpuBindGroup> {
        while (slots.size <= index) {
            val buffer = device.createBuffer(RIBBON_FLOATS.toLong() * Float.SIZE_BYTES, BufferUsage.Uniform + BufferUsage.CopyDst, "stroke ribbon")
            val group = pipeline.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) }
            slots += buffer to group
        }
        return slots[index]
    }

    private fun write(buffer: GpuBuffer, draw: RibbonDraw, view: SceneView) {
        view.viewProjection.copyInto(data, 0)
        draw.model.copyInto(data, MATRIX_FLOATS)
        val rule = draw.rule
        val values = floatArrayOf(
            draw.color.red, draw.color.green, draw.color.blue, draw.color.alpha,
            rule.interiorPixels, rule.silhouettePixels, rule.silhouetteBand, 0f,
            view.viewport.width.toFloat(), view.viewport.height.toFloat(), draw.layer.scale, draw.layer.offset,
        )
        values.copyInto(data, 2 * MATRIX_FLOATS)
        device.queue.writeBuffer(buffer, data)
    }

    override fun keepOnly(wanted: Collection<StrokeRibbon>) {
        val keep = IdentityHashMap<StrokeRibbon, Unit>()
        wanted.forEach { keep[it] = Unit }
        uploaded.keys.filter { it !in keep }.forEach { ribbon ->
            uploaded.remove(ribbon)?.let { (vertices, indices) ->
                vertices.close()
                indices.close()
            }
        }
    }

    override fun close() {
        keepOnly(emptyList())
        slots.forEach { (buffer, group) ->
            group.close()
            buffer.close()
        }
        pipeline.close()
        shader.close()
    }
}

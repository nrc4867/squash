package dev.chieppa.squash.render

import dev.chieppa.stretch.gpu.GpuContext
import dev.chieppa.stretch.gpu.webgpu.BlendComponent
import dev.chieppa.stretch.gpu.webgpu.BlendFactor
import dev.chieppa.stretch.gpu.webgpu.BlendState
import dev.chieppa.stretch.gpu.webgpu.BufferBinding
import dev.chieppa.stretch.gpu.webgpu.BufferUsage
import dev.chieppa.stretch.gpu.webgpu.ColorTarget
import dev.chieppa.stretch.gpu.webgpu.CompareFunction
import dev.chieppa.stretch.gpu.webgpu.CullMode
import dev.chieppa.stretch.gpu.webgpu.DepthState
import dev.chieppa.stretch.gpu.webgpu.GpuBindGroup
import dev.chieppa.stretch.gpu.webgpu.GpuBuffer
import dev.chieppa.stretch.gpu.webgpu.GpuRenderPass
import dev.chieppa.stretch.gpu.webgpu.GpuRenderPipeline
import dev.chieppa.stretch.gpu.webgpu.IndexFormat
import dev.chieppa.stretch.gpu.webgpu.RenderPipelineSpec

private const val MATRIX_FLOATS = 16

/** Keeps what is there: a pipeline blending with this writes only depth. */
private val DEPTH_ONLY = BlendState(
    color = BlendComponent(BlendFactor.Zero, BlendFactor.One),
    alpha = BlendComponent(BlendFactor.Zero, BlendFactor.One),
)

/** The vertex buffer of a see-through cloth's attributes. */
internal fun clothBuffer(context: GpuContext, attributes: FloatArray): GpuBuffer {
    val buffer = context.device.createBuffer(attributes.size.toLong() * Float.SIZE_BYTES, BufferUsage.Vertex + BufferUsage.CopyDst, "cloth attributes")
    context.device.queue.writeBuffer(buffer, attributes)
    return buffer
}

/**
 * Draws see-through cloth ([ClothDraw]) over what the frame's pass holds: it lays its depth first, both faces, and then blends only what
 * matches it, so its own far side never shows through it. Each draw of a frame takes a uniform slot of its own, numbered from 0.
 */
internal class ClothPass(private val context: GpuContext) : AutoCloseable {
    private val device = context.device
    private val shader = device.createShaderModule(CLOTH_WGSL, "see-through cloth")
    private val depth = pipeline(DEPTH_ONLY, DepthState(context.depthFormat))
    private val blend = pipeline(BlendState.AlphaBlending, DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual))
    private val slots = mutableListOf<Pair<GpuBuffer, Map<GpuRenderPipeline, GpuBindGroup>>>()
    private val data = FloatArray(CLOTH_UNIFORM_FLOATS)

    private fun pipeline(blending: BlendState, depthState: DepthState): GpuRenderPipeline = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_cloth",
            buffers = listOf(MESH_VERTEX_LAYOUT, CLOTH_VERTEX_LAYOUT),
            fragmentEntry = "fs_cloth",
            targets = listOf(ColorTarget(context.format, blending)),
            cullMode = CullMode.None,
            depth = depthState,
            sampleCount = context.sampleCount,
            label = "see-through cloth",
        ),
    )

    fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, attributes: GpuBuffer, draw: MeshDraw, view: SceneView) {
        val (uniforms, groups) = slotAt(slot)
        write(uniforms, draw, view)
        pass.setVertexBuffer(0, mesh.vertices)
        pass.setVertexBuffer(1, attributes)
        pass.setIndexBuffer(mesh.indices, IndexFormat.Uint32)
        for (pipeline in listOf(depth, blend)) {
            pass.setPipeline(pipeline)
            pass.setBindGroup(0, groups.getValue(pipeline))
            pass.drawIndexed(mesh.mesh.indices.size)
        }
    }

    private fun slotAt(index: Int): Pair<GpuBuffer, Map<GpuRenderPipeline, GpuBindGroup>> {
        while (slots.size <= index) {
            val buffer = device.createBuffer(CLOTH_UNIFORM_FLOATS.toLong() * Float.SIZE_BYTES, BufferUsage.Uniform + BufferUsage.CopyDst, "cloth draw")
            slots += buffer to listOf(depth, blend).associateWith { pipeline -> pipeline.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) } }
        }
        return slots[index]
    }

    private fun write(buffer: GpuBuffer, draw: MeshDraw, view: SceneView) {
        val material = draw.material
        view.viewProjection.copyInto(data, 0)
        draw.model.copyInto(data, MATRIX_FLOATS)
        var at = 2 * MATRIX_FLOATS
        for (value in listOf(material.toLight.x, material.toLight.y, material.toLight.z, if (material.shading == Shading.TWO_TONE) 1f else 0f)) data[at++] = value
        for (value in listOf(draw.cloth?.lift ?: 0f, draw.opacity, draw.layer.scale, draw.layer.offset)) data[at++] = value
        device.queue.writeBuffer(buffer, data)
    }

    override fun close() {
        slots.forEach { (buffer, groups) ->
            groups.values.forEach(GpuBindGroup::close)
            buffer.close()
        }
        depth.close()
        blend.close()
        shader.close()
    }
}

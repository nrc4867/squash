package dev.chieppa.squash.render

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.RenderMesh
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
import dev.chieppa.stretch.gpu.webgpu.VertexAttribute
import dev.chieppa.stretch.gpu.webgpu.VertexBufferLayout
import dev.chieppa.stretch.gpu.webgpu.VertexFormat

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4

/** Keeps what is there: a pipeline blending with this writes only depth, however the pass's color target is set up. */
private val DEPTH_ONLY = BlendState(
    color = BlendComponent(BlendFactor.Zero, BlendFactor.One),
    alpha = BlendComponent(BlendFactor.Zero, BlendFactor.One),
)

/** A mesh's vertex and index buffers. */
internal class GpuMesh(context: GpuContext, val mesh: RenderMesh) : AutoCloseable {
    val vertices: GpuBuffer = context.device.createBuffer(
        mesh.vertices.size.toLong() * Float.SIZE_BYTES, BufferUsage.Vertex + BufferUsage.CopyDst, "mesh vertices",
    )
    val indices: GpuBuffer = context.device.createBuffer(
        mesh.indices.size.toLong() * Int.SIZE_BYTES, BufferUsage.Index + BufferUsage.CopyDst, "mesh indices",
    )

    init {
        context.device.queue.writeBuffer(vertices, mesh.vertices)
        context.device.queue.writeBuffer(indices, mesh.indices)
    }

    override fun close() {
        vertices.close()
        indices.close()
    }
}

/**
 * Draws meshes into the frame's pass with a material each. A fill shows both its faces, so an open ring reads as solid
 * where it stands proud of what it sits in. A draw below full opacity lays its depth first and then blends only what
 * matches it, so its own far side never shows through. A pass runs after all its uniform writes, so each draw of a
 * frame takes a uniform slot of its own, numbered from 0 by [draw]'s `slot`; the slots grow to the most draws in a frame.
 */
internal class MeshPass(private val context: GpuContext) : AutoCloseable {
    private val device = context.device
    private val shader = device.createShaderModule(MESH_WGSL, "mesh")
    private val layout = VertexBufferLayout(
        (RenderMesh.FLOATS_PER_VERTEX * Float.SIZE_BYTES).toLong(),
        listOf(
            VertexAttribute(VertexFormat.Float32x3, 0, 0),
            VertexAttribute(VertexFormat.Float32x3, (RenderMesh.NORMAL_OFFSET_FLOATS * Float.SIZE_BYTES).toLong(), 1),
        ),
    )
    private val fill = pipeline("vs_fill", "fs_fill", CullMode.None)
    private val outline = pipeline("vs_outline", "fs_outline", CullMode.Front)
    private val fillDepth = pipeline("vs_fill", "fs_fill", CullMode.None, DEPTH_ONLY)
    private val outlineDepth = pipeline("vs_outline", "fs_outline", CullMode.Front, DEPTH_ONLY)
    private val fillBlend = pipeline("vs_fill", "fs_fill", CullMode.None, BlendState.AlphaBlending, DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual))
    private val outlineBlend = pipeline("vs_outline", "fs_outline", CullMode.Front, BlendState.AlphaBlending, DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual))
    private val pipelines = listOf(fill, outline, fillDepth, outlineDepth, fillBlend, outlineBlend)
    private val slots = mutableListOf<Slot>()
    private val data = FloatArray(DRAW_FLOATS)

    /** A pipeline's bind group layout is its own, so a draw's uniforms have a bind group for each. */
    private class Slot(val uniforms: GpuBuffer, val groups: Map<GpuRenderPipeline, GpuBindGroup>)

    private fun pipeline(
        vertex: String,
        fragment: String,
        cull: CullMode,
        blend: BlendState? = null,
        depth: DepthState = DepthState(context.depthFormat),
    ): GpuRenderPipeline = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = vertex,
            buffers = listOf(layout),
            fragmentEntry = fragment,
            targets = listOf(ColorTarget(context.format, blend)),
            cullMode = cull,
            depth = depth,
            sampleCount = context.sampleCount,
            label = vertex,
        ),
    )

    fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, draw: MeshDraw, view: SceneView) {
        val uniforms = slotAt(slot)
        writeUniforms(uniforms.uniforms, draw, view)
        pass.setVertexBuffer(0, mesh.vertices)
        pass.setIndexBuffer(mesh.indices, IndexFormat.Uint32)
        if (draw.opacity < 1f) {
            drawWith(pass, outlineDepth, fillDepth, uniforms, mesh, draw)
            drawWith(pass, outlineBlend, fillBlend, uniforms, mesh, draw)
        } else {
            drawWith(pass, outline, fill, uniforms, mesh, draw)
        }
    }

    private fun drawWith(pass: GpuRenderPass, withOutline: GpuRenderPipeline, withFill: GpuRenderPipeline, uniforms: Slot, mesh: GpuMesh, draw: MeshDraw) {
        if (draw.outlinePixels > 0f) drawOne(pass, withOutline, uniforms, mesh)
        drawOne(pass, withFill, uniforms, mesh)
    }

    private fun drawOne(pass: GpuRenderPass, pipeline: GpuRenderPipeline, uniforms: Slot, mesh: GpuMesh) {
        pass.setPipeline(pipeline)
        pass.setBindGroup(0, uniforms.groups.getValue(pipeline))
        pass.drawIndexed(mesh.mesh.indices.size)
    }

    private fun slotAt(index: Int): Slot {
        while (slots.size <= index) {
            val buffer = device.createBuffer(DRAW_FLOATS.toLong() * Float.SIZE_BYTES, BufferUsage.Uniform + BufferUsage.CopyDst, "mesh draw")
            val groups = pipelines.associateWith { pipeline ->
                pipeline.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) }
            }
            slots += Slot(buffer, groups)
        }
        return slots[index]
    }

    private fun writeUniforms(buffer: GpuBuffer, draw: MeshDraw, view: SceneView) {
        val material = draw.material
        view.viewProjection.copyInto(data, 0)
        draw.model.copyInto(data, MATRIX_FLOATS)
        var at = 2 * MATRIX_FLOATS
        at = data.put(at, material.base)
        at = data.put(at, material.shade)
        at = data.put(
            at, material.toLight.x, material.toLight.y, material.toLight.z, if (material.shading == Shading.TWO_TONE) 1f else 0f,
        )
        at = data.put(at, material.ink)
        at = data.put(at, draw.outlinePixels, view.viewport.width.toFloat(), view.viewport.height.toFloat(), 0f)
        data.put(at, draw.layer.scale, draw.layer.offset, draw.opacity, 0f)
        device.queue.writeBuffer(buffer, data)
    }

    private fun FloatArray.put(at: Int, color: Color) = put(at, color.red, color.green, color.blue, color.alpha)

    private fun FloatArray.put(at: Int, a: Float, b: Float, c: Float, d: Float): Int {
        this[at] = a
        this[at + 1] = b
        this[at + 2] = c
        this[at + 3] = d
        return at + VECTOR_FLOATS
    }

    override fun close() {
        slots.forEach {
            it.groups.values.forEach(GpuBindGroup::close)
            it.uniforms.close()
        }
        pipelines.forEach { it.close() }
        shader.close()
    }
}

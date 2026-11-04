package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.stretch.gpu.GpuContext
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
import dev.chieppa.stretch.gpu.webgpu.IndexFormat
import dev.chieppa.stretch.gpu.webgpu.RenderPipelineSpec
import dev.chieppa.stretch.gpu.webgpu.VertexAttribute
import dev.chieppa.stretch.gpu.webgpu.VertexBufferLayout
import dev.chieppa.stretch.gpu.webgpu.VertexFormat

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4

/**
 * One color layer over a mesh: a cut's [mesh] in the [tones] its slot shows as, solid or with each vertex's opacity in [alphas]. A
 * [MeshDraw] draws its layers in order, each over the last.
 */
@Immutable
data class ColorLayerDraw(val mesh: RenderMesh, val tones: Tones, val alphas: FloatArray? = null)

/** The vertex buffer of a cut's per-vertex opacities. */
internal fun alphaBuffer(context: GpuContext, alphas: FloatArray): GpuBuffer {
    val buffer = context.device.createBuffer(alphas.size.toLong() * Float.SIZE_BYTES, BufferUsage.Vertex + BufferUsage.CopyDst, "layer alphas")
    context.device.queue.writeBuffer(buffer, alphas)
    return buffer
}

/** A cut's opacities as the shader's fourth attribute, one float per vertex in a buffer of their own. */
private val ALPHA_VERTEX_LAYOUT = VertexBufferLayout(Float.SIZE_BYTES.toLong(), listOf(VertexAttribute(VertexFormat.Float32, 0, 3)))

/** Draws a color layer over a mesh already drawn in the frame's pass. */
internal interface ColorLayers : AutoCloseable {
    fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, alphas: GpuBuffer?, layer: ColorLayerDraw, over: MeshDraw, view: SceneView)
}

/**
 * [ColorLayers] by a stable depth test: layers show only faces toward the camera and write no depth, so draw order alone sets which of
 * them shows. A layer with opacities blends over what is there, in the surface's own values, so its fade is linear in sRGB.
 */
internal class ColorLayerPass(context: GpuContext) : ColorLayers {
    private val device = context.device
    private val shader = device.createShaderModule(COLOR_LAYER_WGSL, "color layer")
    private val pipeline = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_layer",
            buffers = listOf(MESH_VERTEX_LAYOUT),
            fragmentEntry = "fs_layer",
            targets = listOf(ColorTarget(context.format)),
            cullMode = CullMode.Back,
            depth = DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual),
            sampleCount = context.sampleCount,
            label = "color layer",
        ),
    )
    private val fading = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_fading",
            buffers = listOf(MESH_VERTEX_LAYOUT, ALPHA_VERTEX_LAYOUT),
            fragmentEntry = "fs_fading",
            targets = listOf(ColorTarget(context.format, BlendState.AlphaBlending)),
            cullMode = CullMode.Back,
            depth = DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual),
            sampleCount = context.sampleCount,
            label = "fading color layer",
        ),
    )

    /** A pipeline's bind group layout is its own, so a slot's uniforms have a bind group for each, the fading one made when first asked for. */
    private inner class Slot(val buffer: GpuBuffer, val group: GpuBindGroup) {
        private val fadingLazily = lazy { fading.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) } }
        val fadingGroup: GpuBindGroup get() = fadingLazily.value

        fun close() {
            if (fadingLazily.isInitialized()) fadingLazily.value.close()
            group.close()
            buffer.close()
        }
    }

    private val slots = ArrayList<Slot>()
    private val data = FloatArray(COLOR_LAYER_FLOATS)

    override fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, alphas: GpuBuffer?, layer: ColorLayerDraw, over: MeshDraw, view: SceneView) {
        val held = slotAt(slot)
        write(held.buffer, layer, over, view)
        if (alphas == null) {
            pass.setPipeline(pipeline)
            pass.setBindGroup(0, held.group)
        } else {
            pass.setPipeline(fading)
            pass.setBindGroup(0, held.fadingGroup)
            pass.setVertexBuffer(1, alphas)
        }
        pass.setVertexBuffer(0, mesh.vertices)
        pass.setIndexBuffer(mesh.indices, IndexFormat.Uint32)
        pass.drawIndexed(mesh.mesh.indices.size)
    }

    private fun slotAt(index: Int): Slot {
        while (slots.size <= index) {
            val buffer = device.createBuffer(COLOR_LAYER_FLOATS.toLong() * Float.SIZE_BYTES, BufferUsage.Uniform + BufferUsage.CopyDst, "color layer")
            val group = pipeline.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) }
            slots += Slot(buffer, group)
        }
        return slots[index]
    }

    private fun write(buffer: GpuBuffer, layer: ColorLayerDraw, over: MeshDraw, view: SceneView) {
        view.viewProjection.copyInto(data, 0)
        over.model.copyInto(data, MATRIX_FLOATS)
        var at = 2 * MATRIX_FLOATS
        at = put(at, layer.tones.lit)
        at = put(at, layer.tones.shadow)
        val light = over.material.toLight
        at = put(at, light.x, light.y, light.z, if (over.material.shading == Shading.TWO_TONE) 1f else 0f)
        put(at, view.viewport.width.toFloat(), view.viewport.height.toFloat(), over.layer.scale, over.layer.offset)
        device.queue.writeBuffer(buffer, data)
    }

    private fun put(at: Int, color: Color) = put(at, color.red, color.green, color.blue, color.alpha)

    private fun put(at: Int, a: Float, b: Float, c: Float, d: Float): Int {
        data[at] = a
        data[at + 1] = b
        data[at + 2] = c
        data[at + 3] = d
        return at + VECTOR_FLOATS
    }

    override fun close() {
        slots.forEach { it.close() }
        fading.close()
        pipeline.close()
        shader.close()
    }
}

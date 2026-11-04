package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.RenderMesh
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

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4

/** One color layer over a mesh: a cut's [mesh] in the [tones] its slot shows as. A [MeshDraw] draws its layers in order, each over the last. */
@Immutable
data class ColorLayerDraw(val mesh: RenderMesh, val tones: Tones)

/** Draws a color layer over a mesh already drawn in the frame's pass. */
internal interface ColorLayers : AutoCloseable {
    fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, layer: ColorLayerDraw, over: MeshDraw, view: SceneView)
}

/** [ColorLayers] by a stable depth test: layers show only faces toward the camera and write no depth, so draw order alone sets which of them shows. */
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
    private val slots = ArrayList<Pair<GpuBuffer, GpuBindGroup>>()
    private val data = FloatArray(COLOR_LAYER_FLOATS)

    override fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, layer: ColorLayerDraw, over: MeshDraw, view: SceneView) {
        val (buffer, group) = slotAt(slot)
        write(buffer, layer, over, view)
        pass.setPipeline(pipeline)
        pass.setBindGroup(0, group)
        pass.setVertexBuffer(0, mesh.vertices)
        pass.setIndexBuffer(mesh.indices, IndexFormat.Uint32)
        pass.drawIndexed(mesh.mesh.indices.size)
    }

    private fun slotAt(index: Int): Pair<GpuBuffer, GpuBindGroup> {
        while (slots.size <= index) {
            val buffer = device.createBuffer(COLOR_LAYER_FLOATS.toLong() * Float.SIZE_BYTES, BufferUsage.Uniform + BufferUsage.CopyDst, "color layer")
            val group = pipeline.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) }
            slots += buffer to group
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
        slots.forEach { (buffer, group) ->
            group.close()
            buffer.close()
        }
        pipeline.close()
        shader.close()
    }
}

package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.ChartShape
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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4

/** Vectors in the `Layer` uniform before its sides' tones. */
private const val SIDES_AFTER_VECTORS = 7

/**
 * One color layer over a mesh: a cut's [mesh] in the [tones] its slot shows as, solid or with each vertex's opacity in [alphas], or kept
 * to a [shape] read at each fragment, its edge smoothed over a pixel. A [MeshDraw] draws its layers in order, each over the last.
 */
@Immutable
data class ColorLayerDraw(val mesh: RenderMesh, val tones: Tones, val alphas: FloatArray? = null, val shape: ChartShape? = null, val sides: LayerSides? = null) {
    /** The per-vertex buffers this layer reads beside its mesh: its sides' shares, else its shape's chart or its opacities. */
    val extras: List<FloatArray>
        get() = sides?.let { listOfNotNull(it.positiveShares ?: it.negativeShares, it.negativeShares ?: it.positiveShares) }
            ?: listOfNotNull(shape?.chart ?: alphas)
}

/**
 * A layer's far sides, drawn in the layer's own pass: [positive] over its tones by each vertex's [positiveShares], then [negative] by
 * [negativeShares], as two fading layers of those tones would show, in one pass over the mesh.
 */
@Immutable
data class LayerSides(val positive: Tones, val positiveShares: FloatArray?, val negative: Tones, val negativeShares: FloatArray?)

/** The vertex buffer of a cut's per-vertex opacities. */
internal fun alphaBuffer(context: GpuContext, alphas: FloatArray): GpuBuffer {
    val buffer = context.device.createBuffer(alphas.size.toLong() * Float.SIZE_BYTES, BufferUsage.Vertex + BufferUsage.CopyDst, "layer alphas")
    context.device.queue.writeBuffer(buffer, alphas)
    return buffer
}

/** A cut's opacities as the shader's fourth attribute, one float per vertex in a buffer of their own. */
private val ALPHA_VERTEX_LAYOUT = VertexBufferLayout(Float.SIZE_BYTES.toLong(), listOf(VertexAttribute(VertexFormat.Float32, 0, 3)))

/** A shaped layer's chart places as the shader's fourth attribute, two floats per vertex in a buffer of their own. */
private val POSITIVE_VERTEX_LAYOUT = VertexBufferLayout(Float.SIZE_BYTES.toLong(), listOf(VertexAttribute(VertexFormat.Float32, 0, 3)))
private val NEGATIVE_VERTEX_LAYOUT = VertexBufferLayout(Float.SIZE_BYTES.toLong(), listOf(VertexAttribute(VertexFormat.Float32, 0, 4)))

private val CHART_VERTEX_LAYOUT = VertexBufferLayout(2L * Float.SIZE_BYTES, listOf(VertexAttribute(VertexFormat.Float32x2, 0, 3)))

/** Draws a color layer over a mesh already drawn in the frame's pass. */
internal interface ColorLayers : AutoCloseable {
    /** Draws [layer] over [over], with [extras] the buffers of its [ColorLayerDraw.extras] in order. */
    fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, extras: List<GpuBuffer>, layer: ColorLayerDraw, over: MeshDraw, view: SceneView)
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

    private val shaped = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_shaped",
            buffers = listOf(MESH_VERTEX_LAYOUT, CHART_VERTEX_LAYOUT),
            fragmentEntry = "fs_shaped",
            targets = listOf(ColorTarget(context.format, BlendState.AlphaBlending)),
            cullMode = CullMode.Back,
            depth = DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual),
            sampleCount = context.sampleCount,
            label = "shaped color layer",
        ),
    )

    private val sided = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_sided",
            buffers = listOf(MESH_VERTEX_LAYOUT, POSITIVE_VERTEX_LAYOUT, NEGATIVE_VERTEX_LAYOUT),
            fragmentEntry = "fs_sided",
            targets = listOf(ColorTarget(context.format)),
            cullMode = CullMode.Back,
            depth = DepthState(context.depthFormat, write = false, compare = CompareFunction.LessEqual),
            sampleCount = context.sampleCount,
            label = "sided color layer",
        ),
    )

    /** A pipeline's bind group layout is its own, so a slot's uniforms have a bind group for each, the fading one made when first asked for. */
    private inner class Slot(val buffer: GpuBuffer, val group: GpuBindGroup) {
        private val fadingLazily = lazy { fading.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) } }
        val fadingGroup: GpuBindGroup get() = fadingLazily.value
        private val shapedLazily = lazy { shaped.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) } }
        val shapedGroup: GpuBindGroup get() = shapedLazily.value
        private val sidedLazily = lazy { sided.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, buffer))) } }
        val sidedGroup: GpuBindGroup get() = sidedLazily.value

        fun close() {
            if (fadingLazily.isInitialized()) fadingLazily.value.close()
            if (shapedLazily.isInitialized()) shapedLazily.value.close()
            if (sidedLazily.isInitialized()) sidedLazily.value.close()
            group.close()
            buffer.close()
        }
    }

    private val slots = ArrayList<Slot>()
    private val data = FloatArray(COLOR_LAYER_FLOATS)

    override fun draw(pass: GpuRenderPass, slot: Int, mesh: GpuMesh, extras: List<GpuBuffer>, layer: ColorLayerDraw, over: MeshDraw, view: SceneView) {
        val held = slotAt(slot)
        write(held.buffer, layer, over, view)
        val alphas = extras.firstOrNull()
        if (layer.sides != null && extras.size == 2) {
            pass.setPipeline(sided)
            pass.setBindGroup(0, held.sidedGroup)
            pass.setVertexBuffer(1, extras[0])
            pass.setVertexBuffer(2, extras[1])
        } else if (layer.shape != null && alphas != null) {
            pass.setPipeline(shaped)
            pass.setBindGroup(0, held.shapedGroup)
            pass.setVertexBuffer(1, alphas)
        } else if (alphas == null) {
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
        at = put(at, view.viewport.width.toFloat(), view.viewport.height.toFloat(), over.layer.scale, over.layer.offset)
        val shape = layer.shape
        if (shape != null) {
            val o = shape.opening
            at = put(at, o.width, o.height, o.lowerLid, o.frontNarrowing)
            val oval = shape.oval
            at = put(at, oval?.u ?: 0f, oval?.v ?: 0f, oval?.halfU ?: 1f, oval?.halfV ?: 1f)
            val angle = shape.turn * PI.toFloat() / 180f
            put(at, cos(angle), sin(angle), if (oval != null) 1f else 0f, shape.ovalTurn * PI.toFloat() / 180f)
        }
        val sides = layer.sides
        if (sides != null) {
            at = 2 * MATRIX_FLOATS + SIDES_AFTER_VECTORS * VECTOR_FLOATS
            at = put(at, sides.positive.lit.red, sides.positive.lit.green, sides.positive.lit.blue, if (sides.positiveShares != null) 1f else 0f)
            at = put(at, sides.positive.shadow)
            at = put(at, sides.negative.lit.red, sides.negative.lit.green, sides.negative.lit.blue, if (sides.negativeShares != null) 1f else 0f)
            put(at, sides.negative.shadow)
        }
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
        shaped.close()
        sided.close()
        fading.close()
        pipeline.close()
        shader.close()
    }
}

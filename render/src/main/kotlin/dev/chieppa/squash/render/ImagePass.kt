package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import dev.chieppa.squash.geom.Vec3
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
import dev.chieppa.stretch.gpu.webgpu.RenderPipelineSpec
import java.util.IdentityHashMap

private const val MATRIX_FLOATS = 16
private const val VECTOR_FLOATS = 4
private const val CORNERS = 4
private const val QUAD_VERTICES = 6

/** Floats in [IMAGE_WGSL]'s `Picture` uniform: the camera, four corners, then size and opacity. */
private const val IMAGE_FLOATS = MATRIX_FLOATS + CORNERS * VECTOR_FLOATS + VECTOR_FLOATS

/** An image's pixels, a row at a time from the top, each packed red in its lowest byte to alpha in its highest. */
@Immutable
class ImagePixels(val width: Int, val height: Int, val rgba: IntArray) {
    init {
        require(width > 0 && height > 0 && rgba.size == width * height) { "$width by $height pixels, given ${rgba.size}" }
    }
}

/**
 * An image laid flat in the scene between four [corners], top-left, top-right, bottom-right then bottom-left, drawn in grey at [opacity].
 * It writes no depth and takes no depth test: a view draws those [SceneView.behind] before its meshes and those [SceneView.over] last.
 */
@Immutable
data class ImageDraw(val pixels: ImagePixels, val corners: List<Vec3>, val opacity: Float) {
    init {
        require(corners.size == CORNERS) { "An image has four corners" }
    }
}

/**
 * WGSL for [ImagePass]: two triangles between the corners, each pixel read from a storage buffer with the four nearest blended,
 * turned grey and faded, since a trace is something to draw over and never a color to match.
 */
private const val IMAGE_WGSL = """
struct Picture {
    view_projection: mat4x4<f32>,
    corners: array<vec4<f32>, 4>,
    size_and_opacity: vec4<f32>,
};

@group(0) @binding(0) var<uniform> picture: Picture;
@group(0) @binding(1) var<storage, read> pixels: array<u32>;

struct Out {
    @builtin(position) clip: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_image(@builtin(vertex_index) index: u32) -> Out {
    var corner = array<u32, 6>(0u, 1u, 2u, 0u, 2u, 3u);
    var uvs = array<vec2<f32>, 4>(vec2<f32>(0.0, 0.0), vec2<f32>(1.0, 0.0), vec2<f32>(1.0, 1.0), vec2<f32>(0.0, 1.0));
    let at = corner[index];
    var out: Out;
    out.clip = picture.view_projection * vec4<f32>(picture.corners[at].xyz, 1.0);
    out.uv = uvs[at];
    return out;
}

fn texel(x: i32, y: i32) -> vec4<f32> {
    let size = vec2<i32>(picture.size_and_opacity.xy);
    let cx = clamp(x, 0, size.x - 1);
    let cy = clamp(y, 0, size.y - 1);
    return unpack4x8unorm(pixels[u32(cy * size.x + cx)]);
}

@fragment
fn fs_image(in: Out) -> @location(0) vec4<f32> {
    let at = in.uv * picture.size_and_opacity.xy - vec2<f32>(0.5, 0.5);
    let base = floor(at);
    let f = at - base;
    let x = i32(base.x);
    let y = i32(base.y);
    let top = mix(texel(x, y), texel(x + 1, y), f.x);
    let bottom = mix(texel(x, y + 1), texel(x + 1, y + 1), f.x);
    let color = mix(top, bottom, f.y);
    let grey = dot(color.rgb, vec3<f32>(0.299, 0.587, 0.114));
    return vec4<f32>(grey, grey, grey, color.a * picture.size_and_opacity.z);
}
"""

/** Draws [ImageDraw]s into the frame's pass and keeps the pixel buffers of those a frame shows. */
internal class ImagePass(context: GpuContext) : AutoCloseable {
    private val device = context.device
    private val shader = device.createShaderModule(IMAGE_WGSL, "trace image")
    private val pipeline = device.createRenderPipeline(
        RenderPipelineSpec(
            shader = shader,
            vertexEntry = "vs_image",
            buffers = emptyList(),
            fragmentEntry = "fs_image",
            targets = listOf(ColorTarget(context.format, BlendState.AlphaBlending)),
            cullMode = CullMode.None,
            depth = DepthState(context.depthFormat, write = false, compare = CompareFunction.Always),
            sampleCount = context.sampleCount,
            label = "trace image",
        ),
    )
    private val uploaded = IdentityHashMap<ImagePixels, GpuBuffer>()
    private val slots = ArrayList<Triple<GpuBuffer, GpuBuffer, GpuBindGroup>>()
    private val data = FloatArray(IMAGE_FLOATS)

    fun draw(pass: GpuRenderPass, slot: Int, draw: ImageDraw, view: SceneView) {
        val group = groupFor(slot, uploaded.getOrPut(draw.pixels) { upload(draw.pixels) })
        view.viewProjection.copyInto(data, 0)
        draw.corners.forEachIndexed { i, corner ->
            val at = MATRIX_FLOATS + i * VECTOR_FLOATS
            data[at] = corner.x
            data[at + 1] = corner.y
            data[at + 2] = corner.z
            data[at + 3] = 1f
        }
        val tail = MATRIX_FLOATS + CORNERS * VECTOR_FLOATS
        data[tail] = draw.pixels.width.toFloat()
        data[tail + 1] = draw.pixels.height.toFloat()
        data[tail + 2] = draw.opacity
        data[tail + 3] = 0f
        device.queue.writeBuffer(slots[slot].first, data)
        pass.setPipeline(pipeline)
        pass.setBindGroup(0, group)
        pass.draw(QUAD_VERTICES)
    }

    /** The bind group for [slot], made again when the slot shows a different image's [pixels]. */
    private fun groupFor(slot: Int, pixels: GpuBuffer): GpuBindGroup {
        while (slots.size <= slot) {
            val uniforms = device.createBuffer(IMAGE_FLOATS.toLong() * Float.SIZE_BYTES, BufferUsage.Uniform + BufferUsage.CopyDst, "trace image")
            slots += Triple(uniforms, pixels, bind(uniforms, pixels))
        }
        val (uniforms, bound, group) = slots[slot]
        if (bound === pixels) return group
        group.close()
        val next = bind(uniforms, pixels)
        slots[slot] = Triple(uniforms, pixels, next)
        return next
    }

    private fun bind(uniforms: GpuBuffer, pixels: GpuBuffer): GpuBindGroup =
        pipeline.bindGroupLayout(0).use { device.createBindGroup(it, listOf(BufferBinding(0, uniforms), BufferBinding(1, pixels))) }

    private fun upload(pixels: ImagePixels): GpuBuffer {
        val buffer = device.createBuffer(pixels.rgba.size.toLong() * Int.SIZE_BYTES, BufferUsage.Storage + BufferUsage.CopyDst, "trace pixels")
        device.queue.writeBuffer(buffer, pixels.rgba)
        return buffer
    }

    /** Lets go of the pixels of every image not in [wanted]. */
    fun keepOnly(wanted: Collection<ImagePixels>) {
        val keep = IdentityHashMap<ImagePixels, Unit>()
        wanted.forEach { keep[it] = Unit }
        val gone = uploaded.keys.filter { it !in keep }
        if (gone.isEmpty()) return
        val dropped = gone.map { uploaded.remove(it) }
        for (i in slots.indices) {
            val (uniforms, bound, group) = slots[i]
            if (dropped.any { it === bound }) {
                group.close()
                uniforms.close()
            }
        }
        slots.removeAll { (_, bound, _) -> dropped.any { it === bound } }
        dropped.forEach { it?.close() }
    }

    override fun close() {
        slots.forEach { (uniforms, _, group) ->
            group.close()
            uniforms.close()
        }
        slots.clear()
        uploaded.values.forEach { it.close() }
        uploaded.clear()
        pipeline.close()
        shader.close()
    }
}

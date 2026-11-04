package dev.chieppa.squash.render

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.stretch.gpu.webgpu.VertexAttribute
import dev.chieppa.stretch.gpu.webgpu.VertexBufferLayout
import dev.chieppa.stretch.gpu.webgpu.VertexFormat

/** The vertices of a [RenderMesh], for any pass drawing one: position at location 0, normal at 1, proxy normal at 2. */
internal val MESH_VERTEX_LAYOUT = VertexBufferLayout(
    (RenderMesh.FLOATS_PER_VERTEX * Float.SIZE_BYTES).toLong(),
    listOf(
        VertexAttribute(VertexFormat.Float32x3, 0, 0),
        VertexAttribute(VertexFormat.Float32x3, (RenderMesh.NORMAL_OFFSET_FLOATS * Float.SIZE_BYTES).toLong(), 1),
        VertexAttribute(VertexFormat.Float32x3, (RenderMesh.PROXY_OFFSET_FLOATS * Float.SIZE_BYTES).toLong(), 2),
    ),
)

/** WGSL every fill shades with: the tone at a proxy normal, so a part's shadow falls where its slices' ellipses turn from the light. */
internal const val TONE_WGSL = """
fn tone(proxy: vec3<f32>, to_light_and_two_tone: vec4<f32>, lit: vec3<f32>, shadow: vec3<f32>) -> vec3<f32> {
    if (to_light_and_two_tone.w > 0.5 && dot(normalize(proxy), to_light_and_two_tone.xyz) <= 0.0) {
        return shadow;
    }
    return lit;
}
"""

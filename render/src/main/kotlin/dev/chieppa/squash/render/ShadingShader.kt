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

/**
 * WGSL every fill shades with: the tone at a proxy normal, so a part's shadow falls where its slices' ellipses turn from the light. A
 * shadow that would show only as a sliver along the silhouette is drawn lit, so two-tone shows a shadow as an area that follows the form,
 * never as a thin strip down an edge: across the surface from the point facing the camera through a shaded point to the silhouette, the
 * shade starts where that arc turns from the light, and where that leaves less than LEAST_SHADOW_SHARE of its width on screen the point
 * is lit. [away] is the way from the camera into the scene, which `away_from` reads from a view projection.
 */
internal const val TONE_WGSL = """
const LEAST_SHADOW_SHARE: f32 = 0.3;

fn away_from(view_projection: mat4x4<f32>) -> vec3<f32> {
    return normalize(vec3<f32>(view_projection[0][2], view_projection[1][2], view_projection[2][2]));
}

fn tone(proxy: vec3<f32>, to_light_and_two_tone: vec4<f32>, lit: vec3<f32>, shadow: vec3<f32>, away: vec3<f32>) -> vec3<f32> {
    let n = normalize(proxy);
    let light = to_light_and_two_tone.xyz;
    if (to_light_and_two_tone.w <= 0.5 || dot(n, light) > 0.0) {
        return lit;
    }
    let toward = -away;
    let lv = dot(light, toward);
    let across = n - toward * dot(n, toward);
    let spread = length(across);
    if (lv > 0.0 && spread > 0.0001) {
        let lu = dot(light, across / spread);
        let sliver = 1.0 - lv / max(sqrt(lv * lv + lu * lu), 0.000001);
        if (sliver < LEAST_SHADOW_SHARE) {
            return lit;
        }
    }
    return shadow;
}
"""

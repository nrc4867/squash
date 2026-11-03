package dev.chieppa.squash.render

/** Floats in [MESH_WGSL]'s `Draw` uniform. */
internal const val DRAW_FLOATS = 52

/** WGSL for [MeshPass]: flat and two-tone fills, and an outline as the mesh's back faces pushed out by a pixel width. */
internal const val MESH_WGSL = """
struct Draw {
    view_projection: mat4x4<f32>,
    model: mat4x4<f32>,
    base: vec4<f32>,
    shade: vec4<f32>,
    to_light_and_two_tone: vec4<f32>,
    ink: vec4<f32>,
    outline_width_and_viewport: vec4<f32>,
};

@group(0) @binding(0) var<uniform> draw: Draw;

struct FillOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) normal: vec3<f32>,
};

@vertex
fn vs_fill(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>) -> FillOut {
    var out: FillOut;
    out.clip = draw.view_projection * draw.model * vec4<f32>(position, 1.0);
    out.normal = (draw.model * vec4<f32>(normal, 0.0)).xyz;
    return out;
}

@fragment
fn fs_fill(in: FillOut) -> @location(0) vec4<f32> {
    if (draw.to_light_and_two_tone.w > 0.5 && dot(normalize(in.normal), draw.to_light_and_two_tone.xyz) <= 0.0) {
        return draw.shade;
    }
    return draw.base;
}

@vertex
fn vs_outline(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>) -> @builtin(position) vec4<f32> {
    let clip = draw.view_projection * draw.model * vec4<f32>(position, 1.0);
    let viewport = draw.outline_width_and_viewport.yz;
    let along = (draw.view_projection * draw.model * vec4<f32>(normal, 0.0)).xy * viewport;
    let reach = length(along);
    if (reach < 0.00001) {
        return clip;
    }
    let pixels = along / reach * draw.outline_width_and_viewport.x;
    return vec4<f32>(clip.xy + pixels / viewport * 2.0 * clip.w, clip.zw);
}

@fragment
fn fs_outline() -> @location(0) vec4<f32> {
    return draw.ink;
}
"""

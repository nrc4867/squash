package dev.chieppa.squash.render

/** Floats in [MESH_WGSL]'s `Draw` uniform. */
internal const val DRAW_FLOATS = 80

/**
 * WGSL for [MeshPass]: flat and two-tone fills shaded by proxy normals and their material, a see-through fill's shine and rim whole, an outline as the mesh's back faces pushed out
 * by a pixel width, set slightly behind its surface, each in its slice of the depth buffer and at its opacity; and a glow's halo, the
 * back faces pushed out further in two shells, the outer at HALO_OPACITY of its strength and the inner over it, so the halo steps down
 * in two flat bands.
 */
internal const val MESH_WGSL = """
struct Draw {
    view_projection: mat4x4<f32>,
    model: mat4x4<f32>,
    base: vec4<f32>,
    shade: vec4<f32>,
    to_light_and_two_tone: vec4<f32>,
    ink: vec4<f32>,
    outline_width_and_viewport: vec4<f32>,
    depth_scale_offset_and_opacity: vec4<f32>,
    f0: vec4<f32>,
    f1: vec4<f32>,
    f2: vec4<f32>,
    f3: vec4<f32>,
    f4: vec4<f32>,
    key: vec4<f32>,
};

@group(0) @binding(0) var<uniform> draw: Draw;

// How far an outline sits behind its surface, in body units, so a line shows where a shape stands clear of what is behind it
// and not where it barely breaks a surface.
const OUTLINE_SINK: f32 = 0.02;

fn in_layer(clip: vec4<f32>) -> vec4<f32> {
    let layer = draw.depth_scale_offset_and_opacity;
    return vec4<f32>(clip.xy, clip.z * layer.x + clip.w * layer.y, clip.w);
}

struct FillOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) proxy: vec3<f32>,
};

@vertex
fn vs_fill(@location(0) position: vec3<f32>, @location(2) proxy: vec3<f32>) -> FillOut {
    var out: FillOut;
    out.clip = in_layer(draw.view_projection * draw.model * vec4<f32>(position, 1.0));
    out.proxy = (draw.model * vec4<f32>(proxy, 0.0)).xyz;
    return out;
}

@fragment
fn fs_fill(in: FillOut) -> @location(0) vec4<f32> {
    let color = finished(in.proxy, draw.to_light_and_two_tone, draw.base.rgb, draw.shade.rgb, away_from(draw.view_projection), draw.f0, draw.f1, draw.f2, draw.f3, draw.f4, draw.key);
    var opacity = draw.depth_scale_offset_and_opacity.z;
    if (draw.f0.x > 0.5 && (all(color == draw.f3.rgb) || all(color == draw.f2.rgb))) {
        opacity = 1.0;
    }
    return vec4<f32>(color, opacity);
}

// The mesh's back faces pushed out by [width] pixels, [sink] body units behind its surface.
fn pushed_out(position: vec3<f32>, normal: vec3<f32>, width: f32, sink: f32) -> vec4<f32> {
    let projection = draw.view_projection;
    let away = normalize(vec3<f32>(projection[0][2], projection[1][2], projection[2][2])) * sink;
    let world = draw.model * vec4<f32>(position, 1.0);
    let at = projection * world;
    let behind = projection * (world + vec4<f32>(away, 0.0));
    let clip = vec4<f32>(at.xy, behind.z / behind.w * at.w, at.w);
    let viewport = draw.outline_width_and_viewport.yz;
    let along = (draw.view_projection * draw.model * vec4<f32>(normal, 0.0)).xy * viewport;
    let reach = length(along);
    if (reach < 0.00001) {
        return in_layer(clip);
    }
    let pixels = along / reach * width;
    return in_layer(vec4<f32>(clip.xy + pixels / viewport * 2.0 * clip.w, clip.zw));
}

@vertex
fn vs_outline(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>) -> @builtin(position) vec4<f32> {
    return pushed_out(position, normal, draw.outline_width_and_viewport.x, OUTLINE_SINK);
}

@fragment
fn fs_outline() -> @location(0) vec4<f32> {
    return vec4<f32>(draw.ink.rgb, draw.depth_scale_offset_and_opacity.z);
}

// A halo sits at its back faces' own depth, so it shows over a surface its part lies close on and never over what stands in front.
const HALO_SINK: f32 = 0.0;
const HALO_OPACITY: f32 = 0.6;
const INNER_SHELL: f32 = 0.5;

@vertex
fn vs_halo(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>, @builtin(instance_index) shell: u32) -> @builtin(position) vec4<f32> {
    let width = draw.f3.w * select(1.0, INNER_SHELL, shell == 1u) + draw.outline_width_and_viewport.x;
    return pushed_out(position, normal, width, HALO_SINK);
}

@fragment
fn fs_halo() -> @location(0) vec4<f32> {
    return vec4<f32>(draw.base.rgb, HALO_OPACITY * draw.f2.w);
}
""" + TONE_WGSL + FINISH_WGSL

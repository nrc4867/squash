package dev.chieppa.squash.render

/** Floats in [COLOR_LAYER_WGSL]'s `Layer` uniform. */
internal const val COLOR_LAYER_FLOATS = 48

/**
 * WGSL for [ColorLayerPass]: triangles lying on their mesh's own triangles, in the mesh's slice of the depth buffer, pulled toward the
 * camera by a fraction of a pixel's depth. That fraction grows where the surface turns away, since there a fragment's depth moves
 * fastest between its mesh and its cut, and it never falls under a few steps of the depth buffer. So a layer passes against its
 * own mesh at any angle and at any zoom, and fails against anything truly in front of it.
 */
internal const val COLOR_LAYER_WGSL = """
struct Layer {
    view_projection: mat4x4<f32>,
    model: mat4x4<f32>,
    lit: vec4<f32>,
    shadow: vec4<f32>,
    to_light_and_two_tone: vec4<f32>,
    viewport_and_depth: vec4<f32>,
};

@group(0) @binding(0) var<uniform> layer: Layer;

// Pixels of depth a layer is pulled forward by, facing the camera and for each unit of the surface's slope, whose count is held to
// MAX_SLOPE; the least it moves in the depth buffer's own range, about twenty-five steps of 24 bits, which clears the rounding of a part far from the origin under a close zoom.
const LIFT_PIXELS: f32 = 0.015;
const SLOPE_LIFT_PIXELS: f32 = 0.015;
const MAX_SLOPE: f32 = 40.0;
const MIN_DEPTH_LIFT: f32 = 0.0000015;

struct LayerOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) proxy: vec3<f32>,
};

struct FadingOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) proxy: vec3<f32>,
    @location(1) alpha: f32,
};

fn lifted(position: vec3<f32>, normal: vec3<f32>, proxy: vec3<f32>) -> LayerOut {
    let projection = layer.view_projection;
    let away = normalize(vec3<f32>(projection[0][2], projection[1][2], projection[2][2]));
    let world = layer.model * vec4<f32>(position, 1.0);
    let at = projection * world;
    let span_y = length(vec3<f32>(projection[0][1], projection[1][1], projection[2][1]));
    let pixel = 2.0 * at.w / (layer.viewport_and_depth.y * span_y);
    let across = length(normal);
    let facing = select(1.0, abs(dot(normal, away)) / max(across, 0.000001), across > 0.000001);
    let slope = min(sqrt(max(1.0 - facing * facing, 0.0)) / max(facing, 0.000001), MAX_SLOPE);
    let nearer = projection * (world - vec4<f32>(away * pixel * (LIFT_PIXELS + SLOPE_LIFT_PIXELS * slope), 0.0));
    let depth = min(nearer.z / nearer.w, at.z / at.w - MIN_DEPTH_LIFT / layer.viewport_and_depth.z);
    var out: LayerOut;
    out.clip = vec4<f32>(at.xy, (depth * layer.viewport_and_depth.z + layer.viewport_and_depth.w) * at.w, at.w);
    out.proxy = (layer.model * vec4<f32>(proxy, 0.0)).xyz;
    return out;
}

@vertex
fn vs_layer(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>, @location(2) proxy: vec3<f32>) -> LayerOut {
    return lifted(position, normal, proxy);
}

@fragment
fn fs_layer(in: LayerOut) -> @location(0) vec4<f32> {
    return vec4<f32>(tone(in.proxy, layer.to_light_and_two_tone, layer.lit.rgb, layer.shadow.rgb), 1.0);
}

@vertex
fn vs_fading(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>, @location(2) proxy: vec3<f32>, @location(3) alpha: f32) -> FadingOut {
    let layered = lifted(position, normal, proxy);
    var out: FadingOut;
    out.clip = layered.clip;
    out.proxy = layered.proxy;
    out.alpha = alpha;
    return out;
}

@fragment
fn fs_fading(in: FadingOut) -> @location(0) vec4<f32> {
    return vec4<f32>(tone(in.proxy, layer.to_light_and_two_tone, layer.lit.rgb, layer.shadow.rgb), in.alpha);
}
""" + TONE_WGSL

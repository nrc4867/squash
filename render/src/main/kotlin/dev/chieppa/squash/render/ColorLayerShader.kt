package dev.chieppa.squash.render

/** Floats in [COLOR_LAYER_WGSL]'s `Layer` uniform. */
internal const val COLOR_LAYER_FLOATS = 76

/**
 * WGSL for [ColorLayerPass]: triangles lying on their mesh's own triangles, in the mesh's slice of the depth buffer, pulled toward the
 * camera by a fraction of a pixel's depth. That fraction grows where the surface turns away, since there a fragment's depth moves
 * fastest between its mesh and its cut, and it never falls under a few steps of the depth buffer. So a layer passes against its
 * own mesh at any angle and at any zoom, and fails against anything truly in front of it. A shaped layer is kept at each fragment to an
 * almond and an oval, each turned, in the chart its vertices carry, as [dev.chieppa.squash.geom.ChartShape] reads them, its edge faded
 * over a pixel.
 */
internal const val COLOR_LAYER_WGSL = """
struct Layer {
    view_projection: mat4x4<f32>,
    model: mat4x4<f32>,
    lit: vec4<f32>,
    shadow: vec4<f32>,
    to_light_and_two_tone: vec4<f32>,
    viewport_and_depth: vec4<f32>,
    opening: vec4<f32>,
    oval: vec4<f32>,
    turn_and_oval: vec4<f32>,
    positive_lit_and_on: vec4<f32>,
    positive_shadow: vec4<f32>,
    negative_lit_and_on: vec4<f32>,
    negative_shadow: vec4<f32>,
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

struct SidedOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) proxy: vec3<f32>,
    @location(1) sides: vec2<f32>,
};

@vertex
fn vs_sided(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>, @location(2) proxy: vec3<f32>, @location(3) positive: f32, @location(4) negative: f32) -> SidedOut {
    let layered = lifted(position, normal, proxy);
    var out: SidedOut;
    out.clip = layered.clip;
    out.proxy = layered.proxy;
    out.sides = vec2<f32>(positive * layer.positive_lit_and_on.w, negative * layer.negative_lit_and_on.w);
    return out;
}

// The layer's tone with its far sides' tones over it by each side's share, as fading layers of those tones drawn over it in turn would show.
@fragment
fn fs_sided(in: SidedOut) -> @location(0) vec4<f32> {
    var color = tone(in.proxy, layer.to_light_and_two_tone, layer.lit.rgb, layer.shadow.rgb);
    color = mix(color, tone(in.proxy, layer.to_light_and_two_tone, layer.positive_lit_and_on.rgb, layer.positive_shadow.rgb), clamp(in.sides.x, 0.0, 1.0));
    color = mix(color, tone(in.proxy, layer.to_light_and_two_tone, layer.negative_lit_and_on.rgb, layer.negative_shadow.rgb), clamp(in.sides.y, 0.0, 1.0));
    return vec4<f32>(color, 1.0);
}

struct ShapedOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) proxy: vec3<f32>,
    @location(1) chart: vec2<f32>,
};

@vertex
fn vs_shaped(@location(0) position: vec3<f32>, @location(1) normal: vec3<f32>, @location(2) proxy: vec3<f32>, @location(3) chart: vec2<f32>) -> ShapedOut {
    let layered = lifted(position, normal, proxy);
    var out: ShapedOut;
    out.clip = layered.clip;
    out.proxy = layered.proxy;
    out.chart = chart;
    return out;
}

// How far outside the almond a chart point lies, along v between its corners: at most zero inside.
fn almond_level(p: vec2<f32>) -> f32 {
    let o = layer.opening;
    let a = clamp(p.x / o.x, -1.0, 1.0);
    let open = sqrt(max(1.0 - a * a, 0.0)) * (1.0 + o.w * a);
    return max(max(p.y - o.y * open, -o.y * o.z * open - p.y), (abs(p.x) - o.x) * o.y / o.x);
}

// How far outside the oval a chart point lies, in shares of its lesser half: at most zero inside.
fn oval_level(p: vec2<f32>) -> f32 {
    let q = (p - layer.oval.xy) / layer.oval.zw;
    return (length(q) - 1.0) * min(layer.oval.z, layer.oval.w);
}

@fragment
fn fs_shaped(in: ShapedOut) -> @location(0) vec4<f32> {
    let c = layer.turn_and_oval.x;
    let s = layer.turn_and_oval.y;
    let p = vec2<f32>(in.chart.x * c + in.chart.y * s, -in.chart.x * s + in.chart.y * c);
    var level = almond_level(p);
    if (layer.turn_and_oval.z > 0.5) {
        let oc = cos(layer.turn_and_oval.w);
        let os = sin(layer.turn_and_oval.w);
        level = max(level, oval_level(vec2<f32>(in.chart.x * oc + in.chart.y * os, -in.chart.x * os + in.chart.y * oc)));
    }
    let alpha = clamp(0.5 - level / max(fwidth(level), 0.0000001), 0.0, 1.0);
    if (alpha <= 0.0) {
        discard;
    }
    return vec4<f32>(tone(in.proxy, layer.to_light_and_two_tone, layer.lit.rgb, layer.shadow.rgb), alpha);
}
""" + TONE_WGSL

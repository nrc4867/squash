package dev.chieppa.squash.render

/** Floats in [COLOR_LAYER_WGSL]'s `Layer` uniform. */
internal const val COLOR_LAYER_FLOATS = 144

/**
 * WGSL for [ColorLayerPass]: triangles lying on their mesh's own triangles, in the mesh's slice of the depth buffer, pulled toward the
 * camera by a fraction of a pixel's depth, and on see-through cloth by its lift too. That fraction grows where the surface turns away, since there a fragment's depth moves
 * fastest between its mesh and its cut, and it never falls under a few steps of the depth buffer. So a layer passes against its
 * own mesh at any angle and at any zoom, and fails against anything truly in front of it. A shaped layer is kept at each fragment to an
 * almond and an oval, each turned, or to a lid's mass about the almond, and by a cut, in the chart its vertices carry, as
 * [dev.chieppa.squash.geom.ChartShape] reads them (see [EYE_SHAPE_WGSL]), its edge faded over a pixel and its cut's over its softness
 * too. A crease layer multiplies what is under it by its tone where its per-vertex shade is at least a half.
 */
internal val COLOR_LAYER_WGSL = """
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
    shape_extra: vec4<f32>,
    cut: vec4<f32>,
    cut_mode: vec4<f32>,
    lid_a: vec4<f32>,
    lid_b: vec4<f32>,
    lid_c: vec4<f32>,
    lid_pose: vec4<f32>,
    lid_paint: vec4<f32>,
    lid_mark: vec4<f32>,
    lid_more: vec4<f32>,
    lift: vec4<f32>,
    f0: vec4<f32>,
    f1: vec4<f32>,
    f2: vec4<f32>,
    f3: vec4<f32>,
    f4: vec4<f32>,
    key: vec4<f32>,
};

// The layer's tone at [proxy] in its own lit and shadow colors, by its material.
fn layer_tone(proxy: vec3<f32>, lit: vec3<f32>, shadow: vec3<f32>) -> vec3<f32> {
    return finished(proxy, layer.to_light_and_two_tone, lit, shadow, away_from(layer.view_projection), layer.f0, layer.f1, layer.f2, layer.f3, layer.f4, layer.key);
}

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
    let nearer = projection * (world - vec4<f32>(away * (pixel * (LIFT_PIXELS + SLOPE_LIFT_PIXELS * slope) + layer.lift.x), 0.0));
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
    return vec4<f32>(layer_tone(in.proxy, layer.lit.rgb, layer.shadow.rgb), 1.0);
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
    return vec4<f32>(layer_tone(in.proxy, layer.lit.rgb, layer.shadow.rgb), in.alpha);
}

// A crease's shade band: where the shade carried per vertex is at least a half, its edge smoothed over a pixel, the color under it
// multiplied by the layer's tone there, its lit color in light and its shadow color in a two-tone shadow, so a band darkens whatever
// color it lies on the same way and a band in shade does not darken it twice over.
@fragment
fn fs_crease(in: FadingOut) -> @location(0) vec4<f32> {
    let band = clamp((in.alpha - 0.5) / max(fwidth(in.alpha), 0.0000001) + 0.5, 0.0, 1.0);
    let toned = layer_tone(in.proxy, layer.lit.rgb, layer.shadow.rgb);
    if (band <= 0.0) {
        discard;
    }
    return vec4<f32>(mix(vec3<f32>(1.0), toned, band), 1.0);
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
    var color = layer_tone(in.proxy, layer.lit.rgb, layer.shadow.rgb);
    color = mix(color, layer_tone(in.proxy, layer.positive_lit_and_on.rgb, layer.positive_shadow.rgb), clamp(in.sides.x, 0.0, 1.0));
    color = mix(color, layer_tone(in.proxy, layer.negative_lit_and_on.rgb, layer.negative_shadow.rgb), clamp(in.sides.y, 0.0, 1.0));
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

// How far outside an oval a chart point lies, in shares of its lesser half: at most zero inside.
fn oval_level_of(p: vec2<f32>, oval: vec4<f32>) -> f32 {
    let q = (p - oval.xy) / oval.zw;
    return (length(q) - 1.0) * min(oval.z, oval.w);
}

// How far outside the layer's oval a chart point lies.
fn oval_level(p: vec2<f32>) -> f32 {
    return oval_level_of(p, layer.oval);
}

@fragment
fn fs_shaped(in: ShapedOut) -> @location(0) vec4<f32> {
    let c = layer.turn_and_oval.x;
    let s = layer.turn_and_oval.y;
    let p = vec2<f32>(in.chart.x * c + in.chart.y * s, -in.chart.x * s + in.chart.y * c);
    let oc = cos(layer.turn_and_oval.w);
    let os = sin(layer.turn_and_oval.w);
    let q = vec2<f32>(in.chart.x * oc + in.chart.y * os, -in.chart.x * os + in.chart.y * oc);
    var level: f32;
    var spread: f32;
    var share = 1.0;
    if (layer.shape_extra.z > 0.5) {
        let pixel = lid_pixel(p);
        level = lid_level(p) + 0.5 * pixel;
        spread = pixel;
    } else if (layer.lid_paint.y > 2.5) {
        level = max(almond_open_level(p), -almond_level(p));
        spread = fwidth(level);
    } else if (layer.lid_paint.y > 1.5) {
        let pixel = lid_pixel(p);
        level = lid_paint_level(p) + 0.5 * pixel;
        spread = pixel;
    } else if (layer.lid_paint.y > 0.5) {
        level = lid_paint_level(p);
        spread = fwidth(level);
        share = lid_paint_share(p);
    } else {
        level = almond_level(p);
        if (layer.turn_and_oval.z > 0.5) {
            level = max(level, oval_level(q));
        }
        spread = fwidth(level);
    }
    var alpha = clamp(0.5 - level / max(spread, 0.0000001), 0.0, 1.0) * share;
    let mode = layer.cut_mode.x;
    if (mode > 0.5) {
        var cut_level: f32;
        if (mode < 1.5) {
            cut_level = oval_level_of(q, layer.cut);
        } else {
            let moved = p - layer.cut.xy;
            cut_level = almond_level(vec2<f32>(moved.x / layer.cut.z, moved.y));
        }
        cut_level = cut_level * layer.cut_mode.y;
        alpha = alpha * clamp(0.5 - cut_level / (max(fwidth(cut_level), 0.0000001) + layer.shape_extra.w), 0.0, 1.0);
    }
    let toned = layer_tone(in.proxy, layer.lit.rgb, layer.shadow.rgb);
    if (alpha <= 0.0) {
        discard;
    }
    return vec4<f32>(toned, alpha);
}
""" + EYE_SHAPE_WGSL + LID_MASS_WGSL + LID_PAINT_WGSL + TONE_WGSL + FINISH_WGSL

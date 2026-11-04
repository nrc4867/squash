package dev.chieppa.squash.render

/** Floats in [RIBBON_WGSL]'s `Ribbon` uniform. */
internal const val RIBBON_FLOATS = 44

/**
 * WGSL for [StrokeRibbonPass]: a line along a stroke's projected path, as wide in pixels as its line rule and weight say at any
 * distance, sitting the draw's lift toward the camera from its surface so it stays over what it is drawn on.
 */
internal const val RIBBON_WGSL = """
struct Ribbon {
    view_projection: mat4x4<f32>,
    model: mat4x4<f32>,
    color: vec4<f32>,
    rule: vec4<f32>,
    viewport_and_depth: vec4<f32>,
};

@group(0) @binding(0) var<uniform> ribbon: Ribbon;

fn on_screen(clip: vec4<f32>) -> vec2<f32> {
    return clip.xy / clip.w * ribbon.viewport_and_depth.xy * 0.5;
}

@vertex
fn vs_ribbon(
    @location(0) position: vec3<f32>,
    @location(1) before: vec3<f32>,
    @location(2) after: vec3<f32>,
    @location(3) normal: vec3<f32>,
    @location(4) edge: f32,
    @location(5) weight: f32,
) -> @builtin(position) vec4<f32> {
    let projection = ribbon.view_projection;
    let away = normalize(vec3<f32>(projection[0][2], projection[1][2], projection[2][2]));
    let world = ribbon.model * vec4<f32>(position, 1.0);
    let at = projection * world;
    let along = on_screen(projection * ribbon.model * vec4<f32>(after, 1.0)) - on_screen(projection * ribbon.model * vec4<f32>(before, 1.0));
    let reach = length(along);
    let side = select(vec2<f32>(0.0, 0.0), vec2<f32>(-along.y, along.x) / reach, reach > 0.0001);
    let across = length(normal);
    let facing = select(1.0, abs(dot(normal, away)) / max(across, 0.000001), across > 0.000001);
    let pixels = weight * mix(ribbon.rule.y, ribbon.rule.x, smoothstep(0.0, ribbon.rule.z, facing));
    let nearer = projection * (world - vec4<f32>(away * ribbon.rule.w, 0.0));
    let shift = side * edge * pixels * 0.5 / (ribbon.viewport_and_depth.xy * 0.5) * at.w;
    return vec4<f32>(at.xy + shift, (nearer.z / nearer.w * ribbon.viewport_and_depth.z + ribbon.viewport_and_depth.w) * at.w, at.w);
}

@fragment
fn fs_ribbon() -> @location(0) vec4<f32> {
    return vec4<f32>(ribbon.color.rgb, 1.0);
}
"""

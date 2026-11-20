package dev.chieppa.squash.render

import dev.chieppa.stretch.gpu.webgpu.VertexAttribute
import dev.chieppa.stretch.gpu.webgpu.VertexBufferLayout
import dev.chieppa.stretch.gpu.webgpu.VertexFormat

/** Floats in [CLOTH_WGSL]'s `Cloth` uniform. */
internal const val CLOTH_UNIFORM_FLOATS = 40

/** A see-through cloth's [ClothDraw.attributes] as the shader's locations 3 to 7. */
internal val CLOTH_VERTEX_LAYOUT = VertexBufferLayout(
    (CLOTH_FLOATS * Float.SIZE_BYTES).toLong(),
    listOf(
        VertexAttribute(VertexFormat.Float32x3, 0, 3),
        VertexAttribute(VertexFormat.Float32x3, 3L * Float.SIZE_BYTES, 4),
        VertexAttribute(VertexFormat.Float32x4, 6L * Float.SIZE_BYTES, 5),
        VertexAttribute(VertexFormat.Float32x4, 10L * Float.SIZE_BYTES, 6),
        VertexAttribute(VertexFormat.Float32x4, 14L * Float.SIZE_BYTES, 7),
    ),
)

/**
 * WGSL for [ClothPass]: see-through cloth in its tones, at an opacity that hides more as the cloth turns from the viewer, as a film of
 * that opacity facing the viewer stacked as many times as one over a power of the cosine of its slant.
 * Where it is a net, each of its two sets of threads covers what its pixel spans of the threads, the threads' coverage averaged over the
 * pixel's span along the net, and fades to the net's mean coverage as its spacing nears a pixel, so a net too fine to draw reads as an
 * even tone. Where a pixel spans two runs of the body, whose nets do not meet, it takes the mean too, and it fades to the mean as far as
 * the net's place is not trusted.
 */
internal const val CLOTH_WGSL = """
struct Cloth {
    view_projection: mat4x4<f32>,
    model: mat4x4<f32>,
    to_light_and_two_tone: vec4<f32>,
    lift_opacity_and_layer: vec4<f32>,
};

@group(0) @binding(0) var<uniform> cloth: Cloth;

const TAU: f32 = 6.2831853;
// The least cosine of a slant a film's thickness is read at, so cloth edge on is whole.
const LEAST_FACING: f32 = 0.04;
// How fast the film thickens as it turns from the viewer: one over this power of the cosine of its slant, so it darkens over a broad
// band toward the silhouette, as sheer cloth is drawn.
const THICKENING: f32 = 2.0;
// Over what share of a spacing a pixel's span fades the threads to their mean.
const FADE_FROM: f32 = 0.1;
const FADE_TO: f32 = 0.25;
// Over what trust in the net's place its threads come in from its mean tone.
const TRUST_FROM: f32 = 0.5;
const TRUST_TO: f32 = 0.85;
// How far a run index changes across a pixel before the pixel spans two runs.
const RUN_STEP: f32 = 0.01;

struct ClothOut {
    @builtin(position) clip: vec4<f32>,
    @location(0) proxy: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) lit: vec3<f32>,
    @location(3) shadow: vec3<f32>,
    @location(4) look: vec4<f32>,
    @location(5) place: vec4<f32>,
    @location(6) sets: vec4<f32>,
};

fn away() -> vec3<f32> {
    return away_from(cloth.view_projection);
}

@vertex
fn vs_cloth(
    @location(0) position: vec3<f32>,
    @location(1) normal: vec3<f32>,
    @location(2) proxy: vec3<f32>,
    @location(3) lit: vec3<f32>,
    @location(4) shadow: vec3<f32>,
    @location(5) look: vec4<f32>,
    @location(6) place: vec4<f32>,
    @location(7) sets: vec4<f32>,
) -> ClothOut {
    let world = cloth.model * vec4<f32>(position, 1.0);
    let at = cloth.view_projection * world;
    let nearer = cloth.view_projection * (world - vec4<f32>(away() * cloth.lift_opacity_and_layer.x, 0.0));
    let depth = nearer.z / nearer.w * at.w;
    var out: ClothOut;
    out.clip = vec4<f32>(at.xy, depth * cloth.lift_opacity_and_layer.z + at.w * cloth.lift_opacity_and_layer.w, at.w);
    out.proxy = (cloth.model * vec4<f32>(proxy, 0.0)).xyz;
    out.normal = (cloth.model * vec4<f32>(normal, 0.0)).xyz;
    out.lit = lit;
    out.shadow = shadow;
    out.look = look;
    out.place = place;
    out.sets = sets;
    return out;
}

// The length of threads w wide, one at each whole number, from minus infinity to t.
fn covered_to(t: f32, w: f32) -> f32 {
    let s = t + 0.5 * w;
    return floor(s) * w + min(fract(s), w);
}

// How much of a pixel spanning [span] of a set of threads round [at] the threads cover.
fn threads(at: f32, span: f32, w: f32) -> f32 {
    if (span < 0.0001) {
        return select(0.0, 1.0, abs(at - round(at)) < 0.5 * w);
    }
    let half = 0.5 * span;
    let filtered = clamp((covered_to(at + half, w) - covered_to(at - half, w)) / span, 0.0, 1.0);
    return mix(filtered, w, smoothstep(FADE_FROM, FADE_TO, span));
}

// One set of threads at a fragment: its value [turn] times its count less [along] times its slope, its span the least of that and the
// same read with v from 0 to 1, so the v seam, where the angle wraps, does not widen it.
fn thread_set(turn: f32, wrapped: f32, along: f32, count: f32, slope: f32, w: f32) -> f32 {
    let at = turn * count - along * slope;
    let other = wrapped * count - along * slope;
    let span = min(abs(dpdx(at)) + abs(dpdy(at)), abs(dpdx(other)) + abs(dpdy(other)));
    return threads(at, span, w);
}

@fragment
fn fs_cloth(in: ClothOut) -> @location(0) vec4<f32> {
    let w = clamp(in.look.z, 0.0, 1.0);
    let turn = atan2(in.place.z, in.place.y) / TAU;
    let wrapped = select(turn, turn + 1.0, turn < 0.0);
    let a = thread_set(turn, wrapped, in.place.x, in.sets.x, in.sets.z, w);
    let b = thread_set(turn, wrapped, in.place.x, in.sets.y, in.sets.w, w);
    let mean = 1.0 - (1.0 - w) * (1.0 - w);
    let spans_runs = abs(dpdx(in.look.w)) + abs(dpdy(in.look.w)) > RUN_STEP;
    let crossed = mix(mean, 1.0 - (1.0 - a) * (1.0 - b), smoothstep(TRUST_FROM, TRUST_TO, in.place.w));
    let knit = select(crossed, mean, spans_runs);
    let coverage = mix(1.0, knit, clamp(in.look.y, 0.0, 1.0));
    let film = clamp(in.look.x * coverage, 0.0, 1.0);
    let facing = max(abs(dot(normalize(in.normal), away())), LEAST_FACING);
    let alpha = 1.0 - pow(max(1.0 - film, 0.0), 1.0 / pow(facing, THICKENING));
    let color = tone(in.proxy, cloth.to_light_and_two_tone, in.lit, in.shadow, away());
    return vec4<f32>(color, alpha * cloth.lift_opacity_and_layer.y);
}
""" + TONE_WGSL

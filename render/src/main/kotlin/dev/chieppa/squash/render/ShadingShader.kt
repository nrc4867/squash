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

fn in_shade(n: vec3<f32>, light: vec3<f32>, away: vec3<f32>) -> bool {
    if (dot(n, light) > 0.0) {
        return false;
    }
    let toward = -away;
    let lv = dot(light, toward);
    let across = n - toward * dot(n, toward);
    let spread = length(across);
    if (lv > 0.0 && spread > 0.0001) {
        let lu = dot(light, across / spread);
        let sliver = 1.0 - lv / max(sqrt(lv * lv + lu * lu), 0.000001);
        if (sliver < LEAST_SHADOW_SHARE) {
            return false;
        }
    }
    return true;
}

fn tone(proxy: vec3<f32>, to_light_and_two_tone: vec4<f32>, lit: vec3<f32>, shadow: vec3<f32>, away: vec3<f32>) -> vec3<f32> {
    if (to_light_and_two_tone.w <= 0.5 || !in_shade(normalize(proxy), to_light_and_two_tone.xyz, away)) {
        return lit;
    }
    return shadow;
}
"""

/**
 * WGSL for a fill's material (`Finish`), after [TONE_WGSL]: `finished` is `tone` where the finish is plain and there is no key light, so
 * a slot with no material draws as two tones always have. Otherwise, in two-tone, a point is in shade only where neither the view's light
 * nor the key light reaches it; with three steps the lit side splits at MID_EDGE of the way to the light into a middle tone halfway to the
 * shadow; with one it is all lit. A shine lies where the surface faces halfway between the key light (else the view's) and the viewer: a
 * spot within its size of that, a band stretched across the light's way on screen with a reflection tone just outside it, or a thin
 * streak across it. A rim lies along the silhouette on the side turned from that light, its width the share of the way in toward the
 * middle that a round form shows it. A glow ignores the light, its middle, where the surface faces the viewer, lifted toward white by its
 * strength.
 */
internal const val FINISH_WGSL = """
const MID_EDGE: f32 = 0.45;
const MID_SHARE: f32 = 0.5;
const GLOW_CORE: f32 = 0.6;
const GLOW_LIFT: f32 = 0.75;
const SPOT_REACH: f32 = 0.5;
const BAND_REACH: f32 = 0.45;
const BAND_STRETCH: f32 = 0.85;
const REFLECTION_REACH: f32 = 0.25;
const STREAK_REACH: f32 = 0.35;
const RIM_SIDE: f32 = 0.35;

fn shine_level(n: vec3<f32>, shine_light: vec3<f32>, toward: vec3<f32>, stretch: f32) -> f32 {
    let half_way = normalize(shine_light + toward);
    var across = cross(toward, shine_light - toward * dot(shine_light, toward));
    let reach = length(across);
    var m = n;
    if (reach > 0.0001) {
        across = across / reach;
        m = normalize(n - across * dot(n, across) * stretch);
    }
    return acos(clamp(dot(m, half_way), -1.0, 1.0));
}

fn finished(
    proxy: vec3<f32>, to_light_and_two_tone: vec4<f32>, lit: vec3<f32>, shadow: vec3<f32>, away: vec3<f32>,
    f0: vec4<f32>, f1: vec4<f32>, f2: vec4<f32>, f3: vec4<f32>, f4: vec4<f32>, key: vec4<f32>,
) -> vec3<f32> {
    if (f0.x < 0.5 && key.w < 0.5) {
        return tone(proxy, to_light_and_two_tone, lit, shadow, away);
    }
    let n = normalize(proxy);
    let toward = -away;
    let facing = dot(n, toward);
    if (f2.w > 0.0) {
        return mix(lit, vec3<f32>(1.0), select(0.0, f2.w * GLOW_LIFT, facing > GLOW_CORE));
    }
    let view_light = to_light_and_two_tone.xyz;
    var shine_light = view_light;
    var reached = dot(n, view_light);
    var shaded = in_shade(n, view_light, away);
    if (key.w > 0.5) {
        let key_light = normalize(key.xyz);
        shine_light = key_light;
        reached = max(reached, dot(n, key_light));
        shaded = shaded && in_shade(n, key_light, away);
    }
    var color = lit;
    if (to_light_and_two_tone.w > 0.5 && f0.y > 1.5) {
        if (shaded) {
            color = shadow;
        } else if (f0.y > 2.5 && reached < MID_EDGE) {
            color = mix(lit, shadow, MID_SHARE);
        }
    }
    let size = f1.w;
    if (f0.w > 0.5 && facing > 0.0 && dot(n, shine_light) > 0.0) {
        if (f0.w < 1.5) {
            if (shine_level(n, shine_light, toward, 0.0) < size * SPOT_REACH) {
                color = f3.rgb;
            }
        } else if (f0.w < 2.5) {
            let level = shine_level(n, shine_light, toward, BAND_STRETCH);
            if (level < size * BAND_REACH) {
                color = f3.rgb;
            } else if (level < size * BAND_REACH + REFLECTION_REACH * size) {
                color = f4.rgb;
            }
        } else if (shine_level(n, shine_light, toward, 1.0) < size * STREAK_REACH) {
            color = f3.rgb;
        }
    }
    let inside = 1.0 - f0.z;
    if (f0.z > 0.0 && abs(facing) < sqrt(max(1.0 - inside * inside, 0.0)) && dot(n, shine_light) < RIM_SIDE) {
        color = f2.rgb;
    }
    return color;
}
"""

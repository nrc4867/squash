package dev.chieppa.squash.render

import dev.chieppa.squash.geom.CLUMP_ROOT
import dev.chieppa.squash.geom.CLUMP_ROOT_SHARE
import dev.chieppa.squash.geom.CLUMP_SHORTER
import dev.chieppa.squash.geom.CLUMP_START
import dev.chieppa.squash.geom.CLUMP_SWEEP
import dev.chieppa.squash.geom.CLUMP_SWEEP_MORE
import dev.chieppa.squash.geom.CURVE_REFINE
import dev.chieppa.squash.geom.CURVE_SAMPLES
import dev.chieppa.squash.geom.FILLET_DEPTH
import dev.chieppa.squash.geom.FLAT_SPAN
import dev.chieppa.squash.geom.FLICK_ROOT
import dev.chieppa.squash.geom.INNER_POINT
import dev.chieppa.squash.geom.LID_FILLET
import dev.chieppa.squash.geom.LID_INSET
import dev.chieppa.squash.geom.LID_TAPER_FROM
import dev.chieppa.squash.geom.LID_TAPER_TO
import dev.chieppa.squash.geom.LOWER_TAPER_POWER
import dev.chieppa.squash.geom.OUTER_END
import dev.chieppa.squash.geom.OUTER_HEIGHT
import dev.chieppa.squash.geom.OUTER_TAPER_FROM
import dev.chieppa.squash.geom.ROUND_POWER
import dev.chieppa.squash.geom.ROUND_SPAN
import dev.chieppa.squash.geom.STROKE_PIECES

/**
 * WGSL for the shaped layer's eye shapes in [COLOR_LAYER_WGSL]'s `Layer` uniform, as [dev.chieppa.squash.geom.ChartAlmond] and
 * [dev.chieppa.squash.geom.ChartLid] read them: the almond, its top flattened and back corner sharpened by `shape_extra`, and the lid's
 * mass about it from `lid_a`, `lid_b` and `lid_c`.
 */
internal val EYE_SHAPE_WGSL = """
const ROUND_POWER: f32 = $ROUND_POWER;
const ROUND_SPAN: f32 = $ROUND_SPAN;
const FLAT_SPAN: f32 = $FLAT_SPAN;
const LID_INSET: f32 = $LID_INSET;
const LID_FILLET: f32 = $LID_FILLET;
const CLUMP_START: f32 = $CLUMP_START;
const FILLET_DEPTH: f32 = $FILLET_DEPTH;
const OUTER_END: f32 = $OUTER_END;
const OUTER_HEIGHT: f32 = $OUTER_HEIGHT;
const OUTER_TAPER_FROM: f32 = $OUTER_TAPER_FROM;
const FLICK_ROOT: f32 = $FLICK_ROOT;
const STROKE_PIECES: i32 = $STROKE_PIECES;
const LID_TAPER_FROM: f32 = $LID_TAPER_FROM;
const LID_TAPER_TO: f32 = $LID_TAPER_TO;
const CLUMP_SWEEP: f32 = $CLUMP_SWEEP;
const CLUMP_SWEEP_MORE: f32 = $CLUMP_SWEEP_MORE;
const CLUMP_SHORTER: f32 = $CLUMP_SHORTER;
const CLUMP_ROOT: f32 = $CLUMP_ROOT;
const CLUMP_ROOT_SHARE: f32 = $CLUMP_ROOT_SHARE;
const LOWER_TAPER_POWER: f32 = $LOWER_TAPER_POWER;
const INNER_POINT: f32 = $INNER_POINT;
const CURVE_SAMPLES: i32 = $CURVE_SAMPLES;
const CURVE_REFINE: i32 = $CURVE_REFINE;

// x raised to power, 0 at 0.
fn raised(x: f32, power: f32) -> f32 {
    return select(0.0, pow(max(x, 0.0000001), power), x > 0.0);
}

// The power a lid's closing is raised to at a: a half, rising toward 1 at the back corner as it sharpens.
fn corner_power(a: f32) -> f32 {
    return ROUND_POWER + (1.0 - ROUND_POWER) * layer.shape_extra.y * clamp(-a, 0.0, 1.0);
}

// How open a lid is at a before its narrowing: 1 less a's size to the span, raised to the power.
fn closing(a: f32, span: f32, power: f32) -> f32 {
    return raised(max(1.0 - raised(abs(a), span), 0.0), power);
}

// How far the top lid rises at a, a share of the way to the front corner, in heights.
fn upper_open(a: f32) -> f32 {
    return closing(a, ROUND_SPAN + FLAT_SPAN * layer.shape_extra.x, corner_power(a)) * (1.0 + layer.opening.w * a);
}

// How far the bottom lid falls at a, before its lower lid's share, in heights.
fn lower_open(a: f32) -> f32 {
    return closing(a, ROUND_SPAN, corner_power(a)) * (1.0 + layer.opening.w * a);
}

// How far outside the almond a chart point lies, along v between its corners: at most zero inside.
fn almond_level(p: vec2<f32>) -> f32 {
    let o = layer.opening;
    let a = clamp(p.x / o.x, -1.0, 1.0);
    if (layer.shape_extra.x == 0.0 && layer.shape_extra.y == 0.0) {
        let open = sqrt(max(1.0 - a * a, 0.0)) * (1.0 + o.w * a);
        return max(max(p.y - o.y * open, -o.y * o.z * open - p.y), (abs(p.x) - o.x) * o.y / o.x);
    }
    return max(max(p.y - o.y * upper_open(a), -o.y * o.z * lower_open(a) - p.y), (abs(p.x) - o.x) * o.y / o.x);
}

fn eased(low: f32, high: f32, x: f32) -> f32 {
    let t = clamp((x - low) / (high - low), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// How much of the upper band is left at a near the front corner, where it comes to a point.
fn inner_point(a: f32) -> f32 {
    return clamp((1.0 - a) / INNER_POINT, 0.0, 1.0);
}

// The upper lid mass's thickness at a along the lid, in heights.
fn lid_thickness(a: f32) -> f32 {
    let inward = 1.0 - (1.0 - layer.lid_a.y) * eased(LID_TAPER_FROM, LID_TAPER_TO, a);
    let drop = eased(0.0, OUTER_HEIGHT, upper_open(a)) * eased(-1.0, OUTER_TAPER_FROM, a);
    let outward = select(1.0, OUTER_END + (1.0 - OUTER_END) * drop, a < 0.0);
    return layer.lid_a.x * inward * outward * inner_point(a);
}

// The smaller of a and b, rounded over k where they meet.
fn smooth_min(a: f32, b: f32, k: f32) -> f32 {
    let h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * FILLET_DEPTH;
}

// How far outside a stroke tapering from root_half at its root to a point at root + reach (x, y) lies, its way across bent by bend_x
// and its way up by bend_y.
fn stroke_level(x: f32, y: f32, root: vec2<f32>, reach: vec2<f32>, root_half: f32, bend_x: f32, bend_y: f32) -> f32 {
    var best = 1000000.0;
    var start = vec2<f32>(0.0, 0.0);
    let q = vec2<f32>((x - root.x) * layer.lid_c.y, y - root.y);
    for (var i = 1; i <= STROKE_PIECES; i = i + 1) {
        let t = f32(i) / f32(STROKE_PIECES);
        let end = vec2<f32>((t + (t * t - t) * bend_x) * reach.x * layer.lid_c.y, (t + (t * t - t) * bend_y) * reach.y);
        let edge = end - start;
        let h = clamp(dot(q - start, edge) / max(dot(edge, edge), 0.000000000001), 0.0, 1.0);
        let at = (f32(i - 1) + h) / f32(STROKE_PIECES);
        best = min(best, length(q - start - edge * h) - root_half * (1.0 - at));
        start = end;
    }
    return best;
}

// The height of the upper lid at a, or with lower set the lower lid's.
fn lid_edge(a: f32, lower: bool) -> f32 {
    if (lower) {
        return -layer.opening.z * lower_open(a);
    }
    return upper_open(a);
}

fn apart(x: f32, y: f32, a: f32, lower: bool) -> f32 {
    let dx = (x - a) * layer.lid_c.y;
    let dy = y - lid_edge(a, lower);
    return dx * dx + dy * dy;
}

// The place along a lid nearest (x, y), and how far that is, widths counted as the lid's aspect in heights.
fn nearest(x: f32, y: f32, lower: bool) -> vec2<f32> {
    var best = -1.0;
    var best_apart = 1000000.0;
    for (var i = 0; i <= CURVE_SAMPLES; i = i + 1) {
        let a = -1.0 + 2.0 * f32(i) / f32(CURVE_SAMPLES);
        let d = apart(x, y, a, lower);
        if (d < best_apart) {
            best_apart = d;
            best = a;
        }
    }
    var low = max(-1.0, best - 2.0 / f32(CURVE_SAMPLES));
    var high = min(1.0, best + 2.0 / f32(CURVE_SAMPLES));
    for (var i = 0; i < CURVE_REFINE; i = i + 1) {
        let first = low + (high - low) / 3.0;
        let second = high - (high - low) / 3.0;
        if (apart(x, y, first, lower) < apart(x, y, second, lower)) {
            high = second;
        } else {
            low = first;
        }
    }
    let a = (low + high) / 2.0;
    return vec2<f32>(a, sqrt(apart(x, y, a, lower)));
}

// Lash clump i of count growing out of the upper lid, at (x, y) in widths and heights.
fn clump_level(i: i32, count: i32, x: f32, y: f32) -> f32 {
    let k = (f32(i) + 0.5) / f32(count);
    let root_x = -1.0 + CLUMP_START + 2.0 * layer.lid_c.x * k;
    let root_y = upper_open(root_x) + 0.5 * lid_thickness(root_x);
    let reach_length = layer.lid_b.w * (1.0 - CLUMP_SHORTER * k);
    let sweep = CLUMP_SWEEP + CLUMP_SWEEP_MORE * (1.0 - k);
    let root_half = CLUMP_ROOT + CLUMP_ROOT_SHARE * reach_length;
    return stroke_level(x, y, vec2<f32>(root_x, root_y), vec2<f32>(-sweep * reach_length, reach_length), root_half, 1.0, 0.0);
}

// The flick past the back corner, rooted across the upper band's end there, running straight out and lifting to its tip.
fn flick_level(x: f32, y: f32) -> f32 {
    let end = layer.lid_a.x * FLICK_ROOT;
    let root_y = (end - LID_INSET) / 2.0;
    let reach = vec2<f32>(-layer.lid_a.z, layer.lid_a.w - root_y);
    return stroke_level(x, y, vec2<f32>(-1.0, root_y), reach, (end + LID_INSET) / 2.0, 0.0, 1.0);
}

// A pixel's span in the lid's level at p, read from the chart itself, which is smooth, rather than from the level, whose creases along
// a band's middle and between its pieces would make a pixel's span jump and the edge break into dots. The mass is drawn half a pixel in
// from its edge, so a band thinning to its point fades out rather than leaving a half-covered line.
fn lid_pixel(p: vec2<f32>) -> f32 {
    return fwidth(p.y);
}

// How far outside the lid's mass a point of the opening's chart lies, in chart units: at most zero inside.
fn lid_level(p: vec2<f32>) -> f32 {
    let x = p.x / layer.opening.x;
    let y = p.y / layer.opening.y;
    var level = 1000000.0;
    let weight = layer.lid_a.x;
    if (weight > 0.0) {
        let top = nearest(x, y, false);
        let top_side = select(top.y, -top.y, abs(x) < 1.0 && y < upper_open(x));
        level = max(top_side - lid_thickness(top.x), -top_side - LID_INSET * inner_point(top.x));
        if (layer.lid_a.z > 0.0) {
            level = smooth_min(level, flick_level(x, y), LID_FILLET);
        }
        let lower = layer.lid_b.x;
        let extent = layer.lid_b.y;
        if (lower > 0.0 && extent > 0.0) {
            let bottom = nearest(x, y, true);
            let bottom_side = select(bottom.y, -bottom.y, abs(x) < 1.0 && y > lid_edge(x, true));
            let s = clamp((bottom.x + 1.0) / (2.0 * extent), 0.0, 1.0);
            let fade = raised(1.0 - s, LOWER_TAPER_POWER);
            let band = max(bottom_side - lower * weight * fade, -bottom_side - LID_INSET * fade);
            level = smooth_min(level, max(band, (bottom.x + 1.0 - 2.0 * extent) * layer.lid_c.y), LID_FILLET);
        }
    }
    let count = i32(layer.lid_b.z + 0.5);
    if (count > 0 && layer.lid_b.w > 0.0) {
        let over_lid = upper_open(clamp(x, -1.0, 1.0)) - LID_INSET - y;
        for (var i = 0; i < count; i = i + 1) {
            level = smooth_min(level, max(clump_level(i, count, x, y), over_lid), LID_FILLET);
        }
    }
    return level * layer.opening.y;
}
"""

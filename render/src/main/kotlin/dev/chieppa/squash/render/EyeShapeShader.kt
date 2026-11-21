package dev.chieppa.squash.render

import dev.chieppa.squash.geom.CLOSED_SOFT
import dev.chieppa.squash.geom.CORNER_FADE_FROM
import dev.chieppa.squash.geom.SHUT_GAP
import dev.chieppa.squash.geom.FLAT_SPAN
import dev.chieppa.squash.geom.ROUND_POWER
import dev.chieppa.squash.geom.ROUND_SPAN

/**
 * WGSL for the shaped layer's eye opening in [COLOR_LAYER_WGSL]'s `Layer` uniform, as [dev.chieppa.squash.geom.ChartAlmond] reads it:
 * the almond, its top flattened and back corner sharpened by `shape_extra`, and its lids' moving edges from `lid_pose` and `lid_paint`,
 * which close it toward their closed line. [LID_MASS_WGSL] and [LID_PAINT_WGSL] draw about these edges.
 */
internal val EYE_SHAPE_WGSL = """
const ROUND_POWER: f32 = $ROUND_POWER;
const ROUND_SPAN: f32 = $ROUND_SPAN;
const FLAT_SPAN: f32 = $FLAT_SPAN;
const CLOSED_SOFT: f32 = $CLOSED_SOFT;
const SHUT_GAP: f32 = $SHUT_GAP;
const CORNER_FADE_FROM: f32 = $CORNER_FADE_FROM;

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

fn is_plain() -> bool {
    return layer.shape_extra.x == 0.0 && layer.shape_extra.y == 0.0;
}

// The open top lid's height at a, in heights.
fn open_top(a: f32) -> f32 {
    if (is_plain()) {
        return sqrt(max(1.0 - a * a, 0.0)) * (1.0 + layer.opening.w * a);
    }
    return upper_open(a);
}

// The open bottom lid's height at a, in heights: negative.
fn open_bottom(a: f32) -> f32 {
    if (is_plain()) {
        return -layer.opening.z * sqrt(max(1.0 - a * a, 0.0)) * (1.0 + layer.opening.w * a);
    }
    return -layer.opening.z * lower_open(a);
}

// The smaller of a and b, eased over k where they are near.
fn soft_min(a: f32, b: f32, k: f32) -> f32 {
    let h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return b + (a - b) * h - k * h * (1.0 - h);
}

// Where the lids' closed line lies at a, in heights, eased into the open lids near the corners.
fn closed_line(a: f32) -> f32 {
    let raw = layer.lid_pose.z - layer.lid_pose.w * a * a + layer.lid_paint.x * a;
    return -soft_min(-soft_min(raw, open_top(a), CLOSED_SOFT), -open_bottom(a), CLOSED_SOFT);
}

// The top lid's moving edge at a, in heights.
fn top_edge(a: f32) -> f32 {
    let top = open_top(a);
    let close = layer.lid_pose.x;
    if (close <= 0.0) {
        return top;
    }
    return top + (closed_line(a) - top) * min(close, 1.0);
}

// The bottom lid's moving edge at a, in heights.
fn bottom_edge(a: f32) -> f32 {
    let bottom = open_bottom(a);
    let rise = layer.lid_pose.y;
    if (rise <= 0.0) {
        return bottom;
    }
    return bottom + (closed_line(a) - bottom) * min(rise, 1.0);
}

// How far outside the almond a chart point lies, along v between its corners and its lids' edges: at most zero inside.
fn almond_level(p: vec2<f32>) -> f32 {
    let o = layer.opening;
    let a = clamp(p.x / o.x, -1.0, 1.0);
    var level: f32;
    if (is_plain()) {
        let open = sqrt(max(1.0 - a * a, 0.0)) * (1.0 + o.w * a);
        level = max(max(p.y - o.y * open, -o.y * o.z * open - p.y), (abs(p.x) - o.x) * o.y / o.x);
    } else {
        level = max(max(p.y - o.y * upper_open(a), -o.y * o.z * lower_open(a) - p.y), (abs(p.x) - o.x) * o.y / o.x);
    }
    if (layer.lid_pose.x <= 0.0 && layer.lid_pose.y <= 0.0) {
        return level;
    }
    let top = top_edge(a);
    let bottom = bottom_edge(a);
    let shut = max(SHUT_GAP - (top - bottom), 0.0) * o.y;
    return max(level, max(p.y - o.y * top, o.y * bottom - p.y) + shut);
}

fn eased(low: f32, high: f32, x: f32) -> f32 {
    let t = clamp((x - low) / (high - low), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}
"""

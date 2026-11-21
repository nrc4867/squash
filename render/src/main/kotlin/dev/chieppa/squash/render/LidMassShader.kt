package dev.chieppa.squash.render

import dev.chieppa.squash.geom.BACK_END
import dev.chieppa.squash.geom.BACK_TAPER_FROM
import dev.chieppa.squash.geom.CLOSE_THIN
import dev.chieppa.squash.geom.CURVE_REFINE
import dev.chieppa.squash.geom.CURVE_SAMPLES
import dev.chieppa.squash.geom.FILLET_DEPTH
import dev.chieppa.squash.geom.INNER_POINT
import dev.chieppa.squash.geom.LASH_ANGLE_INNER
import dev.chieppa.squash.geom.LASH_ANGLE_OUTER
import dev.chieppa.squash.geom.LASH_BEND
import dev.chieppa.squash.geom.LASH_CLOSE_SWING
import dev.chieppa.squash.geom.LASH_REACH
import dev.chieppa.squash.geom.LASH_SHORTER
import dev.chieppa.squash.geom.LASH_START
import dev.chieppa.squash.geom.LASH_TAPER
import dev.chieppa.squash.geom.LID_FILLET
import dev.chieppa.squash.geom.LID_INSET
import dev.chieppa.squash.geom.LID_TAPER_FROM
import dev.chieppa.squash.geom.LID_TAPER_TO
import dev.chieppa.squash.geom.LOWER_TAPER_POWER
import dev.chieppa.squash.geom.SLOPE_STEP
import dev.chieppa.squash.geom.STEEP_FROM
import dev.chieppa.squash.geom.STEEP_TO
import dev.chieppa.squash.geom.STROKE_PIECES

/**
 * WGSL for a lid's ink mass about the eye's opening, as [dev.chieppa.squash.geom.ChartLid] reads it, on [EYE_SHAPE_WGSL]'s moving lid
 * edges: its bands, flick and lashes from `lid_a`, `lid_b`, `lid_c` and `lid_more`, and the tapered strokes [LID_PAINT_WGSL] shares.
 */
internal val LID_MASS_WGSL = """
const LID_INSET: f32 = $LID_INSET;
const LID_FILLET: f32 = $LID_FILLET;
const FILLET_DEPTH: f32 = $FILLET_DEPTH;
const CLOSE_THIN: f32 = $CLOSE_THIN;
const STEEP_FROM: f32 = $STEEP_FROM;
const STEEP_TO: f32 = $STEEP_TO;
const SLOPE_STEP: f32 = $SLOPE_STEP;
const BACK_TAPER_FROM: f32 = $BACK_TAPER_FROM;
const BACK_END: f32 = $BACK_END;
const STROKE_PIECES: i32 = $STROKE_PIECES;
const LID_TAPER_FROM: f32 = $LID_TAPER_FROM;
const LID_TAPER_TO: f32 = $LID_TAPER_TO;
const LASH_START: f32 = $LASH_START;
const LASH_SHORTER: f32 = $LASH_SHORTER;
const LASH_REACH: f32 = $LASH_REACH;
const LASH_ANGLE_OUTER: f32 = $LASH_ANGLE_OUTER;
const LASH_ANGLE_INNER: f32 = $LASH_ANGLE_INNER;
const LASH_BEND: f32 = $LASH_BEND;
const LASH_CLOSE_SWING: f32 = $LASH_CLOSE_SWING;
const LASH_TAPER: f32 = $LASH_TAPER;
const LOWER_TAPER_POWER: f32 = $LOWER_TAPER_POWER;
const INNER_POINT: f32 = $INNER_POINT;
const CURVE_SAMPLES: i32 = $CURVE_SAMPLES;
const CURVE_REFINE: i32 = $CURVE_REFINE;

// How much of the upper band is left at a near the front corner, where it comes to a point.
fn inner_point(a: f32) -> f32 {
    return clamp((1.0 - a) / INNER_POINT, 0.0, 1.0);
}

// The height of the upper lid's edge at a, or with lower set the lower lid's.
fn lid_edge(a: f32, lower: bool) -> f32 {
    if (lower) {
        return bottom_edge(a);
    }
    return top_edge(a);
}

// The open upper lid's height at a, or with lower set the open lower lid's.
fn open_edge(a: f32, lower: bool) -> f32 {
    if (lower) {
        return open_bottom(a);
    }
    return open_top(a);
}

// How level an open lid runs at a: 1 flat, 0 where its true slope passes STEEP_TO.
fn flatness(a: f32, lower: bool) -> f32 {
    let before = max(a - SLOPE_STEP, -1.0);
    let after = min(a + SLOPE_STEP, 1.0);
    let slope = abs(open_edge(after, lower) - open_edge(before, lower)) / ((after - before) * layer.lid_c.y);
    return 1.0 - eased(STEEP_FROM, STEEP_TO, slope);
}

// How much of its weight the mass keeps as the upper lid closes.
fn lid_closing() -> f32 {
    return 1.0 - CLOSE_THIN * clamp(layer.lid_pose.x, 0.0, 1.0);
}

// The upper lid mass's thickness at a along the lid, in heights.
fn lid_thickness(a: f32) -> f32 {
    let inward = 1.0 - (1.0 - layer.lid_a.y) * eased(LID_TAPER_FROM, LID_TAPER_TO, a);
    let back = 1.0 - (1.0 - BACK_END) * eased(BACK_TAPER_FROM, min(layer.lid_c.z, BACK_TAPER_FROM - SLOPE_STEP), a);
    return layer.lid_a.x * inward * back * flatness(a, false) * inner_point(a) * lid_closing();
}

// The smaller of a and b, rounded over k where they meet.
fn smooth_min(a: f32, b: f32, k: f32) -> f32 {
    let h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * FILLET_DEPTH;
}

// How far outside a stroke tapering from root_half at its root to a point at root + reach (x, y) lies, its way across bent by bend_x
// and its way up by bend_y, its width falling off as the way left raised to taper.
fn stroke_level(x: f32, y: f32, root: vec2<f32>, reach: vec2<f32>, root_half: f32, bend_x: f32, bend_y: f32, taper: f32) -> f32 {
    var best = 1000000.0;
    var start = vec2<f32>(0.0, 0.0);
    let q = vec2<f32>((x - root.x) * layer.lid_c.y, y - root.y);
    for (var i = 1; i <= STROKE_PIECES; i = i + 1) {
        let t = f32(i) / f32(STROKE_PIECES);
        let end = vec2<f32>((t + (t * t - t) * bend_x) * reach.x * layer.lid_c.y, (t + (t * t - t) * bend_y) * reach.y);
        let edge = end - start;
        let h = clamp(dot(q - start, edge) / max(dot(edge, edge), 0.000000000001), 0.0, 1.0);
        let at = (f32(i - 1) + h) / f32(STROKE_PIECES);
        let left = 1.0 - at;
        let spread = select(root_half * raised(left, taper), root_half * left, taper == 1.0);
        best = min(best, length(q - start - edge * h) - spread);
        start = end;
    }
    return best;
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

// Lash i of count rising out of the upper lid toward the back corner and curving up to a fine point, at (x, y) in widths and heights.
fn clump_level(i: i32, count: i32, x: f32, y: f32) -> f32 {
    let k = (f32(i) + 0.5) / f32(count);
    let root_x = layer.lid_c.z + LASH_START + 2.0 * layer.lid_c.x * k;
    let root_y = top_edge(root_x) + 0.5 * lid_thickness(root_x);
    let reach_length = layer.lid_b.w * LASH_REACH * (1.0 - LASH_SHORTER * k);
    let rise_angle = LASH_ANGLE_OUTER + (LASH_ANGLE_INNER - LASH_ANGLE_OUTER) * k - LASH_CLOSE_SWING * layer.lid_pose.x;
    let angle = radians(rise_angle);
    let reach = vec2<f32>(-reach_length * cos(angle) / layer.lid_c.y, reach_length * sin(angle));
    return stroke_level(x, y, vec2<f32>(root_x, root_y), reach, layer.lid_more.z, 0.0, LASH_BEND, LASH_TAPER);
}

// The flick, rooted across the upper band's end at its shoulder, running straight out past the back corner and lifting to its tip.
fn flick_level(x: f32, y: f32) -> f32 {
    let shoulder = layer.lid_c.z;
    let end = lid_thickness(shoulder);
    let root_y = top_edge(shoulder) + (end - LID_INSET) / 2.0;
    let reach = vec2<f32>(-1.0 - layer.lid_a.z - shoulder, layer.lid_a.w);
    return stroke_level(x, y, vec2<f32>(shoulder, root_y), reach, (end + LID_INSET) / 2.0, 0.0, 1.0, 1.0);
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
        let top_side = select(top.y, -top.y, abs(x) < 1.0 && y < top_edge(x));
        level = max(top_side - lid_thickness(top.x), -top_side - LID_INSET * inner_point(top.x) * flatness(top.x, false));
        if (layer.lid_a.z > 0.0) {
            level = smooth_min(level, flick_level(x, y), LID_FILLET);
        }
        let lower = layer.lid_b.x;
        let extent = layer.lid_b.y;
        if (lower > 0.0 && extent > 0.0) {
            let shoulder = layer.lid_c.w;
            let bottom = nearest(x, y, true);
            let bottom_side = select(bottom.y, -bottom.y, abs(x) < 1.0 && y > bottom_edge(x));
            let s = clamp((bottom.x - shoulder) / (2.0 * extent), 0.0, 1.0);
            let fade = raised(1.0 - s, LOWER_TAPER_POWER) * flatness(bottom.x, true);
            let band = max(bottom_side - lower * weight * fade * lid_closing(), -bottom_side - LID_INSET * fade);
            level = smooth_min(level, max(band, (bottom.x - shoulder - 2.0 * extent) * layer.lid_c.y), LID_FILLET);
        }
    }
    let count = i32(layer.lid_b.z + 0.5);
    if (count > 0 && layer.lid_b.w > 0.0) {
        let over_lid = top_edge(clamp(x, -1.0, 1.0)) - LID_INSET - y;
        for (var i = 0; i < count; i = i + 1) {
            level = smooth_min(level, max(clump_level(i, count, x, y), over_lid), LID_FILLET);
        }
    }
    return level * layer.opening.y;
}
"""

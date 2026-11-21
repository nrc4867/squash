package dev.chieppa.squash.render

/**
 * WGSL for paint on an eye's upper lid, as [dev.chieppa.squash.geom.ChartLidPaint] reads it, riding [EYE_SHAPE_WGSL]'s moving top edge:
 * with `lid_paint.y` 1 the lid's skin from that edge up to the open lid and `lid_paint.z` heights over it, fading out over `lid_paint.w`;
 * with 2 a mark from `lid_mark` and `lid_more`, drawn with [LID_MASS_WGSL]'s strokes.
 */
internal val LID_PAINT_WGSL = """
// How far outside the lid's own paint a point of the opening's chart lies, in chart units: its skin from the moving edge up to the open
// lid and on above it, or a mark rooted on the lid and kept off the open eye.
fn lid_paint_level(p: vec2<f32>) -> f32 {
    let o = layer.opening;
    let x = p.x / o.x;
    let y = p.y / o.y;
    let a = clamp(x, -1.0, 1.0);
    if (layer.lid_paint.y < 1.5) {
        let roof = open_top(a) + layer.lid_paint.z * max(1.0 - a * a, 0.0);
        return max(max(top_edge(a) - y, y - roof), (abs(x) - 1.0) * layer.lid_c.y) * o.y;
    }
    let mark = layer.lid_mark;
    let root = vec2<f32>(mark.x, top_edge(clamp(mark.x, -1.0, 1.0)) + mark.y);
    let stroke = stroke_level(x, y, root, mark.zw, layer.lid_more.x, 0.0, layer.lid_more.y, 1.0);
    return max(stroke, -almond_level(p) / o.y) * o.y;
}

// How much of the lid's skin paint shows at p: 1 at its moving edge, fading out over its fade above it and toward the corners.
fn lid_paint_share(p: vec2<f32>) -> f32 {
    let fade = layer.lid_paint.w;
    if (layer.lid_paint.y > 1.5 || fade <= 0.0) {
        return 1.0;
    }
    let x = p.x / layer.opening.x;
    let a = clamp(x, -1.0, 1.0);
    return (1.0 - eased(0.0, fade, p.y / layer.opening.y - top_edge(a))) * (1.0 - eased(CORNER_FADE_FROM, 1.0, abs(x)));
}
"""

package dev.chieppa.squash.fieldgpu

import dev.chieppa.squash.geom.field.RunRings

/** Threads in one workgroup of [FIELD_SHADER]. */
internal const val WORKGROUP = 64

/** Floats a ring takes in the rings buffer: middle, tangent, up, u, gap, widest radius and whether it has a section. */
internal const val RING_STRIDE = 16

/**
 * Members' own fields read in WGSL, one query a thread, as a swept solid reads its field over every pair of rings (see
 * [dev.chieppa.squash.geom.field.SweptSolid]) without its grid of likely pairs. Each ring is four vec4s; a member is its origin and ease,
 * its clip box's low corner and whether it is mirrored, and the box's high corner and its solid with whether it is clipped; a query is
 * (x, y, z, member | placed << 31), and its answers its field, u and v, three floats a query.
 */
internal val FIELD_SHADER = """
struct Solid { first: u32, count: u32, startPerU: f32, endPerU: f32 }
struct Params { count: u32, pad0: u32, pad1: u32, pad2: u32 }
struct Member { origin: vec4<f32>, low: vec4<f32>, high: vec4<f32> }

@group(0) @binding(0) var<storage, read> rings: array<vec4<f32>>;
@group(0) @binding(1) var<storage, read> radii: array<f32>;
@group(0) @binding(2) var<storage, read> vtab: array<f32>;
@group(0) @binding(3) var<storage, read> solids: array<Solid>;
@group(0) @binding(4) var<storage, read> queries: array<vec4<u32>>;
@group(0) @binding(5) var<storage, read_write> answers: array<f32>;
@group(0) @binding(6) var<storage, read> params: Params;
@group(0) @binding(7) var<storage, read> members: array<Member>;

const ANGLES: u32 = ${RunRings.ANGLES}u;
const TINY: f32 = 1e-12;
const TWO_PI: f32 = 6.2831855;
const HALF_PI: f32 = 1.5707964;
const PI_F: f32 = 3.1415927;

fn middleOf(ring: u32) -> vec3<f32> { return rings[ring * 4u].xyz; }
fn tangentOf(ring: u32) -> vec3<f32> { let a = rings[ring * 4u]; let b = rings[ring * 4u + 1u]; return vec3<f32>(a.w, b.x, b.y); }
fn upOf(ring: u32) -> vec3<f32> { let b = rings[ring * 4u + 1u]; let c = rings[ring * 4u + 2u]; return vec3<f32>(b.z, b.w, c.x); }
fn uOf(ring: u32) -> f32 { return rings[ring * 4u + 2u].y; }
fn gapOf(ring: u32) -> f32 { return rings[ring * 4u + 2u].z; }
fn reachOf(ring: u32) -> f32 { return rings[ring * 4u + 2u].w; }
fn hasV(ring: u32) -> bool { return rings[ring * 4u + 3u].x > 0.0; }

fn plane(p: vec3<f32>, ring: u32) -> f32 {
    let m = middleOf(ring);
    let t = tangentOf(ring);
    return (p.x - m.x) * t.x + (p.y - m.y) * t.y + (p.z - m.z) * t.z;
}

fn quickAtan2(x: f32, y: f32) -> f32 {
    let ax = abs(x);
    let ay = abs(y);
    if (ax == 0.0 && ay == 0.0) { return 0.0; }
    let swapped = ax > ay;
    var z: f32;
    if (swapped) { z = ay / ax; } else { z = ax / ay; }
    let z2 = z * z;
    var angle = z * (0.9998660 + z2 * (-0.3302995 + z2 * (0.1801410 + z2 * (-0.0851330 + z2 * 0.0208351))));
    if (swapped) { angle = HALF_PI - angle; }
    if (y < 0.0) { angle = PI_F - angle; }
    if (x < 0.0) { return -angle; }
    return angle;
}

fn slotOf(angle: f32) -> f32 {
    var at = angle / TWO_PI * f32(ANGLES);
    at = at - floor(at / f32(ANGLES)) * f32(ANGLES);
    return at;
}

fn radiusAt(ring: u32, a: u32, f: f32) -> f32 {
    return radii[ring * ANGLES + a] * (1.0 - f) + radii[ring * ANGLES + (a + 1u) % ANGLES] * f;
}

fn profile(a: f32, r: f32, a0: f32, r0: f32, a1: f32, r1: f32) -> f32 {
    let da = a1 - a0;
    let dr = r1 - r0;
    let sq = da * da + dr * dr;
    var s = 0.0;
    if (sq > TINY) { s = clamp(((a - a0) * da + (r - r0) * dr) / sq, 0.0, 1.0); }
    let x = a - (a0 + da * s);
    let y = r - (r0 + dr * s);
    return sqrt(x * x + y * y);
}

fn segmentSquared(p: vec3<f32>, ring: u32) -> f32 {
    let m0 = middleOf(ring);
    let d = middleOf(ring + 1u) - m0;
    let q = p - m0;
    let sq = d.x * d.x + d.y * d.y + d.z * d.z;
    var s = 0.0;
    if (sq > TINY) { s = clamp((q.x * d.x + q.y * d.y + q.z * d.z) / sq, 0.0, 1.0); }
    let e = q - d * s;
    return e.x * e.x + e.y * e.y + e.z * e.z;
}

// Pair i's field; x of the result is the field, y how far along, z the angle round.
fn pairField(p: vec3<f32>, b: u32, n: u32, i: u32, before: f32, after: f32) -> vec3<f32> {
    let o = b + i;
    let k = o + 1u;
    let inSlab = before >= 0.0 && after <= 0.0;
    let gap = gapOf(o);
    var t = 0.0;
    var along = 0.0;
    if (inSlab) {
        if (before - after > TINY) { t = before / (before - after); }
        along = t * gap;
    } else if (before < 0.0) {
        along = before;
    } else {
        t = 1.0;
        along = gap + after;
    }
    let s = 1.0 - t;
    var a = tangentOf(o) * s + tangentOf(k) * t;
    let al = sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
    if (al > 0.0) { a = a * (1.0 / al); } else { a = tangentOf(o); }
    let q = p - (middleOf(o) * s + middleOf(k) * t);
    let qa = q.x * a.x + q.y * a.y + q.z * a.z;
    let c = q - a * qa;
    let mixed = upOf(o) * s + upOf(k) * t;
    let ma = mixed.x * a.x + mixed.y * a.y + mixed.z * a.z;
    var up = mixed - a * ma;
    let ul = sqrt(up.x * up.x + up.y * up.y + up.z * up.z);
    if (ul > 0.0) { up = up * (1.0 / ul); } else { up = upOf(o); }
    let side = vec3<f32>(up.y * a.z - up.z * a.y, up.z * a.x - up.x * a.z, up.x * a.y - up.y * a.x);
    let r = sqrt(c.x * c.x + c.y * c.y + c.z * c.z);
    var angle = 0.0;
    if (r > 0.0) { angle = quickAtan2(c.x * side.x + c.y * side.y + c.z * side.z, c.x * up.x + c.y * up.y + c.z * up.z); }
    let slot = slotOf(angle);
    let at = u32(clamp(i32(slot), 0, i32(ANGLES) - 1));
    let f = slot - f32(at);
    let r0 = radiusAt(o, at, f);
    let r1 = radiusAt(k, at, f);
    var d = profile(along, r, 0.0, r0, gap, r1);
    if (i == 0u) { d = min(d, profile(along, r, 0.0, 0.0, 0.0, r0)); }
    if (i + 1u == n - 1u) { d = min(d, profile(along, r, gap, r1, gap, 0.0)); }
    if (inSlab && r < r0 + (r1 - r0) * t) { d = -d; }
    return vec3<f32>(d, along, angle);
}

fn wrapped(v: f32) -> f32 { return v - floor(v); }
fn around(a: f32, b: f32) -> f32 { let d = b - a; return d - floor(d + 0.5); }

fn vAt(ring: u32, angle: f32) -> f32 {
    let slot = slotOf(angle);
    let i = u32(clamp(i32(slot), 0, i32(ANGLES) - 1));
    let a = vtab[ring * ANGLES + i];
    return wrapped(a + around(a, vtab[ring * ANGLES + (i + 1u) % ANGLES]) * (slot - f32(i)));
}

@compute @workgroup_size($WORKGROUP)
fn main(@builtin(global_invocation_id) id: vec3<u32>) {
    if (id.x >= params.count) { return; }
    let query = queries[id.x];
    let member = members[query.w & 0x7fffffffu];
    let place = (query.w >> 31u) == 1u;
    let flags = bitcast<u32>(member.high.w);
    var p = bitcast<vec3<f32>>(query.xyz) - member.origin.xyz;
    if (member.low.w > 0.0) { p.z = -p.z; }
    let solid = solids[flags & 0x7fffffffu];
    let b = solid.first;
    let n = solid.count;
    var first = 0u;
    var nearest = 3.4e38;
    var previous = plane(p, b);
    for (var i = 0u; i + 1u < n; i++) {
        let next = plane(p, b + i + 1u);
        var past = 0.0;
        if (previous < 0.0) { past = -previous; } else if (next > 0.0) { past = next; }
        if (past < nearest) { nearest = past; first = i; }
        previous = next;
    }
    var best = pairField(p, b, n, first, plane(p, b + first), plane(p, b + first + 1u));
    var bestPair = first;
    var before = plane(p, b);
    for (var i = 0u; i + 1u < n; i++) {
        let after = plane(p, b + i + 1u);
        if (i != first) {
            let inSlab = before >= 0.0 && after <= 0.0;
            var skip = false;
            if (best.x <= 0.0) {
                skip = !inSlab;
            } else {
                var past = 0.0;
                if (before < 0.0) { past = -before; } else if (after > 0.0) { past = after; }
                let reach = best.x + max(reachOf(b + i), reachOf(b + i + 1u));
                skip = past > best.x || segmentSquared(p, b + i) > reach * reach;
            }
            if (!skip) {
                let f = pairField(p, b, n, i, before, after);
                if (f.x < best.x) { best = f; bestPair = i; }
            }
        }
        before = after;
    }
    var field = best.x;
    if ((flags >> 31u) == 1u) {
        let lo = member.low.xyz;
        let hi = member.high.xyz;
        field = max(field, max(max(lo.x - p.x, p.x - hi.x), max(max(lo.y - p.y, p.y - hi.y), max(lo.z - p.z, p.z - hi.z))));
    }
    answers[id.x * 3u] = field - member.origin.w;
    if (!place) { return; }
    let o = b + bestPair;
    let k = o + 1u;
    let gap = gapOf(o);
    let along = best.y;
    var t = 0.0;
    if (gap > TINY) { t = clamp(along / gap, 0.0, 1.0); }
    var u = uOf(o) + (uOf(k) - uOf(o)) * t;
    if (bestPair == 0u && along < 0.0) { u = uOf(o) + along / solid.startPerU; }
    else if (k == b + n - 1u && along > gap) { u = uOf(k) + (along - gap) / solid.endPerU; }
    var v = 0.0;
    let h0 = hasV(o);
    let h1 = hasV(k);
    if (!h0) { if (h1) { v = vAt(k, best.z); } }
    else if (!h1) { v = vAt(o, best.z); }
    else { let v0 = vAt(o, best.z); v = wrapped(v0 + around(v0, vAt(k, best.z)) * t); }
    answers[id.x * 3u + 1u] = u;
    answers[id.x * 3u + 2u] = v;
}
"""

package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.posed

/** How much of an arm's length from its root is its shoulder, which the body leaves where the pose put it. */
private const val SHOULDER_SHARE = 0.06f

/** Over how much more of the arm's length the push comes in to the whole of it, so the shoulder bends into it with no step. */
private const val EASE_SHARE = 0.08f

/** How far down the arm, as a share of its length, a ring looks for the body to leave: the upper arm, down to about the elbow. */
private const val UPPER_SHARE = 0.45f

/**
 * How deep in the body a ring of the arm may lie at the shoulder, as a share of the ring's own radius, and over how much more of the
 * arm's length that allowance falls to none: an arm hanging at the side presses into the body's side under the shoulder, where the
 * body hides the overlap.
 */
private const val ARMPIT_OVERLAP = 0.8f
private const val ARMPIT_SHARE = 0.3f

/** How far a point is carried at a time while it looks for the body's surface, how far at most, and how many halvings then find it. */
private const val STEP = 0.01f
private const val MAX_REACH = 0.6f
private const val HALVINGS = 8

/** How many times a ring is carried on while a point of it still lies inside, where the body curves round it. */
private const val PASSES = 4

/** Below this a direction has no length to carry a point along. */
private const val MIN_WAY = 0.2f

/** How far outside the body's surface a pushed point is left, in body units. */
private const val CLEARANCE = 0.006f

/**
 * This posed character with each arm the pose moves pushed out of the body it hangs from where they meet, so a lowered upper arm lies
 * on the body's surface rather than through it: every point of the arm past its shoulder that lies inside its parent is carried out
 * across the body to its surface, and each ring of the arm moves whole with its deepest point. Only the upper arm, to [UPPER_SHARE] of
 * its length, looks for the body, and under the shoulder it may stay a little inside it ([ARMPIT_OVERLAP]); below a pushed ring the
 * push lets go smoothly all the way to the arm's end, so the arm runs in one line from shoulder to wrist, the hand stays where the
 * pose put it, and a garment over the body hides what of the arm lies under it, as [hiddenAgain] decides. The garments' branches over the arm move with the arm's points under them, keeping their ease. A part
 * [rest] holds as this does, which the pose did not move, stays as it is.
 */
fun AssembledCharacter.withArmsOnBody(rest: AssembledCharacter): AssembledCharacter {
    val arms = parts.filter { it.isBodyPart && it.role == Role.ARM && rest.part(requireNotNull(it.id)) !== it }
    if (arms.isEmpty()) return this
    val replaced = HashMap<PlacedPart, PlacedPart>()
    for (arm in arms) {
        val parent = arm.socket?.parent?.let(::part) ?: continue
        val shift = pushOut(arm, listOf(parent)) ?: continue
        replaced[arm] = arm.moved(shift)
        for (branch in parts.filter { it.covering?.part == arm.id }) replaced[branch] = branch.movedOver(arm, shift)
    }
    if (replaced.isEmpty()) return this
    return AssembledCharacter(parts.map { replaced[it] ?: it }, sockets)
}

private val PlacedPart.isBodyPart: Boolean get() = id != null && covering == null && hanging == null

/** How far each vertex of [arm]'s mesh moves to leave [body], or null when none lies inside it deeper than it may. */
private fun pushOut(arm: PlacedPart, body: List<PlacedPart>): Array<Vec3>? {
    val shells = body.mapNotNull { part -> part.inside?.let { part to it } }
    if (shells.isEmpty()) return null
    val mesh = arm.surface.mesh
    val length = arm.surface.axisLength
    if (length <= 0f) return null
    val along = alongShares(arm)
    val across = body.first().let { it.turn * PartBuilder.axes(it.template).along }
    val inside = { at: Vec3 -> shells.any { (_, shell) -> at in shell } }
    val point = { v: Int -> arm.origin + mesh.position(v) }
    val ways = arrayOfNulls<Vec3>(mesh.vertexCount)
    for (v in 0 until mesh.vertexCount) {
        if (along[v] <= SHOULDER_SHARE || along[v] > UPPER_SHARE) continue
        val at = point(v)
        val under = shells.firstOrNull { (_, shell) -> at in shell }?.first ?: continue
        ways[v] = wayOut(nearestNormal(at, under), across)
    }
    if (ways.all { it == null }) return null
    val shift = Array(mesh.vertexCount) { v -> ways[v]?.let { way -> way * (distanceOut(point(v), way, inside) + CLEARANCE) } ?: Vec3.ZERO }
    var first = 0
    for (patch in arm.surface.patches) {
        if (patch.slices != null) shift.byRing(patch, first, along, ways, point, inside)
        first += patch.nu * patch.nv
    }
    if (shift.all { it == Vec3.ZERO }) return null
    for (v in shift.indices) shift[v] = shift[v] * smooth((along[v] - SHOULDER_SHARE) / EASE_SHARE)
    return shift
}

private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/**
 * How far along [arm] each vertex of its mesh lies, as a share of its length from its root: by its ring on a swept patch, by its
 * distance from the root on any other.
 */
private fun alongShares(arm: PlacedPart): FloatArray {
    val mesh = arm.surface.mesh
    val length = arm.surface.axisLength
    val shares = FloatArray(mesh.vertexCount) { mesh.position(it).length / length }
    var first = 0
    for (patch in arm.surface.patches) {
        if (patch.slices != null && patch.nu > 1) {
            for (ring in 0 until patch.nu) for (column in 0 until patch.nv) shares[first + patch.index(ring, column)] = ring.toFloat() / (patch.nu - 1)
        }
        first += patch.nu * patch.nv
    }
    return shares
}

/** How many rings either side of a ring its shift is spread over, as a share of the patch's rings. */
private const val RING_SPREAD = 1f / 16f

/**
 * These shifts with each ring of [patch], whose vertices start at [first], moved whole, so the arm keeps its section and lies against
 * the body rather than flattening on it. Each ring of the upper arm moves the way its points inside would leave on the whole, until
 * none of its points lies deeper inside than [allowance] lets it; then each takes the largest of its neighbours' within the spread,
 * and the mean of those, never less than its own. Every ring past one so moved takes that ring's shift falling smoothly to none at
 * the arm's end, the largest such, so the arm lets go of the body in one long curve with no corner at the elbow; a last mean over
 * the spread, kept even about each ring so the end stays put, smooths where one ring's fall takes over from another's.
 */
private fun Array<Vec3>.byRing(patch: Patch, first: Int, along: FloatArray, ways: Array<Vec3?>, point: (Int) -> Vec3, inside: (Vec3) -> Boolean) {
    val points = Array(patch.nu) { ring -> (0 until patch.nv).map { point(first + patch.index(ring, it)) } }
    val share = FloatArray(patch.nu) { along[first + patch.index(it, 0)] }
    val allowed = FloatArray(patch.nu) { ring -> allowance(share[ring], radiusOf(points[ring])) }
    val deep = { ring: Int, way: Vec3 -> { at: Vec3 -> inside(at + way * allowed[ring]) } }
    val rings = Array(patch.nu) { ring ->
        val members = (0 until patch.nv).map { first + patch.index(ring, it) }.filter { ways[it] != null }
        if (members.isEmpty()) return@Array Vec3.ZERO
        val sum = members.fold(Vec3.ZERO) { total, v -> total + requireNotNull(ways[v]) }
        if (sum.length == 0f) return@Array Vec3.ZERO
        val way = sum.normalized()
        if (points[ring].none(deep(ring, way))) Vec3.ZERO else settled(way * CLEARANCE, points[ring], deep(ring, way))
    }
    val reach = (patch.nu * RING_SPREAD).toInt().coerceAtLeast(1)
    fun around(ring: Int) = maxOf(0, ring - reach)..minOf(patch.nu - 1, ring + reach)
    fun meanOf(near: List<Vec3>) = near.fold(Vec3.ZERO) { sum, it -> sum + it } * (1f / near.size)
    val widest = Array(patch.nu) { ring -> around(ring).map { rings[it] }.maxBy { it.length } }
    val held = Array(patch.nu) { ring ->
        if (share[ring] <= SHOULDER_SHARE || share[ring] > UPPER_SHARE) return@Array Vec3.ZERO
        val mean = meanOf(around(ring).map { widest[it] })
        val start = if (mean.length < rings[ring].length) rings[ring] else mean
        if (start.length == 0f) start else settled(start, points[ring], deep(ring, start.normalized()))
    }
    val falling = Array(patch.nu) { ring ->
        (0..ring).filter { held[it].length > 0f }
            .map { from -> held[from] * fall((share[ring] - share[from]) / (1f - share[from]).coerceAtLeast(MIN_FALL)) }
            .maxByOrNull { it.length } ?: Vec3.ZERO
    }
    for (ring in 0 until patch.nu) {
        val even = minOf(reach, ring, patch.nu - 1 - ring)
        val mean = meanOf((ring - even..ring + even).map { falling[it] })
        val whole = if (mean.length < held[ring].length) held[ring] else mean
        for (column in 0 until patch.nv) this[first + patch.index(ring, column)] = whole
    }
}

/** The shortest stretch of the arm a pushed ring's shift falls to none over, as a share of its length. */
private const val MIN_FALL = 1e-4f

/** How deep a ring [share] of the way along the arm, of [radius], may lie in the body: [ARMPIT_OVERLAP] of it at the shoulder, none past the armpit. */
private fun allowance(share: Float, radius: Float): Float = ARMPIT_OVERLAP * radius * (1f - (share - SHOULDER_SHARE) / ARMPIT_SHARE).coerceIn(0f, 1f)

/** The mean distance of [points] from their middle. */
private fun radiusOf(points: List<Vec3>): Float {
    val middle = points.fold(Vec3.ZERO) { sum, it -> sum + it } * (1f / points.size)
    return points.sumOf { (it - middle).length.toDouble() }.toFloat() / points.size
}

/** [shift] carried on along itself until none of [points] moved by it lies inside, at most [PASSES] times. */
private fun settled(shift: Vec3, points: List<Vec3>, inside: (Vec3) -> Boolean): Vec3 {
    if (shift.length == 0f) return shift
    val way = shift.normalized()
    var moved = shift
    repeat(PASSES) {
        val further = points.maxOf { distanceOut(it + moved, way, inside) }
        if (further == 0f) return moved
        moved += way * (further + CLEARANCE)
    }
    return moved
}

/** Which way a point leaves the body whose surface nearest it faces [facing]: across the body, square to its axis [across]. */
private fun wayOut(facing: Vec3, across: Vec3): Vec3 {
    val square = facing - across * (facing dot across)
    return if (square.length > MIN_WAY) square.normalized() else facing
}

/** How far [point] goes along [way] to leave what [inside] holds; none when it is outside already, and at most [MAX_REACH]. */
private fun distanceOut(point: Vec3, way: Vec3, inside: (Vec3) -> Boolean): Float {
    if (!inside(point)) return 0f
    var outside = STEP
    while (inside(point + way * outside)) {
        outside += STEP
        if (outside > MAX_REACH) return MAX_REACH
    }
    var within = outside - STEP
    repeat(HALVINGS) {
        val middle = (within + outside) / 2f
        if (inside(point + way * middle)) within = middle else outside = middle
    }
    return outside
}

/** The normal of [part]'s mesh at its vertex nearest [point]. */
private fun nearestNormal(point: Vec3, part: PlacedPart): Vec3 {
    val mesh = part.surface.mesh
    var nearest = 0
    var best = Float.POSITIVE_INFINITY
    for (v in 0 until mesh.vertexCount) {
        val off = part.origin + mesh.position(v) - point
        val distance = off dot off
        if (distance < best) {
            best = distance
            nearest = v
        }
    }
    return mesh.normal(nearest)
}

private fun RenderMesh.normal(vertex: Int): Vec3 {
    val at = vertex * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
    return Vec3(vertices[at], vertices[at + 1], vertices[at + 2]).normalized()
}

/** This part with each vertex of its mesh moved by [shift]. */
private fun PlacedPart.moved(shift: Array<Vec3>): PlacedPart = showing(surface.posed(surface.mesh.shifted { shift[it] }), turn)

/** This branch of a garment over [arm] moved as the arm's vertex under each of its own moved. */
private fun PlacedPart.movedOver(arm: PlacedPart, shift: Array<Vec3>): PlacedPart {
    val covering = covering ?: return this
    val first = arm.surface.patches.take(covering.bodyPatch).sumOf { it.nu * it.nv }
    val armPatch = arm.surface.patches.getOrNull(covering.bodyPatch) ?: return this
    val own = surface.patches.firstOrNull() ?: return this
    return showing(
        surface.posed(
            surface.mesh.shifted { v ->
                if (v >= own.nu * own.nv) return@shifted Vec3.ZERO
                val ring = covering.bodyRings.getOrNull(v / own.nv) ?: return@shifted Vec3.ZERO
                val column = covering.bodyColumns.getOrNull(v % own.nv) ?: return@shifted Vec3.ZERO
                shift.getOrNull(first + armPatch.index(ring, column)) ?: Vec3.ZERO
            },
        ),
        turn,
    )
}

/** What share of a pushed ring's shift a ring [t] of the way from it to the arm's end keeps: all at the ring, leaving it level, none at the end. */
private fun fall(t: Float): Float = t.coerceIn(0f, 1f).let { 1f - it * it }

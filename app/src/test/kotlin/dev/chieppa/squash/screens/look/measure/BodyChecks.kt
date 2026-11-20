package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.Vec3

/** The side of the grid an arm's volume is counted in, in body units. */
private const val VOXEL = 0.012f

/**
 * How much of an arm's length from its shoulder is the join and the armpit, left out of what may not lie inside the torso: the push
 * onto the body lets an arm press into the body's side there, as an arm hanging at the side does.
 */
private const val ARMPIT_SHARE = 0.36f

/** How much of an arm's length from its shoulder is its join, which the strip at the shoulder hides. */
private const val JOIN_SHARE = 0.2f

/** How much of a digit's length from its tip counts as its tip. */
private const val TIP_SHARE = 0.3f

/** A part of the body, not a garment's branch, loose end or a part a garment owns. */
val PlacedPart.isBody: Boolean get() = id != null && covering == null && hanging == null && spanning == null && owner == null

private fun AssembledCharacter.bodyWith(role: Role) = parts.filter { it.isBody && it.role == role }

/**
 * How much of each arm, past the share of its length at the shoulder that is its join and armpit, lies inside the part it hangs from,
 * in cubic body units, by side: points of a grid inside both meshes.
 */
fun armInside(body: AssembledCharacter): Map<Side?, Float> = body.bodyWith(Role.ARM).associate { arm ->
    val parent = arm.socket?.parent?.let(body::part) ?: return@associate arm.side to 0f
    val inArm = Winding(arm)
    val inParent = Winding(parent)
    val box = arm.box ?: return@associate arm.side to 0f
    val join = arm.surface.axisLength * ARMPIT_SHARE
    var count = 0
    var x = box.low.x
    while (x <= box.high.x) {
        var y = box.low.y
        while (y <= box.high.y) {
            var z = box.low.z
            while (z <= box.high.z) {
                val p = Vec3(x, y, z)
                if ((p - arm.origin).length > join && inParent.inside(p) && inArm.inside(p)) count++
                z += VOXEL
            }
            y += VOXEL
        }
        x += VOXEL
    }
    arm.side to count * VOXEL * VOXEL * VOXEL
}

/**
 * How deep each arm lies inside the part it hangs from at its deepest, past its join, by side, in body units: the distance from its
 * deepest shown vertex inside that part to the part's nearest vertex.
 */
fun armDepthInside(body: AssembledCharacter): Map<Side?, Float> = body.bodyWith(Role.ARM).associate { arm ->
    val parent = arm.socket?.parent?.let(body::part) ?: return@associate arm.side to 0f
    val inParent = Winding(parent)
    val surface = (0 until parent.surface.mesh.vertexCount).map(parent::point)
    val join = arm.surface.axisLength * JOIN_SHARE
    val shown = (arm.hiding?.indices ?: arm.surface.mesh.indices).toSet()
    val deepest = shown.map(arm::point).filter { (it - arm.origin).length > join && inParent.inside(it) }
        .maxOfOrNull { p -> surface.minOf { (it - p).length } } ?: 0f
    arm.side to deepest
}

/** How far apart the legs' sockets sit, between the roots of a pair of legs, or null with no pair. */
fun hipSpread(body: AssembledCharacter): Float? {
    val legs = body.bodyWith(Role.LEG)
    val left = legs.firstOrNull { it.side == Side.LEFT } ?: return null
    val right = legs.firstOrNull { it.side == Side.RIGHT } ?: return null
    return (left.origin - right.origin).length
}

/** The smallest gap between the tips of neighbouring digits of each part with a digit row, by part, in body units. */
fun digitTipGaps(body: AssembledCharacter): Map<String, Float> = body.parts.filter { it.isBody }.mapNotNull { part ->
    val tips = digitTips(part)
    if (tips.size < 2) return@mapNotNull null
    val gaps = tips.keys.sorted().zipWithNext().mapNotNull { (a, b) ->
        val first = tips[a] ?: return@mapNotNull null
        val second = tips[b] ?: return@mapNotNull null
        first.minOf { p -> second.minOf { q -> (p - q).length } }
    }
    gaps.minOrNull()?.let { requireNotNull(part.id).value to it }
}.toMap()

/** Each digit's tip points in body space, by its index in its row: patches named `row:i`, their rings in the last [TIP_SHARE]. */
private fun digitTips(part: PlacedPart): Map<Int, List<Vec3>> {
    val out = mutableMapOf<Int, List<Vec3>>()
    var first = 0
    for (patch in part.surface.patches) {
        val index = patch.name.substringAfterLast(':', "").toIntOrNull()
        if (index != null && ':' in patch.name) {
            val from = (patch.nu * (1f - TIP_SHARE)).toInt()
            out[index] = (from until patch.nu).flatMap { ring -> (0 until patch.nv).map { part.point(first + patch.index(ring, it)) } }
        }
        first += patch.nu * patch.nv
    }
    return out
}

/** Which join a part's socket ring is, by the part that sits on it. */
fun joinName(part: PlacedPart): String? = when (part.role) {
    Role.NECK -> "neck"
    Role.HEAD -> "head"
    Role.ARM -> "shoulder"
    Role.LEG -> "hip"
    Role.HAND -> "wrist"
    Role.FOOT -> "ankle"
    else -> null
}

/**
 * The length of the curve where each part of the body sitting on another crosses its parent's surface, by join, in body units: a socket
 * ring.
 */
fun socketRings(body: AssembledCharacter, seen: (Vec3) -> Boolean): Map<String, Pair<Float, Float>> {
    val out = mutableMapOf<String, Pair<Float, Float>>()
    for (child in body.parts.filter { it.isBody }) {
        val join = joinName(child) ?: continue
        val parent = child.socket?.parent?.let(body::part) ?: continue
        val tube = child
        val inHost = Winding(parent)
        val mesh = tube.surface.mesh
        val inside = BooleanArray(mesh.vertexCount) { inHost.inside(tube.point(it)) }
        var all = 0f
        var shown = 0f
        val faces = mesh.indices
        for (t in 0 until faces.size / 3) {
            val corners = IntArray(3) { faces[t * 3 + it] }
            val crossings = (0..2).mapNotNull { e ->
                val a = corners[e]
                val b = corners[(e + 1) % 3]
                if (inside[a] == inside[b]) null else (tube.point(a) + tube.point(b)) * 0.5f
            }
            if (crossings.size != 2) continue
            val length = (crossings[0] - crossings[1]).length
            all += length
            if (seen((crossings[0] + crossings[1]) * 0.5f)) shown += length
        }
        val (a, s) = out[join] ?: (0f to 0f)
        out[join] = (a + all) to (s + shown)
    }
    return out
}

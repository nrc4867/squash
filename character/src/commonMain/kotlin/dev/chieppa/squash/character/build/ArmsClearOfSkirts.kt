package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.posed

/** The most an arm turns out at its shoulder to clear a branch hanging beside it, in radians: about 20 degrees. */
private const val MOST_TURN = 0.35f

/** How many halvings find the least turn that clears. */
private const val TURN_HALVINGS = 7

/** How far past clear an arm turns, as a share of the turn that just clears it. */
private const val TURN_MARGIN = 0.1f

/** Below this an arm has no length to turn. */
private const val MIN_LENGTH = 1e-4f

/**
 * This posed character with each arm the pose moves turned out at its shoulder, with all it carries, just far enough that none of it
 * lies inside a garment branch hanging free from the part the arm sits on: a hand lowered beside a skirt rests on its side rather than
 * through it. The turn is about the arm's root ring's middle, in the plane of the arm and the way out from the body, at most
 * [MOST_TURN]. A part [rest] holds as this does stays as it is; itself when no arm needs turning.
 */
fun AssembledCharacter.withArmsClearOfSkirts(rest: AssembledCharacter): AssembledCharacter {
    val skirts = parts.filter { it.covering?.hangsFree == true }
    if (skirts.isEmpty()) return this
    val replaced = HashMap<PlacedPart, PlacedPart>()
    for (arm in parts) {
        val id = arm.id ?: continue
        if (arm.role != Role.ARM || arm.covering != null || arm.hanging != null || rest.part(id) === arm) continue
        val parent = arm.socket?.parent ?: continue
        val shells = skirts.filter { it.covering?.part == parent }.mapNotNull { it.inside }
        if (shells.isEmpty()) continue
        val carried = carriedBy(id)
        val points = carried.flatMap { part -> part.pointsInBody() }
        val pivot = arm.rootMiddle() ?: continue
        val body = part(parent) ?: continue
        val axis = turnAxis(arm, pivot, body) ?: continue
        fun clear(angle: Float) = points.none { p -> turnPoint(p, pivot, axis, angle).let { at -> shells.any { at in it } } }
        if (clear(0f)) continue
        if (!clear(MOST_TURN)) continue
        var within = 0f
        var beyond = MOST_TURN
        repeat(TURN_HALVINGS) {
            val middle = (within + beyond) / 2f
            if (clear(middle)) beyond = middle else within = middle
        }
        val angle = minOf(beyond * (1f + TURN_MARGIN), MOST_TURN)
        for (part in carried) replaced[part] = part.turnedAbout(pivot, axis, angle)
    }
    if (replaced.isEmpty()) return this
    return AssembledCharacter(parts.map { replaced[it] ?: it }, sockets)
}

/** The part [arm] and everything that rides on it: the parts on its sockets and theirs, and every garment branch over any of them. */
private fun AssembledCharacter.carriedBy(arm: PartId): List<PlacedPart> {
    val ids = hashSetOf(arm)
    var grew = true
    while (grew) {
        grew = false
        for (part in parts) {
            val id = part.id ?: continue
            if (part.covering == null && part.socket?.parent in ids && ids.add(id)) grew = true
        }
    }
    return parts.filter { part -> part.id in ids || part.covering?.part in ids }
}

/** The middle of this part's first swept ring as it stands, in body space. */
private fun PlacedPart.rootMiddle(): Vec3? {
    val patch = surface.patches.firstOrNull { it.slices != null } ?: return null
    var sum = Vec3.ZERO
    for (c in 0 until patch.nv) sum += surface.shown(patch.point(patch.index(0, c)))
    return origin + sum * (1f / patch.nv)
}

/** The axis through [pivot] that turns [arm]'s far end straight away from [body]'s middle line, or null when it has no way to go. */
private fun turnAxis(arm: PlacedPart, pivot: Vec3, body: PlacedPart): Vec3? {
    val patch = arm.surface.patches.firstOrNull { it.slices != null } ?: return null
    var end = Vec3.ZERO
    for (c in 0 until patch.nv) end += arm.surface.shown(patch.point(patch.index(patch.nu - 1, c)))
    val reach = arm.origin + end * (1f / patch.nv) - pivot
    val middle = body.box?.let { (it.low + it.high) * 0.5f } ?: return null
    val out = Vec3(0f, 0f, pivot.z - middle.z)
    val axis = reach cross out
    return if (reach.length < MIN_LENGTH || axis.length < MIN_LENGTH) null else axis.normalized()
}

private fun turnPoint(p: Vec3, pivot: Vec3, axis: Vec3, angle: Float): Vec3 {
    if (angle == 0f) return p
    val cos = kotlin.math.cos(angle)
    val sin = kotlin.math.sin(angle)
    val v = p - pivot
    return pivot + v * cos + (axis cross v) * sin + axis * ((axis dot v) * (1f - cos))
}

/** Every vertex of this part's mesh in body space. */
private fun PlacedPart.pointsInBody(): List<Vec3> = List(surface.mesh.vertexCount) { origin + surface.mesh.position(it) }

/** This part turned [angle] radians about the line through [pivot] along [axis], in body space, with the turn of its root bone. */
private fun PlacedPart.turnedAbout(pivot: Vec3, axis: Vec3, angle: Float): PlacedPart {
    val mesh = surface.mesh.turnedAbout(pivot - origin, axis, angle)
    fun turn(v: Vec3) = turnPoint(v, Vec3.ZERO, axis, angle)
    return showing(surface.posed(mesh), Mat3(turn(turn.x), turn(turn.y), turn(turn.z)))
}

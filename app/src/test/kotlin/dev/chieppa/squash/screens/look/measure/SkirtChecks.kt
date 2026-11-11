package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.Vec3

/** The side of the grid a leg's volume is counted in, in body units. */
private const val VOXEL = 0.01f

/** How far below a leg's root a branch over its parent must reach, as a share of the leg's length, to hang over it as a skirt does. */
private const val SKIRT_REACH = 0.08f

/** A ring whose polygon spans less than this is a pole. */
private const val POLE_AREA = 1e-6f

/**
 * How much of each leg lies outside a skirt it should lie inside, in cubic body units, by side: points of a grid inside the leg's mesh,
 * on the inside of the planes of the skirt's waist and hem rings, and outside the skirt's shell. A skirt is a branch all round the part the leg hangs
 * from, of a garment with no branch over the leg, whose lowest ring reaches below the leg's root by [SKIRT_REACH] of the leg's length.
 * Legs under no skirt are left out.
 */
fun legOutsideSkirt(body: AssembledCharacter): Map<Side?, Float> = body.parts.filter { it.isBody && it.role == Role.LEG }.mapNotNull { leg ->
    val skirt = skirtOver(body, leg) ?: return@mapNotNull null
    leg.side to outsideVolume(leg, skirt)
}.toMap()

private fun skirtOver(body: AssembledCharacter, leg: PlacedPart): PlacedPart? {
    val parent = leg.socket?.parent ?: return null
    val reach = leg.surface.axisLength * SKIRT_REACH
    val legGarments = body.parts.mapNotNull { it.covering?.takeIf { c -> c.part == leg.id }?.garment }.toSet()
    return body.parts.filter { it.covering?.part == parent && it.covering?.allRound == true && it.covering?.garment !in legGarments }.firstOrNull { branch ->
        val ends = ringMiddles(branch)
        ends.isNotEmpty() && ends.minOf { it.y } < leg.origin.y - reach
    }
}

private fun outsideVolume(leg: PlacedPart, skirt: PlacedPart): Float {
    val patch = skirt.surface.patches.first()
    val inside = ShellInside(patch) { skirt.origin + skirt.surface.shown(it) }
    val middles = ringMiddles(skirt)
    val waist = middles.last()
    val hem = middles.first()
    val waistFacing = ringFacing(skirt, patch.nu - 1, hem - waist)
    val hemFacing = ringFacing(skirt, 0, waist - hem)
    val inLeg = Winding(leg)
    val box = leg.box ?: return 0f
    var count = 0
    var x = box.low.x
    while (x <= box.high.x) {
        var y = box.low.y
        while (y <= box.high.y) {
            var z = box.low.z
            while (z <= box.high.z) {
                val p = Vec3(x, y, z)
                if (((p - waist) dot waistFacing) >= 0f && ((p - hem) dot hemFacing) >= 0f && !inside.contains(p) && inLeg.inside(p)) count++
                z += VOXEL
            }
            y += VOXEL
        }
        x += VOXEL
    }
    return count * VOXEL * VOXEL * VOXEL
}

/** The normal of ring [ring]'s polygon, turned toward [toward]; [toward] itself for a pole. */
private fun ringFacing(branch: PlacedPart, ring: Int, toward: Vec3): Vec3 {
    val patch = branch.surface.patches.first()
    val points = (0 until patch.nv).map { branch.point(patch.index(ring, it)) }
    val middle = points.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / points.size)
    var normal = Vec3.ZERO
    for (j in points.indices) normal += (points[j] - middle) cross (points[(j + 1) % points.size] - middle)
    if (normal.length < POLE_AREA) return toward
    return if ((normal dot toward) < 0f) normal * -1f else normal
}

/** The middle of each ring of a branch's shell, in body space, ordered as its rings. */
private fun ringMiddles(branch: PlacedPart): List<Vec3> {
    val patch = branch.surface.patches.firstOrNull() ?: return emptyList()
    return (0 until patch.nu).map { ring ->
        (0 until patch.nv).fold(Vec3.ZERO) { sum, c -> sum + branch.point(patch.index(ring, c)) } * (1f / patch.nv)
    }
}

/**
 * How many vertices of each skirt's whole mesh, hidden or not, lie inside a leg it hangs over, by skirt: where a leg cuts the cloth.
 * Skirts as [legOutsideSkirt] finds them.
 */
fun skirtInsideLegs(body: AssembledCharacter): Map<String, Int> {
    val legs = body.parts.filter { it.isBody && it.role == Role.LEG }
    val skirts = legs.mapNotNull { leg -> skirtOver(body, leg)?.let { it to leg } }.groupBy({ it.first }, { it.second })
    return skirts.entries.associate { (skirt, under) ->
        val inLegs = under.map(::Winding)
        requireNotNull(skirt.id).value to (0 until skirt.surface.mesh.vertexCount).count { v -> skirt.point(v).let { p -> inLegs.any { it.inside(p) } } }
    }
}

/**
 * How many vertices of each arm, and of every part on it, lie inside a garment branch hanging free from the part the arm sits on, by
 * side: a hand lowered through a skirt. Arms beside no such branch are left out.
 */
fun armInsideSkirt(body: AssembledCharacter): Map<Side?, Int> = body.parts.filter { it.isBody && it.role == Role.ARM }.mapNotNull { arm ->
    val skirts = body.parts.filter { it.covering?.hangsFree == true && it.covering?.part == arm.socket?.parent }
    if (skirts.isEmpty()) return@mapNotNull null
    val shells = skirts.map { skirt -> ShellInside(skirt.surface.patches.first()) { skirt.origin + skirt.surface.shown(it) } }
    val ids = hashSetOf(requireNotNull(arm.id))
    var grew = true
    while (grew) {
        grew = false
        for (part in body.parts) if (part.isBody && part.socket?.parent in ids && ids.add(requireNotNull(part.id))) grew = true
    }
    arm.side to body.parts.filter { it.id in ids }.sumOf { part -> (0 until part.surface.mesh.vertexCount).count { v -> part.point(v).let { p -> shells.any { it.contains(p) } } } }
}.toMap()

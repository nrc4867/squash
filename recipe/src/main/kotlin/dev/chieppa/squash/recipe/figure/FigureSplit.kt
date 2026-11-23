package dev.chieppa.squash.recipe.figure

import kotlin.math.abs
import kotlin.math.acos

/** Which side of its bone a sample lies on, as the view shows it: [PLUS] is the side its bone's [ViewBone.plus] faces. */
enum class BoneSide { PLUS, MINUS }

/**
 * A sample of the outline given to one part: where along the part (its outline x), how far from its bone, on which side, on which bone,
 * and whether it lies in the crease inside a bent joint, which no width shapes.
 */
data class PartSample(val part: String, val side: BoneSide, val x: Double, val radius: Double, val sample: StrokeSample, val bone: ViewBone, val crease: Boolean = false)

private class Reading(val t: Double, val x: Double, val radius: Double, val side: BoneSide, val facesAway: Boolean)

private fun read(b: ViewBone, p: P2, normal: P2, clamp: Boolean = false): Reading {
    val d = b.to - b.from
    val len2 = (d dot d).takeIf { it > 0 } ?: 1.0
    val t = (((p - b.from) dot d) / len2).let { if (clamp) it.coerceIn(0.0, 1.0) else it }
    val off = p - (b.from + d * t)
    val side = if ((off dot b.plus) >= 0) BoneSide.PLUS else BoneSide.MINUS
    return Reading(t, b.xFrom + (b.xTo - b.xFrom) * t, off.length, side, (off dot normal) > 0)
}

/** A parent within this factor of its child's distance takes the sample: where a trunk and a limb's root lie about as near. */
private const val PARENT_MARGIN = 1.15

/** A parent within this far of its child's surface, in body units, takes the sample when the split reads the body's surfaces. */
private const val SURFACE_MARGIN = 0.002

private fun nearer(a: ViewBone, ra: Double, b: ViewBone, rb: Double): Boolean = when {
    b.parent == a.part -> ra <= rb * PARENT_MARGIN
    a.parent == b.part -> ra * PARENT_MARGIN < rb
    else -> ra < rb
}

private fun nearerSurface(a: ViewBone, da: Double, b: ViewBone, db: Double): Boolean = when {
    b.parent == a.part -> da <= db + SURFACE_MARGIN
    a.parent == b.part -> da + SURFACE_MARGIN < db
    else -> da < db
}

/** The radius a built body's part has on a side of one of its bones as a view shows it, at its outline x; null where it has none. */
fun interface BoneSurface {
    fun radius(bone: ViewBone, side: BoneSide, x: Double): Double?
}

/** The samples [splitByBones] gave a part, and those no bone took. */
class FigureSplit(val taken: List<PartSample>, val unread: List<StrokeSample>)

/** How far a sample read off [bone] as [r] lies from the surface [surface] gives that bone: from the bone itself where it gives none. */
private fun surfaceDistance(bone: ViewBone, r: Reading, surface: BoneSurface): Double =
    surface.radius(bone, r.side, r.x)?.let { abs(r.radius - it) } ?: r.radius

/** The bone whose stretch [p] lies along that lies nearest it, and how it reads there: by its posed surface when [surface] is given. */
private fun nearestBone(bones: List<ViewBone>, p: P2, normal: P2, surface: BoneSurface?, facing: Boolean): Pair<ViewBone, Reading>? {
    var best: Triple<ViewBone, Reading, Double>? = null
    for (bone in bones) {
        val r = read(bone, p, normal)
        if (r.t < -bone.before || r.t > 1 + bone.after || (facing && !r.facesAway)) continue
        val d = if (surface == null) r.radius else surfaceDistance(bone, r, surface)
        val current = best
        val wins = when {
            current == null -> true
            surface == null -> nearer(bone, d, current.first, current.third)
            else -> nearerSurface(bone, d, current.first, current.third)
        }
        if (wins) best = Triple(bone, r, d)
    }
    val corner = if (surface != null && facing) cornerBone(bones, p, normal, surface) else null
    val current = best
    if (corner != null && (current == null || nearerSurface(corner.first, corner.third, current.first, current.third))) return corner.first to corner.second
    return current?.let { it.first to it.second }
}

/**
 * The bone a sample round the outside of a bent joint lies nearest, past the end of the bone before the joint and before the start of the
 * one after it, as the front of a bent knee does: read about the joint, as far from it as it lies; with how far it lies from the
 * surface there, so it goes to the joint rather than to a part behind whose surface lies further.
 */
private fun cornerBone(bones: List<ViewBone>, p: P2, normal: P2, surface: BoneSurface): Triple<ViewBone, Reading, Double>? {
    var best: Triple<ViewBone, Reading, Double>? = null
    for (bone in bones) {
        val r = read(bone, p, normal)
        val atJoint = when {
            r.t > 1 -> bones.any { it.part == bone.part && it.xFrom == bone.xTo }
            r.t < 0 -> bones.any { it.part == bone.part && it.xTo == bone.xFrom }
            else -> false
        }
        if (!atJoint) continue
        val clamped = read(bone, p, normal, clamp = true)
        if (!clamped.facesAway) continue
        val d = surfaceDistance(bone, clamped, surface)
        if (best == null || d < best.third) best = Triple(bone, clamped, d)
    }
    return best
}

/** A joint bent less than this in a view, in radians, has no crease inside it there. */
internal const val CREASE_BEND = 0.5

/** A sample within this many of each bone's surface radii, at its middle, of a bent joint, on its inside, lies in the joint's crease. */
private const val CREASE_REACH = 1.6

/** The side of [bone] facing [toward]. */
private fun sideToward(bone: ViewBone, toward: P2): BoneSide = if ((toward dot bone.plus) >= 0) BoneSide.PLUS else BoneSide.MINUS

/**
 * The bone, and how it reads there, of the bent joint a sample lies in the crease inside of, as the back of a bent knee: on the inside
 * of both bones that meet there, nearer the joint along each than [CREASE_REACH] of the other bone's surface radius at its middle; of the two,
 * the one whose surface lies nearer. Null for a sample in no crease. A crease is its limb's whatever part's surface lies near it, since
 * neither bone's width shapes it, the two surfaces meeting in a corner wherever they meet; a key fills it by its bend.
 */
private fun creaseBone(bones: List<ViewBone>, p: P2, normal: P2, surface: BoneSurface): Pair<ViewBone, Reading>? {
    for (first in bones) {
        val second = bones.firstOrNull { it.part == first.part && it.xFrom == first.xTo && it !== first } ?: continue
        val joint = first.to
        val into = (first.to - first.from).unit()
        val out = (second.to - second.from).unit()
        if (acos((into dot out).coerceIn(-1.0, 1.0)) < CREASE_BEND) continue
        val firstSide = sideToward(first, out)
        val secondSide = sideToward(second, into * -1.0)
        val back = (p - joint) dot (into * -1.0)
        val along = (p - joint) dot out
        val firstRadius = surface.radius(first, firstSide, (first.xFrom + first.xTo) / 2) ?: continue
        val secondRadius = surface.radius(second, secondSide, (second.xFrom + second.xTo) / 2) ?: continue
        if (back < 0 || along < 0 || back > CREASE_REACH * secondRadius || along > CREASE_REACH * firstRadius) continue
        val onFirst = read(first, p, normal)
        val onSecond = read(second, p, normal)
        if (onFirst.side != firstSide || onSecond.side != secondSide) continue
        return if (surfaceDistance(first, onFirst, surface) <= surfaceDistance(second, onSecond, surface)) first to onFirst else second to onSecond
    }
    return null
}

/**
 * [samples] with each stroke's normals turned to face out from the surfaces it lies along: a stroke drawn either way round describes
 * the same side of the body, so its normals are turned over when most of its samples face into the bone whose surface lies nearest.
 */
private fun outward(samples: List<StrokeSample>, bones: List<ViewBone>, surface: BoneSurface): List<StrokeSample> {
    val flipped = samples.groupBy { it.stroke }.filterValues { stroke ->
        val votes = stroke.mapNotNull { s -> nearestBone(bones, s.at, s.normal, surface, facing = false)?.second?.facesAway }
        votes.count { !it } > votes.count { it }
    }.keys
    return samples.map { if (it.stroke in flipped) it.copy(normal = it.normal * -1.0) else it }
}

/**
 * Gives each sample to the nearest bone it can lie on: one whose stretch it lies along, within its reach past either end, and that its
 * outline faces away from, so the body's side under an arm goes to the trunk, not to the arm beside it; a trunk about as near as a limb
 * on it keeps the sample, as the seat does over a thigh's root seen from the side. Given the body's [surface], as a key's outline is
 * read on a body already built, each stroke first faces out from the surfaces it lies along, a sample in the crease inside a bent joint
 * goes to that joint's limb ([creaseBone]), and any other to the bone whose surface, not whose axis, lies nearest it: a stroke under a
 * raised arm describes the arm's underside, however near the trunk's axis.
 */
fun splitByBones(samples: List<StrokeSample>, bones: List<ViewBone>, surface: BoneSurface? = null): FigureSplit {
    val taken = mutableListOf<PartSample>()
    val unread = mutableListOf<StrokeSample>()
    for (sample in if (surface == null) samples else outward(samples, bones, surface)) {
        val crease = surface?.let { creaseBone(bones, sample.at, sample.normal, it) }
        val best = crease ?: nearestBone(bones, sample.at, sample.normal, surface, facing = true)
        if (best == null) {
            unread += sample
            continue
        }
        val (bone, r) = best
        taken += PartSample(bone.part, r.side, r.x, r.radius, sample, bone, crease = crease != null)
    }
    return FigureSplit(taken, unread)
}

/** Where along a stroke the part its samples belong to changes: each join between two parts' stretches, by the pair and the place. */
fun joinsOf(taken: List<PartSample>): List<Pair<Pair<String, String>, P2>> = taken.zipWithNext().filter { (a, b) ->
    a.sample.stroke == b.sample.stroke && a.part != b.part
}.map { (a, b) -> (a.part to b.part) to (a.sample.at + b.sample.at) * 0.5 }

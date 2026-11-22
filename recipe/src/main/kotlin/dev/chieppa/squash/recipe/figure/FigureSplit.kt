package dev.chieppa.squash.recipe.figure

/** Which side of its bone a sample lies on, as the view shows it: [PLUS] is toward the view's +x across the bone. */
enum class BoneSide { PLUS, MINUS }

/** A sample of the outline given to one part: where along the part (its outline x), how far from its bone, on which side, on which bone. */
data class PartSample(val part: String, val side: BoneSide, val x: Double, val radius: Double, val sample: StrokeSample, val bone: ViewBone)

private class Reading(val t: Double, val x: Double, val radius: Double, val side: BoneSide, val facesAway: Boolean)

private fun read(b: ViewBone, p: P2, normal: P2): Reading {
    val d = b.to - b.from
    val len2 = (d dot d).takeIf { it > 0 } ?: 1.0
    val t = ((p - b.from) dot d) / len2
    val off = p - (b.from + d * t)
    // Across the bone toward +x of the view: its direction turned a quarter that way.
    val across = if (d.y <= 0) P2(-d.y, d.x) else P2(d.y, -d.x)
    val side = if ((off dot across) >= 0) BoneSide.PLUS else BoneSide.MINUS
    return Reading(t, b.xFrom + (b.xTo - b.xFrom) * t, off.length, side, (off dot normal) > 0)
}

/** A parent within this factor of its child's distance takes the sample: where a trunk and a limb's root lie about as near. */
private const val PARENT_MARGIN = 1.15

private fun nearer(a: ViewBone, ra: Double, b: ViewBone, rb: Double): Boolean = when {
    b.parent == a.part -> ra <= rb * PARENT_MARGIN
    a.parent == b.part -> ra * PARENT_MARGIN < rb
    else -> ra < rb
}

/** The samples [splitByBones] gave a part, and those no bone took. */
class FigureSplit(val taken: List<PartSample>, val unread: List<StrokeSample>)

/**
 * Gives each sample to the nearest bone it can lie on: one whose stretch it lies along, within its reach past either end, and that its
 * outline faces away from, so the body's side under an arm goes to the trunk, not to the arm beside it; a trunk about as near as a limb
 * on it keeps the sample, as the seat does over a thigh's root seen from the side.
 */
fun splitByBones(samples: List<StrokeSample>, bones: List<ViewBone>): FigureSplit {
    val taken = mutableListOf<PartSample>()
    val unread = mutableListOf<StrokeSample>()
    for (sample in samples) {
        var best: Pair<ViewBone, Reading>? = null
        for (bone in bones) {
            val r = read(bone, sample.at, sample.normal)
            if (r.t < -bone.before || r.t > 1 + bone.after || !r.facesAway) continue
            if (best == null || nearer(bone, r.radius, best.first, best.second.radius)) best = bone to r
        }
        if (best == null) unread += sample else taken += PartSample(best.first.part, best.second.side, best.second.x, best.second.radius, sample, best.first)
    }
    return FigureSplit(taken, unread)
}

/** Where along a stroke the part its samples belong to changes: each join between two parts' stretches, by the pair and the place. */
fun joinsOf(taken: List<PartSample>): List<Pair<Pair<String, String>, P2>> = taken.zipWithNext().filter { (a, b) ->
    a.sample.stroke == b.sample.stroke && a.part != b.part
}.map { (a, b) -> (a.part to b.part) to (a.sample.at + b.sample.at) * 0.5 }

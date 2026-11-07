package dev.chieppa.squash.rig

import dev.chieppa.squash.document.Pose
import kotlin.math.atan2

/**
 * The bones a body curve bends, by the part they bend: the part [behind] the root part, such as a tail, the root part's own,
 * [spine], and the part [ahead], such as the head, its first bone the joint at its base; and the joints whose stretch the curve's
 * length sets, [stretched].
 */
class CurveBones(val behind: List<BoneName>, val spine: List<BoneName>, val ahead: List<BoneName>, val stretched: List<BoneName>)

/** The bones a body curve bends on [rig], or null for a rig with no body chain. */
fun curveBones(rig: Rig): CurveBones? {
    val chain = bodyChain(rig) ?: return null
    fun names(links: List<ChainLink>) = links.map { rig.bones[it.owner].name }.distinct()
    val behind = names(chain.links.take(chain.root)).reversed()
    val spine = names(chain.links.drop(chain.root).filterNot { it.ahead })
    val ahead = names(chain.links.filter { it.ahead })
    val stretched = chain.stretched.mapNotNull { bone -> rig.stretchedBy[bone].takeIf { it >= 0 }?.let { rig.bones[it].name } }.distinct()
    return CurveBones(behind, spine, ahead, stretched)
}

/**
 * How far a pose bends each part of the body chain, in degrees: the part behind, [tail], and the root part, [spine], by the sum of
 * their bones' tips; the joint at the base of the part ahead, [neck]; and how far that part, [head], points from where it rests,
 * seen from the side. A part the chain lacks is null.
 */
class ChainBends(val spine: Float, val neck: Float?, val head: Float?, val tail: Float?)

/** How far [pose] bends [rig]'s body chain; see [ChainBends]. Null for a rig with no body chain. */
fun chainBends(rig: Rig, pose: Pose): ChainBends? {
    val bones = curveBones(rig) ?: return null
    fun tip(name: BoneName) = pose.of(name.part, name.joint)?.degrees ?: 0f
    val head = bones.ahead.firstOrNull()?.let { name ->
        val i = rig.indexOf(name) ?: return@let null
        val rest = rig.bones[i].direction
        val now = boneFrames(rig, pose).turns[i] * rest
        (atan2(now.y, now.x) - atan2(rest.y, rest.x)).let(::wrapped) * RADIANS_TO_DEGREES
    }
    return ChainBends(bones.spine.sumOf { tip(it).toDouble() }.toFloat(), bones.ahead.firstOrNull()?.let(::tip), head, bones.behind.takeIf { it.isNotEmpty() }?.sumOf { tip(it).toDouble() }?.toFloat())
}

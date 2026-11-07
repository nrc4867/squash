package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.block.Joint
import dev.chieppa.squash.character.block.Landmark
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.PartTemplate

/** How many rings a joint needs, as the template lint counts them. */
private const val RINGS_PER_JOINT = 2

/** How near the part's end, in u, a joint must sit to be on it. */
private const val AT_END = 1e-3f

/**
 * This template with the joints and landmarks a part with [params] holds by hand ([HandJoints], [HandLandmarks]) as blocks of its
 * own. Its joints are listed in u order after its other blocks, and a part with a hand-made joint and no joint at its end gains the
 * [HandJoints.TIP] there, so each hand-made joint owns the span to the next; a generic template's fewest rings rise to what its
 * joints need. A part with none builds as its template says. Each
 * joint and landmark placed by hand is called [named] of its own name.
 */
fun PartTemplate.withHandAnatomy(params: Map<String, Float>, named: (String) -> String = { it }): PartTemplate {
    val joints = HandJoints.specs(params)
    val landmarks = HandLandmarks.specs(params)
    if (joints.isEmpty() && landmarks.isEmpty()) return this
    val own = blocks.filter { it.joints().isEmpty() }
    val made = joints.map { Joint(named(it.name), u = it.u, hinge = it.hinge, limits = it.limits, stretch = it.stretch) }
    val tip = if (joints.isNotEmpty() && blocks.none { block -> block.joints().any { it.u >= 1f - AT_END } }) {
        listOf(Joint(HandJoints.TIP, u = 1f, hinge = Hinge.X, limits = HandJoints.TIP_LIMITS))
    } else {
        emptyList()
    }
    val ordered = (blocks.filter { it.joints().isNotEmpty() } + made + tip).sortedBy { block -> block.joints().minOf { it.u } }
    val needed = ordered.sumOf { it.joints().size } * RINGS_PER_JOINT
    val limits = generic?.let { it.copy(rings = it.rings.copy(min = maxOf(it.rings.min, needed), max = maxOf(it.rings.max, needed))) }
    return copy(blocks = own + landmarks.map { Landmark(named(it.name), u = it.u, v = it.v) } + ordered, generic = limits)
}

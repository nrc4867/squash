package dev.chieppa.squash.rig

import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.PartTemplate

/**
 * A part's joints in u order, and which of them own a span of the part. Each joint owns the part from its u to the next
 * joint's; below the first joint the first owns it, and the last owns nothing when there are two or more, since it is the
 * part's end. This is the rule `PartBuilder.mesh` gives each vertex its bone by.
 */
internal class PartJoints(template: PartTemplate) {
    val specs: List<JointSpec> = template.blocks.flatMap { it.joints() }.sortedBy { it.u }

    val isEmpty: Boolean get() = specs.isEmpty()

    /** How many joints, from the first, own a span. */
    val owning: Int get() = if (specs.size <= 1) specs.size else specs.size - 1

    /** The joint owning the part at [u]. */
    fun ownerAt(u: Float): Int = (specs.count { it.u <= u } - 1).coerceIn(0, owning - 1)

    /** The joint that carries a socket at [u]: the last at or below it, so an end joint carries what sits on the part's end. */
    fun carrierAt(u: Float): Int = (specs.count { it.u <= u } - 1).coerceAtLeast(0)

    /** Where the span of the joint at [index] ends along u. */
    fun spanEnd(index: Int): Float = if (index + 1 < specs.size) specs[index + 1].u else 1f
}

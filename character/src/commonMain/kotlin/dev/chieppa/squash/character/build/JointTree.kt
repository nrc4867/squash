package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.DigitChain
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.PartTemplate

/**
 * A part's joints as a tree: its own [chain] along the axis in u order, and a chain per digit its toe rows built, each branching
 * from the chain's joint that owns the u where the digit starts. In the chain each joint owns the part from its u to the next
 * joint's; below the first the first owns it, and the last owns nothing when there are two or more, since it is the part's end.
 * A digit up to its first joint follows its branch point, and each of its joints owns the digit from its u to the next, the last
 * to the digit's tip. [specs] lists the chain, then each digit's joints in row order, and a joint's place there is the bone index
 * `PartBuilder.mesh` tags vertices with. See HLD: data-model, The rig.
 */
class JointTree(template: PartTemplate, patches: Collection<String>) {
    val chain: List<JointSpec> = template.blocks.flatMap { it.joints() }.sortedBy { it.u }

    /** The digits' chains; none on a part with no joints of its own, since a digit has nothing to branch from. */
    val digits: List<DigitChain> =
        if (chain.isEmpty()) emptyList() else patches.toSet().let { names -> template.blocks.flatMap { it.digitChains(names) } }

    val specs: List<JointSpec> = chain + digits.flatMap { it.joints }

    private val digitStart = IntArray(digits.size).also { starts ->
        var at = chain.size
        digits.forEachIndexed { i, digit ->
            starts[i] = at
            at += digit.joints.size
        }
    }

    private val byPatch = digits.withIndex().associate { (i, digit) -> digit.patch to i }

    private val riders: Map<String, Int> = patches.mapNotNull { patch ->
        template.blocks.firstNotNullOfOrNull { it.digitUnder(patch) }?.let(byPatch::get)?.let { patch to it }
    }.toMap()

    /** Whether the part has no joints along its axis, and so no bones. */
    val isEmpty: Boolean get() = chain.isEmpty()

    /** How many joints of the chain, from the first, own a span. */
    val owning: Int get() = if (chain.size <= 1) chain.size else chain.size - 1

    /** The chain's joint owning the part at [u]. */
    fun ownerAt(u: Float): Int = (chain.count { it.u <= u } - 1).coerceIn(0, (owning - 1).coerceAtLeast(0))

    /** The chain's joint that carries a socket at [u]: the last at or below it, so an end joint carries what sits on the part's end. */
    fun carrierAt(u: Float): Int = (chain.count { it.u <= u } - 1).coerceAtLeast(0)

    /** Where the span of the chain's joint at [index] ends along u. */
    fun spanEnd(index: Int): Float = if (index + 1 < chain.size) chain[index + 1].u else 1f

    /** The digit whose bones [patch] follows: the digit's own patch, or one riding on it, as a pad on a toe; null for the rest. */
    fun digitOf(patch: String): Int? = byPatch[patch] ?: riders[patch]

    /** The place in [specs] of joint [k] of digit [digit]. */
    fun indexOf(digit: Int, k: Int): Int = digitStart[digit] + k

    /** The chain's joint digit [digit] branches from, as a place in [specs]. */
    fun rootOf(digit: Int): Int = ownerAt(digits[digit].span.start)

    /** The place in [specs] of the joint a vertex of [patch] at [u] follows. */
    fun boneAt(patch: String, u: Float): Int {
        val digit = digitOf(patch) ?: return ownerAt(u)
        val k = digits[digit].joints.count { it.u <= u }
        return if (k == 0) rootOf(digit) else indexOf(digit, k - 1)
    }

    /** Where the span of joint [k] of digit [digit] ends along u: the next joint, or the digit's tip. */
    fun digitSpanEnd(digit: Int, k: Int): Float = digits[digit].joints.getOrNull(k + 1)?.u ?: digits[digit].span.end
}

package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.character.model.DigitChain
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.surface.JAW_JOINT
import dev.chieppa.squash.document.JawName

/** The patch row every shaped tooth of a mouth is named in, `teeth:<jaw>-<row>-<slot>-<side>`. */
const val TEETH_PATCH = "teeth"

/** The patch a shaped tongue is. */
const val TONGUE_PATCH = "tongue"

private const val SEPARATOR = ":"

/** Where along u a shaped tongue's patch runs, from its root; past any band a part's socket blends across. */
const val TONGUE_ROOT_U = 0.5f
const val TONGUE_TIP_U = 1f

/** Where along the tongue's run, as shares of it from its root, its joints sit: the first at the root, so the whole tongue turns. */
private val TONGUE_JOINT_SHARES = listOf(0.02f, 0.4f, 0.7f)

/** The degrees each of a tongue's joints turns within. */
private val TONGUE_LIMITS = Limits(-80f, 60f)

/** The name of the shaped tooth in slot [slot] of row [row] of [jaw], on the side [side] names. */
fun toothPatchName(jaw: JawName, row: Int, slot: Int, side: String): String = "$TEETH_PATCH$SEPARATOR${jaw.name.lowercase()}-$row-$slot-$side"

/** Whether [patch] is one of a mouth's shaped parts: a tooth or a tongue. */
fun isMouthPart(patch: String): Boolean = isTooth(patch) || patch == TONGUE_PATCH

/** Whether [patch] is a shaped tooth. */
fun isTooth(patch: String): Boolean = patch.startsWith(TEETH_PATCH + SEPARATOR)

/** Whether [patch] is a shaped tooth of the lower jaw, which rides the jaw. */
fun isLowerTooth(patch: String): Boolean = patch.startsWith("$TEETH_PATCH$SEPARATOR${JawName.LOWER.name.lowercase()}-")

/** The names of a shaped tongue's joints, from its root. */
val TONGUE_JOINTS: List<String> = TONGUE_JOINT_SHARES.indices.map { "$TONGUE_PATCH.${it + 1}" }

/**
 * The chains a part's shaped mouth parts in [patches] hang from its [chain] by, where the chain has a jaw: each lower tooth moves whole with
 * the jaw, and the tongue rides the jaw on joints of its own along its length. Upper teeth follow the part's own chain and need none.
 */
fun mouthPartChains(chain: List<JointSpec>, patches: Set<String>): List<DigitChain> {
    if (chain.none { it.name == JAW_JOINT }) return emptyList()
    val teeth = patches.filter(::isLowerTooth).sorted().map { DigitChain(it, 0, Span(0f, 1f), false, emptyList(), rigid = true, from = JAW_JOINT) }
    val tongue = if (TONGUE_PATCH in patches) {
        val joints = TONGUE_JOINT_SHARES.mapIndexed { k, share ->
            JointSpec(TONGUE_JOINTS[k], TONGUE_ROOT_U + (TONGUE_TIP_U - TONGUE_ROOT_U) * share, null, Hinge.X, TONGUE_LIMITS, stretch = true)
        }
        listOf(DigitChain(TONGUE_PATCH, 0, Span(TONGUE_ROOT_U, TONGUE_TIP_U), false, joints, from = JAW_JOINT))
    } else {
        emptyList()
    }
    return teeth + tongue
}

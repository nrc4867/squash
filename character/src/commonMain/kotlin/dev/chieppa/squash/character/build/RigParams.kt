package dev.chieppa.squash.character.build

private const val PREFIX = "rig."
private const val REACH_PREFIX = "${PREFIX}reach."

/**
 * What the Rig stage stores in a part's parameters, where pairs, overrides and undo already work, as the Move tool does. No part's
 * build reads them; the rig does. See HLD: data-model, The rig.
 */
object RigParams {
    /** How far the blend at joint [joint] reaches, as a factor of what the rig guesses. */
    fun reach(joint: String) = "$REACH_PREFIX$joint"

    /** The least and most a reach goes to, and the step the Bones list moves it by. */
    const val MIN_REACH = 0.2f
    const val MAX_REACH = 3f
    const val REACH_STEP = 0.1f

    /** The joint [name] sets the reach of, or null when it is not a reach. */
    fun reachJoint(name: String): String? = name.takeIf { it.startsWith(REACH_PREFIX) }?.removePrefix(REACH_PREFIX)

    fun isRig(name: String) = name.startsWith(PREFIX)
}

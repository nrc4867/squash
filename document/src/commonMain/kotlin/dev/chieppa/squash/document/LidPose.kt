package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** Where an eye's lids stand: its upper [upperClose] of the way down to the closed line and its lower [lowerRise] of the way up, 0 open and 1 shut. */
@Serializable
data class LidPose(val upperClose: Float = 0f, val lowerRise: Float = 0f) {
    init {
        require(upperClose in 0f..1f && lowerRise in 0f..1f) { "A lid stands between 0 and 1, not $upperClose, $lowerRise" }
    }

    companion object {
        /** Both lids open. */
        val OPEN = LidPose()

        /** Both lids on the closed line: a blink. */
        val BLINK = LidPose(1f, 1f)
    }
}

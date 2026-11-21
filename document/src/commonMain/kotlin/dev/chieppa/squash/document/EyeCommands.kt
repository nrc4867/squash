package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The farthest an eye's gaze reaches either way, as a share of its opening's half width or half height. */
const val MOST_GAZE = 1f

/** [document] with the painted eye [id] changed by [change], undone by setting it back. */
private fun changeEye(document: SquashDocument, id: String, change: (PaintedEye) -> PaintedEye): Applied<SquashDocument> {
    val old = requireNotNull(document.character.paintedEyes.firstOrNull { it.id == id }) { "No painted eye $id" }
    return SetPaintedEye(id, change(old)).apply(document).let { Applied(it.document, SetPaintedEye(id, old)) }
}

/** Makes the painted eye [id]'s opening [roundness] tall for its width, keeping its area (see [withRoundness]). */
@Serializable
@SerialName("setEyeRoundness")
data class SetEyeRoundness(val id: String, val roundness: Float) : DocumentCommand {
    init {
        require(roundness > 0f) { "A roundness is above 0, not $roundness" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> = changeEye(document, id) { it.withRoundness(roundness) }
}

/** Sets how far a view from the front sees the painted eye [id]'s front look, from 0, its own look at every angle, to 1 (see [withFrontAmount]). */
@Serializable
@SerialName("setEyeFromFront")
data class SetEyeFromFront(val id: String, val amount: Float) : DocumentCommand {
    init {
        require(amount in 0f..1f) { "From front is between 0 and 1, not $amount" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> = changeEye(document, id) { it.withFrontAmount(amount) }
}

/** Sets where the painted eye [id] looks: [forward] of its opening's half width toward the head's front and [up] of its half height. */
@Serializable
@SerialName("setEyeGaze")
data class SetEyeGaze(val id: String, val forward: Float, val up: Float) : DocumentCommand {
    init {
        require(forward in -MOST_GAZE..MOST_GAZE && up in -MOST_GAZE..MOST_GAZE) { "A gaze is within $MOST_GAZE either way, not $forward, $up" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> = changeEye(document, id) { it.copy(gazeForward = forward, gazeUp = up) }
}

/** Sets where the painted eye [id]'s lids stand: its upper [upperClose] of the way down to the closed line and its lower [lowerRise] of the way up. */
@Serializable
@SerialName("setEyeLids")
data class SetEyeLids(val id: String, val upperClose: Float, val lowerRise: Float) : DocumentCommand {
    init {
        require(upperClose in 0f..1f && lowerRise in 0f..1f) { "A lid stands between 0 and 1, not $upperClose, $lowerRise" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> = changeEye(document, id) { it.withLids(LidPose(upperClose, lowerRise)) }
}

/** Places the painted eye [id] at ([u], [v]) on its head with its opening [width] by [height], turned [tilt] degrees (see [framed]). */
@Serializable
@SerialName("setEyeFrame")
data class SetEyeFrame(val id: String, val u: Float, val v: Float, val width: Float, val height: Float, val tilt: Float) : DocumentCommand {
    init {
        require(width > 0f && height > 0f) { "An eye's opening is above 0 across and up, not $width by $height" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> = changeEye(document, id) { it.framed(u, v, width, height, tilt) }
}

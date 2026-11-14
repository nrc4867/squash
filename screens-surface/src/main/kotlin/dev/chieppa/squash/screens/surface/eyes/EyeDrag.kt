package dev.chieppa.squash.screens.surface.eyes

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.SetEyeFrame

/** How wide or high an eye's opening is dragged at least and at most, as a share of the head's u or v. */
val EYE_OPENING = 0.01f..0.15f

private const val HALF_TURN = 180f
private const val FULL_TURN = 360f

/** What a press on a painted eye's box takes: a corner, which resizes it, the turn handle, or the inside, which moves it. */
sealed interface EyeGrab {
    data class Corner(val index: Int) : EyeGrab
    data object Turn : EyeGrab
    data object Move : EyeGrab
}

/** What [box] offers under [pixel]: a corner or the turn handle [turnReach] above it within [reach] pixels, else the inside; null outside. */
fun grabAt(box: EyeBox, pixel: Offset, reach: Float, turnReach: Float): EyeGrab? {
    val corner = box.corners.withIndex().filter { (it.value - pixel).getDistance() <= reach }.minByOrNull { (it.value - pixel).getDistance() }
    return when {
        corner != null -> EyeGrab.Corner(corner.index)
        (box.turnHandle(turnReach) - pixel).getDistance() <= reach -> EyeGrab.Turn
        box.holds(pixel) -> EyeGrab.Move
        else -> null
    }
}

/**
 * A drag on [eye] through one side's [box], the [mirrored] side or its own, that took [grab] at [down], on the head at [downAt] for a
 * move. Each place it reaches is one command setting the eye's frame from where it started, so the drag is one step however long.
 */
class EyeDrag(private val eye: PaintedEye, private val box: EyeBox, private val grab: EyeGrab, private val mirrored: Boolean, private val down: Offset, private val downAt: Anchor?) {
    private val look = eye.look

    /** The command for the pointer at [pixel], over [at] on the head; null where it says nothing new. */
    fun commandAt(pixel: Offset, at: Anchor?): SetEyeFrame? = when (grab) {
        is EyeGrab.Corner -> box.ownOf(pixel)?.let { own ->
            frame(width = kotlin.math.abs(own.x).coerceIn(EYE_OPENING), height = kotlin.math.abs(own.y).coerceIn(EYE_OPENING))
        }
        EyeGrab.Turn -> {
            val from = box.angleOf(down)
            val to = box.angleOf(pixel)
            if (from == null || to == null) null else frame(tilt = wrapped(look.tilt + to - from))
        }
        EyeGrab.Move -> {
            val start = downAt
            if (start == null || at == null || at.part != start.part || at.patch != start.patch) {
                null
            } else {
                val across = (at.v - start.v) * if (mirrored) -1f else 1f
                frame(u = (eye.anchor.u + at.u - start.u).coerceIn(0f, 1f), v = (eye.anchor.v + across).coerceIn(0f, 1f))
            }
        }
    }

    private fun frame(u: Float = eye.anchor.u, v: Float = eye.anchor.v, width: Float = look.width, height: Float = look.height, tilt: Float = look.tilt) =
        SetEyeFrame(eye.id, u, v, width, height, tilt)

    private fun wrapped(degrees: Float): Float {
        var d = degrees % FULL_TURN
        if (d > HALF_TURN) d -= FULL_TURN
        if (d < -HALF_TURN) d += FULL_TURN
        return d
    }
}

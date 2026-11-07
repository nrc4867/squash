package dev.chieppa.squash.screens.rig

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.rig.BoneName
import kotlinx.coroutines.flow.MutableStateFlow

/** The brush's size on screen to start with, in pixels, and the range it moves in. */
const val SEAM_BRUSH_SIZE = 18f
const val MIN_SEAM_BRUSH = 4f
const val MAX_SEAM_BRUSH = 120f

/** The brush's strength to start with, from 0 to 1. */
const val SEAM_BRUSH_STRENGTH = 0.6f

/** A bone asked to wiggle; [serial] tells two asks for one bone apart. */
data class Wiggle(val bone: BoneName, val serial: Long)

/**
 * What the Rig stage remembers: the bone picked in Bones, which Paint weight paints for and the heat map shows, the joint under the
 * pointer, which the heat map shows ahead of it, the seam picked in Seams, the brush's size in pixels and strength, the wiggle
 * asked for and the pose it holds while it plays. None of it is saved or undone.
 */
@Stable
class RigEditState {
    var picked by mutableStateOf<BoneName?>(null)
    var hovered by mutableStateOf<BoneName?>(null)
    var seam by mutableStateOf<PartId?>(null)
    var brushSize by mutableStateOf(SEAM_BRUSH_SIZE)
    var strength by mutableStateOf(SEAM_BRUSH_STRENGTH)
    var wiggle by mutableStateOf<Wiggle?>(null)
        private set
    private var asked = 0L

    /** The pose a wiggle shows while it plays, held in place of the character's own; null when none plays. */
    val held = MutableStateFlow<Pose?>(null)

    /** The bone whose weight the heat map shows: the one under the pointer, or the picked one. */
    val heat: BoneName? get() = hovered ?: picked

    /** Asks [bone] to wiggle once. */
    fun wiggle(bone: BoneName) {
        wiggle = Wiggle(bone, ++asked)
    }

    /** Ends the wiggle playing, leaving the character as it rests. */
    fun stopWiggle() {
        wiggle = null
        held.value = null
    }

    fun clear() {
        picked = null
        hovered = null
        seam = null
        stopWiggle()
    }
}

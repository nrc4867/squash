package dev.chieppa.squash.character.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.MarkControls
import kotlinx.serialization.Serializable

/**
 * How far each control may go on a mouth of a style, after its scale, so the mouth stays whole from every view: a shape asking for more
 * gives way and goes only as far as its limit. 1 leaves a control free.
 */
@Serializable
@Doc("How far each control may go on this mouth, after its scale, so it stays whole from every view: a shape asking for more goes only this far. 1 leaves a control free.")
data class MouthLimits(
    @Doc("The most it opens.") @Range(min = 0.0, max = 1.0) val open: Float = 1f,
    @Doc("The most it draws its corners out.") @Range(min = 0.0, max = 1.0) val wideOut: Float = 1f,
    @Doc("The most it draws its corners in.") @Range(min = 0.0, max = 1.0) val wideIn: Float = 1f,
    @Doc("The most it puckers.") @Range(min = 0.0, max = 1.0) val round: Float = 1f,
    @Doc("The most it lifts or drops its corners.") @Range(min = 0.0, max = 1.0) val smile: Float = 1f,
    @Doc("The most it raises its upper lip.") @Range(min = 0.0, max = 1.0) val upperLip: Float = 1f,
    @Doc("The most it tucks or presses its lower lip.") @Range(min = 0.0, max = 1.0) val lowerLip: Float = 1f,
    @Doc("The most it lifts or puts out its tongue.") @Range(min = 0.0, max = 1.0) val tongue: Float = 1f,
) {
    /** [controls] held to these limits. */
    fun of(controls: MarkControls): MarkControls = controls.copy(
        open = controls.open.coerceIn(0f, open),
        wide = controls.wide.coerceIn(-wideIn, wideOut),
        round = controls.round.coerceIn(0f, round),
        smile = controls.smile.coerceIn(-smile, smile),
        upperLip = controls.upperLip.coerceIn(0f, upperLip),
        lowerLip = controls.lowerLip.coerceIn(-lowerLip, lowerLip),
        tongue = controls.tongue.coerceIn(0f, tongue),
    )
}

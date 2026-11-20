package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A nail or a claw at each digit's tip of a [Digits] row: an oval painted on the digit's back, a nail; with a [claw] length, a point
 * standing out past the tip of digits joined to a field, painted from the nail's start to its point.
 */
@Serializable
@Doc("A nail or a claw at each digit's tip: an oval on the digit's back centred at a share of its length, so long along it and so wide round it, in a palette slot; a claw stands out past the tip as a point and is painted to it.")
data class DigitNail(
    @Doc("The area's name; each digit's is this with its place after it.")
    val name: String = "nail",
    @SerialName("if")
    @Doc("The boolean parameter that switches the nails or claws on.")
    val condition: ParamRef? = null,
    @Doc("Its middle along the digit, from 0 at the root to 1 at the tip.")
    val at: Float,
    @Doc("How long it runs along the digit, as a share of the digit's length.")
    val length: Float,
    @Doc("How far round the digit it reaches, as a share of a turn.")
    val width: Float,
    @Doc("The palette slot it shows until it is filled.")
    val color: String,
    @Doc("How far a claw stands out past the tip, as a share of the digit's length, up to 2; 0 for a nail.")
    val claw: Param = Param.of(0f),
    @Doc("How far a claw's point curls toward the digit's underside, as a share of the claw's length, -1 to 1.")
    val curl: Param = Param.of(0f),
) {
    fun isOn(values: Map<String, Float>): Boolean = condition?.let { values[it.name] != 0f } ?: true
}

/**
 * A pad under each digit's tip of a [Digits] row, a toe bean: an oval painted on the digit's underside; on digits joined to a field,
 * with a [rise], a ball under the tip that the field's smooth union joins in.
 */
@Serializable
@Doc("A pad under each digit's tip: an oval on the digit's underside centred at a share of its length, so long along it and so wide round it, in a palette slot; with a rise, a ball raised under it on digits joined to a field.")
data class DigitPad(
    @Doc("The area's name; each digit's is this with its place after it.")
    val name: String = "bean",
    @SerialName("if")
    @Doc("The boolean parameter that switches the pads on.")
    val condition: ParamRef? = null,
    @Doc("Its middle along the digit, from 0 at the root to 1 at the tip.")
    val at: Float,
    @Doc("How long it runs along the digit, as a share of the digit's length.")
    val length: Float,
    @Doc("How far round the digit it reaches, as a share of a turn.")
    val width: Float,
    @Doc("The palette slot it shows until it is filled.")
    val color: String,
    @Doc("On digits joined to a field, the radius of the ball raised under it, as a share of the digit's radius; 0 paints it only.")
    val rise: Param = Param.of(0f),
) {
    fun isOn(values: Map<String, Float>): Boolean = condition?.let { values[it.name] != 0f } ?: true
}

/** This number with [values], or 0 where it names a parameter [values] lacks, as when only the names of areas are wanted. */
internal fun Param.or(values: Map<String, Float>): Float = refName?.let { (values[it] ?: 0f) * factor } ?: resolve(values)

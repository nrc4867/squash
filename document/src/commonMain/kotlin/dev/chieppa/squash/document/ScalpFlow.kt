package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How far either way round the head a whorl or a cowlick may sit, in degrees from the front. */
const val MOST_FLOW_AROUND = 180f

/** The lowest and highest up the head a whorl or a cowlick may sit, in degrees from level. */
const val LOWEST_FLOW_UP = 0f
const val HIGHEST_FLOW_UP = 90f

/** The most cowlicks one head's flow holds. */
const val MOST_COWLICKS = 8

/** Whether hair splits along a line, and where. */
@Serializable
enum class PartingKind {
    @SerialName("none") NONE,
    @SerialName("centre") CENTRE,
    @SerialName("side") SIDE,
}

/**
 * The point on the crown hair spirals out from: [around] degrees round the head from the front toward the character's right and [up]
 * degrees up it from level, turning [turn] (positive clockwise seen from above, 0 straight out), with [strength] over the flow round it
 * and reaching [radius] degrees across the head.
 */
@Serializable
@Doc("The crown point hair spirals out from.")
data class Whorl(
    @Doc("Degrees round the head from the front, positive toward the character's right: 180 is the back.") @Range(min = -180.0, max = 180.0)
    val around: Float = 180f,
    @Doc("Degrees up the head from level: 90 is the top.") @Range(min = 0.0, max = 90.0)
    val up: Float = 68f,
    @Doc("How far hair turns round the whorl as it leaves it, -1 to 1: positive clockwise seen from above, 0 straight out.") @Range(min = -1.0, max = 1.0)
    val turn: Float = 0.45f,
    @Doc("How strongly it rules the hair round it, 0 not at all to 2.") @Range(min = 0.0, max = 2.0)
    val strength: Float = 1f,
    @Doc("How far across the head it reaches, in degrees.") @Range(min = 10.0, max = 120.0)
    val radius: Float = 50f,
) {
    init {
        require(around in -MOST_FLOW_AROUND..MOST_FLOW_AROUND && up in LOWEST_FLOW_UP..HIGHEST_FLOW_UP) { "A whorl sits -180 to 180 round and 0 to 90 up, not $around, $up" }
        require(turn in -1f..1f && strength in 0f..2f && radius in 10f..120f) { "A whorl's turn is -1 to 1, its strength 0 to 2 and its radius 10 to 120" }
    }
}

/**
 * A line hair splits along, lying away from it to each side: [kind] none, centre or side; [at] how far across the top it runs, -1 to 1 of
 * the most a part sits off the middle, positive toward the character's right; [length] how far back from the hairline it runs, 1 to the
 * crown; [clean] how sharp the split is. Roots keep off it, so the scalp shows along it as a thin line in [color], when left out a scalp
 * tone, the head's colour darkened toward the hair's.
 */
@Serializable
@Doc("A parting: a line where hair splits and lies away to each side, the scalp showing along it as a thin line.")
data class Parting(
    @Doc("none, centre or side.")
    val kind: PartingKind = PartingKind.NONE,
    @Doc("How far across the top it runs, -1 to 1 of the most a part sits off the middle (about the outer corner of the eye): positive toward the character's right.") @Range(min = -1.0, max = 1.0)
    val at: Float = 0f,
    @Doc("How far back from the hairline it runs: 1 reaches the crown.") @Range(min = 0.2, max = 1.5)
    val length: Float = 1f,
    @Doc("How clean the split is, 0 a soft, wandering split to 1 a sharp, straight line.") @Range(min = 0.0, max = 1.0)
    val clean: Float = 0.8f,
    @Doc("The palette slot of the scalp along it; when left out, a scalp tone: the head's colour darkened toward the hair's.")
    val color: SlotName? = null,
) {
    init {
        require(at in -1f..1f && clean in 0f..1f) { "A parting's place is -1 to 1 and its cleanness 0 to 1" }
        require(length in 0.2f..1.5f) { "A parting's length is 0.2 to 1.5, not $length" }
    }

    /** Whether there is a line at all. */
    val shown: Boolean get() = kind != PartingKind.NONE
}

/**
 * A small local swirl or flip at a point: [around] and [up] in degrees as a whorl's, reaching [radius] degrees, [strength] over the flow
 * round it, turning [turn] (0 flips the hair back against the flow, 1 or -1 swirls it round the point) and lifting the locks over it
 * [lift] degrees off the head.
 */
@Serializable
@Doc("A cowlick: a small swirl or flip at a point, as a fringe flicking up at the hairline.")
data class Cowlick(
    @Doc("Degrees round the head from the front, positive toward the character's right.") @Range(min = -180.0, max = 180.0)
    val around: Float = 15f,
    @Doc("Degrees up the head from level.") @Range(min = 0.0, max = 90.0)
    val up: Float = 48f,
    @Doc("How far across the head it reaches, in degrees.") @Range(min = 4.0, max = 60.0)
    val radius: Float = 16f,
    @Doc("How strongly it rules the hair over it, 0 not at all to 2.") @Range(min = 0.0, max = 2.0)
    val strength: Float = 1f,
    @Doc("0 flips the hair back against the flow; 1 swirls it clockwise round the point seen from outside, -1 the other way.") @Range(min = -1.0, max = 1.0)
    val turn: Float = 0.5f,
    @Doc("Degrees the locks over it lift off the head, most at its middle.") @Range(min = 0.0, max = 60.0)
    val lift: Float = 20f,
) {
    init {
        require(around in -MOST_FLOW_AROUND..MOST_FLOW_AROUND && up in LOWEST_FLOW_UP..HIGHEST_FLOW_UP) { "A cowlick sits -180 to 180 round and 0 to 90 up, not $around, $up" }
        require(radius in 4f..60f && strength in 0f..2f && turn in -1f..1f && lift in 0f..60f) { "A cowlick's radius is 4 to 60, strength 0 to 2, turn -1 to 1 and lift 0 to 60" }
    }
}

/**
 * Which way hair grows over a head's scalp ([on]): from its [whorl], away from its [parting], combed [sweep] toward the character's right
 * and [sweepBack] toward the back over the top, and swirled or flipped by its [cowlicks]. Every lock root takes its growth from it unless
 * its group says otherwise.
 */
@Serializable
@Doc("Which way hair grows over the scalp: a whorl, a parting, an overall sweep and cowlicks. Each lock's root takes its direction from it.")
data class ScalpFlow(
    @Doc("The head it lies on, by part id.")
    val on: PartId,
    val whorl: Whorl = Whorl(),
    val parting: Parting = Parting(),
    @Doc("An overall comb toward one side, -1 to 1: positive toward the character's right; strongest over the top.") @Range(min = -1.0, max = 1.0)
    val sweep: Float = 0f,
    @Doc("An overall comb back over the top, -1 to 1: positive toward the back, negative forward.") @Range(min = -1.0, max = 1.0)
    val sweepBack: Float = 0f,
    @Doc("Small local swirls or flips.")
    val cowlicks: List<Cowlick> = emptyList(),
) {
    init {
        require(sweep in -1f..1f && sweepBack in -1f..1f) { "A flow's sweeps are -1 to 1" }
        require(cowlicks.size <= MOST_COWLICKS) { "A flow holds at most $MOST_COWLICKS cowlicks, not ${cowlicks.size}" }
    }
}

/** The flow over the head [head], or the default flow when the character gives it none. */
fun Character.scalpFlowOn(head: PartId): ScalpFlow = scalpFlows.firstOrNull { it.on == head } ?: ScalpFlow(head)

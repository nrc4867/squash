package dev.chieppa.squash.character.mouth

/**
 * Where a mouth's teeth sit, in its flat frame (x across the face, y down it), as its controls hold it now: the one place tooth placement
 * reads, so a mouth carved into the head can seat its rows on its own walls by giving another seat.
 */
interface TeethSeat {
    /** The middle of the mouth across. */
    val middle: Float

    /** The mouth's width, which tooth sizes are shares of. */
    val width: Float

    /** How far from [middle] the mouth's line reaches on the side of [sign] (1 toward +x, -1 toward -x); no slot sits past it. */
    fun reach(sign: Int): Float

    /** Where the upper jaw's line runs at [x]: its teeth hang down from it, and stay as the mouth opens. */
    fun upperLine(x: Float): Float

    /** Where the lower jaw's line runs at [x]: its teeth stand up from it, and drop with the jaw. */
    fun lowerLine(x: Float): Float

    /** How many flat units up or down a share of the mouth's width of height takes at [x], so a tooth there keeps its height on the head. */
    fun heightScale(x: Float): Float = 1f
}

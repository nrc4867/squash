package dev.chieppa.squash.document

import kotlin.math.sqrt

/** The share of the way at which a blend between two looks takes the second's switches and slots. */
private const val HALFWAY = 0.5f

/** How tall the opening is for its width: 1 is round, less is wider than tall. */
val EyeLook.roundness: Float get() = height / width

/** This look with its opening [roundness] tall for its width, keeping its area. */
fun EyeLook.withRoundness(roundness: Float): EyeLook {
    require(roundness > 0f) { "A roundness is above 0, not $roundness" }
    val area = width * height
    return copy(width = sqrt(area / roundness), height = sqrt(area * roundness))
}

/** This look grown [across] times along the head's u and [up] times along its v: the opening, the iris and where they sit. */
fun EyeLook.scaled(across: Float, up: Float): EyeLook = copy(
    width = width * across,
    height = height * up,
    irisSize = irisSize * sqrt(across * up),
    irisForward = irisForward * across,
    irisUp = irisUp * up,
    browRaise = browRaise * up,
)

/** This look [t] of the way to [to]: each length and turn between the two, the switches and slots the nearer one's. */
fun EyeLook.toward(to: EyeLook, t: Float): EyeLook {
    fun mix(a: Float, b: Float) = a + (b - a) * t
    val near = if (t >= HALFWAY) to else this
    return near.copy(
        width = mix(width, to.width),
        height = mix(height, to.height),
        lowerLid = mix(lowerLid, to.lowerLid),
        frontNarrowing = mix(frontNarrowing, to.frontNarrowing),
        tilt = mix(tilt, to.tilt),
        openingTilt = mix(openingTilt, to.openingTilt),
        lookOut = mix(lookOut, to.lookOut),
        irisSize = mix(irisSize, to.irisSize),
        irisRatio = mix(irisRatio, to.irisRatio),
        irisForward = mix(irisForward, to.irisForward),
        irisUp = mix(irisUp, to.irisUp),
        pupilSize = mix(pupilSize, to.pupilSize),
        highlightSize = mix(highlightSize, to.highlightSize),
        highlightForward = mix(highlightForward, to.highlightForward),
        highlightUp = mix(highlightUp, to.highlightUp),
        browRaise = mix(browRaise, to.browRaise),
        browTilt = mix(browTilt, to.browTilt),
        topFlatness = mix(topFlatness, to.topFlatness),
        outerCorner = mix(outerCorner, to.outerCorner),
        lidMass = near.lidMass.copy(
            weight = mix(lidMass.weight, to.lidMass.weight),
            inner = mix(lidMass.inner, to.lidMass.inner),
            flick = mix(lidMass.flick, to.lidMass.flick),
            flickLift = mix(lidMass.flickLift, to.lidMass.flickLift),
            lower = mix(lidMass.lower, to.lidMass.lower),
            lowerExtent = mix(lidMass.lowerExtent, to.lidMass.lowerExtent),
        ),
        lashClumps = near.lashClumps.copy(
            length = mix(lashClumps.length, to.lashClumps.length),
            spread = mix(lashClumps.spread, to.lashClumps.spread),
            width = mix(lashClumps.width, to.lashClumps.width),
        ),
        lids = near.lids.copy(
            closedAt = mix(lids.closedAt, to.lids.closedAt),
            closedBend = mix(lids.closedBend, to.lids.closedBend),
            closedTilt = mix(lids.closedTilt, to.lids.closedTilt),
            fade = mix(lids.fade, to.lids.fade),
            above = mix(lids.above, to.lids.above),
        ),
        irisShade = near.irisShade.copy(
            upper = mix(irisShade.upper, to.irisShade.upper),
            darken = mix(irisShade.darken, to.irisShade.darken),
            soft = mix(irisShade.soft, to.irisShade.soft),
            lower = mix(irisShade.lower, to.irisShade.lower),
            lighten = mix(irisShade.lighten, to.irisShade.lighten),
            rim = mix(irisShade.rim, to.irisShade.rim),
        ),
        lidShadow = near.lidShadow.copy(depth = mix(lidShadow.depth, to.lidShadow.depth), share = mix(lidShadow.share, to.lidShadow.share)),
    )
}

/** How tall the eye's opening is for its width, in its own look. */
val PaintedEye.roundness: Float get() = look.roundness

/** This eye with its look's opening [roundness] tall for its width, its front look's made rounder or less round by as much. */
fun PaintedEye.withRoundness(roundness: Float): PaintedEye {
    val by = roundness / look.roundness
    return copy(look = look.withRoundness(roundness), front = front?.let { it.withRoundness(it.roundness * by) })
}

/** How far a view from the front sees the eye's front look, 0 where it has none. */
val PaintedEye.frontAmount: Float get() = if (front == null) 0f else fromFront

/**
 * This eye seen [amount] of the way to its front look from the front. An eye with no front look and an amount above 0 gains one: its look
 * with an opening as tall as it is wide, of the same area.
 */
fun PaintedEye.withFrontAmount(amount: Float): PaintedEye {
    val shape = front ?: if (amount > 0f) look.withRoundness(1f) else null
    return copy(front = shape, fromFront = amount)
}

/** The look a view from the front shows: none at 0 of the way, the front look itself at all of it, else between. */
fun PaintedEye.shownFront(): EyeLook? {
    val shape = front ?: return null
    return when {
        fromFront <= 0f -> null
        fromFront >= 1f -> shape
        else -> look.toward(shape, fromFront)
    }
}

/**
 * This eye with its middle at ([u], [v]) on its head, its look's opening [width] by [height] and turned [tilt] degrees; the iris grows
 * with the opening, and the front look is grown and turned by as much.
 */
fun PaintedEye.framed(u: Float, v: Float, width: Float, height: Float, tilt: Float): PaintedEye {
    val across = width / look.width
    val up = height / look.height
    val turn = tilt - look.tilt
    fun reshaped(shape: EyeLook) = shape.scaled(across, up).let { if (turn == 0f) it else it.copy(tilt = it.tilt + turn) }
    return copy(anchor = anchor.copy(u = u, v = v), look = reshaped(look).copy(width = width, height = height, tilt = tilt), front = front?.let(::reshaped))
}

/** This eye with [change] made to its look and its front look alike. */
fun PaintedEye.styled(change: (EyeLook) -> EyeLook): PaintedEye = copy(look = change(look), front = front?.let(change))

/** This eye with its look's highlight [size] of its iris, its front look's grown or shrunk by as much. */
fun PaintedEye.withHighlight(size: Float): PaintedEye =
    copy(look = look.copy(highlightSize = size), front = front?.let { it.copy(highlightSize = it.highlightSize * size / look.highlightSize) })

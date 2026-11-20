package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.geom.CLUMP_SWEEP
import dev.chieppa.squash.geom.CLUMP_SWEEP_MORE
import dev.chieppa.squash.geom.ChartCut
import dev.chieppa.squash.geom.ChartLid
import dev.chieppa.squash.geom.ChartOval
import kotlin.math.abs
import kotlin.math.max

/** How far past its parts' reach a lid mass's layer is cut, a share of that reach. */
private const val LID_REACH_MARGIN = 1.15f

/** How much further than its corner a lid mass's layer reaches across, in opening widths, for its ends' rounding. */
private const val LID_REACH_ACROSS = 0.1f

/** How much wider than the iris the oval its crescent is cut from is, a share of the iris's halves. */
private const val CRESCENT_WIDEN = 0.35f

/**
 * The detail of a painted eye of [look] over its plain fills, in the eye's chart: the lid's ink mass and lash clumps about the opening,
 * the iris's darker top, lighter crescent and rim as cuts of [iris], and the lid's shadow as a cut of the opening. Each is null while the
 * look leaves it off.
 */
class EyeDetailShapes(look: EyeLook, iris: ChartOval) {
    /** The lid's mass and lash clumps about the opening. */
    val lid: ChartLid? = if (!look.lidMass.isOn && !look.lashClumps.isOn) {
        null
    } else {
        val mass = look.lidMass
        val clumps = look.lashClumps
        ChartLid(
            weight = mass.weight,
            inner = mass.inner,
            flick = mass.flick,
            flickLift = mass.flickLift,
            lower = mass.lower,
            lowerExtent = mass.lowerExtent,
            clumps = if (clumps.isOn) clumps.count else 0,
            clumpLength = clumps.length,
            clumpSpread = clumps.spread,
        )
    }

    /** How far from the opening's middle the lid mass may reach across and up or down, in the chart; zero where there is none. */
    val lidReach: Pair<Float, Float> = lid?.let { mass ->
        val clumpLength = if (mass.clumps > 0) mass.clumpLength else 0f
        val across = look.width * (1f + LID_REACH_ACROSS + max(mass.flick, (CLUMP_SWEEP + CLUMP_SWEEP_MORE) * clumpLength))
        val open = 1f + abs(look.frontNarrowing)
        val up = open + mass.weight + clumpLength + max(mass.flickLift, 0f)
        val down = look.lowerLid * open + mass.weight * mass.lower + max(-mass.flickLift, 0f)
        across * LID_REACH_MARGIN to look.height * max(up, down) * LID_REACH_MARGIN
    } ?: (0f to 0f)

    /** The iris's darker top: the iris kept inside itself moved up, so its lower edge follows the iris's top. */
    val irisUpper: ChartCut? = look.irisShade.takeIf { it.upper > 0f }?.let { shade ->
        ChartCut(iris.copy(v = iris.v + 2f * iris.halfV * (1f - shade.upper)), soft = shade.soft * iris.halfV)
    }

    /**
     * The iris's lighter crescent along its bottom: the iris kept outside a wider oval whose bottom lies the crescent's depth up from the
     * iris's, so its horns run up the iris's sides no higher than its middle.
     */
    val irisLower: ChartCut? = look.irisShade.takeIf { it.lower > 0f }?.let { shade ->
        val halfV = iris.halfV * (1f + CRESCENT_WIDEN)
        ChartCut(ChartOval(iris.u, iris.v - iris.halfV + 2f * iris.halfV * shade.lower + halfV, iris.halfU * (1f + CRESCENT_WIDEN), halfV), outside = true)
    }

    /** The iris's darker rim: the iris kept outside itself made narrower by the rim's width. */
    val irisRim: ChartCut? = look.irisShade.takeIf { it.rim > 0f && iris.halfU > 0f }?.let { shade ->
        val inset = shade.rim * iris.halfU
        ChartCut(iris.copy(halfU = iris.halfU - inset, halfV = iris.halfV * (1f - inset / iris.halfU)), outside = true)
    }

    /** The lid's shadow on the white: the opening kept outside itself moved down by the shadow's depth. */
    val lidShadow: ChartCut? = look.lidShadow.takeIf { it.depth > 0f }?.let { shadow ->
        ChartCut(null, dv = -shadow.depth * look.height * (1f + look.lowerLid), outside = true)
    }
}

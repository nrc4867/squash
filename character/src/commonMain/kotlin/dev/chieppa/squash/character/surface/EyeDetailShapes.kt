package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.geom.CLUMP_SWEEP
import dev.chieppa.squash.geom.CLUMP_SWEEP_MORE
import dev.chieppa.squash.geom.ChartCut
import dev.chieppa.squash.geom.ChartLid
import dev.chieppa.squash.geom.ChartOval
import dev.chieppa.squash.geom.LID_ASPECT
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** How far past its parts' reach a lid mass's layer is cut, a share of that reach. */
private const val LID_REACH_MARGIN = 1.15f

/** How much further than its corner a lid mass's layer reaches across, in opening widths, for its ends' rounding. */
private const val LID_REACH_ACROSS = 0.1f

/** How many times taller than the iris the oval its crescent is cut from is. */
private const val CRESCENT_TALL = 1.2f

/** The thickest a crescent is, a share of the iris's height, leaving its horns below the middle. */
private const val MOST_CRESCENT = 0.9f

/** How many times wider than the iris the oval its darker top is cut from is, where there is no crescent. */
private const val SHADE_WIDE = 2f

/** How many times wider than the opening the opening its lid shadow is cut from is, so the shadow thins to nothing toward the corners. */
private const val SHADOW_WIDEN = 1.25f

/**
 * The detail of a painted eye of [look] over its plain fills, in the eye's chart: the lid's ink mass and lash clumps about the opening,
 * the iris's darker top, lighter crescent and rim as cuts of [iris], and the lid's shadow as a cut of the opening. Each is null while the
 * look leaves it off. The lid mass measures distance with the opening's width [lidAspect] times its height, as it lies on the head.
 */
class EyeDetailShapes(look: EyeLook, iris: ChartOval, lidAspect: Float = LID_ASPECT) {
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
            aspect = lidAspect,
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

    /**
     * The oval the iris's lighter crescent is cut from: its bottom the crescent's depth up from the iris's, wider than the iris and
     * covering all of it above, meeting the iris's sides halfway between the crescent's top and the iris's middle, so the crescent is
     * thickest at the bottom and thins to nothing at its horns, below the middle.
     */
    private val crescentOval: ChartOval? = look.irisShade.takeIf { it.lower > 0f }?.let { shade ->
        val thick = (2f * shade.lower).coerceAtMost(MOST_CRESCENT)
        val horn = (1f - thick) / 2f
        val stand = 1f - horn / CRESCENT_TALL
        val wide = sqrt((1f - horn * horn) / (1f - stand * stand))
        val middle = CRESCENT_TALL - 1f + thick
        ChartOval(iris.u, iris.v + middle * iris.halfV, iris.halfU * wide, iris.halfV * CRESCENT_TALL)
    }

    /**
     * The iris's darker top: the iris kept inside the crescent's oval where there is a crescent, so the two tones meet with none of the
     * iris's own showing between; else inside a wider, gently curved oval whose bottom lies the shade's depth down from the iris's top.
     */
    val irisUpper: ChartCut? = look.irisShade.takeIf { it.upper > 0f }?.let { shade ->
        val oval = crescentOval ?: run {
            val halfV = iris.halfV * CRESCENT_TALL
            val bottom = iris.v + iris.halfV - 2f * iris.halfV * shade.upper
            ChartOval(iris.u, bottom + halfV, iris.halfU * SHADE_WIDE, halfV)
        }
        ChartCut(oval, soft = shade.soft * iris.halfV)
    }

    /** The iris's lighter crescent along its bottom: the iris kept outside the crescent's oval. */
    val irisLower: ChartCut? = crescentOval?.let { ChartCut(it, outside = true) }

    /** The iris's darker rim: the iris kept outside itself made narrower by the rim's width. */
    val irisRim: ChartCut? = look.irisShade.takeIf { it.rim > 0f && iris.halfU > 0f }?.let { shade ->
        val inset = shade.rim * iris.halfU
        ChartCut(iris.copy(halfU = iris.halfU - inset, halfV = iris.halfV * (1f - inset / iris.halfU)), outside = true)
    }

    /**
     * The lid's shadow on the white: the opening kept outside itself widened and moved down by the shadow's depth, a band under the upper
     * lid following its curve and thinning to nothing toward the corners.
     */
    val lidShadow: ChartCut? = look.lidShadow.takeIf { it.depth > 0f }?.let { shadow ->
        ChartCut(null, dv = -shadow.depth * look.height * (1f + look.lowerLid), outside = true, widen = SHADOW_WIDEN)
    }
}

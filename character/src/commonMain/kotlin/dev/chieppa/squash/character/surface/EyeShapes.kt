package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.geom.ChartAlmond
import dev.chieppa.squash.geom.ChartOval
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.SurfaceUv
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How many points go round each of an eye's ovals. */
private const val OVAL_POINTS = 32

/** A slit pupil's width, as a share of the iris's. */
private const val SLIT_WIDTH = 0.2f

/** A slit pupil's height, as a share of the iris's. */
private const val SLIT_HEIGHT = 0.95f

/** The lash flick's points, as multiples of the opening's width forward and its height up from its middle. */
private val LASH_LINE = listOf(-0.95f to 0.1f, -1.25f to 0.35f, -1.45f to 0.75f)

/** The brow's points, as multiples of the opening's width forward and its height up from its middle. */
private val BROW_LINE = listOf(-1.0f to 1.5f, -0.1f to 2.05f, 0.8f to 2.1f)

/**
 * The shapes of a painted eye of [look], in its own chart: u forward toward the head's front and v up, each a share of the head's own u
 * and v at the eye's middle, the opening's middle at the origin: its fills as shapes and its ink lines as places along them. The iris,
 * pupil and highlight look [gazeForward] of the opening's half width forward and [gazeUp] of its half height up.
 */
class EyeShapes(private val look: EyeLook, gazeForward: Float = 0f, gazeUp: Float = 0f) {
    /** The opening as an almond before its tilts, and its fills' ovals before the eye's tilt, as a renderer reads them at each fragment. */
    val almond = ChartAlmond(look.width, look.height, look.lowerLid, look.frontNarrowing)

    private val rim = List(OVAL_POINTS) { i -> almond.rim(2.0 * PI * i / OVAL_POINTS).let { turned(it.u, it.v, turn) } }

    /** The opening: an almond, rounder over the top than under, narrowed toward its back corner. */
    val opening: ColorShape = ColorShape.Border(rim)

    /** The lid line round the opening, from its back corner over the top and under the bottom, its first place repeated last. */
    val lid: List<SurfaceUv>? = if (look.lid) rim + rim.first() else null

    private val irisMiddle = SurfaceUv(look.irisForward + gazeForward * look.width, look.irisUp + gazeUp * look.height)
    private val irisHigh = look.irisSize * look.irisRatio

    val irisOval = ChartOval(irisMiddle.u, irisMiddle.v, look.irisSize, irisHigh)

    val pupilOval = if (look.slit) {
        ChartOval(irisMiddle.u, irisMiddle.v, look.irisSize * SLIT_WIDTH, irisHigh * SLIT_HEIGHT)
    } else {
        ChartOval(irisMiddle.u, irisMiddle.v, look.irisSize * look.pupilSize, irisHigh * look.pupilSize)
    }

    val highlightOval = ChartOval(
        irisMiddle.u + look.highlightForward * look.irisSize,
        irisMiddle.v + look.highlightUp * irisHigh,
        look.irisSize * look.highlightSize,
        irisHigh * look.highlightSize,
    )

    val iris: ColorShape = oval(irisOval)
    val pupil: ColorShape = oval(pupilOval)
    val highlight: ColorShape = oval(highlightOval)

    /** The opening's turn, its own tilt and the eye's, in degrees. */
    val turn: Float get() = look.tilt + look.openingTilt

    /** The fills' ovals' turn, the eye's tilt, in degrees. */
    val fillTurn: Float get() = look.tilt

    /** The lash flick at the opening's back corner. */
    val lashes: List<SurfaceUv>? = if (look.lashes) lashLine() else null

    /** The brow over the opening. */
    val brow: List<SurfaceUv>? = if (look.brow) browLine() else null

    private fun oval(o: ChartOval): ColorShape.Border = ColorShape.Border(
        List(OVAL_POINTS) { i ->
            val angle = 2.0 * PI * i / OVAL_POINTS
            turned(o.u + o.halfU * cos(angle).toFloat(), o.v + o.halfV * sin(angle).toFloat(), look.tilt)
        },
    )

    /** The lash flick's places, set at the opening's back corner as its own tilt turns it, then turned with the eye. */
    private fun lashLine(): List<SurfaceUv> {
        val corner = turned(-look.width, 0f, look.openingTilt)
        return LASH_LINE.map { (u, v) -> turned(corner.u + look.width * (u + 1f), corner.v + look.height * v, look.tilt) }
    }

    /** The brow's places, raised by the look's brow raise and turned by its brow tilt about its middle place, then turned with the eye. */
    private fun browLine(): List<SurfaceUv> {
        val places = BROW_LINE.map { (u, v) -> SurfaceUv(look.width * u, look.height * v + look.browRaise) }
        val middle = places[places.size / 2]
        return places.map { p ->
            val own = turned(p.u - middle.u, p.v - middle.v, look.browTilt)
            turned(middle.u + own.u, middle.v + own.v, look.tilt)
        }
    }

    /** ([u], [v]) turned by [degrees] about the opening's middle. */
    private fun turned(u: Float, v: Float, degrees: Float): SurfaceUv {
        val angle = degrees * PI.toFloat() / 180f
        val c = cos(angle)
        val s = sin(angle)
        return SurfaceUv(u * c - v * s, u * s + v * c)
    }
}

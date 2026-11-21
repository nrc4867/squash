package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.LidPose
import dev.chieppa.squash.geom.ChartAlmond
import dev.chieppa.squash.geom.ChartOval
import dev.chieppa.squash.geom.LASH_CLOSE_SWING
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.LID_ASPECT
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

/** The lash flick's points, as multiples of the opening's width forward and its height up from its back corner's place on the rim. */
private val LASH_LINE = listOf(0.05f to 0.1f, -0.25f to 0.35f, -0.45f to 0.75f)

/** Where along the upper lid each further lash rises, as the rim's angle from the back corner over the top, in radians. */
private val LASH_ANGLES = listOf(0.0, 0.5, 0.95)

/** Each further lash's length, a share of the first's. */
private val LASH_LENGTHS = listOf(1f, 0.85f, 0.7f)

/** The brow's points, as multiples of the opening's width forward and its height up from its middle. */
private val BROW_LINE = listOf(-1.0f to 1.5f, -0.1f to 2.05f, 0.8f to 2.1f)

/**
 * The shapes of a painted eye of [look], in its own chart: u forward toward the head's front and v up, each a share of the head's own u
 * and v at the eye's middle, the opening's middle at the origin: its fills as shapes and its ink lines as places along them. The iris,
 * pupil and highlight look [gazeForward] of the opening's half width forward and [gazeUp] of its half height up. The lid mass measures
 * distance with the opening [lidAspect] times as wide as it is high, as it lies on the head. The lids stand at [lidPose], closing the
 * opening's fills and carrying the lid line, the lashes and the lid's mass and paint on their moving edges.
 */
class EyeShapes(private val look: EyeLook, gazeForward: Float = 0f, gazeUp: Float = 0f, lidAspect: Float = LID_ASPECT, lidPose: LidPose = LidPose.OPEN) {
    /** The opening as an almond before its tilts, its lids standing at the pose, and its fills' ovals before the eye's tilt, as a renderer reads them at each fragment. */
    val almond = ChartAlmond(look.width, look.height, look.lowerLid, look.frontNarrowing, look.topFlatness, look.outerCorner, chartLids(look, lidPose, lidAspect))

    private val rim = List(OVAL_POINTS) { i -> almond.rim(2.0 * PI * i / OVAL_POINTS).let { turned(it.u, it.v, turn) } }

    /** The opening wide open: an almond, rounder over the top than under, narrowed toward its back corner, its top flattened and back corner sharpened as the look says. */
    val opening: ColorShape = ColorShape.Border(rim)

    /**
     * The lid line round the opening on its lids' edges, from its back corner over the top and under the bottom, its first place repeated
     * last; none where a lid mass is drawn in its place.
     */
    val lid: List<SurfaceUv>? = if (look.lid && !look.lidMass.isOn) {
        List(OVAL_POINTS) { i -> almond.lidRim(2.0 * PI * i / OVAL_POINTS).let { turned(it.u, it.v, turn) } }.let { it + it.first() }
    } else {
        null
    }

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

    /** The lid mass, lash clumps, iris tones and lid shadow, each shaped about the opening and the iris. */
    val detail = EyeDetailShapes(look, almond, irisOval, lidAspect)

    /** The opening's turn, its own tilt and the eye's, in degrees. */
    val turn: Float get() = look.tilt + look.openingTilt

    /** The fills' ovals' turn, the eye's tilt, in degrees. */
    val fillTurn: Float get() = look.tilt

    /**
     * The lash flicks along the upper lid, the first at the opening's back corner and each further one forward along the lid from there;
     * none where lash clumps take their place.
     */
    val lashes: List<List<SurfaceUv>> = if (look.lashClumps.isOn) emptyList() else (0 until look.lashes.coerceIn(0, LASH_ANGLES.size)).map(::lashLine)

    /** The brow over the opening. */
    val brow: List<SurfaceUv>? = if (look.brow) browLine() else null

    private fun oval(o: ChartOval): ColorShape.Border = ColorShape.Border(
        List(OVAL_POINTS) { i ->
            val angle = 2.0 * PI * i / OVAL_POINTS
            turned(o.u + o.halfU * cos(angle).toFloat(), o.v + o.halfV * sin(angle).toFloat(), look.tilt)
        },
    )

    /**
     * Lash [index]'s places, rising from its place on the upper lid as the opening's own tilt turns it, swept back as the first is and
     * shortened by its share, swung down toward the back corner as the lid closes, then turned with the eye.
     */
    private fun lashLine(index: Int): List<SurfaceUv> {
        val rimPlace = almond.lidRim(LASH_ANGLES[index])
        val root = turned(rimPlace.u, rimPlace.v, look.openingTilt)
        val length = LASH_LENGTHS[index]
        val swing = LASH_CLOSE_SWING * almond.lids.close.coerceIn(0f, 1f)
        return LASH_LINE.map { (u, v) ->
            val out = turned(look.width * u * length, look.height * v * length, swing)
            turned(root.u + out.u, root.v + out.v, look.tilt)
        }
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

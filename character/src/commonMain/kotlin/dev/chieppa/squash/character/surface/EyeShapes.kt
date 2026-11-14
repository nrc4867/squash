package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
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
 * and v at the eye's middle, the opening's middle at the origin: its fills as shapes and its ink lines as places along them.
 */
class EyeShapes(private val look: EyeLook) {
    private val rim = almond()

    /** The opening: an almond, rounder over the top than under, narrowed toward its back corner. */
    val opening: ColorShape = ColorShape.Border(rim)

    /** The lid line round the opening, from its back corner over the top and under the bottom, its first place repeated last. */
    val lid: List<SurfaceUv>? = if (look.lid) rim + rim.first() else null

    private val irisMiddle = SurfaceUv(look.irisForward, look.irisUp)
    private val irisHigh = look.irisSize * look.irisRatio

    val iris: ColorShape = oval(irisMiddle, look.irisSize, irisHigh)

    val pupil: ColorShape = if (look.slit) {
        oval(irisMiddle, look.irisSize * SLIT_WIDTH, irisHigh * SLIT_HEIGHT)
    } else {
        oval(irisMiddle, look.irisSize * look.pupilSize, irisHigh * look.pupilSize)
    }

    val highlight: ColorShape = oval(
        SurfaceUv(irisMiddle.u + look.highlightForward * look.irisSize, irisMiddle.v + look.highlightUp * irisHigh),
        look.irisSize * look.highlightSize,
        look.irisSize * look.highlightSize,
    )

    /** The lash flick at the opening's back corner. */
    val lashes: List<SurfaceUv>? = if (look.lashes) line(LASH_LINE) else null

    /** The brow over the opening. */
    val brow: List<SurfaceUv>? = if (look.brow) line(BROW_LINE) else null

    private fun almond(): List<SurfaceUv> =
        List(OVAL_POINTS) { i ->
            val angle = 2.0 * PI * i / OVAL_POINTS
            val along = cos(angle).toFloat()
            val over = sin(angle).toFloat()
            val open = over * (if (over > 0f) 1f else look.lowerLid) * (1f - look.frontNarrowing * along)
            turned(-look.width * along, look.height * open)
        }

    private fun oval(middle: SurfaceUv, halfU: Float, halfV: Float): ColorShape.Border = ColorShape.Border(
        List(OVAL_POINTS) { i ->
            val angle = 2.0 * PI * i / OVAL_POINTS
            turned(middle.u + halfU * cos(angle).toFloat(), middle.v + halfV * sin(angle).toFloat())
        },
    )

    private fun line(points: List<Pair<Float, Float>>): List<SurfaceUv> = points.map { (u, v) -> turned(look.width * u, look.height * v) }

    /** ([u], [v]) turned by the look's tilt about the opening's middle. */
    private fun turned(u: Float, v: Float): SurfaceUv {
        val angle = look.tilt * PI.toFloat() / 180f
        val c = cos(angle)
        val s = sin(angle)
        return SurfaceUv(u * c - v * s, u * s + v * c)
    }
}

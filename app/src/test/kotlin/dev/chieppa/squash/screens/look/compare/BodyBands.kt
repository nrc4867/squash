package dev.chieppa.squash.screens.look.compare

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.screens.look.shots.ReferenceShot
import dev.chieppa.squash.screens.look.shots.ShotView
import dev.chieppa.stretch.gpu.Vec3

/** The body regions a whole-body shot is scored by. */
enum class Region { HEAD, TORSO, ARMS, LEGS }

/** Reference heights at rest, in metres: the chin with the head's scale, the crotch, and the half width inside which the torso lies. */
private const val CHIN = 1.429f
private const val CROTCH = 0.785f
private const val TORSO_HALF_WIDTH = 0.17f

/**
 * The image bands of a whole-body [shot]: the head above the chin, the legs below the crotch, and between them the torso inside its half
 * width and the arms outside it, from the front and three-quarter views only, since from the side the arms lie over the torso. Bands are
 * set at the rest pose's heights, so in a pose they are approximate. Null for a close-up.
 */
class BodyBands private constructor(private val chinRow: Float, private val crotchRow: Float, private val columns: (Float) -> ClosedFloatingPointRange<Float>?) {
    /** Whether pixel ([x], [y]) lies in [region]. */
    fun inside(region: Region, x: Int, y: Int): Boolean = when (region) {
        Region.HEAD -> y < chinRow
        Region.LEGS -> y >= crotchRow
        Region.TORSO -> y in chinRow..crotchRow && columns(y.toFloat())?.let { x.toFloat() in it } ?: true
        Region.ARMS -> y in chinRow..crotchRow && columns(y.toFloat())?.let { x.toFloat() !in it } ?: false
    }

    private operator fun ClosedFloatingPointRange<Float>.contains(y: Int) = y.toFloat() in this

    /** The regions this shot is scored by. */
    val regions: List<Region> get() = if (columns(chinRow) == null) listOf(Region.HEAD, Region.TORSO, Region.LEGS) else Region.entries

    companion object {
        fun of(shot: ReferenceShot): BodyBands? {
            if (shot.view !in setOf(ShotView.FRONT, ShotView.THREE_QUARTER, ShotView.SIDE)) return null
            val camera = shot.camera.inReference()
            val w = shot.camera.width
            val h = shot.camera.height
            fun project(x: Float, y: Float): Offset = checkNotNull(camera.project(Vec3(x, y, 0f), w, h))
            val chinRow = project(0f, CHIN).y
            val crotchRow = project(0f, CROTCH).y
            val columns: (Float) -> ClosedFloatingPointRange<Float>? = if (shot.view == ShotView.SIDE) {
                { null }
            } else {
                { row ->
                    val height = CHIN + (CROTCH - CHIN) * (row - chinRow) / (crotchRow - chinRow)
                    val a = project(-TORSO_HALF_WIDTH, height).x
                    val b = project(TORSO_HALF_WIDTH, height).x
                    minOf(a, b)..maxOf(a, b)
                }
            }
            return BodyBands(chinRow, crotchRow, columns)
        }
    }
}

package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A grid's side, in vertices. */
private const val SIDE = 10

/** A joint's radius, in body units. */
private const val RADIUS = 0.1f

/**
 * A crease's and a fold's band keep no specks: an island of band smaller than the least is lowered under the band and a larger one kept;
 * and loose cloth standing well off a joint takes none of its band while cloth close round it does.
 */
class CreaseShadeTest {
    /** A [SIDE] by [SIDE] grid of vertices, two triangles a square. */
    private val grid: IntArray = buildList {
        for (y in 0 until SIDE - 1) for (x in 0 until SIDE - 1) {
            val a = y * SIDE + x
            addAll(listOf(a, a + 1, a + SIDE, a + 1, a + SIDE + 1, a + SIDE))
        }
    }.toIntArray()

    @Test
    fun aSpeckOfBandIsLoweredAndAPatchKept() {
        val shade = FloatArray(SIDE * SIDE)
        shade[2 * SIDE + 2] = 0.8f
        shade[2 * SIDE + 3] = 0.8f
        for (y in 5 until 9) for (x in 5 until 9) shade[y * SIDE + x] = 0.7f
        val kept = withoutSpecks(shade, grid, least = 8)
        assertTrue(kept[2 * SIDE + 2] < BAND_FROM && kept[2 * SIDE + 3] < BAND_FROM, "the two-vertex speck shows no band")
        assertEquals(0.7f, kept[6 * SIDE + 6], "the sixteen-vertex patch keeps its band")
        assertEquals(0.8f, shade[2 * SIDE + 2], "the shade itself is left as it was")
        val clean = FloatArray(SIDE * SIDE).also { for (y in 5 until 9) for (x in 5 until 9) it[y * SIDE + x] = 0.7f }
        assertSame(clean, withoutSpecks(clean, grid, least = 8), "no speck, nothing copied")
    }

    @Test
    fun clothWellOffAJointTakesNoneOfItsBand() {
        val elbow = CreaseJoint(1, 0, 0, Vec3.ZERO, Vec3.X, Vec3.X, RADIUS, inner = Vec3.Y, strength = 1f)
        val turn = rotation(Vec3.Z, (100.0 * PI / 180.0).toFloat())
        val frames = BoneFrames(listOf(Affine.IDENTITY, Affine(turn, Vec3.ZERO)), listOf(Mat3.IDENTITY, turn))
        val posed = PosedCreaseJoint.of(elbow, frames, 1f, 0.3f)!!
        val close = posed.at + posed.inner * RADIUS
        val off = posed.at + posed.inner * (RADIUS * 2.7f)
        val vertices = FloatArray(2 * RenderMesh.FLOATS_PER_VERTEX).also {
            it.put(0, close)
            it.put(RenderMesh.FLOATS_PER_VERTEX, off)
        }
        val reach = CreaseReach(listOf(elbow), listOf(intArrayOf(0, 1)))
        val cloth = JointCreaseField.of(reach, listOf(posed), vertices, 2, cloth = true)
        val skin = JointCreaseField.of(reach, listOf(posed), vertices, 2)
        assertTrue(cloth.shade[0] > BAND_FROM, "cloth close round the joint takes its band: ${cloth.shade[0]}")
        assertEquals(0f, cloth.shade[1], "cloth 2.7 radii off takes none")
        assertTrue(skin.shade[1] > 0f, "the same place on skin would")
    }

    @Test
    fun skinTheBendCarriesWholeTakesItsShadeButNoInk() {
        val neck = CreaseJoint(1, 0, 0, Vec3.ZERO, Vec3.X, Vec3.X, RADIUS, inner = Vec3.Y, strength = 1f)
        val turn = rotation(Vec3.Z, (60.0 * PI / 180.0).toFloat())
        val frames = BoneFrames(listOf(Affine.IDENTITY, Affine(turn, Vec3.ZERO)), listOf(Mat3.IDENTITY, turn))
        val posed = PosedCreaseJoint.of(neck, frames, 1f, 0.3f)!!
        val on = posed.at + posed.inner * RADIUS
        val vertices = FloatArray(2 * RenderMesh.FLOATS_PER_VERTEX).also {
            it.put(0, on)
            it.put(RenderMesh.FLOATS_PER_VERTEX, on)
        }
        val reach = CreaseReach(listOf(neck), listOf(intArrayOf(0, 1)), listOf(booleanArrayOf(true, false)))
        val field = JointCreaseField.of(reach, listOf(posed), vertices, 2)
        assertTrue(field.ink[0] > 0f, "skin the bend folds is inked")
        assertEquals(0f, field.ink[1], "skin the bend carries whole is not")
        assertEquals(field.shade[0], field.shade[1], "both take the shade")
    }
}

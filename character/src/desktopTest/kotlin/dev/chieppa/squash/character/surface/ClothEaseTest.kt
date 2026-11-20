package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ClothHang
import dev.chieppa.squash.document.EaseKey
import dev.chieppa.squash.document.EaseProfile
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.SweptSolid
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RINGS = 9
private const val AROUND = 24

/**
 * A garment's ease over a part: dropped seams and gathering bands shape it, a uniform ease grows nothing of its own, a varying one grows
 * each ring by its ease there, and cloth over an upright part narrowing under it hangs straight, no further out than its limit, a quarter of it at the sides.
 */
class ClothEaseTest {
    /** An upright tube from radius 0.06 at its foot, u 0, to 0.1 at its top, u 1. */
    private val tube: SweptSolid = run {
        val middles = List(RINGS) { Vec3(0f, it / (RINGS - 1f), 0f) }
        val points = List(RINGS) { i ->
            val r = radiusAt(i / (RINGS - 1f))
            List(AROUND) { k ->
                val a = 2 * PI * k / AROUND
                middles[i] + Vec3((r * cos(a)).toFloat(), 0f, (r * sin(a)).toFloat())
            }
        }
        val vs = FloatArray(AROUND) { it.toFloat() / AROUND }
        requireNotNull(SweptSolid.ofRings(points, vs, List(RINGS) { Vec3.Y }, List(RINGS) { Vec3.X }, FloatArray(RINGS) { it / (RINGS - 1f) }))
    }

    private fun radiusAt(u: Float) = 0.06f + 0.04f * u

    @Test
    fun aDroppedSeamCarriesTheEaseAboveItDownThePartAndPinchesAtTheSeam() {
        val ease = ClothEase(EaseProfile.flat(0.01f), drop = Drop(0.2f, 0.03f))
        assertEquals(0.03f, ease.at(0.05f, 0f, 0f), 1e-4f)
        assertEquals(0.01f, ease.at(0.5f, 0f, 0f), 1e-4f)
        assertTrue(ease.at(0.2f, 0f, 0f) < ease.at(0.18f, 0f, 0f))
        assertTrue(ease.least <= ease.at(0.2f, 0f, 0f))
        assertEquals(0.03f, ease.most)
    }

    @Test
    fun aGatheringBandPullsTheClothInToItsEase() {
        val cells = BooleanArray(CHART_CELLS) { it / CHART_COLUMNS >= 56 }
        val band = checkNotNull(ClothField.of(listOf(cells)))
        val ease = ClothEase(EaseProfile(listOf(EaseKey(0f, 0.01f), EaseKey(1f, 0.03f))), gathers = listOf(Gather(band, -1, 0.004f)))
        assertEquals(0.004f, ease.at(0.97f, 0.3f, 0f), 1e-4f)
        assertEquals(EaseProfile(listOf(EaseKey(0f, 0.01f), EaseKey(1f, 0.03f))).at(0.6f), ease.at(0.6f, 0.3f, 0f), 1e-5f)
        assertEquals(0.004f, ease.least)
        assertTrue(!ease.uniform)
    }

    @Test
    fun aFoldedBandStandsOutByItsThicknessAsAStep() {
        val cells = BooleanArray(CHART_CELLS) { it / CHART_COLUMNS >= 56 }
        val band = checkNotNull(ClothField.of(listOf(cells)))
        val ease = ClothEase(EaseProfile.flat(0.01f), folds = listOf(Fold(band, -1, 0.006f)))
        assertEquals(0.016f, ease.at(0.95f, 0.3f, 0f), 1e-4f)
        assertEquals(0.01f, ease.at(0.8f, 0.3f, 0f), 1e-5f)
        val edge = 56f / CHART_ROWS
        assertTrue(ease.at(edge + 1f / CHART_ROWS, 0.3f, 0f) - ease.at(edge - 1f / CHART_ROWS, 0.3f, 0f) > 0.005f, "the fold rises within a row either side of its edge")
        assertEquals(0.016f, ease.most, 1e-6f)
        assertTrue(!ease.uniform)
    }

    @Test
    fun innerLayersAddTheirEase() {
        val ease = ClothEase.flat(0.02f).over(listOf(ClothEase(EaseProfile(listOf(EaseKey(0f, 0.004f), EaseKey(1f, 0.01f))))))
        assertEquals(0.024f, ease.at(0f, 0f, 0f), 1e-6f)
        assertEquals(0.03f, ease.at(1f, 0f, 0f), 1e-6f)
        assertEquals(0.024f, ease.least, 1e-6f)
    }

    @Test
    fun aUniformEaseThatDoesNotHangGrowsNoRunOfItsOwn() {
        val runs = GrownRuns()
        assertNull(runs.of(tube, ClothEase.flat(0.01f), null, 0.01f, 0f, 1f))
        assertNull(runs.of(tube, null, null, 0.01f, 0f, 1f))
    }

    @Test
    fun aVaryingEaseGrowsEachRingByItsEasePastTheLeast() {
        val ease = ClothEase(EaseProfile(listOf(EaseKey(0f, 0.01f), EaseKey(1f, 0.03f))))
        val grown = assertNotNull(GrownRuns().of(tube, ease, null, ease.least, 0f, 1f))
        for (u in listOf(0.25f, 0.5f, 0.75f)) {
            val extra = ease.at(u, 0f, 0f) - ease.least
            assertEquals(radiusAt(u) + extra, -grown.distance(Vec3(0f, u, 0f)), 3e-3f, "at u $u")
        }
    }

    @Test
    fun clothOverANarrowingUprightPartHangsStraightToItsLimit() {
        val hang = ClothHang(0.02f)
        val hung = assertNotNull(GrownRuns().of(tube, null, hang, 0f, 0f, 1f))
        for (u in listOf(0.1f, 0.3f, 0.9f)) {
            val front = minOf(radiusAt(1f), radiusAt(u) + hang.limit)
            val side = minOf(radiusAt(1f), radiusAt(u) + hang.limit / 4f)
            assertEquals(0f, hung.distance(Vec3(front, u, 0f)), 3e-3f, "front at u $u")
            assertEquals(0f, hung.distance(Vec3(0f, u, side)), 3e-3f, "side at u $u")
        }
        val flared = assertNotNull(GrownRuns().of(tube, null, ClothHang(0.2f, 0.1f), 0f, 0f, 1f))
        assertTrue(-flared.distance(Vec3(0f, 0.3f, 0f)) > radiusAt(1f))
    }
}

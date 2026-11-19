package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A clump's profile and tip, a volume's curtains and hem, and the band of shine round it. See HLD: parts, Hair. */
class HairShapesTest {
    private val lock = ClumpShape(width = 0.06f, thickness = 0.02f)

    @Test
    fun `a single pointed tip closes to nothing and a lobed tip keeps its width to its end`() {
        assertEquals(0f, lock.widthAt(1f), 1e-5f)
        assertEquals(0f, lock.thicknessAt(1f), 1e-5f)
        val lobed = lock.copy(points = 3, notch = 0.1f)
        assertTrue(lobed.widthAt(0.95f) > 0.01f)
        assertTrue(lobed.widthAt(0.95f) > lock.widthAt(0.95f) * 3f)
        assertEquals(0f, lobed.widthAt(1f), 1e-5f)
        assertEquals(0f, lobed.thicknessAt(1f), 1e-5f)
    }

    @Test
    fun `a round tip stays wider than a pointed one as it closes`() {
        val round = lock.copy(tipRound = 1f)
        assertTrue(round.widthAt(0.9f) > lock.widthAt(0.9f))
    }

    @Test
    fun `the root flares wider than just past it`() {
        assertTrue(lock.widthAt(0f) > lock.widthAt(0.2f) * 1.1f)
    }

    @Test
    fun `lobes are cut back between them and not at their middles`() {
        val lobed = lock.copy(points = 2, notch = 0.1f)
        assertEquals(0f, lobed.cutBack(1f, -0.5f), 1e-5f)
        assertEquals(0.1f, lobed.cutBack(1f, 0f), 1e-5f)
        assertEquals(0f, lobed.cutBack(0.2f, 0f), 1e-5f)
    }

    @Test
    fun `a clump's last ring is flat and its twist turns its rings`() {
        val axis = AxisSpline(listOf(Vec3.ZERO, Vec3(0f, -0.2f, 0f)))
        val spec = TubeSpec(
            name = "body", axis = axis, uStart = 0f, uEnd = 1f, rings = 16, segments = 8, section = SectionProfile.CIRCLE.spline(8), radius = 0.03f,
            startClosure = EndClosure.OPEN, endClosure = EndClosure.POINTED, up = Vec3(1f, 0f, 0f),
        )
        val slices = ClumpBuilder.slices(spec, lock.copy(twist = 90f))
        assertEquals(0f, slices.last().height, 1e-5f)
        assertTrue(abs(slices.first().frame.up dot slices.last().frame.up) < 0.1f)
        val patch = ClumpBuilder.patch(spec, lock.copy(points = 3, notch = 0.1f))
        assertEquals(16 * 8, patch.nu * patch.nv)
    }

    private val bob = VolumeShape(length = 0.3f, crown = 0.55f, width = 0.5f, back = 0.55f, sides = 0.45f, fringe = -0.05f, rise = 0.3f)

    @Test
    fun `at the front the sides fall past the face and the middle stops above it`() {
        val front = bob.sectionAt(0.85f, 32).sample(64)
        val under = front.filter { it.y < 0f }
        val middle = under.minBy { abs(it.x) }
        val side = under.minBy { it.y }
        assertTrue(side.y < middle.y - 0.005f, "the curtain at ${side.y} hangs below the fringe at ${middle.y}")
        assertTrue(abs(side.x) > abs(middle.x) + 0.02f)
    }

    @Test
    fun `the crown of the volume is the top of its sphere`() {
        val crown = Vec3(0.15f, bob.riseAt(0.5f) + bob.topAt(0.5f) * 0.6f, 0f)
        val sphere = bob.sphereOf(crown, Vec3.X, Vec3.Y, Vec3(0f, 0f, -1f))
        assertEquals(1f, sphere.y, 0.05f)
        assertEquals(0f, sphere.x, 0.05f)
    }

    @Test
    fun `the shine band holds its own height and its loops lie on the volume's chart`() {
        val band = ShineBand(at = 0.5f, width = 0.2f, jag = 0.05f, teeth = 6)
        assertTrue(band.inside(Vec3(0f, 0.5f, 0.86f)) > 0f)
        assertTrue(band.inside(Vec3(0f, 0.9f, 0.4f)) < 0f)
        val loops = band.loops()
        assertEquals(2, loops.size)
        loops.flatten().forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
    }

    @Test
    fun `the hem follows each ring's lowest stretch`() {
        val loops = bob.hemLoops(0.05f)
        assertEquals(2, loops.size)
        assertTrue(loops[0].all { it.y in 0.25f..0.5f })
    }

    @Test
    fun `locks cut the hem up between their points and leave the top whole`() {
        val parted = bob.copy(locks = 12, lockDepth = 0.3f)
        val whole = bob.sectionAt(0.3f, 48).controlPoints
        val cut = parted.sectionAt(0.3f, 48).controlPoints
        val tops = whole.indices.filter { whole[it].y > 0f }
        tops.forEach { assertEquals(whole[it].y, cut[it].y, 1e-6f) }
        val raised = whole.indices.filter { whole[it].y < 0f }.map { cut[it].y - whole[it].y }
        assertTrue(raised.all { it >= -1e-6f })
        assertTrue(raised.max() > 0.3f * bob.length * 0.5f, "a notch rises ${raised.max()}")
        assertTrue(raised.min() < 0.3f * bob.length * 0.1f, "a point stays near the hem at ${raised.min()}")
    }

    @Test
    fun `a swept fringe hangs longer toward its side`() {
        val swept = bob.copy(sweep = 0.8f).sectionAt(0.95f, 48).controlPoints
        val left = swept.filter { it.y < 0f && it.x > 0f }.minOf { it.y }
        val right = swept.filter { it.y < 0f && it.x < 0f }.minOf { it.y }
        assertTrue(left < right, "the swept side reaches $left, the other $right")
    }

    @Test
    fun `a lobed, twisted, waving clump still closes its last ring to a point`() {
        val axis = AxisSpline(listOf(Vec3.ZERO, Vec3(0f, -0.27f, 0f)))
        val spec = TubeSpec(
            name = "body", axis = axis, uStart = 0f, uEnd = 1f, rings = 32, segments = 16, section = SectionProfile.CIRCLE.spline(16), radius = 0.1f,
            startClosure = EndClosure.OPEN, endClosure = EndClosure.POINTED, up = Vec3(1f, 0f, 0f),
        )
        val shape = ClumpShape(width = 0.25f, thickness = 0.2f, taper = 0.51f, swell = 0.24f, tipRound = 0.72f, tipReach = 0.37f, points = 2, notch = 0.21f, twist = 315f, wave = 0.82f, waves = 0.52f, flare = 0.98f, cup = -0.44f)
        val patch = ClumpBuilder.patch(spec, shape)
        val last = (0 until patch.nv).map { patch.point(patch.index(patch.nu - 1, it)) }
        last.forEach { assertTrue((it - last[0]).length < 1e-6f, "the last ring spreads to ${(it - last[0]).length}") }
    }
}

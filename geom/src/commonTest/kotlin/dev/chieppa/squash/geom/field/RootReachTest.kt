package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A round post on a ball's top, wider than the ball is round there, rises out of the ball with no step under its root. */
class RootReachTest {
    /** A ball of radius 0.3 whose top lies at y 0.05. */
    private class Ball : Solid {
        val centre = Vec3(0f, -0.25f, 0f)
        override val low = centre - Vec3(0.3f, 0.3f, 0.3f)
        override val high = centre + Vec3(0.3f, 0.3f, 0.3f)
        override fun distance(p: Vec3): Float = (p - centre).length - 0.3f
    }

    private val ball = Ball()
    private val radius = 0.15f
    private val foot = 0.04f

    private fun ring(y: Float): List<Vec3> = List(AROUND) { k ->
        val a = 2 * PI * k / AROUND
        Vec3((radius * cos(a)).toFloat(), y, (radius * sin(a)).toFloat())
    }

    private fun post(reach: Float): SweptSolid {
        val rooted = RootedRings(List(RINGS) { ring(foot + 0.4f * it / (RINGS - 1f)) }, List(RINGS) { Vec3.Y }, List(RINGS) { Vec3.X }, FloatArray(RINGS) { it / (RINGS - 1f) }, null, reach)
        return requireNotNull(SweptSolid.ofRings(rooted.points, FloatArray(AROUND) { it.toFloat() / AROUND }, rooted.tangents, rooted.ups, rooted.us))
    }

    private fun joined(reach: Float): JoinedField {
        val parent = JoinedMember(ball, Vec3.ZERO, false, 0f)
        val bare = JoinedMember(post(reach), Vec3.ZERO, false, BLEND, parent = 0, part = 1)
        return JoinedField(listOf(parent, JoinedMember(bare.solid, Vec3.ZERO, false, BLEND, 0, 1, angles = JoinAngles.of(bare, parent, BLEND))))
    }

    @Test
    fun aRootOverAClosedTopsEdgeCarriesOnIntoIt() {
        val root = ring(foot)
        assertTrue(root.any { ball.distance(it) > 0f }, "the post's root stands out over the ball's edge")
        val reach = rootReach(root, Vec3(0f, -1f, 0f), radius) { ball.distance(it) }
        assertEquals(radius, reach, 1e-6f, "its whole root radius lies inside the ball")
        val under = Vec3(radius - 0.01f, foot - 0.02f, 0f)
        assertTrue(joined(0f).value(under) > 0f, "a flat root leaves a step under its edge")
        assertTrue(joined(reach).value(under) < 0f, "the carried-on root fills it")
        val side = Vec3(radius + 0.005f, foot + 0.1f, 0f)
        assertEquals(joined(0f).value(side), joined(reach).value(side), 1e-4f, "the post above its root is as it was")
    }

    @Test
    fun aRootNowhereInsideItsParentStaysFlat() {
        assertEquals(0f, rootReach(ring(0.5f), Vec3(0f, -1f, 0f), radius) { ball.distance(it) })
    }

    private companion object {
        const val RINGS = 6
        const val AROUND = 24
        const val BLEND = 0.02f
    }
}

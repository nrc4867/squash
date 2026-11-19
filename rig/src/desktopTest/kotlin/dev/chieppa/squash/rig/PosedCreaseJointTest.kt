package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A joint's radius, in body units. */
private const val RADIUS = 0.1f

/** Radians a degree. */
private const val DEGREES = (PI / 180.0).toFloat()

/** The share of a full crease a joint shows at rest. */
private const val REST = 0.3f

/**
 * A crease joint on a limb along x, its pivot at the origin: faint on its rest side at rest, on the side it closes toward once bent and
 * deeper and wider round the limb the further it bends, and nowhere with no rest side and no bend.
 */
class PosedCreaseJointTest {
    private val elbow = CreaseJoint(1, 0, 0, Vec3.ZERO, Vec3.X, Vec3.X, RADIUS, inner = Vec3.Y, strength = 1f)
    private val straight = CreaseJoint(1, 0, 0, Vec3.ZERO, Vec3.X, Vec3.X, RADIUS, inner = null, strength = 1f)

    private fun bentBy(degrees: Float): BoneFrames {
        val turn = rotation(Vec3.Z, degrees * DEGREES)
        return BoneFrames(listOf(Affine.IDENTITY, Affine(turn, Vec3.ZERO)), listOf(Mat3.IDENTITY, turn))
    }

    @Test
    fun atRestOnlyAJointWithARestSideCreasesAndFaintly() {
        val rest = assertNotNull(PosedCreaseJoint.of(elbow, bentBy(0f), 1f, REST))
        assertEquals(REST, rest.amount, 1e-5f)
        assertEquals(1f, rest.facing(Vec3.Y * RADIUS), 1e-5f)
        assertNull(PosedCreaseJoint.of(straight, bentBy(0f), 1f, REST))
        assertNull(PosedCreaseJoint.of(elbow, bentBy(0f), 0f, REST))
    }

    @Test
    fun aBendDeepensAndWidensTheCreaseOnTheSideItClosesToward() {
        val half = assertNotNull(PosedCreaseJoint.of(elbow, bentBy(45f), 1f, REST))
        val deep = assertNotNull(PosedCreaseJoint.of(elbow, bentBy(100f), 1f, REST))
        assertTrue(deep.amount > half.amount && half.amount > REST, "${half.amount} then ${deep.amount}")
        assertEquals(1f, deep.amount, 1e-5f)
        assertTrue(deep.width > half.width)
        val inside = deep.at + deep.inner * RADIUS
        val outside = deep.at - deep.inner * RADIUS
        val closing = (Vec3(cos(100f * DEGREES), sin(100f * DEGREES), 0f) - Vec3.X).normalized()
        assertTrue((deep.inner dot closing) > 0.99f, "the crease lies inside the bend: ${deep.inner}")
        assertEquals(deep.amount, deep.around(deep.facing(inside)), 1e-5f)
        assertEquals(0f, deep.around(deep.facing(outside)))
        fun offInner(joint: PosedCreaseJoint) = joint.at + (joint.inner * cos(60f * DEGREES) + (joint.plane cross joint.inner) * sin(60f * DEGREES)) * RADIUS
        assertTrue(deep.around(deep.facing(offInner(deep))) > 0f, "a deep crease reaches 60 degrees round")
        assertEquals(0f, half.around(half.facing(offInner(half))), "a shallower one does not")
    }

    @Test
    fun bendingTheOtherWayOpensTheRestCreaseAndCreasesTheOtherSide() {
        val opened = assertNotNull(PosedCreaseJoint.of(elbow, bentBy(-60f), 1f, REST))
        assertTrue(opened.inner.y < -0.8f, "${opened.inner}")
        val bent = assertNotNull(PosedCreaseJoint.of(straight, bentBy(60f), 1f, REST))
        assertTrue(bent.inner.y > 0.8f, "${bent.inner}")
    }
}

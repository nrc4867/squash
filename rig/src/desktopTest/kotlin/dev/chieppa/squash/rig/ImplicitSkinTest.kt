package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.IN_TURN
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.Capsule
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.field.smoothstep
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.SurfaceMesher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RADIUS = 0.2f
private const val CELL = 0.04f
private const val BLEND = 0.08f

/** A limb of two tubes, an upper one along x from the origin to the elbow at x = 1 and a lower one on to x = 2, skinned by two bones. */
class ImplicitSkinTest {
    private val elbow = Vec3(1f, 0f, 0f)
    private val upper = Capsule(Vec3.ZERO, elbow, RADIUS)
    private val lower = Capsule(elbow, Vec3(2f, 0f, 0f), RADIUS)
    private val field = JoinedField(listOf(JoinedMember(Tube(upper), Vec3.ZERO, false, 0f), JoinedMember(Tube(lower), Vec3.ZERO, false, BLEND, parent = 0, part = 1)))
    private val mesh: SurfaceMesh = SurfaceMesher(CELL).begin(field).let { run ->
        while (true) (run.next() ?: break).forEach { it() }
        run.mesh
    }
    private val corners = mesh.vertexCount
    private val arm = PartId("arm")
    private val rig = Rig(
        listOf(
            Bone(BoneName(arm, "base"), null, Vec3.ZERO, Vec3.X, 1f, Vec3.Z, null, false),
            Bone(BoneName(arm, "elbow"), BoneName(arm, "base"), elbow, Vec3.X, 1f, Vec3.Z, null, false),
        ),
        emptyList(),
    )
    private val skin = SurfaceSkin(
        IntArray(corners * MAX_INFLUENCES) { i -> if (i % MAX_INFLUENCES < 2) i % MAX_INFLUENCES else -1 },
        FloatArray(corners * MAX_INFLUENCES) { i ->
            val lowerShare = smoothstep(1f - RADIUS, 1f + RADIUS, mesh.positions[(i / MAX_INFLUENCES) * 3])
            when (i % MAX_INFLUENCES) {
                0 -> 1f - lowerShare
                1 -> lowerShare
                else -> 0f
            }
        },
        BooleanArray(mesh.quadCount),
    )
    private val surface = SkinSurface(field, CELL, mesh.positions, mesh.normals, mesh.triangles, mesh.winners, mesh.held, mesh.heldShares, listOf(intArrayOf(0), intArrayOf(1)))

    /** The elbow bent by 90 degrees, the lower tube turned from x toward y. */
    private val bent = BoneFrames(listOf(Affine.IDENTITY, Affine(Mat3(Vec3.Y, Vec3.X * -1f, Vec3.Z), elbow - Mat3(Vec3.Y, Vec3.X * -1f, Vec3.Z) * elbow)), emptyList())

    private fun blended(frames: BoneFrames): FloatArray = FloatArray(corners * 3).also { out ->
        for (v in 0 until corners) {
            val p = Vec3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2])
            var sum = Vec3.ZERO
            for (slot in 0 until 2) sum += frames.transforms[skin.bones[v * MAX_INFLUENCES + slot]](p) * skin.weights[v * MAX_INFLUENCES + slot]
            out[v * 3] = sum.x
            out[v * 3 + 1] = sum.y
            out[v * 3 + 2] = sum.z
        }
    }

    private suspend fun refit(frames: BoneFrames): Pair<FloatArray, Refit?> {
        val positions = blended(frames)
        return positions to ImplicitSkin(rig, surface, skin).refit(frames, positions, IN_TURN)
    }

    private fun point(positions: FloatArray, v: Int) = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])

    /** The plain union of the two tubes as [bent] poses them, the lower read back through its bone. */
    private fun posedUnion(p: Vec3): Float {
        val back = Mat3(Vec3.Y * -1f, Vec3.X, Vec3.Z)
        return minOf(upper.distance(p), lower.distance(back * (p - elbow) + elbow))
    }

    /** How near the elbow the outer corner of the bend comes: the least distance from it of the vertices past it on the outside. */
    private fun outerReach(positions: FloatArray): Float =
        (0 until corners).map { point(positions, it) }.filter { it.x > elbow.x && it.y < 0f }.minOf { (it - elbow).length }

    @Test
    fun `the rest pose moves no vertex`() = runTest {
        val rest = BoneFrames(listOf(Affine.IDENTITY, Affine.IDENTITY), emptyList())
        val (positions, refit) = refit(rest)
        assertNull(refit)
        assertTrue(positions.contentEquals(blended(rest)))
    }

    @Test
    fun `a right-angle bend keeps the outer corner round`() = runTest {
        val lbs = outerReach(blended(bent))
        val (positions, _) = refit(bent)
        val implicit = outerReach(positions)
        assertTrue(lbs < RADIUS * 0.85f, "linear blend collapses the outer corner to $lbs")
        assertTrue(implicit > RADIUS * 0.9f, "the outer corner comes $implicit from the elbow, under the tubes' radius $RADIUS")
    }

    @Test
    fun `a right-angle bend creases inside without skin buried in either tube`() = runTest {
        val buried = { positions: FloatArray -> (0 until corners).minOf { posedUnion(point(positions, it)) } }
        assertTrue(buried(blended(bent)) < -2f * CELL, "linear blend buries skin in the inner bend")
        val (positions, _) = refit(bent)
        assertTrue(buried(positions) > -0.5f * CELL, "skin lies ${-buried(positions)} inside the posed tubes")
    }

    @Test
    fun `a refit offsets vertices in place and leaves the rigid stretches alone`() = runTest {
        val before = blended(bent)
        val (positions, refit) = refit(bent)
        val made = checkNotNull(refit)
        assertEquals(corners * 3, positions.size)
        val offsets = made.offsetsOf(corners)
        for (v in 0 until corners) {
            val shift = Vec3(offsets[v * 3], offsets[v * 3 + 1], offsets[v * 3 + 2])
            assertEquals(0f, (point(before, v) + shift - point(positions, v)).length, 1e-5f)
            val x = mesh.positions[v * 3]
            if (x < 0.5f || x > 1.5f) assertEquals(0f, shift.length, "vertex $v at x $x lies on a rigid stretch")
            assertTrue(shift.length < RADIUS, "vertex $v moved ${shift.length}")
        }
    }

    private class Tube(private val capsule: Capsule) : Solid {
        override fun distance(p: Vec3): Float = capsule.distance(p)
        override val low: Vec3 get() = capsule.low
        override val high: Vec3 get() = capsule.high
    }
}

package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.IN_TURN
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.Capsule
import dev.chieppa.squash.geom.field.JoinAngles
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.geom.surface.HELD
import dev.chieppa.squash.geom.surface.SurfaceMesh
import dev.chieppa.squash.geom.surface.SurfaceMesher
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

private const val CELL = 0.03f
private const val BLEND = 0.1f

/**
 * A limb hanging beside a body whose axis lies [offset] from the body's along z, of [radius], joined to it by a blend the rest field
 * shares by the angle between their gradients, as a foreleg beside a chest or a short sleeve beside a top, skinned rigidly by one bone
 * each.
 */
private class HangingLimb(offset: Float, radius: Float) {
    val pivot = Vec3(0f, 0.9f, offset)
    val body = Capsule(Vec3.ZERO, Vec3(0f, 1f, 0f), 0.2f)
    private val bodyMember = JoinedMember(Tube(body), Vec3.ZERO, false, 0f)
    val limbMember = JoinedMember(Tube(Capsule(pivot, Vec3(0f, 0.3f, offset), radius)), Vec3.ZERO, false, BLEND, parent = 0, part = 1).let { plain ->
        JoinedMember(plain.solid, plain.origin, false, BLEND, parent = 0, part = 1, angles = JoinAngles.of(plain, bodyMember, BLEND))
    }
    val field = JoinedField(listOf(bodyMember, limbMember))
    val mesh: SurfaceMesh = SurfaceMesher(CELL).begin(field).let { run ->
        while (true) (run.next() ?: break).forEach { it() }
        run.mesh
    }
    val corners = mesh.vertexCount
    val rig = Rig(
        listOf(
            Bone(BoneName(PartId("torso"), "base"), null, Vec3.ZERO, Vec3.Y, 1f, Vec3.Z, null, false),
            Bone(BoneName(PartId("arm"), "base"), BoneName(PartId("torso"), "base"), pivot, Vec3.Y * -1f, 0.6f, Vec3.X, null, false),
        ),
        emptyList(),
    )
    val skin = SurfaceSkin(
        IntArray(corners * MAX_INFLUENCES) { i -> if (i % MAX_INFLUENCES == 0) mesh.winners[i / MAX_INFLUENCES] else -1 },
        FloatArray(corners * MAX_INFLUENCES) { i -> if (i % MAX_INFLUENCES == 0) 1f else 0f },
        BooleanArray(mesh.quadCount),
    )
    val surface = SkinSurface(field, CELL, mesh.positions, mesh.normals, mesh.triangles, mesh.winners, mesh.held, mesh.heldShares, listOf(intArrayOf(0), intArrayOf(1)))

    /** The limb turned about its pivot by [degrees] about x, its lower end swinging toward -z, into the body for a positive turn. */
    fun turned(degrees: Float): BoneFrames {
        val a = Math.toRadians(degrees.toDouble())
        val r = Mat3(Vec3.X, Vec3(0f, cos(a).toFloat(), sin(a).toFloat()), Vec3(0f, -sin(a).toFloat(), cos(a).toFloat()))
        return BoneFrames(listOf(Affine.IDENTITY, Affine(r, pivot - r * pivot)), emptyList())
    }

    fun blended(frames: BoneFrames): FloatArray = FloatArray(corners * 3).also { out ->
        for (v in 0 until corners) {
            val p = frames.transforms[skin.bones[v * MAX_INFLUENCES]](point(mesh.positions, v))
            out[v * 3] = p.x
            out[v * 3 + 1] = p.y
            out[v * 3 + 2] = p.z
        }
    }

    /** Whether corner [v] holds any share of the body. */
    fun holdsBody(v: Int): Boolean = (0 until HELD).any { mesh.held[v * HELD + it] == 0 && mesh.heldShares[v * HELD + it] > 0f }
}

private fun point(positions: FloatArray, v: Int) = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])

private class Tube(private val capsule: Capsule) : Solid {
    override fun distance(p: Vec3): Float = capsule.distance(p)
    override val low: Vec3 get() = capsule.low
    override val high: Vec3 get() = capsule.high
}

class ImplicitSkinJoinTest {
    @Test
    fun `each corner's level at rest is the surface it was meshed on, in a shallow join's fillet too`() {
        val limb = HangingLimb(offset = 0.09f, radius = 0.13f)
        val posedField = PosedField(limb.surface, limb.skin, limb.rig)
        val members = VertexMembers(limb.surface, limb.skin, posedField)
        val reader = PosedFieldReader(posedField, CELL * 0.25f)
        val joins = RestJoins(limb.corners)
        var filleted = 0
        var worst = 0f
        for (v in 0 until limb.corners) {
            val p = point(limb.mesh.positions, v)
            if (limb.limbMember.blendAt(p) in BLEND * 0.2f..BLEND * 0.8f) filleted++
            val level = reader.readRest(posedField.rest, p, members.sets, v * MOST_MEMBERS, members.counts[v], joins)
            worst = maxOf(worst, abs(level - limb.field.value(p)))
        }
        assertTrue(filleted > 0, "some corners lie where the rest field shares the blend by the angle")
        assertTrue(worst < CELL * 0.05f, "a corner's level at rest lies $worst off the field it was meshed on")
    }

    @Test
    fun `corners linear blend buries in the body stay where it leaves them`() = runTest {
        val limb = HangingLimb(offset = 0.25f, radius = 0.12f)
        val frames = limb.turned(25f)
        val positions = limb.blended(frames)
        val before = positions.copyOf()
        ImplicitSkin(limb.rig, limb.surface, limb.skin).refit(frames, positions, IN_TURN)
        val buried = (0 until limb.corners).filter { v -> !limb.holdsBody(v) && limb.body.distance(point(before, v)) < -CELL }
        assertTrue(buried.isNotEmpty(), "the turn buries some of the limb in the body")
        for (v in buried) assertTrue((point(positions, v) - point(before, v)).length == 0f, "buried corner $v was moved onto the body")
    }
}

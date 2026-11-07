package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ResetPose
import dev.chieppa.squash.document.SetBoneAngle
import dev.chieppa.squash.geom.RenderMesh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPoseTest {
    private val leg = PartId("back-leg-l")

    @Test
    fun theRestPoseDrawsEveryPartsRestMeshItself() = runTest {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionPose(session).posed
        runCurrent()
        assertTrue(session.graph.settled.value)
        val assembled = checkNotNull(session.assembly.output.value)
        val posed = checkNotNull(node.output.value)
        for (part in assembled.parts.filter { it.id != null }) assertSame(part.surface.mesh, posed.meshes[part.id], "${part.id}")
    }

    @Test
    fun aTurnedBoneMovesItsPartAndSettles() = runTest {
        val session = CharacterSession(savedFennec(), shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val node = SessionPose(session).posed
        runCurrent()
        val rest = checkNotNull(node.output.value).meshes.getValue(leg)
        session.send(SetBoneAngle(leg, "knee", 60f, bothSides = true))
        assertFalse(session.graph.settled.value)
        runCurrent()
        assertTrue(session.graph.settled.value)
        val posed = checkNotNull(node.output.value)
        val bent = posed.meshes.getValue(leg)
        assertNotSame(rest, bent)
        assertNotSame(rest, posed.meshes.getValue(PartId("back-leg-r")))
        assertSame(checkNotNull(session.assembly.output.value).part(PartId("front-leg-l"))?.surface?.mesh, posed.meshes[PartId("front-leg-l")])
        assertTrue((0 until bent.vertexCount).any { (bent.position(it) - rest.position(it)).length > 1e-3f })
        assertNormalsAreUnit(bent, rest)
        session.send(ResetPose)
        runCurrent()
        assertSame(rest, checkNotNull(node.output.value).meshes.getValue(leg))
    }

    @Test
    fun aPosedMeshSitsWhereThePosedVerticesAre() {
        val posed = fennec.rig.posed(dev.chieppa.squash.document.Pose(listOf(dev.chieppa.squash.document.BonePose(leg, "hip", 30f))))
        val part = checkNotNull(fennec.rig.part(leg))
        val mesh = posed.meshes.getValue(leg)
        val body = posed.parts.getValue(leg)
        for (v in 0 until mesh.vertexCount) {
            val at = mesh.position(v) + part.origin
            assertEquals(body[v * 3], at.x, 1e-5f)
            assertEquals(body[v * 3 + 1], at.y, 1e-5f)
            assertEquals(body[v * 3 + 2], at.z, 1e-5f)
        }
    }

    /** Every normal [rest] gives a direction stays one unit long in [mesh]. */
    private fun assertNormalsAreUnit(mesh: RenderMesh, rest: RenderMesh) {
        for (v in 0 until mesh.vertexCount) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
            val r = rest.vertices
            if (r[at] == 0f && r[at + 1] == 0f && r[at + 2] == 0f) continue
            val n = mesh.vertices
            val length = kotlin.math.sqrt(n[at] * n[at] + n[at + 1] * n[at + 1] + n[at + 2] * n[at + 2])
            assertTrue(abs(length - 1f) < 1e-4f, "normal $v is $length long")
        }
    }
}

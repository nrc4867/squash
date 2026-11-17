package dev.chieppa.squash.screens.surface.rig

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.cat.savedCat
import dev.chieppa.squash.screens.restSurface
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.squash.screens.woman.WOMAN_WALK
import dev.chieppa.squash.screens.woman.savedWoman
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A joint that lies this deep inside the body at rest, in body units, is one the surface wraps. */
private const val WRAPPED_DEPTH = -0.01f

/** The direction of the ray that counts a point's crossings of the surface, tilted off every axis so it meets no edge. */
private val RAY = Vec3(1f, 0.0137f, 0.0071f)

/** The Rig stage's joint dots against the body they are drawn over, at rest and in a pose, with the display pose under both. */
class RigJointsTest {
    private val work = TestWork()

    private class Shown(val rig: Rig, val joints: List<Vec3>, val surface: RenderMesh, val wrapped: List<Int>)

    private fun shown(document: SquashDocument, pose: Pose?): Shown {
        val session = CharacterSession(document, TemplateLibrary(shipped.parts, shipped.plans), work.scope, work.dispatcher)
        val posed = SessionPose(session)
        if (pose != null) session.send(SetPose(pose))
        work.settle(session)
        val rig = assertNotNull(posed.rig.rig.output.value)
        val surface = assertNotNull(posed.surface.posed.output.value?.posed?.surface)
        val field = session.restSurface.field.field
        val wrapped = rig.bones.indices.filter { field.value(rig.bones[it].pivot) < WRAPPED_DEPTH }
        return Shown(rig, jointPoints(rig, session.document.value.character, null), surface.render, wrapped)
    }

    private fun assertJointsInside(shown: Shown, name: String) {
        assertTrue(shown.wrapped.size > shown.rig.bones.size / 2, "$name: ${shown.wrapped.size} of ${shown.rig.bones.size}: most joints lie inside the rest body")
        val outside = shown.wrapped.filter { !contains(shown.surface, shown.joints[it]) }.map { shown.rig.bones[it].name }
        assertEquals(emptyList(), outside, "$name: joints the shown body does not wrap")
    }

    @Test
    fun theWomansJointsLieInsideHerBodyInTheDisplayPose() = assertJointsInside(shown(savedWoman(), null), "woman at rest")

    @Test
    fun theWomansJointsLieInsideHerBodyInAPoseOverTheDisplayPose() = assertJointsInside(shown(savedWoman(), WOMAN_WALK), "woman walking")

    @Test
    fun theCatsJointsLieInsideItsBodyInTheDisplayPose() = assertJointsInside(shown(savedCat(), null), "cat at rest")

    /** Whether [point] lies inside the closed [mesh]: a ray from it crosses its triangles an odd number of times. */
    private fun contains(mesh: RenderMesh, point: Vec3): Boolean {
        var crossings = 0
        for (t in 0 until mesh.indices.size / 3) {
            val a = mesh.position(mesh.indices[t * 3])
            val b = mesh.position(mesh.indices[t * 3 + 1])
            val c = mesh.position(mesh.indices[t * 3 + 2])
            if (hits(point, a, b, c)) crossings++
        }
        return crossings % 2 == 1
    }

    /** Moller-Trumbore: whether the ray from [origin] along [RAY] meets the triangle [a], [b], [c] ahead of it. */
    private fun hits(origin: Vec3, a: Vec3, b: Vec3, c: Vec3): Boolean {
        val e1 = b - a
        val e2 = c - a
        val p = RAY.cross(e2)
        val det = e1.dot(p)
        if (kotlin.math.abs(det) < 1e-12f) return false
        val from = origin - a
        val u = from.dot(p) / det
        if (u < 0f || u > 1f) return false
        val q = from.cross(e1)
        val v = RAY.dot(q) / det
        if (v < 0f || u + v > 1f) return false
        return e2.dot(q) / det > 0f
    }
}

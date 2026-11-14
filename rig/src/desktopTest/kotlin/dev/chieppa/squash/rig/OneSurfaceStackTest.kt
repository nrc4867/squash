package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.rig.StackFixture.assertClose
import dev.chieppa.squash.rig.StackFixture.assertSameGarment
import dev.chieppa.squash.rig.StackFixture.assertSameLayer
import dev.chieppa.squash.rig.StackFixture.stackSession
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.document.SetParameter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A one surface with everything on it (see [StackFixture]). An edit the graph builds again where it reaches gives the same surface, paint,
 * eyes, garments, hidden skin and weights as the whole stack built afresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OneSurfaceStackTest {
    private val leg = StackFixture.leg

    @Test
    fun anEditBuiltWhereItReachesMatchesTheWholeStackBuiltAfresh() = runTest {
        val session = stackSession()
        val pose = SessionPose(session)
        session.send(SetParameter(leg.id, "size", EDITED_SIZE))
        runCurrent()
        check(session.graph.settled.value)
        val edited = assertNotNull(session.characterSurface.output.value?.surface)
        assertTrue(assertNotNull(edited.mesh.change).kept > 0, "the edit keeps vertices the leg does not reach")
        val placed = assertNotNull(session.placed.output.value)
        val fresh = assertNotNull(SurfaceBuilder().build(placed))
        assertContentEquals(fresh.mesh.triangles, edited.mesh.triangles)
        assertClose(fresh.mesh.positions, edited.mesh.positions)

        val character = session.document.value.character
        val shapes = character.parts.mapNotNull { part -> session.heldShapes(part.id).output.value?.let { part.id to it } }.toMap()
        val freshPaint = SurfacePaint.of(fresh, shapes) + SurfaceEyes.of(fresh, character.paintedEyes)
        val editedPaint = assertNotNull(session.surfacePaint.output.value?.paint)
        assertEquals(freshPaint.layers.size, editedPaint.layers.size)
        freshPaint.layers.zip(editedPaint.layers).forEach { (a, b) -> assertSameLayer(a, b) }
        assertEquals(freshPaint.marks.keys, editedPaint.marks.keys)

        val garments = GarmentBuilder().start(fresh.field, fresh.mesh, placed, surfaceCovers(character))
        while (true) (garments.next() ?: break).forEach { it() }
        val freshHiding = HiddenSkin(fresh, garments.garments).run()
        val freshWorn = freshHiding.worn
        val dress = assertNotNull(session.oneSurface.dress.output.value?.dress)
        assertEquals(freshWorn.size, dress.garments.size)
        freshWorn.zip(dress.garments).forEach { (a, b) -> assertSameGarment(a, b) }
        assertContentEquals(assertNotNull(freshHiding.skin).triangles, assertNotNull(dress.skin).triangles)

        val skin = assertNotNull(pose.surface.skin.output.value)
        val rig = skin.rig
        val freshSkin = SurfaceSkin.of(rig, fresh)
        assertContentEquals(freshSkin.bones, assertNotNull(skin.skin).bones)
        assertClose(freshSkin.weights, assertNotNull(skin.skin).weights)
        freshWorn.zip(skin.garments).forEach { (garment, weights) -> assertContentEquals(SurfaceSkin.of(rig, garment.surface).bones, weights.bones) }
    }

    @Test
    fun surfaceWorkOnTheOneSurfaceFollowsItIntoAPose() = runTest {
        val session = stackSession()
        SessionPose(session)
        val stripe = MarkId("stripe")
        session.send(AddMarks(leg.id, listOf(Mark(stripe, listOf(0.3f, 0.5f, 0.7f).map { Anchor(leg.id, BODY_PATCH, it, 0.25f) }))))
        session.send(SetPose(Pose(listOf(BonePose(leg.id, "knee", 60f)))))
        runCurrent()
        check(session.graph.settled.value)
        val posed = assertNotNull(session.oneSurface.posed.value?.output?.value?.posed).surface
        val rest = assertNotNull(session.bodySurfaceWork.output.value?.parts?.get(leg.id)).marks.first { it.id == stripe }.points
        val shown = assertNotNull(session.posedSurfaceWork.output.value?.parts?.get(leg.id)).marks.first { it.id == stripe }.points
        val cell = posed.mesh.cell
        assertTrue((shown.last().position - rest.last().position).length > cell, "the bent knee carries the stroke's end")
        for (point in shown) {
            val nearest = (0 until posed.mesh.vertexCount).minOf { v ->
                (Vec3(posed.positions[v * 3], posed.positions[v * 3 + 1], posed.positions[v * 3 + 2]) - point.position).length
            }
            assertTrue(nearest < cell, "${point.anchor} lies on the posed surface, ${nearest} from its nearest vertex")
        }
    }
}

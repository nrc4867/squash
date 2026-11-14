package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.GarmentBuilder
import dev.chieppa.squash.character.surface.HiddenSkin
import dev.chieppa.squash.character.surface.PaintLayer
import dev.chieppa.squash.character.surface.SurfaceBuilder
import dev.chieppa.squash.character.surface.SurfaceEyes
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.character.surface.surfaceCovers
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOneSurface
import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.Wardrobe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far a value the edited stack laid may lie from the one a fresh build lays. */
private const val CLOSE = 1e-5f

/**
 * A one surface with everything on it: a torso, a head with painted eyes and a front leg, worn under a sweater over the torso and the
 * leg. An edit the graph builds again where it reaches gives the same surface, paint, eyes, garments, hidden skin and weights as the
 * whole stack built afresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OneSurfaceStackTest {
    private val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 2))
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("head"), 2), SocketRef(torso.id, "neck"))
    private val leg = PartInstance(PartId("leg-l"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"), pair = PairId("front-legs"), side = Side.LEFT)
    private val sweater = GarmentId("sweater")
    private val outfit = OutfitId("winter")

    private fun TestScope.stackSession(): Pair<CharacterSession, SessionPose> {
        val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
        val session = CharacterSession(AddParts(listOf(torso, head, leg)).apply(empty).document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val pose = SessionPose(session)
        val slot = GarmentSlot(GarmentSlotId("body"), "Body")
        val garment = Garment(sweater, "Sweater", slot.id, branches = listOf(GarmentBranch(torso.id, 0.1f, 0.6f), GarmentBranch(leg.id, 0f, 0.5f)))
        session.send(SetOneSurface(true))
        session.send(SetPaintedEye("eyes", PaintedEye("eyes", Anchor(head.id, BODY_PATCH, 0.6f, 0.185f), EyeLook())))
        session.send(SetWardrobe(Wardrobe(listOf(garment), listOf(slot), listOf(Outfit(outfit, "Winter", mapOf(slot.id to sweater))), outfit)))
        runCurrent()
        check(session.graph.settled.value)
        return session to pose
    }

    @Test
    fun anEditBuiltWhereItReachesMatchesTheWholeStackBuiltAfresh() = runTest {
        val (session, pose) = stackSession()
        session.send(SetParameter(leg.id, "size", 1.02f))
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
        val (session, _) = stackSession()
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

    private fun assertSameLayer(a: PaintLayer, b: PaintLayer) {
        assertEquals(a.slot, b.slot)
        assertContentEquals(a.cut.triangles, b.cut.triangles)
        assertContentEquals(a.cut.from, b.cut.from)
        assertClose(a.cut.t, b.cut.t)
    }

    private fun assertSameGarment(a: SurfaceGarment, b: SurfaceGarment) {
        assertSameSurface(a.surface, b.surface)
        assertClose(a.coverage, b.coverage)
        assertContentEquals(a.cut.triangles, b.cut.triangles)
        assertEquals(a.hems.size, b.hems.size)
    }

    private fun assertSameSurface(a: CharacterSurface, b: CharacterSurface) {
        assertContentEquals(a.mesh.triangles, b.mesh.triangles)
        assertClose(a.mesh.positions, b.mesh.positions)
    }

    private fun assertClose(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertEquals(expected[i], actual[i], CLOSE, "value $i")
    }
}

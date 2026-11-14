package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.PaintLayer
import dev.chieppa.squash.character.surface.SurfaceGarment
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
import dev.chieppa.squash.document.SetPaintedEye
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** How far a value an edited stack laid may lie from the one a fresh build lays. */
internal const val CLOSE = 1e-5f

/** The leg's size an edit sets, a little past its own. */
internal const val EDITED_SIZE = 1.02f

/** A one surface with everything on it: a torso, a head with painted eyes and a front leg, worn under a sweater over the torso and the leg. */
internal object StackFixture {
    val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 2))
    val head = PartInstance(PartId("head"), TemplateRef(TemplateId("head"), 2), SocketRef(torso.id, "neck"))
    val leg = PartInstance(PartId("leg-l"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"), pair = PairId("front-legs"), side = Side.LEFT)

    /** A paw on the leg's end, for a garment the paw comes out through. */
    val paw = PartInstance(PartId("paw-l"), TemplateRef(TemplateId("paw"), 1), SocketRef(leg.id, "paw"), pair = PairId("front-paws"), side = Side.LEFT)

    /** The character's session, settled with everything on, the sweater over the leg down to [legTo], and with [withPaw] a paw on the leg. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun TestScope.stackSession(legTo: Float = 0.5f, withPaw: Boolean = false): CharacterSession {
        val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
        val session = CharacterSession(AddParts(listOf(torso, head, leg) + listOfNotNull(paw.takeIf { withPaw })).apply(empty).document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val slot = GarmentSlot(GarmentSlotId("body"), "Body")
        val sweater = GarmentId("sweater")
        val outfit = OutfitId("winter")
        val garment = Garment(sweater, "Sweater", slot.id, branches = listOf(GarmentBranch(torso.id, 0.1f, 0.6f), GarmentBranch(leg.id, 0f, legTo)))
        session.send(SetOneSurface(true))
        session.send(SetPaintedEye("eyes", PaintedEye("eyes", Anchor(head.id, BODY_PATCH, 0.6f, 0.185f), EyeLook())))
        session.send(SetWardrobe(Wardrobe(listOf(garment), listOf(slot), listOf(Outfit(outfit, "Winter", mapOf(slot.id to sweater))), outfit)))
        runCurrent()
        check(session.graph.settled.value)
        return session
    }

    fun assertClose(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) assertEquals(expected[i], actual[i], CLOSE, "value $i")
    }

    fun assertSameLayer(a: PaintLayer, b: PaintLayer) {
        assertEquals(a.slot, b.slot)
        assertContentEquals(a.cut.triangles, b.cut.triangles)
        assertContentEquals(a.cut.from, b.cut.from)
        assertClose(a.cut.t, b.cut.t)
    }

    fun assertSameGarment(a: SurfaceGarment, b: SurfaceGarment) {
        assertSameSurface(a.surface, b.surface)
        assertClose(a.coverage, b.coverage)
        assertContentEquals(a.cut.triangles, b.cut.triangles)
        assertEquals(a.hems.size, b.hems.size)
    }

    fun assertSameSurface(a: CharacterSurface, b: CharacterSurface) {
        assertContentEquals(a.mesh.triangles, b.mesh.triangles)
        assertClose(a.mesh.positions, b.mesh.positions)
    }
}

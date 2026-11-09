package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Wardrobe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A worn garment's change to a part reaches its build and its place, and taking the garment off gives the part back exactly. */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyChangeBuildTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val leg = PartId("leg")
    private val paw = PartId("paw")
    private val boots = GarmentId("boots")
    private val feet = GarmentSlot(GarmentSlotId("feet"), "Feet")
    private val outfit = OutfitId("out")

    private fun ref(id: String) = library.latest(TemplateId(id))!!.ref

    private fun TestScope.session(): CharacterSession {
        val parts = listOf(
            PartInstance(torso, ref("torso")),
            PartInstance(leg, ref("leg"), SocketRef(torso, "front-leg")),
            PartInstance(paw, ref("paw"), SocketRef(leg, "paw")),
        )
        val document = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1), parts))
        val session = CharacterSession(document, library, backgroundScope, StandardTestDispatcher(testScheduler))
        session.send(dev.chieppa.squash.document.SetParameter(paw, "toes", 4f))
        runCurrent()
        return session
    }

    private fun CharacterSession.wear(change: BodyPartChange, scope: TestScope) {
        val garment = Garment(boots, "Boots", feet.id, changes = mapOf(paw to change))
        send(SetWardrobe(Wardrobe(listOf(garment), listOf(feet), listOf(Outfit(outfit, "Out", mapOf(feet.id to boots))), outfit)))
        scope.runCurrent()
        check(graph.settled.value)
    }

    private fun CharacterSession.pawPatches() = assembly.output.value?.part(paw)?.surface?.patches?.map { it.name }

    @Test
    fun aRedrawDropsTheToesAndTakingItOffBringsThemBack() = runTest {
        val session = session()
        val bare = assertNotNull(session.pawPatches())
        assertTrue(bare.any { it.startsWith("toes:") }, "$bare")
        session.wear(BodyPartChange.Redraw(PartData(params = mapOf("toes" to 0f))), this)
        assertTrue(assertNotNull(session.pawPatches()).none { it.startsWith("toes:") })
        session.send(ShowOutfit(null))
        runCurrent()
        assertEquals(bare, session.pawPatches())
    }

    @Test
    fun aHiddenPartIsNotPlaced() = runTest {
        val session = session()
        session.wear(BodyPartChange.Hide, this)
        assertNull(session.assembly.output.value?.part(paw))
        assertNotNull(session.assembly.output.value?.part(leg))
    }

    @Test
    fun aSwapPlacesThePartOnItsOtherTemplate() = runTest {
        val session = session()
        session.wear(BodyPartChange.Swap(ref("foot")), this)
        assertEquals(TemplateId("foot"), session.assembly.output.value?.part(paw)?.template?.id)
    }
}

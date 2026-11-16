package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnRow
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The biped plan rests its arms in A-pose, and a part's sockets keep their place inside it whatever a worn garment draws over it. See HLD: data-model, Placing parts. */
@OptIn(ExperimentalCoroutinesApi::class)
class APoseTest {
    private val plan = assertNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("biped") })
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val girth = 0.5f

    private fun torsoOnly(): Pair<SquashDocument, PartId> {
        val new = newCharacter(plan, shipped, "Biped")
        return new.commands.fold(new.document) { document, command -> command.apply(document).document } to new.root
    }

    /** The biped with its arms placed on the shoulders as Add part places them. */
    private fun armed(): Pair<SquashDocument, List<PartId>> {
        val (document, torso) = torsoOnly()
        val shoulder = openSockets(document, plan, shipped).first { it.parent == torso && it.name == "shoulder" }
        val placed = placeOnRow(shoulder, emptyList(), assertNotNull(shipped.template(TemplateId("arm"))), document)
        val parts = placed.flatMap { it.first }
        val armed = AddParts(parts, pairs = placed.flatMap { (made, data) -> made.mapNotNull { it.pair }.map { it to data } }.toMap()).apply(document).document
        return armed to parts.map { it.id }
    }

    @Test
    fun addPartPlacesAnArmLeaningOutAsTheShoulderSpreads() {
        val (document, arms) = armed()
        val left = assertNotNull(document.character.part(arms.first()))
        assertEquals(Side.LEFT, left.side)
        assertEquals(45f, document.character.resolvedShape(left).params[PlacementParams.OUTWARD])
    }

    @Test
    fun aPlannedArmRestsInAPose() = runTest {
        val (document, _) = torsoOnly()
        val session = session(document)
        val arms = assertNotNull(session.assembly.output.value).parts.filter { it.planned && it.role == Role.ARM }
        assertEquals(2, arms.size)
        arms.forEach { arm ->
            val rest = arm.template.axis.rest.let { Vec3(it.x, it.y, it.z) }.normalized()
            assertEquals(0.707f, abs(rest.z), 0.02f, "$rest")
        }
    }

    @Test
    fun aGarmentThatThickensTheTorsoLeavesTheArmsWhereTheyWere() = runTest {
        val (document, arms) = armed()
        val torso = assertNotNull(document.character.parts.firstOrNull { it.socket == null }).id
        val session = session(document)
        val bare = arms.map { assertNotNull(session.assembly.output.value?.part(it)).origin }
        val bareWidth = assertNotNull(session.assembly.output.value?.part(torso)?.box).size.z
        val slot = GarmentSlot(GarmentSlotId("top"), "Top")
        val coat = Garment(GarmentId("coat"), "Coat", slot.id, changes = mapOf(torso to BodyPartChange.Redraw(PartData(params = mapOf("girth" to girth)))))
        val outfit = OutfitId("out")
        session.send(SetWardrobe(Wardrobe(listOf(coat), listOf(slot), listOf(Outfit(outfit, "Out", mapOf(slot.id to coat.id))), outfit)))
        runCurrent()
        assertTrue(assertNotNull(session.assembly.output.value?.part(torso)?.box).size.z > bareWidth * 1.2f, "the coat widens the torso")
        val worn = arms.map { assertNotNull(session.assembly.output.value?.part(it)).origin }
        bare.zip(worn).forEach { (a, b) -> assertEquals(0f, (a - b).length, 1e-4f, "$a against $b") }
        session.send(ShowOutfit(null))
        runCurrent()
        assertEquals(bare, arms.map { assertNotNull(session.assembly.output.value?.part(it)).origin })
    }

    private fun TestScope.session(document: SquashDocument): CharacterSession {
        val session = CharacterSession(document, library, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        check(session.graph.settled.value)
        return session
    }
}

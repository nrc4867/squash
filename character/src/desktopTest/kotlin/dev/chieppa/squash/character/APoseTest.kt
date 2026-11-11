package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.character.build.withArmsOnBody
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
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellInside
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

/**
 * The biped plan rests its arms in A-pose, a part's sockets keep their place inside it whatever a worn garment draws over it, and a
 * posed arm lies on the body rather than through it. See
 * HLD: data-model, Placing parts.
 */
/** How far into the body the test sinks an arm, and how much of its rings from the root count as its shoulder. */
private const val INTO_BODY = 0.12f
private const val PAST_SHOULDER = 0.2f

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

    @Test
    fun anArmPosedIntoTheTorsoIsPushedOutOntoIt() = runTest {
        val (document, arms) = armed()
        val rest = assertNotNull(session(document).assembly.output.value)
        val left = assertNotNull(rest.part(arms.first()))
        val torso = assertNotNull(rest.parts.firstOrNull { it.socket == null && it.id != null })
        val inward = Vec3(0f, 0f, INTO_BODY)
        val sunk = RenderMesh(left.surface.mesh.vertices.copyOf().also { v ->
            for (i in 0 until left.surface.mesh.vertexCount) v[i * RenderMesh.FLOATS_PER_VERTEX + 2] += inward.z
        }, left.surface.mesh.indices)
        val posed = rest.posedBy(object : PosedParts {
            override val meshes = mapOf(requireNotNull(left.id) to sunk)
            override val turns = emptyMap<PartId, Mat3>()
        })
        val shell = ShellInside(assertNotNull(torso.surface.patches.firstOrNull { it.slices != null })) { torso.origin + torso.surface.shown(it) }
        fun insideAfterShoulder(body: dev.chieppa.squash.character.build.AssembledCharacter): Int {
            val arm = assertNotNull(body.part(requireNotNull(left.id)))
            val patch = arm.surface.patches.single()
            return (0 until patch.nu * patch.nv).filter { it / patch.nv > patch.nu * PAST_SHOULDER }.count { arm.origin + arm.surface.mesh.position(it) in shell }
        }
        assertTrue(insideAfterShoulder(posed) > 0, "the sunk arm lies in the torso")
        assertEquals(0, insideAfterShoulder(posed.withArmsOnBody(rest)))
    }

    private fun TestScope.session(document: SquashDocument): CharacterSession {
        val session = CharacterSession(document, library, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        check(session.graph.settled.value)
        return session
    }
}

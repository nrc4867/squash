package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
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
/**
 * How far into the body the test sinks an arm, and the share of its rings from the root that is its upper arm past its armpit, where
 * the arm may no longer press into the body.
 */
private const val INTO_BODY = 0.12f
private const val PAST_ARMPIT = 0.36f
private const val UPPER_ARM = 0.45f

/** How much faster than evenly from the upper arm to its end a pushed arm may let go anywhere along the way, so it bends no corner. */
private const val MOST_STEEP = 2.5f

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

    /** The biped's left arm sunk [INTO_BODY] into its torso as a pose would leave it, with the rest it was sunk from and the torso's inside. */
    private fun TestScope.sunkArm(): Triple<AssembledCharacter, AssembledCharacter, PartId> {
        val (document, arms) = armed()
        val rest = assertNotNull(session(document).assembly.output.value)
        val left = assertNotNull(rest.part(arms.first()))
        val sunk = RenderMesh(left.surface.mesh.vertices.copyOf().also { v ->
            for (i in 0 until left.surface.mesh.vertexCount) v[i * RenderMesh.FLOATS_PER_VERTEX + 2] += INTO_BODY
        }, left.surface.mesh.indices)
        val posed = rest.posedBy(object : PosedParts {
            override val meshes = mapOf(requireNotNull(left.id) to sunk)
            override val turns = emptyMap<PartId, Mat3>()
        })
        return Triple(rest, posed, requireNotNull(left.id))
    }

    @Test
    fun anArmPosedIntoTheTorsoIsPushedOutOntoItDownToItsUpperArm() = runTest {
        val (rest, posed, left) = sunkArm()
        val torso = assertNotNull(rest.parts.firstOrNull { it.socket == null && it.id != null })
        val shell = ShellInside(assertNotNull(torso.surface.patches.firstOrNull { it.slices != null })) { torso.origin + torso.surface.shown(it) }
        fun insidePastArmpit(body: AssembledCharacter): Int {
            val arm = assertNotNull(body.part(left))
            val patch = arm.surface.patches.first { it.slices != null }
            return (0 until patch.nu * patch.nv).filter { it / patch.nv > patch.nu * PAST_ARMPIT && it / patch.nv < patch.nu * UPPER_ARM }.count { arm.origin + arm.surface.mesh.position(it) in shell }
        }
        assertTrue(insidePastArmpit(posed) > 0, "the sunk arm lies in the torso")
        assertEquals(0, insidePastArmpit(posed.withArmsOnBody(rest)))
    }

    @Test
    fun aPushedArmLetsGoOfTheBodyInOneCurveDownToItsEnd() = runTest {
        val (rest, posed, left) = sunkArm()
        val before = ringMiddles(assertNotNull(posed.part(left)))
        val after = ringMiddles(assertNotNull(posed.withArmsOnBody(rest).part(left)))
        val shifts = before.zip(after).map { (a, b) -> b - a }
        val most = shifts.maxOf { it.length }
        assertTrue(most > 0f, "the arm is pushed")
        assertEquals(0f, shifts.last().length, most * 0.01f, "the arm's end, where the hand hangs, stays where the pose put it")
        val upper = (shifts.size * UPPER_ARM).toInt()
        val even = shifts[upper].length / (shifts.size - 1 - upper)
        for (ring in upper until shifts.size - 1) {
            val step = shifts[ring].length - shifts[ring + 1].length
            assertTrue(step >= -most * 1e-3f, "the push only lets go below the upper arm, ring $ring")
            assertTrue(step <= even * MOST_STEEP, "it lets go over the whole forearm, ring $ring: $step against $even")
        }
    }

    private fun ringMiddles(arm: PlacedPart): List<Vec3> {
        val patch = arm.surface.patches.first { it.slices != null }
        return (0 until patch.nu).map { ring -> (0 until patch.nv).fold(Vec3.ZERO) { sum, column -> sum + arm.surface.mesh.position(patch.index(ring, column)) } * (1f / patch.nv) }
    }

    private fun TestScope.session(document: SquashDocument): CharacterSession {
        val session = CharacterSession(document, library, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        check(session.graph.settled.value)
        return session
    }
}

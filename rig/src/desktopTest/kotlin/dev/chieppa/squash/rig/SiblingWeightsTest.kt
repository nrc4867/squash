package dev.chieppa.squash.rig

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.geom.surface.HELD
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two legs on one trunk, bare and under cloth over both: a vertex follows both legs' bones only where the trunk they grow from holds it
 * too, at the crotch they share, so cloth meeting between the legs further down follows each leg and does not stretch across them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Tag("integration")
class SiblingWeightsTest {
    private val torso = PartInstance(PartId("torso"), TemplateRef(TemplateId("torso"), 2))
    private val left = PartInstance(PartId("leg-l"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"), pair = PairId("front-legs"), side = Side.LEFT)
    private val right = PartInstance(PartId("leg-r"), TemplateRef(TemplateId("leg"), 1), SocketRef(torso.id, "front-leg"), pair = PairId("front-legs"), side = Side.RIGHT)

    @Test
    fun aVertexFollowsBothLegsOnlyWhereTheTrunkHoldsIt() = runTest {
        val empty = SquashDocument(Character(CharacterId("dog"), "Dog", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
        val session = CharacterSession(AddParts(listOf(torso, left, right)).apply(empty).document, shippedLibrary, backgroundScope, StandardTestDispatcher(testScheduler))
        val slot = GarmentSlot(GarmentSlotId("body"), "Body")
        val coat = Garment(
            GarmentId("coat"), "Coat", slot.id,
            branches = listOf(GarmentBranch(torso.id, 0.0f, 0.5f), GarmentBranch(left.id, 0f, 1f), GarmentBranch(right.id, 0f, 1f)),
        )
        val pose = SessionPose(session)
        session.send(SetWardrobe(Wardrobe(listOf(coat), listOf(slot), listOf(Outfit(OutfitId("out"), "Out", mapOf(slot.id to coat.id))), OutfitId("out"))))
        runCurrent()
        check(session.graph.settled.value)
        val held = assertNotNull(pose.surface.skin.output.value)
        val rig = held.rig
        val legOf = rig.bones.map { bone -> bone.name.part.takeIf { it == left.id || it == right.id } }
        val surfaces = listOf(assertNotNull(held.surface) to assertNotNull(held.skin)) + assertNotNull(held.dress).garments.map { it.surface }.zip(held.garments)
        for ((surface, skin) in surfaces) {
            val members = surface.field.field.members
            val torsoRuns = members.indices.filter { surface.field.runs[it].partId == torso.id }.toSet()
            for (v in 0 until surface.mesh.vertexCount) {
                val legs = (0 until MAX_INFLUENCES).mapNotNull { k -> skin.bones[v * MAX_INFLUENCES + k].takeIf { it >= 0 && skin.weights[v * MAX_INFLUENCES + k] > 0f }?.let(legOf::get) }.toSet()
                if (legs.size < 2) continue
                val byTrunk = (0 until HELD).any { k -> surface.mesh.held[v * HELD + k] in torsoRuns }
                assertTrue(byTrunk, "vertex $v follows both legs but the trunk does not hold it")
            }
        }
    }
}

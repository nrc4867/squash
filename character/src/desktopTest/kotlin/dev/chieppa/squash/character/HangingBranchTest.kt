package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.character.build.withHangingPushed
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnRow
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetWardrobe
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Wardrobe
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.cuffPartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.ShellInside
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

/** How far before the torso's start the skirt hangs, in u, how far up it runs, and how far forward the test leans a leg per unit down it. */
private const val HEM = -0.8f
private const val WAIST = 0.3f
private const val STEP = 0.8f

/**
 * A branch that runs on past its part's start hangs free: the legs inside it are covered rather than cut through, and wherever a pose
 * carries a leg into it, it is pushed out clear of the leg. See HLD: data-model, Garments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HangingBranchTest {
    private val plan = assertNotNull(shipped.plans.firstOrNull { it.id == BodyPlanId("biped") })
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val skirt = GarmentId("skirt")

    /** The biped with its legs on the hips and a skirt hanging from its waist, worn. */
    private fun skirted(): Triple<SquashDocument, PartId, List<PartId>> {
        val new = newCharacter(plan, shipped, "Biped")
        val bare = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        val hip = openSockets(bare, plan, shipped).first { it.parent == new.root && it.name == "hip" }
        val placed = placeOnRow(hip, emptyList(), assertNotNull(shipped.template(TemplateId("leg"))), bare)
        val legs = placed.flatMap { it.first }
        val legged = AddParts(legs, pairs = placed.flatMap { (made, data) -> made.mapNotNull { it.pair }.map { it to data } }.toMap()).apply(bare).document
        val slot = GarmentSlot(GarmentSlotId("bottom"), "Bottom")
        val garment = Garment(skirt, "Skirt", slot.id, branches = listOf(GarmentBranch(new.root, HEM, WAIST)))
        val outfit = OutfitId("out")
        val wardrobe = Wardrobe(listOf(garment), listOf(slot), listOf(Outfit(outfit, "Out", mapOf(slot.id to skirt))), outfit)
        return Triple(SetWardrobe(wardrobe).apply(legged).document, new.root, legs.map { it.id })
    }

    @Test
    fun legsInsideAHangingSkirtAreCoveredNotCutThrough() = runTest {
        val (document, torso, legs) = skirted()
        val body = assertNotNull(session(document).assembly.output.value)
        val branch = assertNotNull(body.part(branchPartId(skirt, torso)))
        assertEquals(legs.toSet(), assertNotNull(branch.covering).hangsOver)
        legs.forEach { assertNull(body.part(cuffPartId(skirt, it)), "no cuff round ${it.value}") }
        assertEquals(0, skirtInside(body, branch, legs), "the legs lie inside the skirt at rest")
    }

    @Test
    fun aLegSwungIntoTheSkirtPushesItOut() = runTest {
        val (document, torso, legs) = skirted()
        val rest = assertNotNull(session(document).assembly.output.value)
        val leg = assertNotNull(rest.part(legs.first()))
        val forward = RenderMesh(leg.surface.mesh.vertices.copyOf().also { v ->
            for (i in 0 until leg.surface.mesh.vertexCount) {
                val down = (-leg.surface.mesh.position(i).y).coerceAtLeast(0f)
                v[i * RenderMesh.FLOATS_PER_VERTEX] += down * STEP
            }
        }, leg.surface.mesh.indices)
        val posed = rest.posedBy(object : PosedParts {
            override val meshes = mapOf(requireNotNull(leg.id) to forward)
            override val turns = emptyMap<PartId, Mat3>()
        })
        val branch = branchPartId(skirt, torso)
        assertTrue(skirtInside(posed, assertNotNull(posed.part(branch)), legs) > 0, "the swung leg cuts the skirt")
        val pushed = posed.withHangingPushed()
        assertEquals(0, skirtInside(pushed, assertNotNull(pushed.part(branch)), legs), "the skirt clears the leg")
    }

    /** How many of [branch]'s vertices lie inside any of [legs] in [body]. */
    private fun skirtInside(body: AssembledCharacter, branch: PlacedPart, legs: List<PartId>): Int {
        val insides = legs.map { id -> assertNotNull(body.part(id)).let { leg -> ShellInside(leg.surface.patches.first { it.slices != null }) { leg.origin + leg.surface.shown(it) } } }
        val mesh = branch.surface.mesh
        return (0 until mesh.vertexCount).count { v -> (branch.origin + mesh.position(v)).let { p -> insides.any { p in it } } }
    }

    private fun TestScope.session(document: SquashDocument): CharacterSession {
        val session = CharacterSession(document, library, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        check(session.graph.settled.value)
        return session
    }
}

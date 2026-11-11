package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.character.build.withArmsClearOfSkirts
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
import dev.chieppa.squash.geom.Vec3
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

/** How far before the torso's start the skirt hangs, in u, how far up it runs, how far forward the test leans a leg per unit down it, and how far it lowers an arm, in radians. */
private const val HEM = -0.8f
private const val WAIST = 0.3f
private const val STEP = 0.8f
private const val LOWER = 0.5f

/**
 * A branch that runs on past its part's start hangs free: the legs inside it are covered rather than cut through, wherever a pose
 * carries a leg into it, it is pushed out clear of the leg, and an arm lowered into it turns out clear of it. See HLD: data-model, Garments.
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

    @Test
    fun anArmLoweredIntoTheSkirtTurnsOutClearOfIt() = runTest {
        val (document, torso, _) = skirted()
        val shoulder = openSockets(document, plan, shipped).first { it.parent == torso && it.name == "shoulder" }
        val placed = placeOnRow(shoulder, emptyList(), assertNotNull(shipped.template(TemplateId("arm"))), document)
        val arms = placed.flatMap { it.first }
        val armed = AddParts(arms, pairs = placed.flatMap { (made, data) -> made.mapNotNull { it.pair }.map { it to data } }.toMap()).apply(document).document
        val rest = assertNotNull(session(armed).assembly.output.value)
        val arm = assertNotNull(rest.part(arms.first().id))
        val shell = assertNotNull(rest.part(branchPartId(skirt, torso))).let { ShellInside(it.surface.patches.first()) { p -> it.origin + it.surface.shown(p) } }
        val lowered = listOf(LOWER, -LOWER).map { angle -> arm.turnedAboutRoot(angle) }.maxBy { mesh -> inside(arm, mesh, shell) }
        assertTrue(inside(arm, lowered, shell) > 0, "the lowered arm reaches into the skirt")
        val posed = rest.posedBy(object : PosedParts {
            override val meshes = mapOf(requireNotNull(arm.id) to lowered)
            override val turns = emptyMap<PartId, Mat3>()
        })
        val turned = assertNotNull(posed.withArmsClearOfSkirts(rest).part(requireNotNull(arm.id)))
        assertEquals(0, inside(turned, turned.surface.mesh, shell), "the arm turns out clear of the skirt")
    }

    /** This arm's mesh turned [angle] radians about the body's forward axis through the middle of its root ring. */
    private fun PlacedPart.turnedAboutRoot(angle: Float): RenderMesh {
        val patch = surface.patches.first { it.slices != null }
        val root = (0 until patch.nv).fold(Vec3.ZERO) { sum, c -> sum + surface.shown(patch.point(patch.index(0, c))) } * (1f / patch.nv)
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        val out = surface.mesh.vertices.copyOf()
        for (v in 0 until surface.mesh.vertexCount) {
            val p = surface.mesh.position(v) - root
            val at = v * RenderMesh.FLOATS_PER_VERTEX
            out[at + 1] = root.y + p.y * cos - p.z * sin
            out[at + 2] = root.z + p.y * sin + p.z * cos
        }
        return RenderMesh(out, surface.mesh.indices)
    }

    private fun inside(arm: PlacedPart, mesh: RenderMesh, shell: ShellInside) = (0 until mesh.vertexCount).count { arm.origin + mesh.position(it) in shell }

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

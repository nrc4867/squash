package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.BRANCH_RINGS
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.character.build.hiddenAgain
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.document.cuffPartId
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Less than this, in body units, is staying put. */
private const val STILL = 1e-4f

/** A cap over part of the way round, ears through it with cuffs, and a sleeve filleted into a sweater's body. */
class GarmentJoinsTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val head = PartId("head")
    private val earL = PartId("ear-l")
    private val earR = PartId("ear-r")
    private val legL = PartId("leg-l")
    private val legR = PartId("leg-r")
    private val cap = GarmentId("cap")
    private val sweater = GarmentId("sweater")
    private val twins = mapOf(earL to earR, earR to earL, legL to legR, legR to legL)

    private val body: AssembledCharacter by lazy {
        fun ref(id: String) = library.latest(TemplateId(id))!!.ref
        val placed = listOf(
            AssemblyPart(torso, ref("torso"), null, null, null),
            AssemblyPart(head, ref("head"), SocketRef(torso, "neck"), null, null),
            AssemblyPart(earL, ref("ear"), SocketRef(head, "ear"), PairId("ears"), Side.LEFT),
            AssemblyPart(earR, ref("ear"), SocketRef(head, "ear"), PairId("ears"), Side.RIGHT),
            AssemblyPart(legL, ref("leg"), SocketRef(torso, "front-leg"), PairId("legs"), Side.LEFT),
            AssemblyPart(legR, ref("leg"), SocketRef(torso, "front-leg"), PairId("legs"), Side.RIGHT),
        )
        assembleNow(placed, { PartData() }, plan, library)
    }

    private fun wearing(vararg garments: Garment) = body.dressedIn(Dressing(garments.toList(), twins))

    private val capOnly = Garment(cap, "Cap", branches = listOf(GarmentBranch(head, 0f, 0.45f, fromV = -0.22f, toV = 0.22f)))

    @Test
    fun aCapIsOpenRoundWithItsEdgesAsHems() {
        val shell = assertNotNull(wearing(capOnly).part(branchPartId(cap, head))).surface.patches.single()
        val headPatch = assertNotNull(body.part(head)).surface.patches.first { it.slices != null }
        assertFalse(shell.closedV)
        assertEquals(BRANCH_RINGS, shell.nu)
        assertTrue(shell.nv < headPatch.nv, "${shell.nv} columns over less than the way round")
        assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(shell) { 0 }, setOf(0, shell.nu - 1)))
    }

    @Test
    fun aCapHidesOnlyTheSkinUnderIt() {
        val bare = assertNotNull(body.part(head)).surface.mesh.indices.size
        val covered = assertNotNull(wearing(capOnly).part(head)).surface.mesh.indices.size
        assertTrue(covered < bare, "the crown is hidden")
        assertTrue(covered > bare / 2, "the face and jaw show: $covered of $bare")
    }

    @Test
    fun theEarsComeThroughTheCapWithCuffsAndAHiddenEarStaysIn() {
        val character = wearing(capOnly)
        for (ear in listOf(earL, earR)) assertNotNull(character.part(cuffPartId(cap, ear)), "a cuff round $ear")
        val bare = wearing(capOnly.copy(changes = mapOf(earL to BodyPartChange.Through(cuff = false))))
        assertNull(bare.part(cuffPartId(cap, earL)))
        assertNull(bare.part(cuffPartId(cap, earR)), "a change on one side holds for its twin")
        val hidden = wearing(capOnly.copy(changes = mapOf(earL to BodyPartChange.Hide)))
        assertNull(hidden.part(cuffPartId(cap, earR)))
    }

    @Test
    fun aSleeveFilletsIntoTheSweaterAndTheSweaterStays() {
        val torsoOnly = Garment(sweater, "Sweater", branches = listOf(GarmentBranch(torso, 0.4f, 0.97f)))
        val alone = assertNotNull(wearing(torsoOnly).part(branchPartId(sweater, torso))).surface.patches.single()
        val both = wearing(torsoOnly.copy(branches = torsoOnly.branches + GarmentBranch(legL, 0f, 0.6f)))
        val joined = assertNotNull(both.part(branchPartId(sweater, torso))).surface.patches.single()
        assertTrue((0 until alone.nu * alone.nv).all { (joined.point(it) - alone.point(it)).length < STILL }, "the sweater's body stays still")
        val sleeve = assertNotNull(both.part(branchPartId(sweater, legL)))
        val bareSleeve = assertNotNull(wearing(torsoOnly.copy(branches = listOf(GarmentBranch(legL, 0f, 0.6f)))).part(branchPartId(sweater, legL)))
        val moves = sleeve.surface.patches.single().let { patch -> (0 until patch.nu * patch.nv).map { (patch.point(it) - bareSleeve.surface.patches.single().point(it)).length } }
        assertTrue(moves.any { it > STILL }, "the sleeve moves onto the fillet")
        assertTrue(sleeve.surface.mesh.indices.size < bareSleeve.surface.mesh.indices.size, "the sleeve's top inside the sweater is left out")
    }

    private val torsoBranch = GarmentBranch(torso, 0.4f, 0.97f)
    private val sleeved = Garment(sweater, "Sweater", branches = listOf(torsoBranch, GarmentBranch(legL, 0f, 0.6f)))

    private fun shownFaces(character: AssembledCharacter, part: PartId) = assertNotNull(character.part(part)).surface.mesh.indices.size / 3

    @Test
    fun aLegUnderItsOwnSleeveJoinsTheSweaterByTheFilletAlone() {
        val sleeves = wearing(sleeved)
        for (leg in listOf(legL, legR)) assertNull(sleeves.part(cuffPartId(sweater, leg)), "no cuff round $leg under its own sleeve")
        val unholed = wearing(Garment(sweater, "Sweater", branches = listOf(torsoBranch), changes = mapOf(legL to BodyPartChange.Hide)))
        assertEquals(shownFaces(unholed, branchPartId(sweater, torso)), shownFaces(sleeves, branchPartId(sweater, torso)), "no hole in the sweater's body")
        val sleeveless = wearing(Garment(sweater, "Sweater", branches = listOf(torsoBranch)))
        assertNotNull(sleeveless.part(cuffPartId(sweater, legL)), "a sleeveless sweater still lets the leg through")
        assertTrue(shownFaces(sleeveless, branchPartId(sweater, torso)) < shownFaces(sleeves, branchPartId(sweater, torso)), "with a hole")
    }

    @Test
    fun aLegUnderOneGarmentsSleeveStillComesThroughAnother() {
        val vest = GarmentId("vest")
        val character = wearing(sleeved, Garment(vest, "Vest", branches = listOf(GarmentBranch(torso, 0.45f, 0.95f))))
        for (leg in listOf(legL, legR)) {
            assertNull(character.part(cuffPartId(sweater, leg)))
            assertNotNull(character.part(cuffPartId(vest, leg)), "$leg comes through the vest with a cuff")
        }
    }

    @Test
    fun theSkinUnderAnOwnSleeveStaysHidden() {
        val bare = shownFaces(body, legL)
        val sleeves = wearing(sleeved)
        assertTrue(shownFaces(sleeves, legL) < bare, "the sleeve hides the leg under it")
        val hiding = assertNotNull(assertNotNull(sleeves.part(legL)).hiding)
        assertTrue(branchPartId(sweater, torso) in hiding.inside, "the sweater's body hides what of the leg lies in it, at rest and posed")
        assertTrue(hiding.whole.isEmpty(), "and no hole leaves the leg whole")
        val carried = carriedTogether(sleeves, sleeves.parts.mapNotNull { it.id }, Vec3(0f, 0.05f, 0.02f))
        assertEquals(shownFaces(sleeves, legL), shownFaces(carried, legL), "the leg carried with the sweater stays hidden as it was")
    }

    /** [character] with [parts]' meshes moved by [by] together, as a pose carrying them would show them, and hidden again. */
    private fun carriedTogether(character: AssembledCharacter, parts: List<PartId>, by: Vec3): AssembledCharacter {
        val meshes = parts.associateWith { part ->
            val mesh = assertNotNull(character.part(part)).surface.mesh
            val vertices = mesh.vertices.copyOf()
            for (v in 0 until mesh.vertexCount) {
                val at = v * RenderMesh.FLOATS_PER_VERTEX
                vertices[at] += by.x
                vertices[at + 1] += by.y
                vertices[at + 2] += by.z
            }
            RenderMesh(vertices, mesh.indices)
        }
        val pose = object : PosedParts {
            override val meshes = meshes
            override val turns = emptyMap<PartId, Mat3>()
        }
        return character.posedBy(pose).hiddenAgain(character)
    }
}

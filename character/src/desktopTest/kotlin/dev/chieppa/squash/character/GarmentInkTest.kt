package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.character.build.partGrids
import dev.chieppa.squash.character.build.socketCrease
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NEAR = 1e-4f

/** The lines a garment inks: its hem and cuffs by default, a join only with its switch, each off with its switch. */
class GarmentInkTest {
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

    private fun wearing(garment: Garment) = body.dressedIn(Dressing(listOf(garment), twins))

    private fun inked(character: AssembledCharacter, part: PartId) = socketCrease(character, assertNotNull(character.part(part)))?.runs.orEmpty()

    private val capOnly = Garment(cap, "Cap", branches = listOf(GarmentBranch(head, 0f, 0.45f, fromV = -0.22f, toV = 0.22f)))
    private val sweaterOn = Garment(sweater, "Sweater", branches = listOf(GarmentBranch(torso, 0.4f, 0.97f), GarmentBranch(legL, 0f, 0.6f)))

    @Test
    fun aHemIsInkedByDefaultAndNotWithItsSwitchOff() {
        val hem = inked(wearing(capOnly), branchPartId(cap, head))
        assertTrue(hem.isNotEmpty(), "the cap's edges are inked")
        assertTrue(hem.all { run -> run.all { it.anchor.part == branchPartId(cap, head) } })
        assertEquals(emptyList(), inked(wearing(capOnly.copy(inkHem = false)), branchPartId(cap, head)))
    }

    @Test
    fun aJoinIsInkedOnlyWithItsSwitch() {
        val sleeve = branchPartId(sweater, legL)
        val plain = inked(wearing(sweaterOn), sleeve)
        val joined = inked(wearing(sweaterOn.copy(joins = setOf(legL))), sleeve)
        assertTrue(joined.size > plain.size, "the join adds a line: ${plain.size} runs, then ${joined.size}")
        assertTrue(inked(wearing(sweaterOn.copy(joins = setOf(legL))), branchPartId(sweater, legR)).size > plain.size, "a join holds for both sides")
        assertEquals(emptyList(), inked(wearing(sweaterOn.copy(inkHem = false)), sleeve), "with no hem and no join the sleeve inks nothing")
    }

    @Test
    fun cuffsAreInkedWithBothSwitches() {
        assertTrue(inked(wearing(capOnly), cuffPartId(cap, earL)).isNotEmpty())
        assertEquals(emptyList(), inked(wearing(capOnly.copy(inkCuffs = false)), cuffPartId(cap, earL)))
        val own = capOnly.copy(changes = mapOf(earL to BodyPartChange.Through(inkCuff = false)))
        assertEquals(emptyList(), inked(wearing(own), cuffPartId(cap, earR)), "the change on one side holds for its twin")
    }

    @Test
    fun aCuffReachesItsSizePastWhereThePartComesOut() {
        val short = assertNotNull(assertNotNull(wearing(capOnly).part(cuffPartId(cap, earL))).covering).to
        val long = capOnly.copy(changes = mapOf(earL to BodyPartChange.Through(cuffSize = 0.3f)))
        val reach = assertNotNull(assertNotNull(wearing(long).part(cuffPartId(cap, earL))).covering).to
        assertEquals(0.2f, reach - short, NEAR)
        assertTrue(short < 0.6f, "a short band, reaching $short along the ear")
    }

    @Test
    fun aCuffHidesTheSkinUnderIt() {
        val bare = assertNotNull(body.part(earL)).surface.mesh.indices.size
        val cuffed = assertNotNull(wearing(capOnly).part(earL)).surface.mesh.indices.size
        assertTrue(cuffed < bare, "the ear's root under its cuff is hidden: $cuffed of $bare")
    }

    @Test
    fun noContourRunsAlongHiddenSkin() {
        val side = { _: Vec3 -> Vec3(0f, 0f, 1f) }
        fun contourPoints(character: AssembledCharacter) = partGrids(assertNotNull(character.part(torso))).sumOf { grid -> grid.contours(side).sumOf { it.points.size } }
        val bare = contourPoints(body)
        val covered = contourPoints(wearing(sweaterOn))
        assertTrue(covered < bare, "the torso's outline under the sweater is left out: $covered of $bare points")
    }

    @Test
    fun anEarThroughWithNoCuffIsInkedWhereItComesOut() {
        val bare = capOnly.copy(changes = mapOf(earL to BodyPartChange.Through(cuff = false)))
        assertTrue(inked(wearing(bare), branchPartId(cap, head)).size > inked(wearing(capOnly), branchPartId(cap, head)).size)
        assertNull(wearing(bare).part(cuffPartId(cap, earL)))
    }
}

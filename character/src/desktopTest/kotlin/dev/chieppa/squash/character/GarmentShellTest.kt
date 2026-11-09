package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.BRANCH_RINGS
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val EASE = 0.03f
private const val TOLERANCE = 1e-3f

/** A garment's branches are the body's slices grown by an ease, taken further by a drawing, carried past a part's end. */
class GarmentShellTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val legL = PartId("leg-l")
    private val legR = PartId("leg-r")
    private val sweater = GarmentId("sweater")

    private val body: AssembledCharacter by lazy {
        val legs = PairId("legs")
        val leg = library.latest(TemplateId("leg"))!!.ref
        val placed = listOf(
            AssemblyPart(torso, library.latest(TemplateId("torso"))!!.ref, null, null, null),
            AssemblyPart(legL, leg, SocketRef(torso, "front-leg"), legs, Side.LEFT),
            AssemblyPart(legR, leg, SocketRef(torso, "front-leg"), legs, Side.RIGHT),
        )
        assembleNow(placed, { PartData() }, plan, library)
    }

    private fun dressed(vararg branches: GarmentBranch) =
        body.dressedIn(Dressing(listOf(Garment(sweater, "Sweater", branches = branches.toList())), mapOf(legL to legR)))

    /** A rectangle from [from] to [to] along x and [half] either side of y = 0, as joined straight cubics. */
    private fun box(from: Float, to: Float, half: Float): OutlineCurve {
        val corners = listOf(CurvePoint(from, -half), CurvePoint(to, -half), CurvePoint(to, half), CurvePoint(from, half), CurvePoint(from, -half))
        return OutlineCurve(listOf(corners.first()) + corners.zipWithNext { a, b ->
            listOf(CurvePoint(a.x + (b.x - a.x) / 3f, a.y + (b.y - a.y) / 3f), CurvePoint(a.x + 2f * (b.x - a.x) / 3f, a.y + 2f * (b.y - a.y) / 3f), b)
        }.flatten())
    }

    private fun swept(character: AssembledCharacter, part: PartId): Patch = assertNotNull(character.part(part)).surface.patches.first { it.slices != null }

    @Test
    fun aBranchIsAFixedGridOverTheBodysSlicesEveryWayOut() {
        val character = dressed(GarmentBranch(torso, 0.3f, 0.9f, EASE))
        val shell = assertNotNull(character.part(branchPartId(sweater, torso)))
        val patch = shell.surface.patches.single()
        val bodyPatch = swept(character, torso)
        assertEquals(BRANCH_RINGS to bodyPatch.nv, patch.nu to patch.nv)
        assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(patch) { 0 }, setOf(0, patch.nu - 1)))
        val covering = assertNotNull(shell.covering)
        for (ring in 1 until patch.nu - 1) {
            val under = bodyPatch.slices!![covering.bodyRings[ring]]
            val over = patch.slices!![ring]
            assertTrue(over.height >= under.height + EASE, "ring $ring: ${over.height} over ${under.height}")
            assertTrue(over.width >= under.width + EASE, "ring $ring: ${over.width} over ${under.width}")
        }
    }

    @Test
    fun aDrawingThatStandsOffSetsTheHeightAndOneThatHugsLeavesTheEase() {
        val plain = swept(dressed(GarmentBranch(torso, 0.3f, 0.9f, EASE)), branchPartId(sweater, torso))
        val axes = PartBuilder.axes(assertNotNull(body.part(torso)).template)
        val wide = box(-1f, 3f, 2f)
        val tight = box(-1f, 3f, 0.001f)
        val drawn = swept(dressed(GarmentBranch(torso, 0.3f, 0.9f, EASE, mapOf(View.SIDE to wide))), branchPartId(sweater, torso))
        val hugged = swept(dressed(GarmentBranch(torso, 0.3f, 0.9f, EASE, mapOf(View.SIDE to tight))), branchPartId(sweater, torso))
        for (ring in 1 until plain.nu - 1) {
            assertEquals(4f, drawn.slices!![ring].height, TOLERANCE, "ring $ring takes the drawing's height")
            assertEquals(plain.slices!![ring].height, hugged.slices!![ring].height, TOLERANCE, "ring $ring keeps the ease")
            assertEquals(0f, drawn.slices!![ring].frame.center dot axes.up, 0.05f, "ring $ring is centred on the drawing")
        }
    }

    @Test
    fun aBranchCarriesOnPastItsPartsEnd() {
        val character = dressed(GarmentBranch(legL, 0f, 1.3f, EASE))
        val patch = swept(character, branchPartId(sweater, legL))
        val leg = swept(character, legL)
        val end = leg.slices!!.last()
        val tip = patch.slices!!.last()
        val along = (tip.frame.center - end.frame.center) dot end.frame.tangent
        assertTrue(along > 0f, "the sleeve runs on past the leg's end")
        val last = leg.slices!!.last { it.height > 0f }
        assertEquals(last.height + 2f * EASE, tip.height, TOLERANCE, "at the size of the leg's last ring")
    }

    @Test
    fun aBranchOnAPairedPartDressesBothSidesTheRightOneMirrored() {
        val character = dressed(GarmentBranch(legL, 0f, 0.6f, EASE))
        val left = assertNotNull(character.part(branchPartId(sweater, legL)))
        val right = assertNotNull(character.part(branchPartId(sweater, legR)))
        assertEquals(false, left.mirrored)
        assertEquals(true, right.mirrored)
        assertEquals(assertNotNull(character.part(legR)).origin, right.origin)
    }

    @Test
    fun nothingWornLeavesTheCharacterAsItIs() {
        assertTrue(body.dressedIn(Dressing()) === body)
    }

    @Test
    fun theSkinUnderABranchIsHiddenAndItsGridKept() {
        val character = dressed(GarmentBranch(torso, 0.3f, 0.9f, EASE))
        val bare = assertNotNull(body.part(torso)).surface
        val covered = assertNotNull(character.part(torso)).surface
        assertEquals(bare.mesh.vertexCount, covered.mesh.vertexCount, "every vertex stays, so anchors still land")
        assertTrue(covered.mesh.indices.size < bare.mesh.indices.size, "triangles under the sweater are left out")
        val shell = assertNotNull(character.part(branchPartId(sweater, torso)))
        val inside = dev.chieppa.squash.geom.ShellInside(shell.surface.patches.single(), { shell.origin + shell.surface.shown(it) })
        val torsoOrigin = assertNotNull(character.part(torso)).origin
        for (t in 0 until covered.mesh.indices.size / 3) {
            val corners = (0 until 3).map { torsoOrigin + covered.mesh.position(covered.mesh.indices[t * 3 + it]) }
            assertTrue(corners.any { !inside.covers(it) }, "triangle $t shows and lies where the sweater does not cover it")
        }
        assertTrue(assertNotNull(character.part(torso)).surface.patches === bare.patches)
    }
}

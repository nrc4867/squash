package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.PosedParts
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.patchWithFirst
import dev.chieppa.squash.character.build.posedBy
import dev.chieppa.squash.character.build.socketCrease
import dev.chieppa.squash.character.build.stripJoining
import dev.chieppa.squash.character.build.withStripsPosed
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How far a leg is carried in the posed case, in body units. */
private const val CARRIED = 0.05f

/** Closer than this, in body units, is the same place. */
private const val SAME = 1e-4f

/**
 * A leg sitting in a torso is trimmed at a ring clear of it and joined to it by a strip lying on both, with the torso's faces under the
 * strip left out and the leg's own patch and its anchors as they were; the strip inks only when asked, and a pose builds it again
 * between where the leg and the torso now are. See HLD: look, Where parts meet.
 */
class SocketStripsTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val leg = PartId("leg")

    private fun assembled(inked: Boolean = false): AssembledCharacter {
        val placed = listOf(
            AssemblyPart(torso, assertNotNull(library.latest(TemplateId("torso"))).ref, null, null, null),
            AssemblyPart(
                leg, assertNotNull(library.latest(TemplateId("leg"))).ref, SocketRef(torso, "front-leg"), null, null,
                placement = if (inked) mapOf(JoinParams.INK to 1f) else emptyMap(),
            ),
        )
        return assembleNow(placed, { PartData() }, plan, library)
    }

    private val character by lazy { assembled() }

    @Test
    fun theLegCarriesAStripFromTheTorsoToARingClearOfIt() {
        val (tube, strip) = assertNotNull(character.stripJoining(leg))
        assertEquals(leg, tube.id)
        assertEquals(torso, strip.host)
        val (own, ownFirst) = assertNotNull(tube.patchWithFirst(strip.tube))
        val (patch, first) = assertNotNull(tube.patchWithFirst(strip.patch))
        val torsoInside = assertNotNull(character.part(torso)).insideTest()
        for (j in 0 until own.nv) {
            val ring = tube.point(ownFirst + own.index(strip.ring, j))
            val end = tube.point(first + patch.index(patch.nu - 1, j))
            assertEquals(0f, (ring - end).length, SAME, "the strip ends on the leg's ring")
            assertTrue(ring !in torsoInside, "the ring the strip meets lies clear of the torso")
        }
        assertTrue(patch.ringUs.all { it in -1f..0f }, "the strip's u runs below the leg's own")
    }

    @Test
    fun theLegsFacesPastItsRingAndTheTorsosUnderTheStripAreLeftOut() {
        val (tube, strip) = assertNotNull(character.stripJoining(leg))
        val (own, ownFirst) = assertNotNull(tube.patchWithFirst(strip.tube))
        val trimmed = (ownFirst until ownFirst + strip.ring * own.nv).toSet()
        assertTrue(tube.surface.mesh.indices.none { it in trimmed }, "no face of the leg is drawn below its ring")
        val body = assertNotNull(character.part(torso))
        val hiding = assertNotNull(body.hiding)
        assertTrue(hiding.fixed.any { it }, "the torso hides what lies under the strip")
        assertTrue(body.surface.mesh.indices.size < hiding.indices.size)
    }

    @Test
    fun theStripInksOnlyWhenTheJoinSaysSo() {
        assertNull(socketCrease(character, assertNotNull(character.part(leg))))
        val inked = assembled(inked = true)
        val crease = assertNotNull(socketCrease(inked, assertNotNull(inked.part(leg))))
        val (_, strip) = assertNotNull(inked.stripJoining(leg))
        val run = crease.runs.single()
        assertTrue(run.all { it.anchor.patch == strip.tube }, "the line runs round the leg's own ring")
    }

    @Test
    fun aPoseBuildsTheStripAgainWhereTheLegNowIs() {
        val placed = assertNotNull(character.part(leg))
        val moved = RenderMesh(
            placed.surface.mesh.vertices.copyOf().also { v -> for (i in 0 until placed.surface.mesh.vertexCount) v[i * RenderMesh.FLOATS_PER_VERTEX] += CARRIED },
            placed.surface.mesh.indices,
        )
        val posed = character.posedBy(object : PosedParts {
            override val meshes = mapOf(leg to moved)
            override val turns = emptyMap<PartId, Mat3>()
        }).withStripsPosed(character)
        val (tube, strip) = assertNotNull(posed.stripJoining(leg))
        val (own, ownFirst) = assertNotNull(tube.patchWithFirst(strip.tube))
        val (patch, first) = assertNotNull(tube.patchWithFirst(strip.patch))
        val rest = assertNotNull(character.part(leg))
        for (j in 0 until own.nv) {
            assertEquals(0f, (tube.point(ownFirst + own.index(strip.ring, j)) - tube.point(first + patch.index(patch.nu - 1, j))).length, SAME)
            assertEquals(0f, (rest.point(first + j) - tube.point(first + j)).length, SAME, "the foot stays on the torso, which did not move")
        }
    }

    private fun PlacedPart.point(vertex: Int): Vec3 = origin + surface.mesh.position(vertex)

    private fun PlacedPart.insideTest() = assertNotNull(surface.patches.firstOrNull { it.slices != null }).let { patch ->
        dev.chieppa.squash.geom.ShellInside(patch) { origin + surface.shown(it) }
    }
}

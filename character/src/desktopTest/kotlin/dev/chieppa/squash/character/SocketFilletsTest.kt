package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Blend
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val RADIUS = 0.2f

/** Less than this, in body units, is staying put: a field fitted to samples lowers the union by a hair far from the torso. */
private const val STILL = 1e-3f

/** A leg whose blend block says `socket` grows a fillet where it leaves the torso, and moves nothing else. */
class SocketFilletsTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val torso = PartId("torso")
    private val leg = PartId("leg")

    private fun assembled(on: Boolean): Map<PartId, List<Patch>> {
        val parts = shipped.parts.map { template ->
            if (on && template.id.value == "leg") template.copy(blocks = template.blocks + Blend(radius = Param.of(RADIUS), between = emptyList(), socket = true)) else template
        }
        val library = TemplateLibrary(parts, shipped.plans)
        val placed = listOf(
            AssemblyPart(torso, library.latest(TemplateId("torso"))!!.ref, null, null, null),
            AssemblyPart(leg, library.latest(TemplateId("leg"))!!.ref, SocketRef(torso, "front-leg"), null, null),
        )
        val character = assembleNow(placed, { PartData() }, plan, library)
        return listOf(torso, leg).associateWith { assertNotNull(character.part(it)).surface.patches }
    }

    @Test
    fun theLegFlaresIntoTheTorsoAndTheTorsoStays() {
        val off = assembled(on = false)
        val on = assembled(on = true)
        assertEquals(off.getValue(torso), on.getValue(torso), "the torso keeps its place")
        val before = off.getValue(leg).single()
        val after = on.getValue(leg).single()
        assertEquals(before.nu to before.nv, after.nu to after.nv)
        assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(after) { 0 }, setOf(0)))
        val moves = (0 until before.nu * before.nv).map { (after.point(it) - before.point(it)).length }
        assertTrue(moves.any { it > 0f }, "the leg's top moves")
        val lowerHalf = (before.nu / 2 * before.nv until before.nu * before.nv)
        assertTrue(lowerHalf.all { moves[it] < STILL }, "the leg's lower half stays")
    }
}

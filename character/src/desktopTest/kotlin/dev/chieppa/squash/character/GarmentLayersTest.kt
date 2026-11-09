package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.branchPartId
import dev.chieppa.squash.geom.ShellInside
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val EASE = 0.03f
private const val TOLERANCE = 1e-3f

/** A scarf over a sweater stands off the sweater, and what of the sweater it covers is hidden. */
class GarmentLayersTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val sweater = Garment(GarmentId("sweater"), "Sweater", layer = 1, branches = listOf(GarmentBranch(torso, 0.4f, 0.97f, EASE)))
    private val scarf = Garment(GarmentId("scarf"), "Scarf", layer = 2, branches = listOf(GarmentBranch(torso, 0.8f, 0.97f, EASE)))

    private val body: AssembledCharacter by lazy {
        assembleNow(listOf(AssemblyPart(torso, library.latest(TemplateId("torso"))!!.ref, null, null, null)), { PartData() }, plan, library)
    }

    private fun shellOf(character: AssembledCharacter, garment: Garment) = assertNotNull(character.part(branchPartId(garment.id, torso)))

    @Test
    fun anOuterLayerStandsOffTheOneUnderIt() {
        val alone = shellOf(body.dressedIn(Dressing(listOf(scarf))), scarf).surface.patches.single()
        val over = shellOf(body.dressedIn(Dressing(listOf(sweater, scarf))), scarf).surface.patches.single()
        for (ring in 1 until alone.nu - 1) {
            assertEquals(alone.slices!![ring].height + 2f * EASE, over.slices!![ring].height, TOLERANCE, "ring $ring")
        }
    }

    @Test
    fun whatAnOuterLayerCoversOfAnInnerOneIsHidden() {
        val sweaterAlone = shellOf(body.dressedIn(Dressing(listOf(sweater))), sweater)
        val dressed = body.dressedIn(Dressing(listOf(sweater, scarf)))
        val inner = shellOf(dressed, sweater)
        val outer = shellOf(dressed, scarf)
        assertTrue(inner.surface.mesh.indices.size < sweaterAlone.surface.mesh.indices.size, "the sweater under the scarf is hidden")
        val inside = ShellInside(outer.surface.patches.single(), { outer.origin + outer.surface.shown(it) })
        for (t in 0 until inner.surface.mesh.indices.size / 3) {
            val corners = (0 until 3).map { inner.origin + inner.surface.mesh.position(inner.surface.mesh.indices[t * 3 + it]) }
            assertTrue(corners.any { !inside.covers(it) }, "triangle $t of the sweater shows where the scarf does not cover it")
        }
        assertEquals(shellOf(body.dressedIn(Dressing(listOf(scarf))), scarf).surface.mesh.indices.size, outer.surface.mesh.indices.size, "nothing hides the outermost layer")
    }
}

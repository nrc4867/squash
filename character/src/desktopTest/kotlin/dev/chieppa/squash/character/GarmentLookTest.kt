package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.character.build.GarmentView
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.character.build.seenAs
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.branchPartId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shown hides what the garments cover, Ghosted keeps the body whole under them, Hidden wears nothing, and a garment out of view is left off. */
class GarmentLookTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val sweater = Garment(GarmentId("sweater"), "Sweater", layer = 1, branches = listOf(GarmentBranch(torso, 0.4f, 0.97f)))
    private val scarf = Garment(GarmentId("scarf"), "Scarf", layer = 2, branches = listOf(GarmentBranch(torso, 0.8f, 0.97f)))
    private val dressing = Dressing(listOf(sweater, scarf))

    private val body: AssembledCharacter by lazy {
        assembleNow(listOf(AssemblyPart(torso, library.latest(TemplateId("torso"))!!.ref, null, null, null)), { PartData() }, plan, library)
    }

    private fun skin(character: AssembledCharacter) = assertNotNull(character.part(torso)).surface.mesh.indices.size
    private fun shell(character: AssembledCharacter, garment: Garment) = character.part(branchPartId(garment.id, torso))

    @Test
    fun shownHidesTheSkinUnderTheGarments() {
        assertTrue(skin(body.dressedIn(dressing.seenAs(GarmentView(GarmentLook.SHOWN)))) < skin(body))
    }

    @Test
    fun ghostedKeepsTheSkinAndEveryLayerWhole() {
        val ghosted = body.dressedIn(dressing.seenAs(GarmentView(GarmentLook.GHOSTED)))
        assertEquals(skin(body), skin(ghosted))
        val sweaterAlone = assertNotNull(shell(body.dressedIn(Dressing(listOf(sweater))), sweater)).surface.mesh.indices.size
        assertEquals(sweaterAlone, assertNotNull(shell(ghosted, sweater)).surface.mesh.indices.size, "the scarf hides nothing of the sweater")
    }

    @Test
    fun hiddenWearsNothing() {
        val hidden = body.dressedIn(dressing.seenAs(GarmentView(GarmentLook.HIDDEN)))
        assertEquals(skin(body), skin(hidden))
        assertNull(shell(hidden, sweater))
        assertNull(shell(hidden, scarf))
    }

    @Test
    fun aGarmentOutOfViewIsLeftOff() {
        val seen = body.dressedIn(dressing.seenAs(GarmentView(GarmentLook.SHOWN, setOf(scarf.id))))
        assertNull(shell(seen, scarf))
        assertNotNull(shell(seen, sweater))
    }
}

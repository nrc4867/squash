package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.GrainField
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.Grain
import dev.chieppa.squash.document.GrainComb
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.fennecDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A share of a unit direction that counts as pointing the way it was asked. */
private const val MOSTLY = 0.7f
private const val SAME = 1e-4f

class GrainFieldTest {
    private val document = fennecDocument()
    private val folder = TemplateFolder.shipped()
    private val session = openSession(document)

    private fun patch(part: PartId): Patch =
        assertNotNull(session.assembly.output.value?.part(part)?.surface?.patches?.first { it.name == BODY_PATCH })

    private fun spec(part: PartId) = assertNotNull(folder.template(assertNotNull(document.character.part(part)).template)).grain

    @Test
    fun aTailsFurRunsTowardItsTip() {
        val patch = patch(TAIL)
        val direction = GrainField(spec(TAIL), null).directionAt(patch, 0.5f, 0.25f)
        assertTrue((direction dot patch.surfaceAt(0.5f, 0.25f).alongU.normalized()) > MOSTLY, "along the axis")
    }

    @Test
    fun aTorsosFurRunsBackFromItsChest() {
        val patch = patch(TORSO)
        val direction = GrainField(spec(TORSO), null).directionAt(patch, 0.4f, 0.0f)
        assertTrue((direction dot patch.surfaceAt(0.4f, 0.0f).alongU.normalized()) < -MOSTLY, "toward the start of the axis")
    }

    @Test
    fun aRuffStandsOutward() {
        val patch = patch(TORSO)
        val field = GrainField(spec(TORSO), null)
        fun rise(u: Float, v: Float) = field.directionAt(patch, u, v) dot patch.surfaceAt(u, v).normal
        assertTrue(rise(0.85f, 0.5f) > rise(0.4f, 0.5f) + 0.2f, "the chest's ruff rises more than the flank: ${rise(0.85f, 0.5f)} and ${rise(0.4f, 0.5f)}")
    }

    @Test
    fun aCombOverridesTheFieldNearItAndFadesAway() {
        val patch = patch(TAIL)
        val field = GrainField(spec(TAIL), Grain(listOf(GrainComb(u = 0.5f, v = 0.25f, du = 0f, dv = 0.1f))))
        val around = patch.surfaceAt(0.5f, 0.25f).aroundV.normalized()
        assertTrue((field.directionAt(patch, 0.5f, 0.25f) dot around) > MOSTLY, "at the comb, around the tail as combed")
        val far = field.directionAt(patch, 0.5f, 0.75f)
        val bare = GrainField(spec(TAIL), null).directionAt(patch, 0.5f, 0.75f)
        assertTrue((far - bare).length < SAME, "past its reach it changes nothing")
    }

    @Test
    fun aCombHoldsAcrossTheSeamOfAClosedPatch() {
        val patch = patch(TAIL)
        val field = GrainField(spec(TAIL), Grain(listOf(GrainComb(u = 0.5f, v = 0.99f, du = 0f, dv = 0.1f))))
        assertTrue((field.directionAt(patch, 0.5f, 0.01f) dot patch.surfaceAt(0.5f, 0.01f).aroundV.normalized()) > MOSTLY)
    }
}

package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.shipped
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.TemplateId
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Cloth reaching a hand's far end the whole way round follows each of its digits; cloth short of it leaves them bare. */
@Tag("integration")
class GloveDigitsTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val hand = PartId("hand")

    /** A hand of [digits] digits alone. */
    private fun body(digits: Int): AssembledCharacter {
        val part = AssemblyPart(hand, library.latest(TemplateId("hand"))!!.ref, null, null, null)
        return assembleNow(listOf(part), { PartData(params = mapOf("digits" to digits.toFloat())) }, null, library)
    }

    private fun worn(body: AssembledCharacter, to: Float): WornCover {
        val garment = Garment(GarmentId("glove"), "Glove", GarmentSlotId("hands"), fit = 0.003f, branches = listOf(GarmentBranch(hand, 0f, to, 0.003f))).withBoxesPainted()
        return WornCover.of(GarmentCover(garment, garment.branches.associateBy { it.part }), body, emptyList(), ShellCache())
    }

    @Test
    fun aGloveOverTheWholeHandGrowsEveryDigitAndAShortOneNone() {
        for (digits in listOf(3, 4, 5)) {
            val body = body(digits)
            val solids = RunSolids()
            val field = assertNotNull(fieldOf(body, solids))
            val digitRuns = field.runs.count { !it.first && isDigitRun(it.part, it.patch) }
            assertEquals(digits, digitRuns, "the hand builds $digits digits")
            val glove = worn(body, 1f)
            assertTrue(glove.followsDigits(hand), "cloth to the hand's end follows its digits")
            val grown = assertNotNull(garmentField(field, glove, body, solids, RunClips()))
            val fingers = grown.runs.indices.filter { !grown.runs[it].first && isDigitRun(grown.runs[it].part, grown.runs[it].patch) }
            assertEquals(digits, fingers.size, "a glove finger over each of the $digits digits")
            assertTrue(fingers.all { grown.field.members[it].parent >= 0 && grown.field.members[it].ease > 0f }, "each joins the cloth it grows from, standing off")
            val cuff = worn(body, 0.5f)
            assertTrue(!cuff.followsDigits(hand), "cloth short of the end leaves the digits")
            assertTrue(assertNotNull(garmentField(field, cuff, body, solids, RunClips())).runs.none { !it.first && isDigitRun(it.part, it.patch) })
        }
    }
}

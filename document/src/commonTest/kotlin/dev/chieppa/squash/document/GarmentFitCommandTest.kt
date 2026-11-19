package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.LEG_L
import dev.chieppa.squash.document.WinterFennec.SWEATER
import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A garment's fit, ink weight and hem roll: kept within their ranges, set by commands that undo. */
class GarmentFitCommandTest {
    @Test
    fun inkWeightAndHemRollAreSetWithinTheirRangesAndUndo() {
        val start = WinterFennec.dressed()
        assertEquals(CLOTH_INK_WEIGHT, start.character.wardrobe.garment(SWEATER)?.inkWeight)
        scenario(start) {
            run(SetGarmentInkWeight(SWEATER, 1.4f))
            run(SetGarmentHemRoll(SWEATER, 0.005f))
            expect {
                val sweater = checkNotNull(it.character.wardrobe.garment(SWEATER))
                assertEquals(1.4f, sweater.inkWeight)
                assertEquals(0.005f, sweater.hemRoll)
            }
            run(SetGarmentInkWeight(SWEATER, -1f))
            run(SetGarmentHemRoll(SWEATER, 1f))
            expect {
                val sweater = checkNotNull(it.character.wardrobe.garment(SWEATER))
                assertEquals(0f, sweater.inkWeight)
                assertEquals(MOST_HEM_ROLL, sweater.hemRoll)
            }
            repeat(4) { undo() }
            expect(start)
        }
    }

    @Test
    fun aGarmentNeverStandsOffByANegativeFit() {
        assertFailsWith<IllegalArgumentException> { Garment(SWEATER, "Sweater", fit = -0.01f) }
        assertFailsWith<IllegalArgumentException> { Garment(SWEATER, "Sweater", partFits = mapOf(LEG_L to -0.01f)) }
        assertFailsWith<IllegalArgumentException> { SetGarmentFit(SWEATER, -0.01f).apply(WinterFennec.dressed()) }
    }
}

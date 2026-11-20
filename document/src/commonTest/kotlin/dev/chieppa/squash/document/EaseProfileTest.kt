package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.LEG_L
import dev.chieppa.squash.document.WinterFennec.SWEATER
import dev.chieppa.squash.document.WinterFennec.TORSO
import dev.chieppa.stretch.test.scenario
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A stand-off that varies along a part: smooth through its keys with no overshoot, front and back, and set by commands that undo. */
class EaseProfileTest {
    private val sleeve = EaseProfile(listOf(EaseKey(0f, 0.006f), EaseKey(0.7f, 0.02f), EaseKey(1f, 0.03f)))

    @Test
    fun aProfilePassesThroughItsKeysAndIsFlatPastItsEnds() {
        assertEquals(0.006f, sleeve.at(0f), 1e-6f)
        assertEquals(0.02f, sleeve.at(0.7f), 1e-6f)
        assertEquals(0.03f, sleeve.at(1f), 1e-6f)
        assertEquals(0.006f, sleeve.at(-0.2f), 1e-6f)
        assertEquals(0.03f, sleeve.at(1.3f), 1e-6f)
    }

    @Test
    fun aProfileRisesSmoothlyWithNoStepAndNoOvershoot() {
        val steps = 400
        var last = sleeve.at(0f)
        var biggest = 0f
        for (i in 1..steps) {
            val at = sleeve.at(i.toFloat() / steps)
            assertTrue(at >= last - 1e-7f, "falls at ${i.toFloat() / steps}")
            assertTrue(at in sleeve.least..sleeve.most)
            biggest = maxOf(biggest, abs(at - last))
            last = at
        }
        assertTrue(biggest < 0.0003f, "steps by $biggest")
        val peak = EaseProfile(listOf(EaseKey(0f, 0.01f), EaseKey(0.5f, 0.03f), EaseKey(1f, 0.004f)))
        assertTrue((0..100).all { peak.at(it / 100f) in 0.004f..0.03f })
    }

    @Test
    fun theBackStandsOffApartAndTheSidesTakeTheMean() {
        val tee = EaseProfile(listOf(EaseKey(0f, 0.02f, 0.03f), EaseKey(1f, 0.01f)))
        assertEquals(0.02f, tee.at(0f, 0f), 1e-6f)
        assertEquals(0.03f, tee.at(0f, 1f), 1e-6f)
        assertEquals(0.025f, tee.at(0f, 0.5f), 1e-6f)
        assertEquals(0.01f, tee.least)
        assertEquals(0.03f, tee.most)
        assertTrue(EaseProfile.flat(0.01f).uniform)
    }

    @Test
    fun keysOutOfOrderOrANegativeEaseAreRefused() {
        assertFailsWith<IllegalArgumentException> { EaseProfile(emptyList()) }
        assertFailsWith<IllegalArgumentException> { EaseProfile(listOf(EaseKey(0.5f, 0.01f), EaseKey(0.2f, 0.01f))) }
        assertFailsWith<IllegalArgumentException> { EaseKey(0f, -0.01f) }
        assertFailsWith<IllegalArgumentException> { ClothHang(-0.01f) }
        assertFailsWith<IllegalArgumentException> { Garment(SWEATER, "Sweater", drops = mapOf(LEG_L to 0.8f)) }
        assertFailsWith<IllegalArgumentException> { GarmentArea(GarmentAreaId("cuff"), "", fold = -0.01f) }
    }

    @Test
    fun aGarmentWithNoProfileStandsOffByItsFitEverywhere() {
        val garment = Garment(SWEATER, "Sweater", fit = 0.01f, branches = listOf(GarmentBranch(TORSO)))
        assertTrue(garment.profileOf(garment.branches.single()).uniform)
        assertEquals(0.01f, garment.profileOf(garment.branches.single()).at(0.4f))
    }

    @Test
    fun easeHangDropGatherAndFoldAreSetByCommandsThatUndo() {
        val start = WinterFennec.dressed()
        val area = checkNotNull(start.character.wardrobe.garment(SWEATER)).areas.first().id
        scenario(start) {
            run(SetPartEase(SWEATER, TORSO, sleeve))
            run(SetGarmentHang(SWEATER, ClothHang(0.02f, 0.1f)))
            run(SetGarmentDrop(SWEATER, LEG_L, 0.2f))
            run(SetAreaGather(SWEATER, area, 0.004f))
            run(SetAreaFold(SWEATER, area, 0.5f))
            expect {
                val sweater = checkNotNull(it.character.wardrobe.garment(SWEATER))
                assertEquals(sleeve, sweater.eases[TORSO])
                assertEquals(ClothHang(0.02f, 0.1f), sweater.hang)
                assertEquals(0.2f, sweater.drops[LEG_L])
                assertEquals(0.004f, sweater.area(area)?.gather)
                assertEquals(MOST_FOLD, sweater.area(area)?.fold, "a fold is held to its most")
            }
            run(SetGarmentDrop(SWEATER, LEG_L, 0f))
            expect { assertNull(checkNotNull(it.character.wardrobe.garment(SWEATER)).drops[LEG_L]) }
            repeat(6) { undo() }
            expect(start)
        }
    }
}

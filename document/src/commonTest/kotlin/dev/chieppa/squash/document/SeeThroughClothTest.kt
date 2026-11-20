package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.SWEATER
import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SeeThroughClothTest {
    private val area = GarmentAreaId("leg")

    @Test
    fun anAreaIsSeeThroughWhenSheerOrANet() {
        assertFalse(GarmentArea(area, "").seeThrough)
        assertFalse(GarmentArea(area, "", opacity = 1f).seeThrough, "a whole opacity hides all")
        assertTrue(GarmentArea(area, "", opacity = 0.3f).seeThrough)
        assertTrue(GarmentArea(area, "", net = ClothNet()).seeThrough)
    }

    @Test
    fun aNetAndAnOpacityHoldToTheirRanges() {
        assertFailsWith<IllegalArgumentException> { GarmentArea(area, "", opacity = 1.2f) }
        assertFailsWith<IllegalArgumentException> { ClothNet(hole = 0.02f, thread = 0.03f) }
        assertFailsWith<IllegalArgumentException> { ClothNet(angle = 120f) }
        val held = ClothNet.of(NetPattern.SQUARE, hole = 5f, thread = 4.9f, angle = -10f)
        assertEquals(MOST_NET_HOLE, held.hole)
        assertEquals(MOST_NET_HOLE * ClothNet.MOST_THREAD_SHARE, held.thread)
        assertEquals(0f, held.angle)
    }

    @Test
    fun seeThroughIsLeftOutOfWhatTheClothsShapeReads() {
        val plain = Garment(SWEATER, "Stockings", areas = listOf(GarmentArea(area, "")))
        assertSame(plain, plain.unprinted)
        val sheer = plain.copy(areas = listOf(GarmentArea(area, "", opacity = 0.3f, net = ClothNet())))
        assertEquals(plain, sheer.unprinted)
    }

    @Test
    fun opacityAndNetAreSetByCommandsThatUndo() {
        val start = WinterFennec.dressed()
        val first = checkNotNull(start.character.wardrobe.garment(SWEATER)).areas.first().id
        val net = ClothNet(NetPattern.SQUARE, hole = 0.05f, thread = 0.006f, angle = 10f)
        scenario(start) {
            run(SetAreaOpacity(SWEATER, first, 1.4f))
            run(SetAreaNet(SWEATER, first, net))
            expect {
                val held = checkNotNull(it.character.wardrobe.garment(SWEATER)?.area(first))
                assertEquals(1f, held.opacity, "an opacity is held to 1")
                assertEquals(net, held.net)
            }
            run(SetAreaOpacity(SWEATER, first, null))
            run(SetAreaNet(SWEATER, first, null))
            expect {
                val held = checkNotNull(it.character.wardrobe.garment(SWEATER)?.area(first))
                assertNull(held.opacity)
                assertNull(held.net)
            }
            repeat(4) { undo() }
            expect(start)
        }
    }
}

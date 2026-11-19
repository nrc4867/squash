package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Region
import dev.chieppa.squash.document.RegionId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a garment prints on its cloth: its regions as shapes on the parts they lie on, its inked lines laid densely and cut to the cloth. */
class GarmentPrintsTest {
    private val torso = PartId("torso")
    private val leg = PartId("leg-l")

    private fun at(part: PartId, u: Float, v: Float) = Anchor(part, BODY_PATCH, u, v)

    private val drop = Region(RegionId("drop"), listOf(at(torso, 0.6f, 0.0f), at(torso, 0.7f, 0.05f), at(torso, 0.8f, 0.0f), at(torso, 0.7f, -0.05f)), SlotName("red"), ink = true, corners = setOf(2))

    @Test
    fun aRegionIsAShapeOnEachPartItsBorderLiesOnAndNoneElsewhere() {
        val prints = GarmentPrints(listOf(drop))
        val shapes = prints.shapesOn(torso, BODY_PATCH)
        assertEquals(1, shapes.size)
        assertEquals(SlotName("red"), shapes.single().slot)
        assertEquals(emptyList(), prints.shapesOn(leg, BODY_PATCH))
    }

    @Test
    fun anInkedBorderIsOneEvenLineAlongItsCurveBackToItsStart() {
        val line = GarmentPrints(listOf(drop)).lines { _, _ -> true }.getValue(torso).single()
        assertEquals(MarkWidth(listOf(WidthStop(0f, 1f), WidthStop(1f, 1f))), line.width)
        val places = line.points.map { it.anchor }
        assertTrue(places.size > drop.border.size * 4, "laid densely along its curve, not straight between its four places")
        assertTrue(abs(places.first().u - places.last().u) < 1e-4f && abs(places.first().v - places.last().v) < 1e-4f, "closed")
        assertEquals(emptyMap(), GarmentPrints(listOf(drop.copy(ink = false))).lines { _, _ -> true })
    }

    @Test
    fun aMarkTakesPlacesEveryHalfCellBetweenItsAnchorsOnOnePart() {
        val stitch = Mark(MarkId("stitch"), listOf(at(leg, 0.1f, 0.4f), at(leg, 0.1f + 8f / CHART_ROWS, 0.4f)), slot = SlotName("thread"))
        val line = GarmentPrints(marks = listOf(stitch)).lines { _, _ -> true }.getValue(leg).single()
        assertEquals(17, line.points.size)
        assertEquals(SlotName("thread"), line.slot)
        val unInked = stitch.copy(ink = false)
        assertEquals(emptyMap(), GarmentPrints(marks = listOf(unInked)).lines { _, _ -> true })
    }

    @Test
    fun aMarkAcrossTheSeamOfAClosedPartTakesTheShortWayRound() {
        val across = Mark(MarkId("across"), listOf(at(torso, 0.5f, 0.95f), at(torso, 0.5f, 0.05f)))
        val places = GarmentPrints(marks = listOf(across)).lines { _, _ -> true }.getValue(torso).single().points.map { it.anchor.v }
        assertTrue(places.all { it >= 0.95f || it <= 0.05f }, "never through the back: $places")
    }

    @Test
    fun aLineIsCutToItsPlacesOnTheClothEachRunKeptAsAMarkOfItsOwn() {
        val places = List(10) { ResolvedPoint(at(torso, it / 10f, 0f), Vec3.ZERO, Vec3.ZERO) }
        val mark = ResolvedMark(MarkId("m"), places, MarkWidth(), true, false, emptyMap())
        val cut = clothCut(mapOf(torso to listOf(mark))) { it.u < 0.3f || it.u in 0.45f..0.75f || it.u > 0.85f }.getValue(torso)
        assertEquals(listOf(3, 3), cut.map { it.points.size }, "the lone place past 0.85 is no line")
        assertEquals(listOf(mark), clothCut(mapOf(torso to listOf(mark))) { true }.getValue(torso))
    }
}

package dev.chieppa.squash.screens.single

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.sheet.LiveStroke
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.sheetPartOf
import dev.chieppa.squash.character.catalog.DIRECTION_ORDER
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.squash.uidesign.ThumbEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WIDTH = 128
private const val HEIGHT = 88

class ViewThumbnailsTest {
    private val part = sheetPartOf(shipped.parts.first { it.id.value == "leg" })
    private val outline = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0f, 0.1f), CurvePoint(0.1f, 0.1f), CurvePoint(0.1f, 0f)))

    private fun thumbnails(
        outlines: Map<View, OutlineCurve> = emptyMap(),
        current: View? = null,
        live: LiveStroke? = null,
        templateHint: Boolean = true,
    ) = viewThumbnails(SheetSelection(part, outlines), current, live, templateHint, WIDTH, HEIGHT).associateBy { it.view }

    @Test
    fun thereIsOneThumbnailPerViewInTheBoardsOrderOfDirections() {
        val shown = viewThumbnails(SheetSelection(part, emptyMap()), null, null, true, WIDTH, HEIGHT)
        assertEquals(DIRECTION_ORDER, shown.map { it.body })
        assertEquals(listOf(View.SIDE, View.FRONT, View.TOP), shown.map { it.view }, "a leg looks at its Front from above")
    }

    @Test
    fun aPartAlongTheBodyShowsEachViewFromItsOwnDirection() {
        val paw = sheetPartOf(shipped.parts.first { it.id.value == "paw" })
        assertEquals(VIEW_ORDER, viewThumbnails(SheetSelection(paw, emptyMap()), null, null, true, WIDTH, HEIGHT).map { it.view })
    }

    @Test
    fun aViewWithNothingDrawnIsMissingAndSnaps() {
        val shown = thumbnails(mapOf(View.SIDE to outline), View.SIDE)
        for (view in listOf(View.TOP, View.FRONT)) {
            assertEquals(ThumbEdge.MISSING, shown.getValue(view).edge, "$view")
            assertTrue(shown.getValue(view).snaps, "$view")
        }
    }

    @Test
    fun theViewOnScreenIsLitAndDoesNotSnap() {
        for (outlines in listOf(emptyMap(), mapOf(View.SIDE to outline))) {
            val side = thumbnails(outlines, View.SIDE).getValue(View.SIDE)
            assertEquals(ThumbEdge.CURRENT, side.edge)
            assertFalse(side.snaps)
        }
    }

    @Test
    fun aDrawnViewOffScreenIsPlainAndStillSnaps() {
        val side = thumbnails(mapOf(View.SIDE to outline), View.TOP).getValue(View.SIDE)
        assertEquals(ThumbEdge.PLAIN, side.edge)
        assertTrue(side.snaps)
    }

    @Test
    fun whileFreeNoViewIsLit() {
        assertTrue(thumbnails(current = null).values.none { it.edge == ThumbEdge.CURRENT })
    }

    @Test
    fun aDrawnViewShowsItsOutlineAndNoHint() {
        val side = thumbnails(mapOf(View.SIDE to outline)).getValue(View.SIDE)
        assertEquals(1, side.lines.size)
        assertTrue(side.lines.single().size > 2, "the curve is sampled")
        assertTrue(side.hint.isEmpty())
    }

    @Test
    fun anUndrawnViewShowsTheTemplateOnlyWhileTheHintIsOn() {
        val on = thumbnails().getValue(View.TOP)
        assertTrue(on.lines.isEmpty())
        assertTrue(on.hint.isNotEmpty())
        assertTrue(thumbnails(templateHint = false).getValue(View.TOP).hint.isEmpty())
        assertEquals(ThumbEdge.MISSING, thumbnails(templateHint = false).getValue(View.TOP).edge, "missing whatever the hint shows")
    }

    @Test
    fun aStrokeShowsInItsViewAsItGrows() {
        val stroke = LiveStroke(View.TOP, listOf(CurvePoint(0f, 0f), CurvePoint(0.05f, 0.05f)))
        val shown = thumbnails(current = View.TOP, live = stroke)
        assertEquals(2, shown.getValue(View.TOP).lines.single().size)
        assertTrue(shown.getValue(View.TOP).hint.isEmpty())
        assertTrue(shown.getValue(View.FRONT).lines.isEmpty(), "and nowhere else")
    }

    @Test
    fun whatIsDrawnFitsInsideThePicture() {
        for (thumbnail in thumbnails().values) {
            for (point in thumbnail.hint.flatten()) {
                assertTrue(point.x in 0f..WIDTH.toFloat() && point.y in 0f..HEIGHT.toFloat(), "${thumbnail.view}: $point")
            }
        }
    }
}

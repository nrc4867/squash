package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.stretch.test.scenario
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 0.005f

class StrokeOutlineTest {
    private fun loop(cx: Float, count: Int = 300) = List(count) {
        val angle = 2.0 * PI * it / (count - 1)
        CurvePoint(cx + cos(angle).toFloat(), 0.8f * sin(angle).toFloat())
    }

    @Test
    fun endsFollowTheViewAndTheTemplate() {
        assertEquals(StrokeEnds.Closed, strokeEnds(View.FRONT, End.START))
        assertEquals(StrokeEnds.Closed, strokeEnds(View.SIDE, null))
        assertEquals(StrokeEnds.CutAtPlane(above = true), strokeEnds(View.SIDE, End.START))
        assertEquals(StrokeEnds.CutAtPlane(above = true), strokeEnds(View.TOP, End.START))
        assertEquals(StrokeEnds.CutAtPlane(above = false), strokeEnds(View.TOP, End.END))
    }

    @Test
    fun anOutlineHoldsThreePointsPerSegmentPlusOneAndItsErrorInPixels() {
        val fitted = assertNotNull(fittedOutline(loop(1f), StrokeEnds.Closed, PIXEL))
        assertEquals(0, (fitted.curve.points.size - 1) % 3)
        assertTrue(fitted.errorPixels <= 1.5f, "${fitted.errorPixels} px")
    }

    @Test
    fun noOutlineFromAStrokeThatMakesNone() {
        assertNull(fittedOutline(listOf(CurvePoint(0f, 0f)), StrokeEnds.Closed, PIXEL))
        assertNull(fittedOutline(loop(-3f), StrokeEnds.CutAtPlane(), PIXEL))
    }

    @Test
    fun strokesAreOneUndoStepEachThroughTheCommand() {
        val id = PartId("leg")
        val part = PartInstance(id, TemplateRef(TemplateId("leg"), 1))
        val empty = SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(BodyPlanId("p"), 1)))
        val first = assertNotNull(fittedOutline(loop(0.6f), StrokeEnds.CutAtPlane(), PIXEL)).curve
        val second = assertNotNull(fittedOutline(loop(0.3f), StrokeEnds.CutAtPlane(), PIXEL)).curve
        scenario(empty) {
            run(AddPart(part), SetOutline(id, View.SIDE, first), SetOutline(id, View.SIDE, second))
            expect { assertEquals(second, it.character.resolved(id)?.outlines?.get(View.SIDE)) }
            undo()
            expect { assertEquals(first, it.character.resolved(id)?.outlines?.get(View.SIDE)) }
            undo()
            expect { assertNull(it.character.resolved(id)?.outlines?.get(View.SIDE)) }
        }
    }
}

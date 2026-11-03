package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

class OutlineGestureTest {
    private val leg = PartId("leg")
    private val start = OutlineCurve(List(7) { CurvePoint(it.toFloat(), 1f) })
    private val document = SquashDocument(
        Character(
            CharacterId("fennec"),
            "Fennec",
            BodyPlanRef(BodyPlanId("canine-quadruped"), 1),
            listOf(PartInstance(leg, TemplateRef(TemplateId("leg"), 1), own = PartData(outlines = mapOf(View.SIDE to start)))),
        ),
    )

    private fun SquashDocument.side() = character.resolved(leg)?.outlines?.get(View.SIDE)

    @Test
    fun aHandleDragMergesItsPreviewsIntoOneUndoStep() {
        val partway = start.moved(4, CurvePoint(4f, 2f), corner = true)
        val landed = start.moved(4, CurvePoint(4f, 3f), corner = true)
        scenario(document) {
            gesture(SetOutline(leg, View.SIDE, partway), SetOutline(leg, View.SIDE, landed))
            expect { assertEquals(landed, it.side()) }
            assertEquals(1, history.steps.value.done.size)
            undo()
            expect(document)
            redo()
            expect { assertEquals(landed, it.side()) }
        }
    }

    @Test
    fun anOversketchIsOneCommandAndUndoesToTheExactCurve() {
        val next = start.moved(3, CurvePoint(3f, 5f))
        scenario(document) {
            run(SetOutline(leg, View.SIDE, next))
            undo()
            expect(document)
        }
    }
}

package dev.chieppa.squash.screens.surface.eyes

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.PaintedEye
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetPaintedEye
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.stretch.test.scenario
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CLOSE = 1e-4f
private const val REACH = 9f
private const val TURN_REACH = 18f

/** A drag on a painted eye's box: what a press takes, the command each place sends, the mirrored side moving the eye mirrored, one step a drag. */
class EyeDragTest {
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("tube"), 1))
    private val eye = PaintedEye("eyes", Anchor(head.id, BODY_PATCH, 0.6f, 0.2f), EyeLook(width = 0.08f, height = 0.04f), front = EyeLook(width = 0.06f, height = 0.06f))
    private val start = SetPaintedEye(eye.id, eye).apply(
        AddParts(listOf(head)).apply(SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1)))).document,
    ).document

    /** The eye seen square on, 1000 pixels to a unit of its chart, its chart's up running up the pane. */
    private val box = EyeBox(Offset(100f, 100f), Offset(1000f, 0f), Offset(0f, -1000f), 0.08f, 0.04f, 0f)

    private fun close(expected: Float, actual: Float, what: String) = assertTrue(abs(expected - actual) < CLOSE, "$what: $expected, not $actual")

    @Test
    fun aPressTakesACornerTheTurnHandleOrTheInside() {
        assertEquals(EyeGrab.Corner(2), grabAt(box, Offset(181f, 61f), REACH, TURN_REACH))
        assertEquals(EyeGrab.Turn, grabAt(box, Offset(100f, 42f), REACH, TURN_REACH))
        assertEquals(EyeGrab.Move, grabAt(box, Offset(110f, 100f), REACH, TURN_REACH))
        assertNull(grabAt(box, Offset(300f, 100f), REACH, TURN_REACH))
    }

    @Test
    fun aCornerDragResizesAndIsOneStep() {
        val drag = EyeDrag(eye, box, EyeGrab.Corner(2), mirrored = false, Offset(180f, 60f), null)
        val first = assertNotNull(drag.commandAt(Offset(190f, 55f), null))
        val last = assertNotNull(drag.commandAt(Offset(200f, 50f), null))
        close(0.1f, last.width, "the corner sets the width")
        close(0.05f, last.height, "and the height")
        scenario(start) {
            gesture(first, last)
            expect { close(0.1f, it.character.paintedEyes.single().look.width, "the drag's last place holds") }
            undo()
            expect(start)
        }
    }

    @Test
    fun theTurnHandleTurnsTheEyeByThePointersSweep() {
        val drag = EyeDrag(eye, box, EyeGrab.Turn, mirrored = false, Offset(100f, 42f), null)
        val turned = assertNotNull(drag.commandAt(Offset(58f, 100f), null))
        close(90f, turned.tilt, "a quarter turn of the pointer about the middle")
        assertEquals(eye.look.width to eye.look.height, turned.width to turned.height)
    }

    @Test
    fun aMoveOnTheMirroredSideMovesTheEyeMirroredAndIsOneStep() {
        val grabbed = Anchor(head.id, BODY_PATCH, 0.6f, 0.8f)
        val drag = EyeDrag(eye, box, EyeGrab.Move, mirrored = true, Offset(100f, 100f), grabbed)
        val step = assertNotNull(drag.commandAt(Offset(110f, 100f), grabbed.copy(u = 0.61f, v = 0.78f)))
        val moved = assertNotNull(drag.commandAt(Offset(120f, 100f), grabbed.copy(u = 0.62f, v = 0.75f)))
        close(0.62f, moved.u, "along the head as the pointer went")
        close(0.25f, moved.v, "and across mirrored, toward the eye's own side's middle")
        assertNull(drag.commandAt(Offset(120f, 100f), Anchor(PartId("torso"), BODY_PATCH, 0.5f, 0.5f)), "off the head it says nothing")
        scenario(start) {
            gesture(step, moved)
            expect {
                val now = it.character.paintedEyes.single()
                close(0.25f, now.anchor.v, "the eye moved")
                assertEquals(eye.front, now.front, "a move leaves the front look's shape")
            }
            undo()
            expect(start)
        }
    }
}

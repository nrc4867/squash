package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val CLOSE = 1e-5f

/** The painted eye's Roundness, From front, gaze and frame: each a command that undoes, a drag of them one step, the front look following. */
class EyeCommandsTest {
    private val tube = TemplateRef(TemplateId("tube"), 1)
    private val head = PartInstance(PartId("head"), tube)
    private val front = EyeLook(width = 0.06f, height = 0.09f, tilt = 20f, highlightSize = 0.4f, lashes = false)
    private val eye = PaintedEye("eyes", Anchor(head.id, BODY_PATCH, 0.6f, 0.2f), EyeLook(width = 0.08f, height = 0.04f), front = front)
    private val start = SetPaintedEye(eye.id, eye).apply(
        AddParts(listOf(head)).apply(SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1)))).document,
    ).document

    private fun SquashDocument.eye() = character.paintedEyes.single()

    private fun close(expected: Float, actual: Float, what: String) = assertTrue(abs(expected - actual) < CLOSE, "$what: $expected, not $actual")

    @Test
    fun roundnessKeepsTheOpeningsAreaAndRoundsTheFrontLookAsMuch() {
        scenario(start) {
            run(SetEyeRoundness(eye.id, 1f))
            expect {
                val now = it.eye()
                close(now.look.width, now.look.height, "a round opening")
                close(eye.look.width * eye.look.height, now.look.width * now.look.height, "the same area")
                close(front.roundness * 2f, assertNotNullFront(now).roundness, "the front look twice as round, as the look is")
            }
            undo()
            expect(start)
        }
        assertFailsWith<IllegalArgumentException> { SetEyeRoundness(eye.id, 0f) }
    }

    @Test
    fun fromFrontBlendsTowardTheFrontLookAndAtNoneShowsOneLook() {
        assertSame(front, eye.shownFront(), "all the way shows the front look itself")
        scenario(start) {
            run(SetEyeFromFront(eye.id, 0.5f))
            expect {
                val shown = assertNotNullLook(it.eye().shownFront())
                close((eye.look.width + front.width) / 2f, shown.width, "halfway across")
                close(10f, shown.tilt, "halfway turned")
            }
            run(SetEyeFromFront(eye.id, 0f))
            expect { assertNull(it.eye().shownFront(), "none shows the eye's own look at every angle") }
            undo()
            undo()
            expect(start)
        }
        val plain = eye.copy(front = null)
        assertEquals(0f, plain.frontAmount)
        val rounder = plain.withFrontAmount(0.4f)
        close(1f, assertNotNullFront(rounder).roundness, "an eye with no front look gains a round one")
        close(0.4f, rounder.frontAmount, "at the amount asked")
    }

    @Test
    fun gazeIsSetAndUndoesWithinItsReach() {
        scenario(start) {
            run(SetEyeGaze(eye.id, 0.5f, -0.25f))
            expect { assertEquals(0.5f to -0.25f, it.eye().gazeForward to it.eye().gazeUp) }
            undo()
            expect(start)
        }
        assertFailsWith<IllegalArgumentException> { SetEyeGaze(eye.id, 1.5f, 0f) }
    }

    @Test
    fun aFramesDragIsOneStepAndTheFrontLookFollows() {
        scenario(start) {
            gesture(SetEyeFrame(eye.id, 0.62f, 0.2f, 0.09f, 0.04f, 0f), SetEyeFrame(eye.id, 0.65f, 0.22f, 0.12f, 0.06f, 10f))
            expect {
                val now = it.eye()
                assertEquals(0.65f to 0.22f, now.anchor.u to now.anchor.v)
                assertEquals(Triple(0.12f, 0.06f, 10f), Triple(now.look.width, now.look.height, now.look.tilt))
                val followed = assertNotNullFront(now)
                close(front.width * 1.5f, followed.width, "the front look as much wider")
                close(front.height * 1.5f, followed.height, "and as much taller")
                close(front.tilt + 10f, followed.tilt, "and turned as far")
            }
            undo()
            expect(start)
        }
    }

    @Test
    fun aStyleChangeReachesBothLooksAndTheHighlightScalesTheFronts() {
        val slit = eye.styled { it.copy(slit = true) }
        assertTrue(slit.look.slit && assertNotNullFront(slit).slit)
        val larger = eye.withHighlight(eye.look.highlightSize * 2f)
        close(front.highlightSize * 2f, assertNotNullFront(larger).highlightSize, "the front highlight grows as much")
    }

    @Test
    fun theCommandsAndNewFieldsSerialize() {
        val json = Json { classDiscriminator = "type" }
        listOf(SetEyeRoundness(eye.id, 1f), SetEyeFromFront(eye.id, 0.5f), SetEyeGaze(eye.id, 0.1f, 0.2f), SetEyeFrame(eye.id, 0.5f, 0.5f, 0.1f, 0.05f, 3f)).forEach {
            assertEquals<DocumentCommand>(it, json.decodeFromString<DocumentCommand>(json.encodeToString<DocumentCommand>(it)))
        }
        val saved = Json.encodeToString(PaintedEye.serializer(), eye.copy(front = null))
        assertTrue("fromFront" !in saved && "gaze" !in saved, "defaults stay out of saved files: $saved")
        assertEquals(eye.copy(front = null), Json.decodeFromString(PaintedEye.serializer(), saved))
    }

    private fun assertNotNullFront(eye: PaintedEye): EyeLook = assertNotNullLook(eye.front)

    private fun assertNotNullLook(look: EyeLook?): EyeLook = kotlin.test.assertNotNull(look)
}

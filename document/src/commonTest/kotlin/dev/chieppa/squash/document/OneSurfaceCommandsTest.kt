package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one-surface switch, a socket's blend and a painted eye: each a step that undoes, the blend shared by a pair, and neither written while unset. */
class OneSurfaceCommandsTest {
    private val tube = TemplateRef(TemplateId("tube"), 1)
    private val body = PartInstance(PartId("body"), tube)
    private val socket = SocketRef(body.id, "socket.1")
    private val pair = PairId("legs")
    private val left = PartInstance(PartId("leg-l"), tube, socket, side = Side.LEFT, pair = pair)
    private val right = PartInstance(PartId("leg-r"), tube, socket, side = Side.RIGHT, pair = pair)
    private val placed = AddParts(listOf(body, left, right))
        .apply(SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1)))).document

    @Test
    fun theSwitchTurnsOffAndUndoes() {
        scenario(placed) {
            run(SetOneSurface(false))
            expect { assertFalse(it.character.oneSurface) }
            undo()
            expect(placed)
        }
    }

    @Test
    fun aPaintedEyeIsSetChangedAndRemovedEachAStepThatUndoes() {
        val eye = PaintedEye("eyes", Anchor(body.id, BODY_PATCH, 0.6f, 0.2f))
        val wider = eye.copy(look = EyeLook(width = 0.09f))
        scenario(placed) {
            run(SetPaintedEye(eye.id, eye))
            expect { assertEquals(listOf(eye), it.character.paintedEyes) }
            run(SetPaintedEye(eye.id, wider))
            expect { assertEquals(listOf(wider), it.character.paintedEyes) }
            run(SetPaintedEye(eye.id, null))
            expect { assertTrue(it.character.paintedEyes.isEmpty()) }
            undo()
            undo()
            expect { assertEquals(listOf(eye), it.character.paintedEyes) }
            undo()
            expect(placed)
        }
        assertFailsWith<IllegalArgumentException> { SetPaintedEye("other", eye) }
    }

    @Test
    fun aBlendSetOnOneSideHoldsOnBothAndUndoes() {
        scenario(placed) {
            run(SetSocketBlend(left.id, BLEND))
            expect {
                assertEquals(BLEND, it.character.part(left.id)?.blend)
                assertEquals(BLEND, it.character.part(right.id)?.blend, "the pair shares its join")
                assertNull(it.character.part(body.id)?.blend)
            }
            undo()
            expect(placed)
        }
    }

    @Test
    fun aPartOnNoSocketHasNoBlendToSet() {
        assertFailsWith<IllegalArgumentException> { SetSocketBlend(body.id, BLEND).apply(placed) }
        assertFailsWith<IllegalArgumentException> { SetSocketBlend(left.id, -BLEND) }
    }

    @Test
    fun bothSurviveASaveAndNeitherIsWrittenUnset() {
        val set = SetOneSurface(true).apply(SetSocketBlend(left.id, BLEND).apply(placed).document).document
        assertEquals(set, readDocument(Json.encodeToString(SquashDocument.serializer(), set)))
        val plain = Json.encodeToString(SquashDocument.serializer(), placed)
        assertFalse("\"blend\"" in plain || "\"oneSurface\"" in plain, "a character without them writes neither")
    }

    private companion object {
        const val BLEND = 0.05f
    }
}

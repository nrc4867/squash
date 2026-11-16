package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AddParametersTest {
    private val head = PartInstance(PartId("head"), TemplateRef(TemplateId("blob"), 1))
    private val document = AddPart(head).apply(
        SquashDocument(Character(CharacterId("dog"), "Dog", BodyPlanRef(BodyPlanId("blank"), 1))),
    ).document

    private fun params(of: SquashDocument) = of.character.resolvedShape(of.character.part(head.id)!!).params

    @Test
    fun addingNamesThePartDoesNotHoldSetsThemAndUndoesToNone() {
        scenario(document) {
            run(AddParameters(head.id, mapOf("socket.1.u" to 0.4f, "socket.1.v" to 0.5f, "socket.1.pair" to null)))
            expect { assertEquals(mapOf("socket.1.u" to 0.4f, "socket.1.v" to 0.5f), params(it)) }
            undo()
            expect { assertEquals(emptyMap(), params(it)) }
        }
    }

    @Test
    fun addingANameThePartHoldsFailsAndLeavesItAsItWas() {
        val held = AddParameters(head.id, mapOf("socket.1.u" to 0.4f, "socket.1.v" to 0.5f)).apply(document).document
        assertFailsWith<IllegalArgumentException> { AddParameters(head.id, mapOf("socket.1.u" to 0.9f, "socket.1.v" to 0f)).apply(held) }
        assertEquals(mapOf("socket.1.u" to 0.4f, "socket.1.v" to 0.5f), params(held))
    }
}

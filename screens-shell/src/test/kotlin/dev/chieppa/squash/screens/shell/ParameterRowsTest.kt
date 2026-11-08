package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParameterRowsTest {
    private val paw = assertNotNull(shipped.template(TemplateId("paw")))
    private val rows = parameterRows(paw).associateBy { it.name }

    @Test
    fun rowsComeFromTheTemplatesSpec() {
        assertEquals(listOf("size", "toes", "spread", "pads", "webs"), parameterRows(paw).map { it.name })
        assertEquals(0.4f..2.5f, rows.getValue("size").range)
        assertEquals("deg", rows.getValue("spread").unit)
        assertTrue(rows.getValue("pads").isFlag)
        assertNull(rows.getValue("pads").range)
    }

    @Test
    fun aSetValueWinsOverTheDefault() {
        val row = parameterRows(paw, mapOf("toes" to 5f)).first { it.name == "toes" }
        assertEquals(5f, row.value)
        assertEquals(0f, rows.getValue("toes").value)
    }

    @Test
    fun valuesStayInsideTheLimitsAndOnTheStep() {
        val leg = assertNotNull(shipped.template(TemplateId("leg")))
        val stance = parameterRows(leg).first { it.name == "stance" }
        assertEquals(stance.max, stance.constrain(9f))
        assertEquals(stance.min, stance.constrain(-1f))
        val size = rows.getValue("size")
        assertEquals(1.234f, size.constrain(1.234f))
        assertEquals(0.4f, size.constrain(0f))
    }

    @Test
    fun aParameterWithValuesTakesOnlyThose() {
        val toes = rows.getValue("toes")
        assertEquals(0f..5f, toes.range)
        assertEquals(listOf(0f, 0f, 3f, 3f, 3f, 4f, 5f, 5f), listOf(-1f, 1f, 2f, 3f, 3.4f, 3.7f, 5f, 9f).map(toes::constrain))
    }

    @Test
    fun spansGroupConsecutiveValues() {
        assertEquals(listOf(0f..0f, 3f..5f), rows.getValue("toes").spans)
        assertEquals(listOf(0.4f..2.5f), rows.getValue("size").spans)
        assertEquals(emptyList(), rows.getValue("pads").spans)
    }
}

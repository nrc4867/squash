package dev.chieppa.squash.screens.shell

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
        assertEquals(listOf("size", "toes", "spread", "pads"), parameterRows(paw).map { it.name })
        assertEquals(0.4f..2.5f, rows.getValue("size").range)
        assertEquals("deg", rows.getValue("spread").unit)
        assertTrue(rows.getValue("pads").isFlag)
        assertNull(rows.getValue("pads").range)
    }

    @Test
    fun aSetValueWinsOverTheDefault() {
        val row = parameterRows(paw, mapOf("toes" to 5f)).first { it.name == "toes" }
        assertEquals(5f, row.value)
        assertEquals(4f, rows.getValue("toes").value)
    }

    @Test
    fun valuesStayInsideTheLimitsAndOnTheStep() {
        val toes = rows.getValue("toes")
        assertEquals(4f, toes.constrain(3.7f))
        assertEquals(5f, toes.constrain(9f))
        assertEquals(3f, toes.constrain(-1f))
        val size = rows.getValue("size")
        assertEquals(1.234f, size.constrain(1.234f))
        assertEquals(0.4f, size.constrain(0f))
    }
}

package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StatusTextTest {
    private val leg = assertNotNull(shipped.template(TemplateId("leg")))
    private val curve = OutlineCurve(List(4) { CurvePoint(it.toFloat(), 0f) })

    @Test
    fun viewsCountAsDrawnOrFromTemplate() {
        assertEquals(ViewCounts(0, 3), viewCounts(leg, emptyMap()))
        assertEquals(ViewCounts(1, 2), viewCounts(leg, mapOf(View.SIDE to curve)))
    }

    @Test
    fun namesFromFilesReadAsWords() {
        assertEquals("Front leg", displayName("front-leg"))
        assertEquals("Paw claws", displayName("pawClaws"))
        assertEquals("Size", displayName("size"))
    }
}

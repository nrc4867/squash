package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import dev.chieppa.stretch.ui.flattenTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartTreeSidesTest {
    private val plan = shipped.plans.first()
    private val curve = OutlineCurve(List(4) { CurvePoint(it.toFloat(), 1f) })

    private fun withLegs(): Pair<SquashDocument, List<dev.chieppa.squash.document.PartInstance>> {
        val new = newCharacter(plan, shipped, "Fennec")
        val base = new.commands.fold(new.document) { d, c -> c.apply(d).document }
        val socket = openSockets(base, plan, shipped).first { it.paired }
        val legs = placeOnSocket(socket, checkNotNull(shipped.template(TemplateId("leg"))), base)
        return AddParts(legs).apply(base).document to legs
    }

    private fun rows(document: SquashDocument, mirror: Boolean) =
        flattenTree(partTree(document, plan, shipped, mirror)) { true }.map { it.node.item }

    @Test
    fun aPairIsOneRowWhileItsSidesAreTheSame() {
        val (document, _) = withLegs()
        assertTrue(rows(document, mirror = true).none { it is PartRow.SideOf })
    }

    @Test
    fun mirrorOffGivesEachSideARow() {
        val (document, legs) = withLegs()
        val sides = rows(document, mirror = false).filterIsInstance<PartRow.SideOf>()
        assertEquals(legs.map { it.id }.toSet(), sides.map { it.part.id }.toSet())
        assertEquals(listOf(Side.LEFT, Side.RIGHT), sides.map { it.side })
        assertTrue(sides.none { it.differs })
    }

    @Test
    fun anOverrideKeepsTheSidesReachableWithMirrorOn() {
        val (document, legs) = withLegs()
        val right = legs.first { it.side == Side.RIGHT }
        val differing = SetOutline(right.id, View.SIDE, curve, thisSide = true).apply(document).document
        val sides = rows(differing, mirror = true).filterIsInstance<PartRow.SideOf>()
        assertEquals(mapOf(Side.LEFT to false, Side.RIGHT to true), sides.associate { it.side to it.differs })
    }
}

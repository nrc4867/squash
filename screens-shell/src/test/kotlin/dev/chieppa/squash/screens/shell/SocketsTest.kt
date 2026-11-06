package dev.chieppa.squash.screens.shell

import dev.chieppa.squash.character.catalog.ViewCounts
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.socketFor
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.catalog.viewCounts
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BreakPair
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.View
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocketsTest {
    private fun rooted(): SquashDocument {
        val new = newCharacter(caninePlan, shipped, "Fennec")
        return new.commands.fold(new.document) { document, command -> command.apply(document).document }
    }

    private fun SquashDocument.place(socketName: String, type: String): SquashDocument {
        val socket = openSockets(this, caninePlan, shipped).first { it.name == socketName }
        return placeOnSocket(socket, assertNotNull(shipped.template(TemplateId(type))), this)
            .fold(this) { document, part -> AddPart(part).apply(document).document }
    }

    @Test
    fun theRootOffersThePlansSocketsWithALoadedTemplate() {
        val names = openSockets(rooted(), caninePlan, shipped).map { it.name }
        assertEquals(listOf("neck", "front-leg", "back-leg", "tail"), names)
    }

    @Test
    fun aFilledSocketStopsBeingOfferedAndItsPartOffersItsOwn() {
        val names = openSockets(rooted().place("neck", "head"), caninePlan, shipped).map { it.name }
        assertFalse("neck" in names)
        assertTrue("ear" in names)
    }

    @Test
    fun aPairedSocketPlacesBothSidesUnderOnePairId() {
        val document = rooted().place("front-leg", "leg")
        val legs = document.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(listOf(Side.LEFT, Side.RIGHT), legs.map { it.side })
        assertEquals(1, legs.map { it.pair }.toSet().size)
        assertNotNull(legs.first().pair)
        assertEquals(1, legs.map { it.socket?.parent }.toSet().size)
    }

    @Test
    fun aPartOnAPairedParentPairsAcrossItsSides() {
        val document = rooted().place("front-leg", "leg")
        assertEquals(1, openSockets(document, caninePlan, shipped).count { it.name == "paw" })
        val paws = document.place("paw", "paw").character.parts.filter { it.socket?.socket == "paw" }
        val legs = document.character.parts.filter { it.socket?.socket == "front-leg" }
        assertEquals(legs.map { it.id }, paws.map { it.socket?.parent })
        assertEquals(legs.map { it.side }, paws.map { it.side })
        assertEquals(1, paws.map { it.pair }.toSet().size)
    }

    @Test
    fun aSingleSocketPlacesOneUnpairedPart() {
        val tail = rooted().place("tail", "tail").character.parts.single { it.socket?.socket == "tail" }
        assertNull(tail.pair)
        assertNull(tail.side)
    }

    @Test
    fun aTypeGoesToTheCurrentSocketWhenItCanOtherwiseTheFirstThatTakesIt() {
        val sockets = openSockets(rooted(), caninePlan, shipped)
        val back = sockets.first { it.name == "back-leg" }
        val tail = sockets.first { it.name == "tail" }
        assertEquals(back, socketFor(TemplateId("leg"), sockets, back))
        assertEquals("front-leg", socketFor(TemplateId("leg"), sockets, tail)?.name)
        assertNull(socketFor(TemplateId("paw"), sockets, tail))
    }

    @Test
    fun theTreeShowsAPairOnceWithItsOpenSocketsBeneathIt() {
        val roots = partTree(rooted().place("front-leg", "leg"), caninePlan, shipped)
        val torso = roots.single()
        assertEquals("Torso", torso.item.label)
        val labels = torso.children.map { it.item.label }
        assertEquals(listOf("Front leg", "Neck", "Back leg", "Tail"), labels)
        val leg = torso.children.first()
        assertTrue(leg.item is PartRow.Placed)
        assertEquals(listOf("Paw"), leg.children.map { it.item.label })
        assertTrue(leg.children.single().item is PartRow.Open)
    }

    @Test
    fun theTreeNamesAPartByItsTypeUnlessItsSocketNamesItCloser() {
        val roots = partTree(rooted().place("neck", "head").place("front-leg", "leg"), caninePlan, shipped)
        val labels = roots.single().children.map { it.item.label }
        assertTrue("Head" in labels, "a head on the neck reads Head, in $labels")
        assertTrue("Front leg" in labels, "a leg on the front-leg socket reads Front leg, in $labels")
    }

    private val curve = OutlineCurve(List(4) { CurvePoint(it.toFloat(), 0f) })

    private fun SquashDocument.legs(): List<PartInstance> = character.parts.filter { it.socket?.socket == "front-leg" }

    private fun SquashDocument.draw(part: PartInstance, thisSide: Boolean = false): SquashDocument =
        SetOutline(part.id, View.SIDE, curve, thisSide).apply(this).document

    private fun progress(document: SquashDocument) = partProgress(document, caninePlan, shipped)

    @Test
    fun progressCountsDrawnPartsWithAPairOnce() {
        val placed = rooted().place("front-leg", "leg")
        assertEquals(PartProgress(0, 8), progress(placed))
        assertEquals(PartProgress(1, 8), progress(placed.draw(placed.legs().first())))
        assertEquals(PartProgress(1, 8), progress(placed.draw(placed.legs().last())))
    }

    @Test
    fun anOverrideOnTheRightAloneCountsThePairAsDrawn() {
        val placed = rooted().place("front-leg", "leg")
        val drawn = placed.draw(placed.legs().last(), thisSide = true)
        assertEquals(PartProgress(1, 8), progress(drawn))
        val row = partTree(drawn, caninePlan, shipped).single().children.first().item
        assertEquals(true, (row as PartRow.Placed).drawn)
    }

    @Test
    fun aDrawnPairShowsDrawnInTheTreeAndBothSidesCountTheView() {
        val placed = rooted().place("front-leg", "leg")
        val drawn = placed.draw(placed.legs().first())
        val row = partTree(drawn, caninePlan, shipped).single().children.first().item
        assertEquals(true, (row as PartRow.Placed).drawn)
        val leg = assertNotNull(shipped.template(TemplateId("leg")))
        for (side in drawn.legs()) {
            assertEquals(ViewCounts(1, 2), viewCounts(leg, drawn.character.resolved(side).outlines))
        }
    }

    @Test
    fun aBrokenPairsSidesAreEachAPartInTheTreeAndTheCount() {
        val placed = rooted().place("front-leg", "leg")
        val pair = assertNotNull(placed.legs().first().pair)
        val broken = BreakPair(pair).apply(placed.draw(placed.legs().first())).document
        assertEquals(PartProgress(2, 9), progress(broken))
        val labels = partTree(broken, caninePlan, shipped).single().children.map { it.item.label }
        assertEquals(2, labels.count { it == "Front leg" })
        assertEquals(2, openSockets(broken, caninePlan, shipped).count { it.name == "paw" })
    }
}

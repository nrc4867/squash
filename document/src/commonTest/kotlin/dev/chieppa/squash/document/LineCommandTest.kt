package dev.chieppa.squash.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val TORSO = PartId("torso")
private val LEFT = PartId("leg-l")
private val RIGHT = PartId("leg-r")
private val PAIR = PairId("legs")
private val WEIGHT = LineWeightId("back")

private fun ref(id: String) = TemplateRef(TemplateId(id), 1)

private fun document(): SquashDocument {
    val start = SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("p"), 1)))
    val parts = AddParts(
        listOf(
            PartInstance(TORSO, ref("torso")),
            PartInstance(LEFT, ref("leg"), SocketRef(TORSO, "leg"), pair = PAIR, side = Side.LEFT),
            PartInstance(RIGHT, ref("leg"), SocketRef(TORSO, "leg"), pair = PAIR, side = Side.RIGHT),
        ),
    )
    return parts.apply(start).document
}

private fun weight(part: PartId, v: Float = 0.25f) = LineWeight(WEIGHT, listOf(Anchor(part, BODY_PATCH, 0.5f, v)), 0.2f, 0.6f)

class LineCommandTest {
    @Test
    fun aWeightIsAddedAndItsUndoRestoresTheDocument() {
        val start = document()
        val applied = SetLineWeight(TORSO, WEIGHT, weight(TORSO)).apply(start)
        assertEquals(listOf(weight(TORSO)), applied.document.character.resolved(TORSO)?.surface?.lineWeights)
        assertEquals(start, applied.undo.apply(applied.document).document)
    }

    @Test
    fun aPairSharesAWeightAndEachSideReadsItOnItsOwnPart() {
        val written = SetLineWeight(LEFT, WEIGHT, weight(LEFT)).apply(document()).document
        val left = written.character.resolved(LEFT)?.surface?.lineWeights?.single()
        val right = written.character.resolved(RIGHT)?.surface?.lineWeights?.single()
        assertEquals(LEFT, left?.points?.single()?.part)
        assertEquals(RIGHT, right?.points?.single()?.part)
    }

    @Test
    fun aWriteFromTheRightStoresTheSameAsFromTheLeft() {
        val fromLeft = SetLineWeight(LEFT, WEIGHT, weight(LEFT)).apply(document()).document
        val fromRight = SetLineWeight(RIGHT, WEIGHT, weight(RIGHT)).apply(document()).document
        assertEquals(fromLeft, fromRight)
    }

    @Test
    fun aSideCanHoldItsOwnAmount() {
        val written = SetLineWeight(LEFT, WEIGHT, weight(LEFT)).apply(document()).document
        val applied = SetLineWeightAmount(RIGHT, WEIGHT, 0.9f, thisSide = true).apply(written)
        assertEquals(0.6f, applied.document.character.resolved(LEFT)?.surface?.lineWeights?.single()?.amount)
        assertEquals(0.9f, applied.document.character.resolved(RIGHT)?.surface?.lineWeights?.single()?.amount)
        assertEquals(written, applied.undo.apply(applied.document).document)
    }

    @Test
    fun amountOfAWeightNotThereIsRefused() {
        assertFailsWith<IllegalArgumentException> { SetLineWeightAmount(TORSO, WEIGHT, 0.5f).apply(document()) }
    }

    @Test
    fun aWeightIsRemovedInPlaceAndUndoPutsItBack() {
        val other = LineWeightId("other")
        val first = SetLineWeight(TORSO, WEIGHT, weight(TORSO)).apply(document()).document
        val both = SetLineWeight(TORSO, other, weight(TORSO).copy(id = other)).apply(first).document
        val removed = SetLineWeight(TORSO, WEIGHT, null).apply(both)
        assertEquals(listOf(other), removed.document.character.resolved(TORSO)?.surface?.lineWeights?.map { it.id })
        assertEquals(both, removed.undo.apply(removed.document).document)
    }

    @Test
    fun rulesAreSetAndUndone() {
        val start = document()
        val applied = SetLineRules(LineRules(silhouette = 6f, interior = 1f, taper = 0.2f)).apply(start)
        assertEquals(6f, applied.document.character.lineRules.silhouette)
        assertEquals(start, applied.undo.apply(applied.document).document)
    }

    @Test
    fun aWeightOrARuleOutOfRangeIsRefused() {
        assertFailsWith<IllegalArgumentException> { weight(TORSO).copy(radius = 0f) }
        assertFailsWith<IllegalArgumentException> { weight(TORSO).copy(amount = -1f) }
        assertFailsWith<IllegalArgumentException> { LineRules(taper = 1.5f) }
    }
}

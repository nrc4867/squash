package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Pairs made by hand and templates changed in place: one step each, nothing a part held lost. See HLD: data-model, Pairs by hand. */
class JoinPairTest {
    private val tube = TemplateRef(TemplateId("tube"), 1)
    private val body = PartInstance(PartId("body"), tube)
    private val onLeft = SocketRef(body.id, "socket.1")
    private val onRight = SocketRef(body.id, "socket.2")
    private val side = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0f), CurvePoint(1f, 1f), CurvePoint(0f, 1f)))
    private val other = OutlineCurve(side.points.map { CurvePoint(it.x, it.y + 1f) })
    private val top = OutlineCurve(side.points.reversed())
    private val left = PartInstance(PartId("leg-a"), tube, onLeft, role = Role.LEG, roleGuessed = true)
    private val right = PartInstance(PartId("leg-b"), tube, onRight, role = Role.OTHER)
    private val pair = PairId("legs")
    private val placed = listOf(
        AddParts(listOf(body, left, right)),
        SetOutline(left.id, View.SIDE, side),
        SetParameter(left.id, "girth", 0.1f),
        SetOutlines(right.id, mapOf(View.SIDE to other, View.TOP to top)),
        SetParameter(right.id, "girth", 0.1f),
    ).fold(SquashDocument(Character(CharacterId("c"), "C", BodyPlanRef(BodyPlanId("blank"), 1)))) { held, command -> command.apply(held).document }

    @Test
    fun theLeftsDataIsSharedAndTheRightKeepsWhatDiffersAsItsOverrides() {
        scenario(placed) {
            run(JoinPair(pair, left.id, right.id, onLeft))
            expect {
                val leftNow = checkNotNull(it.character.part(left.id))
                val rightNow = checkNotNull(it.character.part(right.id))
                assertEquals(Side.LEFT, leftNow.side)
                assertEquals(Side.RIGHT, rightNow.side)
                assertEquals(onLeft, rightNow.socket, "the right side sits on the left's socket, mirrored")
                assertEquals(Role.LEG, rightNow.role, "both sides share the left's role")
                assertEquals(side, it.character.resolved(leftNow).outlines[View.SIDE])
                assertEquals(other, it.character.resolved(rightNow).outlines[View.SIDE], "the right's own drawing stays as its override")
                assertEquals(top, it.character.resolved(rightNow).outlines[View.TOP])
                assertEquals(setOf(View.SIDE, View.TOP), it.character.overridesOf(rightNow).outlines.keys)
                assertEquals(emptyMap(), it.character.overridesOf(rightNow).params, "a value both held alike is shared, not overridden")
            }
            undo()
            expect(placed)
            redo()
            expect { assertEquals(pair, it.character.part(right.id)?.pair) }
        }
    }

    @Test
    fun breakingTheJoinedPairLeavesEachSideWithWhatItShows() {
        val joined = JoinPair(pair, left.id, right.id, onLeft).apply(placed).document
        val broken = BreakPair(pair).apply(joined).document
        assertNull(broken.character.part(right.id)?.pair)
        assertEquals(other, broken.character.resolved(right.id)?.outlines?.get(View.SIDE))
        assertEquals(side, broken.character.resolved(left.id)?.outlines?.get(View.SIDE))
    }

    @Test
    fun onlyTwoUnpairedPartsJoin() {
        val joined = JoinPair(pair, left.id, right.id, onLeft).apply(placed).document
        assertFailsWith<IllegalArgumentException> { JoinPair(PairId("again"), left.id, right.id, onLeft).apply(joined) }
        assertFailsWith<IllegalArgumentException> { JoinPair(PairId("self"), left.id, left.id, onLeft).apply(placed) }
    }

    @Test
    fun aTemplateChangeKeepsTheDrawingAndUndoes() {
        val leg = TemplateRef(TemplateId("leg"), 2)
        scenario(placed) {
            run(SetTemplate(left.id, leg))
            expect {
                assertEquals(leg, it.character.part(left.id)?.template)
                assertEquals(placed.character.resolved(left.id), it.character.resolved(left.id))
            }
            undo()
            expect(placed)
        }
    }

    @Test
    fun theCommandsAreSerializable() {
        val commands: List<DocumentCommand> = listOf(JoinPair(pair, left.id, right.id, onLeft), SetTemplate(left.id, tube))
        commands.forEach { assertEquals(it, Json.decodeFromString(DocumentCommand.serializer(), Json.encodeToString(DocumentCommand.serializer(), it))) }
    }
}

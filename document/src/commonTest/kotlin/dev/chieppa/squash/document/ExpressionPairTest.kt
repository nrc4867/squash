package dev.chieppa.squash.document

import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionPairTest : PairedFennec() {
    @Test
    fun expressionMarksAreSharedReadForTheSideAndOverridden() {
        val eye = eyeGroup(MarkGroupId("eye"), "Eye")
        val glad = ExpressionSet(ExpressionId("glad"), "Glad")
        val lid = line("lid", legL)
        val brow = line("brow", legL)
        val ownBrow = Mark(MarkId("own"), listOf(on(legR, 0.1f), on(legR, 0.2f)))
        fun SquashDocument.shows(part: PartInstance) =
            character.expressionMarks(character.expressions.single(), checkNotNull(character.part(part.id)), eye.id)
        val set = listOf(SetMarkGroup(legL.id, eye.id, eye), SetExpression(glad.id, glad)).fold(placed) { d, c -> c.apply(d).document }
        scenario(set) {
            run(
                SetExpressionMarks(glad.id, legL.id, eye.id, MarkRole.LID, listOf(lid)),
                SetExpressionMarks(glad.id, legL.id, eye.id, MarkRole.BROW, listOf(brow)),
                SetExpressionMarks(glad.id, legR.id, eye.id, MarkRole.BROW, listOf(ownBrow), thisSide = true),
            )
            expect {
                assertEquals(listOf(lid), it.shows(legL)[MarkRole.LID])
                assertEquals(listOf(line("lid", legR)), it.shows(legR)[MarkRole.LID])
                assertEquals(listOf(brow), it.shows(legL)[MarkRole.BROW])
                assertEquals(listOf(ownBrow), it.shows(legR)[MarkRole.BROW])
            }
            undo()
            expect { assertEquals(listOf(line("brow", legR)), it.shows(legR)[MarkRole.BROW]) }
            undo()
            undo()
            expect(set)
        }
    }

    @Test
    fun expressionMarksAreStoredTheSameFromEitherSide() {
        val eye = MarkGroupId("eye")
        val glad = ExpressionSet(ExpressionId("glad"), "Glad")
        val start = SetExpression(glad.id, glad).apply(placed).document
        val fromLeft = SetExpressionMarks(glad.id, legL.id, eye, MarkRole.LID, listOf(line("lid", legL))).apply(start).document
        val fromRight = SetExpressionMarks(glad.id, legR.id, eye, MarkRole.LID, listOf(line("lid", legR))).apply(start).document
        assertEquals(fromLeft, fromRight)
    }

    @Test
    fun breakingAPairGivesEachSideItsExpressionMarksAndUndoesExactly() {
        val eye = eyeGroup(MarkGroupId("eye"), "Eye")
        val glad = ExpressionSet(ExpressionId("glad"), "Glad")
        val lid = line("lid", legL)
        val before = listOf(
            SetMarkGroup(legL.id, eye.id, eye),
            SetExpression(glad.id, glad),
            SetExpressionMarks(glad.id, legL.id, eye.id, MarkRole.LID, listOf(lid)),
        ).fold(placed) { d, c -> c.apply(d).document }
        fun SquashDocument.lidOn(part: PartInstance) =
            character.expressionMarks(character.expressions.single(), checkNotNull(character.part(part.id)), eye.id)[MarkRole.LID]
        scenario(before) {
            run(BreakPair(legs))
            expect {
                assertEquals(listOf(lid), it.lidOn(legL))
                assertEquals(listOf(line("lid", legR)), it.lidOn(legR))
            }
            run(SetExpressionMarks(glad.id, legR.id, eye.id, MarkRole.LID, listOf(line("lid2", legR))))
            expect {
                assertEquals(listOf(lid), it.lidOn(legL))
                assertEquals(listOf(line("lid2", legR)), it.lidOn(legR))
            }
            undo()
            undo()
            expect(before)
        }
    }

}

package dev.chieppa.squash.character

import dev.chieppa.squash.character.mouth.FlatMark
import dev.chieppa.squash.character.mouth.MouthDrawing
import dev.chieppa.squash.character.mouth.MouthShaper
import dev.chieppa.squash.document.MarkControls
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.geom.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DEPTH = 0.45f
private const val SLACK = 1e-4f

/** The one mouth shaper on a flat lip line two units wide: what each control does, riding, and what draws over the lips. */
class MouthShaperTest {
    private val lip = listOf(Vec2(-1f, 0f), Vec2(0f, 0f), Vec2(1f, 0f))

    /** A fang hanging from the lip line at the middle's left. */
    private val fang = listOf(Vec2(-0.3f, -0.02f), Vec2(-0.1f, -0.02f), Vec2(-0.2f, 0.25f))

    /** A tongue drawn as it sits in a fully open mouth. */
    private val tongue = listOf(Vec2(-0.3f, 0.5f), Vec2(0f, 0.4f), Vec2(0.3f, 0.5f), Vec2(0f, 0.7f))

    private fun shape(controls: MarkControls, teeth: List<FlatMark> = emptyList(), tongues: List<FlatMark> = emptyList()) =
        MouthShaper.shape(MouthDrawing(lip, teeth = teeth, tongue = tongues), controls, DEPTH)

    @Test
    fun aMouthAtRestShowsOnlyItsDrawnLine() {
        val rest = shape(MarkControls())
        assertTrue(rest.restsAsDrawn)
        assertNull(rest.opening)
    }

    @Test
    fun openDropsTheLowerLipByTheDepthAtTheMiddleAndNothingAtTheCorners() {
        val open = shape(MarkControls(open = 1f))
        val lower = open.lower.single()
        assertEquals(DEPTH * 2f, lower[lower.size / 2].y, SLACK)
        assertEquals(0f, lower.first().y, SLACK)
        assertEquals(0f, lower.last().y, SLACK)
        assertTrue(!open.lipMoved, "open alone leaves the upper lip as drawn")
    }

    @Test
    fun smileLiftsTheCornersAndWideDrawsThemOut() {
        val smiling = shape(MarkControls(smile = 1f)).upper.single()
        assertTrue(smiling.first().y < 0f && smiling.last().y < 0f, "the corners rise")
        assertEquals(0f, smiling[smiling.size / 2].y, SLACK)
        val wide = shape(MarkControls(wide = 1f)).upper.single()
        assertTrue(wide.last().x > 1f && wide.first().x < -1f)
        val round = shape(MarkControls(round = 1f)).upper.single()
        assertTrue(round.last().x < 1f, "round draws the corners in")
    }

    @Test
    fun aPressCreasesUnderTheLipsAndATuckTakesBackTheDrop() {
        assertTrue(shape(MarkControls(lowerLip = -1f)).crease != null)
        val tucked = shape(MarkControls(open = 1f, lowerLip = 1f)).lower.single()
        assertTrue(tucked[tucked.size / 2].y < DEPTH * 2f)
    }

    @Test
    fun aFangInsideAClosedMouthHidesAndOneFlaggedOverShowsAndBreaksTheLipInk() {
        assertTrue(shape(MarkControls(), teeth = listOf(FlatMark(MarkId("fang"), fang))).teeth.isEmpty())
        val over = shape(MarkControls(), teeth = listOf(FlatMark(MarkId("fang"), fang, over = true)))
        assertEquals(1, over.teeth.size)
        assertTrue(over.upper.size == 2, "the lip ink breaks under the fang")
        assertTrue(over.lipMoved)
    }

    @Test
    fun anUpperToothRidesTheUpperLip() {
        val raised = shape(MarkControls(open = 0.5f, upperLip = 1f), teeth = listOf(FlatMark(MarkId("fang"), fang, over = true)))
        assertTrue(raised.teeth.single().loop.minOf { it.y } < fang.minOf { it.y }, "raising the upper lip lifts the tooth")
    }

    @Test
    fun aTongueAtOnePassesTheLowerLipOfAClosedMouth() {
        val blep = shape(MarkControls(tongue = 1f), tongues = listOf(FlatMark(MarkId("tongue"), tongue)))
        val piece = blep.tongue.single()
        assertTrue(piece.over, "it draws over the lips")
        assertTrue(piece.loop.maxOf { it.y } > 0f, "it hangs below the lip line")
        assertTrue(shape(MarkControls(tongue = 0.3f), tongues = listOf(FlatMark(MarkId("tongue"), tongue))).tongue.isEmpty(), "a lifted tongue stays in a shut mouth")
    }

    /** An upper lip drawn above the line and a lower one below it. */
    private val upperLip = listOf(Vec2(-1f, 0f), Vec2(0f, -0.2f), Vec2(1f, 0f), Vec2(0f, -0.02f))
    private val lowerLip = listOf(Vec2(-1f, 0f), Vec2(0f, 0.02f), Vec2(1f, 0f), Vec2(0f, 0.3f))

    private fun lips(controls: MarkControls) = MouthShaper.shape(MouthDrawing(lip, lipsFilled = true, lipShapes = listOf(upperLip, lowerLip)), controls, DEPTH)

    @Test
    fun drawnLipsRestAsDrawnAndMoveWithTheLips() {
        val rest = lips(MarkControls())
        assertTrue(rest.restsAsDrawn, "drawn lips at rest add nothing to what is painted")
        assertEquals(listOf(upperLip, lowerLip), rest.lips)
        val open = lips(MarkControls(open = 1f)).lips
        assertEquals(upperLip, open[0], "the upper lip holds while the jaw drops")
        assertEquals(0.3f + DEPTH * 2f, open[1][3].y, SLACK, "the lower lip's middle drops with the lower lip")
        val round = lips(MarkControls(round = 1f)).lips
        assertTrue(round[1].maxOf { it.x } < 1f, "a pucker draws the lips' corners in")
        val pressed = lips(MarkControls(lowerLip = -1f)).lips
        assertTrue(pressed[0][1].y > -0.2f, "pressed lips thin")
    }
}

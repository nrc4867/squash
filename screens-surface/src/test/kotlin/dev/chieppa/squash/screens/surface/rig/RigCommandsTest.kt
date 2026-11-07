package dev.chieppa.squash.screens.surface.rig

import dev.chieppa.squash.character.build.RigParams
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RigCommandsTest {
    private val fennec = savedFennec()
    private val knee = BoneName(PartId("front-leg-l"), "knee")
    private val twinKnee = BoneName(PartId("front-leg-r"), "knee")

    @Test
    fun aReachIsOneStepSharedByThePairOrHeldByOneSide() {
        val both = checkNotNull(reachCommand(fennec.character, knee, 1.4f, thisSide = false))
        scenario(fennec) {
            run(both)
            expect {
                assertEquals(1.4f, reachOf(it.character, knee), 1e-6f)
                assertEquals(1.4f, reachOf(it.character, twinKnee), 1e-6f)
            }
            undo()
            expect(fennec)
        }
        val one = checkNotNull(reachCommand(fennec.character, knee, 0.6f, thisSide = true))
        scenario(fennec) {
            run(one)
            expect {
                assertEquals(0.6f, reachOf(it.character, knee), 1e-6f)
                assertEquals(1f, reachOf(it.character, twinKnee))
            }
            undo()
            expect(fennec)
        }
    }

    @Test
    fun aReachHoldsToItsRange() {
        assertEquals(RigParams.MAX_REACH, reachCommand(fennec.character, knee, 99f, false)?.value)
        assertEquals(RigParams.MIN_REACH, reachCommand(fennec.character, knee, 0f, false)?.value)
    }

    @Test
    fun resetAndToTwinAskForWhatTheyActOn() {
        assertNull(resetCommand(fennec.character, PartId("front-leg-l"), false), "nothing painted, nothing to reset")
        assertNull(toTwinCommand(fennec.character, PartId("tail")), "the tail has no twin")
    }

    @Test
    fun twinsShareAHueAndTheRightIsDarker() {
        val colors = rigPartColors(fennec.character)
        assertEquals(fennec.character.parts.size, colors.size)
        val pairs = fennec.character.parts.filter { it.side == Side.LEFT }
        for (left in pairs) {
            val right = checkNotNull(fennec.character.twinOf(left))
            val l = colors.getValue(left.id)
            val r = colors.getValue(right.id)
            assertEquals(l.red * RIGHT_SIDE_SHADE, r.red, 1f / 255f)
        }
        val groups = fennec.character.parts.filter { it.side != Side.RIGHT }.map { colors.getValue(it.id) }
        assertEquals(groups.size, groups.toSet().size, "each part or pair has its own color")
    }
}

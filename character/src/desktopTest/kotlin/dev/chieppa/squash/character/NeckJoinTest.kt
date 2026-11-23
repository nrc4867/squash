package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.NeckJoin
import dev.chieppa.squash.document.NeckStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A head's neck style read from its placement: blended where none is given, each style's value read back, and slimmer necks joining tighter. */
class NeckJoinTest {
    @Test
    fun aPlacementWithNoStyleIsBlended() {
        assertEquals(NeckJoin.BLENDED, NeckJoin.of(emptyMap()))
        assertEquals(1f, NeckJoin.BLENDED.blendShare)
        assertEquals(0f, NeckJoin.BLENDED.shade)
    }

    @Test
    fun eachStyleReadsBackFromItsValue() {
        NeckStyle.entries.forEach { style ->
            val join = NeckJoin.of(style)
            assertEquals(join, NeckJoin.of(mapOf(JoinParams.NECK to join.value)))
            assertEquals(style, join.style)
        }
    }

    @Test
    fun aMoreDefinedNeckJoinsTighterTurnsTheHeadMoreWholeAndShadesMore() {
        val order = listOf(NeckJoin.BLENDED, NeckJoin.BETWEEN, NeckJoin.DEFINED)
        order.zipWithNext().forEach { (looser, tighter) ->
            assertTrue(tighter.blendShare < looser.blendShare)
            assertTrue(tighter.seamShare < looser.seamShare)
            assertTrue(tighter.shade > looser.shade)
        }
    }
}

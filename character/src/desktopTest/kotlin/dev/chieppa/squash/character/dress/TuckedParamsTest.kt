package dev.chieppa.squash.character.dress

import dev.chieppa.squash.document.TuckKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a tucked set of digits writes on its part: no toes, no claws, or fingers fused and folded together into a mitten. */
class TuckedParamsTest {
    @Test
    fun tuckedToesAndClawsBuildNone() {
        assertEquals(mapOf("toes" to 0f), tuckedParams(TuckKind.TOES))
        assertEquals(mapOf("claws" to 0f), tuckedParams(TuckKind.CLAWS))
    }

    @Test
    fun tuckedFingersFuseToTheTipsAndFoldTogether() {
        val mitten = tuckedParams(TuckKind.FINGERS)
        assertTrue(mitten.getValue("merge") > 1f, "fingers fused past 1 join to the tips")
        assertTrue(mitten.getValue("spread") < 1f, "fingers fold closer together than at rest")
    }
}

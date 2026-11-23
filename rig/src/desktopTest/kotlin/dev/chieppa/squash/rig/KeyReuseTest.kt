package dev.chieppa.squash.rig

import dev.chieppa.squash.character.keys.KeyShape
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** A key's shape carried onto the vertices a new mesh keeps: each kept vertex moved as it was, the rest left out or the whole refused. */
class KeyReuseTest {
    private val shape = KeyShape(intArrayOf(1, 3, 4), floatArrayOf(1f, 0f, 0f, 2f, 0f, 0f, 3f, 0f, 0f), floatArrayOf(0f, 1f, 0f, 0f, 2f, 0f, 0f, 3f, 0f))

    @Test
    fun keptVerticesTakeTheirMovesAtTheirNewPlaces() {
        val carried = checkNotNull(shape.carried(intArrayOf(0, 7, 2, 8, 9), whole = true))
        assertContentEquals(intArrayOf(7, 8, 9), carried.moved)
        assertContentEquals(shape.offsets, carried.offsets)
    }

    @Test
    fun aVertexTheMeshDidNotKeepIsLeftOutOrRefusesTheWhole() {
        val kept = intArrayOf(0, 7, 2, -1, 9)
        assertNull(shape.carried(kept, whole = true))
        val carried = checkNotNull(shape.carried(kept, whole = false))
        assertContentEquals(intArrayOf(7, 9), carried.moved)
        assertContentEquals(floatArrayOf(1f, 0f, 0f, 3f, 0f, 0f), carried.offsets)
        assertContentEquals(floatArrayOf(0f, 1f, 0f, 0f, 3f, 0f), carried.turns)
    }
}

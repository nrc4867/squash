package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolSectionsTest {
    private val tools = listOf(6, 2, 1, 4, 5)

    @Test
    fun withRoomNothingCollapses() {
        assertEquals(emptySet(), collapsedSections(tools, emptySet(), 2000.dp))
    }

    @Test
    fun theLastSectionsCollapseFirstAndOneButtonNever() {
        val tight = collapsedSections(tools, emptySet(), 600.dp)
        assertTrue(4 in tight)
        assertTrue(0 !in tight)
        assertEquals(setOf(0, 1, 3, 4), collapsedSections(tools, emptySet(), 10.dp))
    }

    @Test
    fun theArmedSectionCollapsesLast() {
        val tight = collapsedSections(tools, setOf(4), 500.dp)
        assertTrue(4 !in tight, "the armed section holds while another can give way")
        assertTrue(3 in tight)
    }

    @Test
    fun whatCollapsesLeavesTheRowInsideItsRoom() {
        val room = 400.dp
        val collapsed = collapsedSections(tools, emptySet(), room, 38.dp, 13.dp)
        val width = tools.indices.sumOf { (if (it in collapsed) 1 else tools[it]) * 38 } + 13 * (tools.size - 1)
        assertTrue(width <= 400, "$width")
    }
}

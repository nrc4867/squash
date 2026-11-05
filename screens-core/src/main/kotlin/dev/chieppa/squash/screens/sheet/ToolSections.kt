package dev.chieppa.squash.screens.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.frame.ToolbarSeparator
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.ToolButton
import dev.chieppa.stretch.ui.Tooltip
import dev.chieppa.stretch.ui.opensOnPress

private val TOOL_SIZE = 34.dp
private val TOOL_GAP = 4.dp
private val SEPARATOR_ROOM = 13.dp
private val POPOVER_INSET = 26.dp
private val CORNER_SIZE = 6.dp
private val CORNER_INSET = 3.dp

/**
 * A run of tools in the strip. Where the strip has room it shows [content], its [tools] buttons; where it has not, one button with [icon] and a
 * corner mark, which opens them under [label]. [armed] is the tool of this section that is armed, which lights that button.
 */
class ToolSection(
    val tag: String,
    val label: String,
    val icon: ImageVector,
    val tools: Int,
    val armed: BuildTool? = null,
    val content: @Composable () -> Unit,
)

/**
 * Which of the sections holding [tools] buttons each collapse to fit [room] between separators: the last first, so the first keep their buttons, and
 * those in [armed] only once no other is left. A section of one button stays.
 */
fun collapsedSections(tools: List<Int>, armed: Set<Int>, room: Dp, step: Dp = TOOL_SIZE + TOOL_GAP, separator: Dp = SEPARATOR_ROOM): Set<Int> {
    val collapsed = hashSetOf<Int>()
    fun width() = tools.indices.fold(separator * (tools.size - 1).coerceAtLeast(0)) { total, i -> total + step * (if (i in collapsed) 1 else tools[i]) }
    val order = tools.indices.reversed().filter { tools[it] > 1 }.sortedBy { it in armed }
    for (section in order) {
        if (width() <= room) break
        collapsed += section
    }
    return collapsed
}

/** [sections] between separators, as many whole as the width allows. */
@Composable
fun ToolSections(sections: List<ToolSection>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val armed = sections.indices.filterTo(hashSetOf()) { sections[it].armed != null }
        val collapsed = if (constraints.hasBoundedWidth) collapsedSections(sections.map { it.tools }, armed, maxWidth) else emptySet()
        Row(horizontalArrangement = Arrangement.spacedBy(TOOL_GAP), verticalAlignment = Alignment.CenterVertically) {
            sections.forEachIndexed { index, section ->
                if (index > 0) ToolbarSeparator()
                if (index in collapsed) CollapsedSection(section) else section.content()
            }
        }
    }
}

@Composable
private fun CollapsedSection(section: ToolSection) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(section.armed) { open = false }
    val mark = Stretch.colors.inkMuted
    Box {
        Tooltip(section.label, emptyList()) {
            ToolButton(section.icon, section.label, section.armed != null, { open = true }, Modifier.opensOnPress(open) { open = true }.testTag(section.tag), size = TOOL_SIZE)
        }
        Canvas(Modifier.align(Alignment.BottomEnd).padding(CORNER_INSET).size(CORNER_SIZE)) {
            drawPath(
                Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                },
                mark,
            )
        }
        Popover(open, { open = false }, section.label, width = (TOOL_SIZE + TOOL_GAP) * section.tools + POPOVER_INSET) {
            Row(horizontalArrangement = Arrangement.spacedBy(TOOL_GAP), verticalAlignment = Alignment.CenterVertically) { section.content() }
        }
    }
}

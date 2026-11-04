package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_part_areas
import dev.chieppa.squash.uidesign.generated.shell_part_joints
import dev.chieppa.squash.uidesign.generated.shell_part_mirrored
import dev.chieppa.squash.uidesign.generated.shell_part_off
import dev.chieppa.squash.uidesign.generated.shell_part_on
import dev.chieppa.squash.uidesign.generated.shell_part_pair
import dev.chieppa.squash.uidesign.generated.shell_part_range
import dev.chieppa.squash.uidesign.generated.shell_part_tool_knows
import dev.chieppa.squash.uidesign.generated.shell_part_you_draw
import dev.chieppa.squash.uidesign.generated.shell_view_front
import dev.chieppa.squash.uidesign.generated.shell_view_side
import dev.chieppa.squash.uidesign.generated.shell_view_top
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.stringResource

/** A view's name. */
@Composable
fun viewLabel(view: View): String = stringResource(
    when (view) {
        View.SIDE -> Res.string.shell_view_side
        View.TOP -> Res.string.shell_view_top
        View.FRONT -> Res.string.shell_view_front
    },
)

/** The template's views: with a part's [outlines], lit where it has one; without them, lit where a person draws. */
@Composable
fun YouDrawSection(template: PartTemplate, outlines: Map<View, OutlineCurve>?) {
    DrawerSection(stringResource(Res.string.shell_part_you_draw)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (view in VIEW_ORDER) {
                val sets = template.views[view] ?: continue
                val drawn = outlines != null && view in outlines
                val lit = if (outlines != null) drawn else sets.isDrawnByHand()
                Chip(
                    viewLabel(view),
                    Modifier.testTag(ShellTags.viewChip(view.name)),
                    kind = if (lit) ChipKind.On else ChipKind.Default,
                    icon = if (drawn) StretchIcons.Check else null,
                )
            }
        }
    }
}

/** What the template decides for you: its parameters' limits, its joints and, on a [paired] socket, the mirror. */
@Composable
fun ToolKnowsSection(template: PartTemplate, paired: Boolean) {
    DrawerSection(stringResource(Res.string.shell_part_tool_knows)) {
        for (row in parameterRows(template)) {
            val spans = row.spans.map { span ->
                if (span.start == span.endInclusive) formatNumber(span.start)
                else stringResource(Res.string.shell_part_range, formatNumber(span.start), formatNumber(span.endInclusive))
            }
            val value = when {
                row.isFlag -> stringResource(if (row.value != 0f) Res.string.shell_part_on else Res.string.shell_part_off)
                spans.isNotEmpty() -> spans.joinToString(", ")
                else -> formatNumber(row.value)
            }
            FactRow(displayName(row.name), value)
        }
        val joints = template.blocks.flatMap { it.joints() }.map { it.name }
        if (joints.isNotEmpty()) FactRow(stringResource(Res.string.shell_part_joints), joints.joinToString(", "))
        if (paired) FactRow(stringResource(Res.string.shell_part_pair), stringResource(Res.string.shell_part_mirrored))
    }
}

/** The template's tagged areas, when it has any. */
@Composable
fun AreasSection(template: PartTemplate) {
    val areas = template.blocks.flatMap { it.areas() }
    if (areas.isEmpty()) return
    DrawerSection(stringResource(Res.string.shell_part_areas)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            areas.forEach { Chip(displayName(it.name)) }
        }
    }
}

@Composable
internal fun FactRow(label: String, value: String) {
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        BasicText(value, style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
    }
}

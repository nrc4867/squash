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
import dev.chieppa.squash.character.catalog.VIEW_ORDER
import dev.chieppa.squash.character.catalog.isDrawnByHand
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind

/** A chip for each of the template's views, lit where a person draws it. */
@Composable
fun YouDrawChips(template: PartTemplate) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (view in VIEW_ORDER) {
            val sets = template.views[view] ?: continue
            Chip(viewLabel(view), Modifier.testTag(ShellTags.viewChip(view.name)), kind = if (sets.isDrawnByHand()) ChipKind.On else ChipKind.Default)
        }
    }
}

/** How many joints the template's blocks hold. */
fun PartTemplate.jointCount(): Int = blocks.sumOf { it.joints().size }

@Composable
internal fun FactRow(label: String, value: String) {
    SettingRow(label) {
        Spacer(Modifier.weight(1f))
        BasicText(value, style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkMuted)), maxLines = 1)
    }
}

package dev.chieppa.squash.screens.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_part_preset
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Chip
import dev.chieppa.stretch.ui.ChipKind
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** How near a parameter must lie to a preset's value for the part to count as wearing that preset. */
private const val SAME_VALUE = 1e-4f

/**
 * A template's presets as chips, the one the part's parameters match lit: a press sets every value the preset holds in one step, to this
 * side alone with [thisSide]. Nothing for a template with none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetRow(session: CharacterSession, part: PartId, template: PartTemplate, params: Map<String, Float>, thisSide: Boolean) {
    if (template.presets.isEmpty()) return
    val values = template.defaults + params
    SettingRow(stringResource(Res.string.shell_part_preset)) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            template.presets.forEach { (name, preset) ->
                val worn = preset.all { (key, value) -> values[key]?.let { abs(it - value) < SAME_VALUE } == true }
                Chip(
                    displayName(name),
                    Modifier.testTag(ShellTags.preset(name)),
                    kind = if (worn) ChipKind.On else ChipKind.Default,
                    onClick = { session.send(SetParameters(part, preset, thisSide)) },
                )
            }
        }
    }
}

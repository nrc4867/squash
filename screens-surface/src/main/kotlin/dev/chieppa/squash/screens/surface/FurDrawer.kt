package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.SetFurRule
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.fur_length
import dev.chieppa.squash.uidesign.generated.fur_pixels_value
import dev.chieppa.squash.uidesign.generated.fur_section
import dev.chieppa.squash.uidesign.generated.fur_spacing
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private val READOUT_WIDTH = 72.dp
private val SPACING_RANGE = 4f..48f
private val LENGTH_RANGE = 3f..48f

/** Test tags for the Fur section's controls. */
object FurDrawerTags {
    const val SPACING = "fur.spacing"
    const val LENGTH = "fur.length"
}

/** The Lines drawer's Fur section for the selected part, which holds the rule: Spacing and Length, each drag one step. */
@Composable
internal fun FurSection(state: AppState, session: CharacterSession) {
    val document by session.document.collectAsState()
    val selected by state.selectedPart.collectAsState()
    val part = selected?.let { document.character.part(it) } ?: return
    val rule = document.character.resolved(part).surface.fur ?: FurRule()
    val thisSide = state.thisSide && part.pair != null
    val drag = rememberCommandDrag(session)
    val spacing = stringResource(Res.string.fur_spacing)
    val length = stringResource(Res.string.fur_length)
    fun send(next: FurRule) = drag.update(SetFurRule(part.id, next, thisSide))
    DrawerSection(stringResource(Res.string.fur_section), divider = false) {
        SettingRow(spacing) {
            RuleSlider(rule.spacingPixels, SPACING_RANGE, spacing, FurDrawerTags.SPACING, drag::finish) { send(rule.copy(spacingPixels = it)) }
            Readout(stringResource(Res.string.fur_pixels_value, rule.spacingPixels.roundToInt()), READOUT_WIDTH)
        }
        SettingRow(length) {
            RuleSlider(rule.lengthPixels, LENGTH_RANGE, length, FurDrawerTags.LENGTH, drag::finish) { send(rule.copy(lengthPixels = it)) }
            Readout(stringResource(Res.string.fur_pixels_value, rule.lengthPixels.roundToInt()), READOUT_WIDTH)
        }
    }
}

@Composable
private fun RowScope.RuleSlider(value: Float, range: ClosedFloatingPointRange<Float>, label: String, tag: String, onFinished: () -> Unit, onChange: (Float) -> Unit) {
    Slider(value, onChange, label, Modifier.weight(1f).testTag(tag), valueRange = range, onValueChangeFinished = onFinished)
}

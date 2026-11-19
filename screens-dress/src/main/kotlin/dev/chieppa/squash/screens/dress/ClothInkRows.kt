package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.MOST_CLOTH_INK_WEIGHT
import dev.chieppa.squash.document.MOST_HEM_ROLL
import dev.chieppa.squash.document.SetGarmentHemRoll
import dev.chieppa.squash.document.SetGarmentInkWeight
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_hem_roll
import dev.chieppa.squash.uidesign.generated.dress_ink_weight
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100f

/** Thousandths of a body unit, as the Hem roll row reads out. */
private const val THOUSANDTHS = 1000f

/**
 * Ink weight: the weight of the garment's silhouette and inked edges as a share of the skin's; and Hem roll: how far its hems, neck
 * openings and cuffs roll out past the cloth, in thousandths of a body unit.
 */
@Composable
internal fun ClothInkRows(session: CharacterSession, garment: Garment) {
    val gesture = remember(session) { SliderGesture(session) }
    val weight = stringResource(Res.string.dress_ink_weight)
    SettingRow(weight) {
        Slider(garment.inkWeight, { gesture.change(SetGarmentInkWeight(garment.id, it)) }, weight, Modifier.weight(1f).testTag(DressTags.INK_WEIGHT), 0f..MOST_CLOTH_INK_WEIGHT, gesture::done)
        Readout(stringResource(Res.string.edit_percent, (garment.inkWeight * PERCENT).roundToInt()))
    }
    val roll = stringResource(Res.string.dress_hem_roll)
    SettingRow(roll) {
        Slider(garment.hemRoll, { gesture.change(SetGarmentHemRoll(garment.id, it)) }, roll, Modifier.weight(1f).testTag(DressTags.HEM_ROLL), 0f..MOST_HEM_ROLL, gesture::done)
        Readout((garment.hemRoll * THOUSANDTHS).roundToInt().toString())
    }
}

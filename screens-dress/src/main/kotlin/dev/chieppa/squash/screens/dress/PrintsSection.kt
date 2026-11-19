package dev.chieppa.squash.screens.dress

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.SetGarmentMark
import dev.chieppa.squash.document.SetGarmentRegion
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.surface.paint.SlotChoice
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_print_color
import dev.chieppa.squash.uidesign.generated.dress_print_ink
import dev.chieppa.squash.uidesign.generated.dress_print_remove
import dev.chieppa.squash.uidesign.generated.dress_print_weight
import dev.chieppa.squash.uidesign.generated.dress_prints
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Icon
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val ICON = 12.dp

private const val PERCENT = 100f

/** The weights Print line offers, as shares of the line rule's. */
private val WEIGHTS = 0.2f..2f

/** What [garment]'s cloth prints: a row for each region and each line, with its cross taking it away; no section while it prints nothing. */
@Composable
internal fun PrintsSection(session: CharacterSession, garment: Garment) {
    if (garment.regions.isEmpty() && garment.marks.isEmpty()) return
    DrawerSection(stringResource(Res.string.dress_prints)) {
        garment.regions.forEach { region ->
            PrintRow(SquashIcons.PrintRegion, region.id.value) { session.send(SetGarmentRegion(garment.id, region.id, null)) }
        }
        garment.marks.forEach { mark ->
            PrintRow(SquashIcons.PrintLine, mark.id.value) { session.send(SetGarmentMark(garment.id, mark.id, null)) }
        }
    }
}

@Composable
private fun PrintRow(icon: ImageVector, id: String, remove: () -> Unit) {
    ListRow(false, {}, Modifier.testTag(DressTags.print(id))) {
        Icon(icon, Stretch.colors.inkMuted, size = ICON)
        RowText(id, Modifier.weight(1f))
        IconButton(StretchIcons.Close, stringResource(Res.string.dress_print_remove, id), remove, Modifier.testTag(DressTags.removePrint(id)))
    }
}

/**
 * The print tools' options: the color a print takes, the garment's own for a region and the ink's for a line until one is picked; then
 * Ink border for Print region, or the line's Weight for Print line.
 */
@Composable
internal fun PrintOptions(state: AppState, session: CharacterSession, region: Boolean) {
    val document by session.document.collectAsState()
    val slot by state.dress.printSlot.collectAsState()
    val ink by state.dress.printInk.collectAsState()
    val weight by state.dress.printWeight.collectAsState()
    val picked by state.dress.garment.collectAsState()
    val garment = picked?.let(document.character.wardrobe::garment)
    val title = stringResource(Res.string.dress_print_color)
    SettingRow(title) {
        Spacer(Modifier.weight(1f))
        SlotChoice(
            document.character.palette,
            slot ?: if (region) garment?.color else null,
            title,
            DressTags.PRINT_COLOR,
            { DressTags.PRINT_COLOR + "." + it.value },
            { state.dress.printSlot.value = it },
            editor = session,
        )
    }
    if (region) {
        SwitchRow(stringResource(Res.string.dress_print_ink), ink, Modifier.testTag(DressTags.PRINT_INK)) { state.dress.printInk.value = it }
    } else {
        val label = stringResource(Res.string.dress_print_weight)
        SettingRow(label) {
            Slider(weight, { state.dress.printWeight.value = it }, label, Modifier.weight(1f).testTag(DressTags.PRINT_WEIGHT), WEIGHTS)
            Readout(stringResource(Res.string.edit_percent, (weight * PERCENT).roundToInt()))
        }
    }
}

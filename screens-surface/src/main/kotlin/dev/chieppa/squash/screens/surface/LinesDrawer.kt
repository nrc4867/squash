package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.SetLineRules
import dev.chieppa.squash.document.SetLineWeightAmount
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.lines.MAX_WIDTH_SIZE
import dev.chieppa.squash.screens.lines.MIN_WIDTH_SIZE
import dev.chieppa.squash.screens.lines.PickedWeight
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.squash.uidesign.generated.lines_amount
import dev.chieppa.squash.uidesign.generated.lines_amount_value
import dev.chieppa.squash.uidesign.generated.lines_body_surface
import dev.chieppa.squash.uidesign.generated.lines_interior
import dev.chieppa.squash.uidesign.generated.lines_lives_on
import dev.chieppa.squash.uidesign.generated.lines_px
import dev.chieppa.squash.uidesign.generated.lines_rules
import dev.chieppa.squash.uidesign.generated.lines_silhouette
import dev.chieppa.squash.uidesign.generated.lines_size
import dev.chieppa.squash.uidesign.generated.lines_size_value
import dev.chieppa.squash.uidesign.generated.lines_taper
import dev.chieppa.squash.uidesign.generated.lines_width_tool
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.NumberField
import dev.chieppa.stretch.ui.Slider
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100f
private const val WEIGHT_STEP = 0.5f
private val READOUT_WIDTH = 48.dp
private val WEIGHT_RANGE = 0f..40f

/** Test tags for the Lines drawer's controls. */
object LinesDrawerTags {
    const val SILHOUETTE = "lines.silhouette"
    const val INTERIOR = "lines.interior"
    const val TAPER = "lines.taper"
    const val SIZE = "lines.size"
    const val AMOUNT = "lines.amount"
    const val LIVES_ON = "lines.livesOn"
}

/** The command setting [picked]'s amount, or null when an undo or an erase has taken it from where an edit would land. */
fun amountCommand(character: Character, picked: PickedWeight, amount: Float, thisSide: Boolean): SetLineWeightAmount? {
    val part = character.part(picked.part) ?: return null
    val side = thisSide && part.pair != null
    if (character.editBase(part, side).surface.lineWeights.none { it.id == picked.id }) return null
    return SetLineWeightAmount(picked.part, picked.id, amount, side)
}

/** One line weight in pixels, to a tenth. */
fun weightLabel(pixels: Float): String = "%.1f".format(java.util.Locale.ROOT, pixels)

/** The Lines drawer: the rules that weigh the character's lines, and what the Line width tool lays down. */
@Composable
fun LinesDrawer(state: AppState) {
    val session by state.session.collectAsState()
    val open = session ?: return
    RulesSection(open)
    WidthToolSection(state, open)
}

/** One drag on a control is one undo step: the values it passes through are previews of a single command. */
@Composable
private fun rememberCommandDrag(session: CharacterSession): CommandDrag {
    val drag = remember(session) { CommandDrag(session.history) }
    DisposableEffect(drag) { onDispose { drag.finish() } }
    return drag
}

private class CommandDrag(private val history: History<SquashDocument>) {
    private var gesture by mutableStateOf<History<SquashDocument>.Transaction?>(null)

    fun update(command: DocumentCommand) = (gesture ?: history.begin().also { gesture = it }).update(command)

    fun finish() {
        gesture?.commit()
        gesture = null
    }
}

@Composable
private fun RulesSection(session: CharacterSession) {
    val document by session.document.collectAsState()
    val rules = document.character.lineRules
    val drag = rememberCommandDrag(session)
    val silhouette = stringResource(Res.string.lines_silhouette)
    val interior = stringResource(Res.string.lines_interior)
    val taper = stringResource(Res.string.lines_taper)
    val px = stringResource(Res.string.lines_px)
    fun send(next: LineRules) = session.send(SetLineRules(next))
    DrawerSection(stringResource(Res.string.lines_rules)) {
        SettingRow(silhouette) {
            Spacer(Modifier.weight(1f))
            NumberField(rules.silhouette, { send(rules.copy(silhouette = it)) }, silhouette, Modifier.testTag(LinesDrawerTags.SILHOUETTE), unit = px, step = WEIGHT_STEP, range = WEIGHT_RANGE, format = ::weightLabel)
        }
        SettingRow(interior) {
            Spacer(Modifier.weight(1f))
            NumberField(rules.interior, { send(rules.copy(interior = it)) }, interior, Modifier.testTag(LinesDrawerTags.INTERIOR), unit = px, step = WEIGHT_STEP, range = WEIGHT_RANGE, format = ::weightLabel)
        }
        SettingRow(taper) {
            Slider(rules.taper, { drag.update(SetLineRules(rules.copy(taper = it))) }, taper, Modifier.weight(1f).testTag(LinesDrawerTags.TAPER), onValueChangeFinished = drag::finish)
            Readout(stringResource(Res.string.edit_percent, (rules.taper * PERCENT).roundToInt()))
        }
    }
}

@Composable
private fun WidthToolSection(state: AppState, session: CharacterSession) {
    val lines = state.lines
    val drag = rememberCommandDrag(session)
    val size = stringResource(Res.string.lines_size)
    val amount = stringResource(Res.string.lines_amount)
    val thisSide = state.thisSide
    DrawerSection(stringResource(Res.string.lines_width_tool), divider = false) {
        SettingRow(size) {
            Slider(lines.size, { lines.size = it }, size, Modifier.weight(1f).testTag(LinesDrawerTags.SIZE), MIN_WIDTH_SIZE..MAX_WIDTH_SIZE)
            Readout(stringResource(Res.string.lines_size_value, lines.size.roundToInt()))
        }
        SettingRow(amount) {
            Slider(
                lines.amount,
                { value ->
                    lines.amount = value
                    lines.picked?.let { amountCommand(session.document.value.character, it, value, thisSide) }?.let(drag::update)
                },
                amount,
                Modifier.weight(1f).testTag(LinesDrawerTags.AMOUNT),
                onValueChangeFinished = drag::finish,
            )
            Readout(stringResource(Res.string.lines_amount_value, (lines.amount * PERCENT).roundToInt()))
        }
        SettingRow(stringResource(Res.string.lines_lives_on)) {
            Spacer(Modifier.weight(1f))
            BasicText(stringResource(Res.string.lines_body_surface), Modifier.testTag(LinesDrawerTags.LIVES_ON), style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.inkSubtle)))
        }
    }
}

@Composable
private fun Readout(text: String) {
    BasicText(text, Modifier.width(READOUT_WIDTH), style = Stretch.type.body.merge(TextStyle(color = Stretch.colors.inkSubtle)), maxLines = 1)
}

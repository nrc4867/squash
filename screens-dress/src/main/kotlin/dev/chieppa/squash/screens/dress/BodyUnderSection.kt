package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_body_under
import dev.chieppa.squash.uidesign.generated.dress_change_hide
import dev.chieppa.squash.uidesign.generated.dress_change_redraw
import dev.chieppa.squash.uidesign.generated.dress_change_swap
import dev.chieppa.squash.uidesign.generated.dress_change_through
import dev.chieppa.squash.uidesign.generated.dress_cuff
import dev.chieppa.squash.uidesign.generated.dress_cuff_size
import dev.chieppa.squash.uidesign.generated.dress_holes
import dev.chieppa.squash.uidesign.generated.dress_ink_cuff
import dev.chieppa.squash.uidesign.generated.dress_swap_to
import dev.chieppa.squash.uidesign.generated.dress_through
import dev.chieppa.stretch.frame.Dropdown
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.frame.ListRow
import dev.chieppa.stretch.frame.RowText
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import dev.chieppa.stretch.ui.formatNumber
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What a garment can do to a body part under it, as the Body under section offers it. */
enum class ChangeKind(val label: StringResource) {
    HIDE(Res.string.dress_change_hide),
    THROUGH(Res.string.dress_change_through),
    REDRAW(Res.string.dress_change_redraw),
    SWAP(Res.string.dress_change_swap),
}

/** The kind of [change], or null for none. */
fun kindOf(change: BodyPartChange?): ChangeKind? = when (change) {
    null -> null
    BodyPartChange.Hide -> ChangeKind.HIDE
    is BodyPartChange.Through -> ChangeKind.THROUGH
    is BodyPartChange.Redraw -> ChangeKind.REDRAW
    is BodyPartChange.Swap -> ChangeKind.SWAP
}

/**
 * The change a press on [kind] makes to [part] under a garment that holds [held] for it: pressing the kind it holds takes the change
 * away; Redraw starts from the part's own data, and Swap from [swapTo], the first other template its socket takes.
 */
fun changeFor(kind: ChangeKind, held: BodyPartChange?, character: Character, part: PartInstance, swapTo: TemplateRef?): BodyPartChange? = when {
    kindOf(held) == kind -> null
    kind == ChangeKind.HIDE -> BodyPartChange.Hide
    kind == ChangeKind.THROUGH -> BodyPartChange.Through()
    kind == ChangeKind.REDRAW -> BodyPartChange.Redraw(character.resolved(part))
    else -> swapTo?.let { BodyPartChange.Swap(it) }
}

/** The templates [part] can be swapped to: those its socket takes, other than its own, at their newest versions. */
@Composable
private fun swapChoices(state: AppState, part: PartInstance, assembled: AssembledCharacter?): List<TemplateRef> {
    val takes = assembled?.part(part.id)?.socketSpec?.takes.orEmpty()
    return takes.filter { it != part.template.id }.mapNotNull { state.templates.template(it)?.ref }
}

/**
 * Body under the garment: a row per part it covers, comes out of or changes, with Hide, Through, Redraw and Swap, none pressed while
 * the garment leaves the part as it is; Through shows its cuff, the cuff's size and its ink, Swap the template it swaps to. Then Holes,
 * the parts that come out through it.
 */
@Composable
internal fun BodyUnderSection(state: AppState, session: CharacterSession, character: Character, garment: Garment) {
    val assembled by remember(session) { session.assembly.output }.collectAsState()
    val under = character.partsUnder(garment, assembled)
    DrawerSection(stringResource(Res.string.dress_body_under, garment.name)) {
        under.forEach { row -> PartChangeRows(state, session, character, garment, row, assembled) }
    }
    val holes = under.filter { it.through }
    if (holes.isEmpty()) return
    DrawerSection(stringResource(Res.string.dress_holes)) {
        holes.forEach { hole ->
            ListRow(state.selectedPart.collectAsState().value == hole.part.id, { state.selectedPart.value = hole.part.id }, Modifier.testTag(DressTags.hole(hole.part.id))) {
                RowText(hole.part.socket?.socket?.let(::displayName) ?: dressPartName(state, hole.part), Modifier.weight(1f))
                RowText(stringResource(Res.string.dress_through), subtle = true)
            }
        }
    }
}

@Composable
private fun PartChangeRows(state: AppState, session: CharacterSession, character: Character, garment: Garment, row: PartUnder, assembled: AssembledCharacter?) {
    val part = row.part
    val swaps = swapChoices(state, part, assembled)
    SettingRow(dressPartName(state, part)) {}
    val labels = ChangeKind.entries.associateWith { stringResource(it.label) }
    Segmented(
        listOf<ChangeKind?>(ChangeKind.HIDE, ChangeKind.THROUGH, ChangeKind.REDRAW, ChangeKind.SWAP),
        kindOf(row.change),
        { kind ->
            if (kind != null) session.send(SetBodyPartChange(garment.id, part.id, changeFor(kind, row.change, character, part, swaps.firstOrNull())))
        },
        { it?.let(labels::getValue).orEmpty() },
        Modifier.testTag(DressTags.change(part.id)),
        fill = true,
        compact = true,
        enabled = { it != ChangeKind.SWAP || swaps.isNotEmpty() || row.change is BodyPartChange.Swap },
    )
    when (val change = row.change) {
        is BodyPartChange.Through -> ThroughRows(session, garment, part, change)
        is BodyPartChange.Swap -> {
            val choices = (swaps + change.template).distinct()
            val label = stringResource(Res.string.dress_swap_to)
            SettingRow(label) {
                Dropdown(
                    choices,
                    change.template,
                    { session.send(SetBodyPartChange(garment.id, part.id, change.copy(template = it))) },
                    { displayName(it.id.value) },
                    label,
                    Modifier.weight(1f).testTag(DressTags.swap(part.id)),
                )
            }
        }
        else -> Unit
    }
}

/** A part coming through: its cuff, the cuff's size and its ink. */
@Composable
private fun ThroughRows(session: CharacterSession, garment: Garment, part: PartInstance, through: BodyPartChange.Through) {
    val gesture = remember(session) { SliderGesture(session) }
    fun set(next: BodyPartChange.Through) = session.send(SetBodyPartChange(garment.id, part.id, next))
    SwitchRow(stringResource(Res.string.dress_cuff), through.cuff, Modifier.testTag(DressTags.cuff(part.id))) { set(through.copy(cuff = it)) }
    val size = stringResource(Res.string.dress_cuff_size)
    SettingRow(size) {
        Slider(
            through.cuffSize,
            { gesture.change(SetBodyPartChange(garment.id, part.id, through.copy(cuffSize = it))) },
            size,
            Modifier.weight(1f).testTag(DressTags.cuffSize(part.id)),
            onValueChangeFinished = gesture::done,
        )
        Readout(formatNumber(through.cuffSize))
    }
    SwitchRow(stringResource(Res.string.dress_ink_cuff), through.inkCuff, Modifier.testTag(DressTags.inkCuff(part.id))) { set(through.copy(inkCuff = it)) }
}

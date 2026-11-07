package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.catalog.displayName
import dev.chieppa.squash.character.catalog.shownName
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PaletteSlot
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SetSlotLabel
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_slot_add
import dev.chieppa.squash.uidesign.generated.color_slot_all
import dev.chieppa.squash.uidesign.generated.color_slot_hidden
import dev.chieppa.squash.uidesign.generated.color_slot_new
import dev.chieppa.squash.uidesign.generated.color_slot_remove
import dev.chieppa.squash.uidesign.generated.color_slot_rename
import dev.chieppa.stretch.frame.Popover
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import dev.chieppa.stretch.ui.Swatch
import dev.chieppa.stretch.ui.Tooltip
import dev.chieppa.stretch.ui.holdsTyping
import dev.chieppa.stretch.ui.opensOnPress
import org.jetbrains.compose.resources.stringResource

internal const val NEW_SLOT_GREY = 0.6f
private const val SHELF_LINES = 2
private val SWATCH_SIZE = 30.dp
private val SLOT_GAP = 6.dp
private val NAME_INSET = 6.dp

/** The palette's swatches, up to two lines of them, with the rest behind a button that opens every slot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SlotSwatches(state: AppState, palette: Palette, current: SlotName?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perLine = ((maxWidth + SLOT_GAP) / (SWATCH_SIZE + SLOT_GAP)).toInt().coerceAtLeast(1)
        val whole = perLine * SHELF_LINES
        val capacity = if (palette.slots.size <= whole) whole else whole - 1
        val names = palette.slots.map { it.name }
        val shown = state.paint.shelf.shown(names, current, capacity)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SLOT_GAP), verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
            shown.forEach { name ->
                palette.slots.firstOrNull { it.name == name }?.let { SlotSwatch(it, it.name == current) { state.paint.slot = it.name } }
            }
            if (names.size > shown.size) AllSlots(state, palette, current, names.size - shown.size)
        }
    }
}

@Composable
private fun SlotSwatch(slot: PaletteSlot, selected: Boolean, onPick: () -> Unit) {
    Swatch(Color(slot.color.red, slot.color.green, slot.color.blue), slot.shownName, selected, onPick, Modifier.testTag(PaintTags.slot(slot.name.value)))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllSlots(state: AppState, palette: Palette, current: SlotName?, hidden: Int) {
    var open by remember { mutableStateOf(false) }
    val c = Stretch.colors
    val label = stringResource(Res.string.color_slot_all)
    val shape = Stretch.shapes.segShape
    Box {
        Tooltip(label, emptyList()) {
            Box(
                Modifier
                    .testTag(PaintTags.ALL_SLOTS)
                    .opensOnPress(open) { open = true }
                    .size(SWATCH_SIZE)
                    .padding(NAME_INSET / 2)
                    .background(c.surfaceFloat, shape)
                    .border(Stretch.stroke, c.lineControl, shape)
                    .clickable(onClickLabel = label) { open = true },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(stringResource(Res.string.color_slot_hidden, hidden), style = Stretch.type.bodySm.merge(TextStyle(color = c.inkControl)), maxLines = 1)
            }
        }
        Popover(open, { open = false }, label) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP), verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                palette.slots.forEach { slot ->
                    SlotSwatch(slot, slot.name == current) {
                        state.paint.slot = slot.name
                        open = false
                    }
                }
            }
        }
    }
}

/** The slot in use, by the name a double click lets you change, with New color and Delete beside it. Deleting a slot makes the one before it current. */
@Composable
internal fun SlotActions(state: AppState, session: CharacterSession, character: Character, current: SlotName?) {
    val newName = stringResource(Res.string.color_slot_new)
    val palette = character.palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SLOT_GAP), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            palette.slots.firstOrNull { it.name == current }?.let { slot -> SlotName(slot) { session.send(SetSlotLabel(slot.name, it)) } }
        }
        Button(stringResource(Res.string.color_slot_add), {
            val name = freshSlotName(character, newName)
            session.send(SetPaletteSlot(name, SlotColor(NEW_SLOT_GREY, NEW_SLOT_GREY, NEW_SLOT_GREY)))
            state.paint.slot = name
        }, Modifier.testTag(PaintTags.ADD_SLOT), kind = ButtonKind.Quiet, icon = SquashIcons.AddSlot)
        if (current != null) {
            Button(stringResource(Res.string.color_slot_remove), {
                state.paint.slot = slotBefore(palette, current)
                session.send(SetPaletteSlot(current, null))
            }, Modifier.testTag(PaintTags.REMOVE_SLOT), kind = ButtonKind.Quiet, icon = SquashIcons.RemoveSlot)
        }
    }
}

/** The slot in use once [slot] of [palette] is gone: the one before it, or for the first, the one after. */
internal fun slotBefore(palette: Palette, slot: SlotName): SlotName? {
    val names = palette.slots.map { it.name }
    val at = names.indexOf(slot)
    return if (at > 0) names[at - 1] else names.getOrNull(at + 1)
}

/** A slot's name not yet in [character]'s palette, from [base] with a number when it must. */
internal fun freshSlotName(character: Character, base: String): SlotName =
    generateSequence(1) { it + 1 }.map { SlotName(if (it == 1) base else "$base-$it") }.first { character.palette.color(it) == null }

/** [slot]'s name, which a double click opens for typing. Enter or leaving keeps what was typed, Escape what was there, and an empty name goes back to the slot's own. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotName(slot: PaletteSlot, onLabel: (String?) -> Unit) {
    var editing by remember(slot.name) { mutableStateOf(false) }
    val style = Stretch.type.value.merge(TextStyle(color = Stretch.colors.inkStrong))
    if (!editing) {
        val rename = stringResource(Res.string.color_slot_rename)
        Tooltip(rename, emptyList()) {
            BasicText(
                slot.shownName,
                Modifier.fillMaxWidth().testTag(PaintTags.SLOT_NAME).combinedClickable(onClickLabel = rename, onClick = {}, onDoubleClick = { editing = true }),
                style = style,
                maxLines = 1,
            )
        }
        return
    }
    fun finish(typed: String) {
        editing = false
        val name = typed.trim().takeUnless { it.isEmpty() || it == displayName(slot.name.value) }
        if (name != slot.label) onLabel(name)
    }
    var value by remember { mutableStateOf(TextFieldValue(slot.shownName, TextRange(0, slot.shownName.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PaintTags.SLOT_NAME_FIELD)
            .holdsTyping()
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) finish(value.text) }
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    hadFocus = false
                    editing = false
                    true
                } else {
                    false
                }
            }
            .background(Stretch.colors.surfaceFloat, Stretch.shapes.segShape)
            .border(Stretch.stroke, Stretch.colors.lineControl, Stretch.shapes.segShape)
            .padding(horizontal = NAME_INSET, vertical = 2.dp),
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Stretch.colors.inkStrong),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            hadFocus = false
            finish(value.text)
        }),
    )
}

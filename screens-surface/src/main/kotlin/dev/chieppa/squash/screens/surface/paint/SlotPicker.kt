package dev.chieppa.squash.screens.surface.paint

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SetPaletteSlot
import dev.chieppa.squash.document.SlotColor
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.color_picker_eyedropper
import dev.chieppa.squash.uidesign.generated.color_picker_hex
import dev.chieppa.squash.uidesign.generated.color_picker_hue
import dev.chieppa.squash.uidesign.generated.color_picker_invalid_hex
import dev.chieppa.squash.uidesign.generated.color_picker_square
import dev.chieppa.stretch.core.History
import dev.chieppa.stretch.ui.ColorPicker
import org.jetbrains.compose.resources.stringResource

/**
 * The picker that edits [slot] of the palette in place, showing [color]. One drag of the picker is one undo step, however many colors it passes
 * through. The picker reports a change finished as a press turns into a drag as well as at the end, so a held pointer's step closes on its release,
 * and only a change with no pointer down, as a typed hex, closes on the report.
 */
@Composable
internal fun SlotPicker(session: CharacterSession, slot: SlotName, color: Color, modifier: Modifier = Modifier) {
    var gesture by remember { mutableStateOf<History<SquashDocument>.Transaction?>(null) }
    var held by remember { mutableStateOf(false) }
    fun close() {
        gesture?.commit()
        gesture = null
    }
    DisposableEffect(Unit) { onDispose { close() } }
    ColorPicker(
        color,
        { picked ->
            val open = gesture ?: session.history.begin().also { gesture = it }
            open.update(SetPaletteSlot(slot, SlotColor(picked.red, picked.green, picked.blue)))
        },
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                held = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                held = false
                close()
            }
        },
        onChangeFinished = { if (!held) close() },
        hexDescription = stringResource(Res.string.color_picker_hex),
        squareDescription = stringResource(Res.string.color_picker_square),
        hueDescription = stringResource(Res.string.color_picker_hue),
        eyedropperDescription = stringResource(Res.string.color_picker_eyedropper),
        invalidHex = stringResource(Res.string.color_picker_invalid_hex),
    )
}

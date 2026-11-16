package dev.chieppa.squash.screens.dress.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.chieppa.stretch.ui.TextField

/**
 * A name typed in place, which opens with the focus. For a [new] item it opens empty with [name], the automatic one, as its placeholder;
 * otherwise it opens holding [name] all selected, so typing replaces it. Enter, or the focus leaving, keeps what is typed, and Esc drops
 * it. [onKeep] gets the text as typed, empty when nothing was, which keeps [name].
 */
@Composable
internal fun RenameField(name: String, new: Boolean, contentDescription: String, onKeep: (String) -> Unit, onDrop: () -> Unit, modifier: Modifier = Modifier) {
    var value by remember(name, new) { mutableStateOf(if (new) TextFieldValue() else TextFieldValue(name, TextRange(0, name.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    fun finish(keep: Boolean) {
        if (done) return
        done = true
        if (keep) onKeep(value.text) else onDrop()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value,
        { value = it },
        modifier
            .focusRequester(focus)
            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) finish(keep = true) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        finish(keep = true)
                        true
                    }
                    Key.Escape -> {
                        finish(keep = false)
                        true
                    }
                    else -> false
                }
            },
        placeholder = if (new) name else "",
        contentDescription = contentDescription,
    )
}

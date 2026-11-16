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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.chieppa.stretch.ui.TextField

/** A name typed in place: it opens holding [name] with the focus, Enter keeps what is typed and Esc drops it. */
@Composable
internal fun RenameField(name: String, contentDescription: String, onKeep: (String) -> Unit, onDrop: () -> Unit, modifier: Modifier = Modifier) {
    var text by remember(name) { mutableStateOf(name) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        text,
        { text = it },
        modifier.focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> {
                    onKeep(text)
                    true
                }
                Key.Escape -> {
                    onDrop()
                    true
                }
                else -> false
            }
        },
        contentDescription = contentDescription,
    )
}

package dev.chieppa.squash.screens.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import dev.chieppa.squash.character.model.BodyPlan
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.shell_open_failed
import dev.chieppa.squash.uidesign.generated.shell_save_failed
import dev.chieppa.stretch.core.ActionRegistry
import dev.chieppa.stretch.core.Keymap
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.document.FileStorage
import dev.chieppa.stretch.frame.FrameLayout
import dev.chieppa.stretch.frame.ToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

/**
 * What the window's screens share: file handling, the drawer layout, the action registry and the toasts. The keymap starts from
 * what [keys] kept, and [keymapChanged] keeps it again.
 */
@Stable
class Shell(
    val state: AppState,
    val chooser: FileChooser,
    val toasts: ToastState,
    private val scope: CoroutineScope,
    val content: BuildContent,
    private val keys: KeymapStore? = null,
) {
    val registry = ActionRegistry()
    val keymap = Keymap(registry, keys?.load().orEmpty())

    /** Keeps the keymap's changes for the next run. */
    fun keymapChanged() {
        keys?.save(keymap.changes)
    }

    var layout by mutableStateOf(DEFAULT_LAYOUT)
    var palette by mutableStateOf(false)
    var settings by mutableStateOf(false)

    fun newCharacter() {
        state.screen.value = Screen.NEW_CHARACTER
    }

    /** Leaves New character without making one. */
    fun dismissNew() {
        state.screen.value = if (state.session.value != null) Screen.WORKSPACE else Screen.HOME
    }

    fun create(plan: BodyPlan, name: String) = state.whenSaved { state.createCharacter(plan, name) }

    fun close() = state.whenSaved { state.closeCharacter() }

    fun chooseAndOpen() = state.whenSaved {
        scope.launch { chooser.chooseOpen(state.format)?.let(::open) }
    }

    fun open(path: String) = state.whenSaved {
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { FileStorage.read(path) }
                state.open(state.format.decode(bytes), path)
            }.onSuccess {
                state.recent.add(path)
                state.screen.value = Screen.WORKSPACE
            }.onFailure {
                state.recent.remove(path)
                toasts.show(getString(Res.string.shell_open_failed, Path(path).nameWithoutExtension))
            }
        }
    }

    fun save(asNew: Boolean = false) {
        val file = state.file.value ?: return
        val name = state.session.value?.document?.value?.character?.name.orEmpty()
        scope.launch {
            val target = file.path?.takeIf { !asNew && state.format.saves(it) } ?: chooser.chooseSave(state.format, name) ?: return@launch
            runCatching { file.saveTo(target) }
                .onSuccess { state.recent.add(target) }
                .onFailure { toasts.show(getString(Res.string.shell_save_failed, it.message.orEmpty())) }
        }
    }

    /** Takes a layout from the frame, except on New character, which shows every drawer shut. */
    fun changeLayout(next: FrameLayout) {
        if (state.screen.value != Screen.NEW_CHARACTER) layout = next
    }
}
